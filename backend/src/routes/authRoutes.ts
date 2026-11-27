import { Router } from 'express';
import { z } from 'zod';
import { env } from '../config/env.js';
import {
  generateOpaqueToken,
  hashSecret,
  normalizeAccessCode,
} from '../lib/crypto.js';
import { supabase } from '../lib/supabase.js';
import { requireUserAuth } from '../middleware/auth.js';
import { authRateLimiter } from '../middleware/rateLimit.js';
import type { DbAccessCode, DbUser } from '../types/models.js';

export const authRouter = Router();

const activateSchema = z.object({
  access_code: z.string().trim().min(4).max(64),
  device_id: z.string().trim().min(6).max(128),
  platform: z.string().trim().max(32).optional().default('android'),
});

function formatUserResponse(user: DbUser) {
  return {
    id: user.id,
    display_name: user.display_name,
    status: user.status,
    device_bound: user.device_bound,
    created_at: user.created_at,
    last_seen_at: user.last_seen_at,
  };
}

function formatAccessCodeResponse(code: DbAccessCode) {
  return {
    id: code.id,
    masked_code: code.masked_code,
    status: code.status,
    claimed_at: code.claimed_at,
    expires_at: code.expires_at,
    single_user_locked: code.single_user_locked,
  };
}

/**
 * POST /api/auth/activate
 * Atomically verifies and claims a single-user access code via Supabase RPC `claim_access_code`.
 * Rejects invalid (404), disabled/expired (403), and already-claimed (409) codes.
 */
authRouter.post('/activate', authRateLimiter, async (req, res) => {
  const parsed = activateSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({
      code: 'INVALID_REQUEST',
      message: 'A valid access_code and device_id are required.',
    });
    return;
  }

  try {
    const normalized = normalizeAccessCode(parsed.data.access_code);
    const codeHash = hashSecret(normalized);

    const { data: rpcRows, error: rpcError } = await supabase.rpc('claim_access_code', {
      p_code_hash: codeHash,
      p_device_id: parsed.data.device_id,
      p_platform: parsed.data.platform,
    });

    if (rpcError) {
      console.error('claim_access_code RPC error:', rpcError);
      res.status(500).json({
        code: 'ACTIVATION_ERROR',
        message: 'Unable to validate access code right now.',
      });
      return;
    }

    const result = Array.isArray(rpcRows) ? rpcRows[0] : rpcRows;
    if (!result || result.outcome !== 'OK') {
      const errCode = result?.error_code || 'INVALID_CODE';
      const errMessage = result?.error_message || 'Access code activation rejected.';
      const status =
        errCode === 'INVALID_CODE'
          ? 404
          : errCode === 'CODE_ALREADY_CLAIMED'
            ? 409
            : 403;

      res.status(status).json({
        code: errCode,
        message: errMessage,
      });
      return;
    }

    const userId = result.user_id as string;
    const accessCodeId = result.access_code_id as string;

    // Issue opaque high-entropy bearer session token; store only its keyed hash
    const rawToken = generateOpaqueToken();
    const tokenHash = hashSecret(rawToken);
    const expiresAt = new Date(
      Date.now() + env.USER_SESSION_TTL_HOURS * 60 * 60 * 1000
    ).toISOString();

    const { error: sessionInsertError } = await supabase.from('user_sessions').insert({
      user_id: userId,
      access_code_id: accessCodeId,
      token_hash: tokenHash,
      device_id: parsed.data.device_id,
      platform: parsed.data.platform,
      expires_at: expiresAt,
    });

    if (sessionInsertError) {
      console.error('Failed to persist user session:', sessionInsertError);
      res.status(500).json({
        code: 'SESSION_CREATE_FAILED',
        message: 'Could not initialize user session.',
      });
      return;
    }

    const [{ data: user }, { data: accessCode }] = await Promise.all([
      supabase.from('users').select('*').eq('id', userId).single<DbUser>(),
      supabase.from('access_codes').select('*').eq('id', accessCodeId).single<DbAccessCode>(),
    ]);

    if (!user || !accessCode) {
      res.status(500).json({
        code: 'ACCOUNT_LOAD_ERROR',
        message: 'Account was activated but profile could not be loaded.',
      });
      return;
    }

    res.status(200).json({
      authenticated: true,
      session_token: rawToken,
      user: formatUserResponse(user),
      access_code: formatAccessCodeResponse(accessCode),
    });
  } catch (err) {
    console.error('POST /api/auth/activate error:', err);
    res.status(500).json({
      code: 'INTERNAL_ERROR',
      message: 'Unexpected error while activating access code.',
    });
  }
});

/**
 * GET /api/auth/session
 * Verifies the active Bearer session token and returns current user + access code status.
 */
authRouter.get('/session', requireUserAuth, (req, res) => {
  const auth = req.userAuth!;
  res.status(200).json({
    authenticated: true,
    session_token: null,
    user: formatUserResponse(auth.user),
    access_code: formatAccessCodeResponse(auth.accessCode),
  });
});

/**
 * POST /api/auth/logout
 * Revokes the authenticated session token server-side.
 */
authRouter.post('/logout', requireUserAuth, async (req, res) => {
  try {
    const auth = req.userAuth!;
    await supabase
      .from('user_sessions')
      .update({ revoked_at: new Date().toISOString() })
      .eq('id', auth.sessionId);

    res.status(204).send();
  } catch (err) {
    console.error('POST /api/auth/logout error:', err);
    res.status(500).json({
      code: 'LOGOUT_ERROR',
      message: 'Failed to revoke session.',
    });
  }
});
