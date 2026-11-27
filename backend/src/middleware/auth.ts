import type { NextFunction, Request, Response } from 'express';
import { hashSecret } from '../lib/crypto.js';
import { supabase } from '../lib/supabase.js';
import type { DbAccessCode, DbUser } from '../types/models.js';

export interface AuthenticatedUserContext {
  sessionId: string;
  tokenHash: string;
  user: DbUser;
  accessCode: DbAccessCode;
}

export interface AuthenticatedAdminContext {
  sessionId: string;
  adminEmail: string;
}

declare global {
  namespace Express {
    interface Request {
      userAuth?: AuthenticatedUserContext;
      adminAuth?: AuthenticatedAdminContext;
    }
  }
}

function extractBearerToken(req: Request): string | null {
  const header = req.header('authorization');
  if (!header) return null;
  const match = header.match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || null;
}

/**
 * Validates user Bearer session tokens against `user_sessions`,
 * verifying active user status and non-disabled/non-expired access code on every request.
 */
export async function requireUserAuth(
  req: Request,
  res: Response,
  next: NextFunction
): Promise<void> {
  try {
    const rawToken = extractBearerToken(req);
    if (!rawToken) {
      res.status(401).json({
        code: 'UNAUTHORIZED',
        message: 'Authentication required. Please sign in with your access code.',
      });
      return;
    }

    const tokenHash = hashSecret(rawToken);
    const { data: sessionRow, error: sessionErr } = await supabase
      .from('user_sessions')
      .select('id, user_id, access_code_id, expires_at, revoked_at')
      .eq('token_hash', tokenHash)
      .is('revoked_at', null)
      .maybeSingle();

    if (sessionErr || !sessionRow) {
      res.status(401).json({
        code: 'SESSION_INVALID',
        message: 'Session is invalid or has been signed out.',
      });
      return;
    }

    if (new Date(sessionRow.expires_at).getTime() <= Date.now()) {
      await supabase
        .from('user_sessions')
        .update({ revoked_at: new Date().toISOString() })
        .eq('id', sessionRow.id);
      res.status(401).json({
        code: 'SESSION_EXPIRED',
        message: 'Your session has expired. Please sign in again.',
      });
      return;
    }

    const [{ data: user }, { data: accessCode }] = await Promise.all([
      supabase.from('users').select('*').eq('id', sessionRow.user_id).maybeSingle<DbUser>(),
      supabase
        .from('access_codes')
        .select('*')
        .eq('id', sessionRow.access_code_id)
        .maybeSingle<DbAccessCode>(),
    ]);

    if (!user || user.status !== 'active') {
      res.status(401).json({
        code: 'ACCOUNT_INACTIVE',
        message: 'This account is suspended or revoked.',
      });
      return;
    }

    if (!accessCode || accessCode.status === 'disabled' || accessCode.status === 'expired') {
      res.status(401).json({
        code: 'CODE_REVOKED',
        message: 'Your access code is no longer active.',
      });
      return;
    }

    if (accessCode.expires_at && new Date(accessCode.expires_at).getTime() <= Date.now()) {
      await supabase
        .from('access_codes')
        .update({ status: 'expired' })
        .eq('id', accessCode.id);
      res.status(401).json({
        code: 'CODE_EXPIRED',
        message: 'Your access code has expired.',
      });
      return;
    }

    // Best-effort last_used_at refresh without delaying media requests
    void supabase
      .from('user_sessions')
      .update({ last_used_at: new Date().toISOString() })
      .eq('id', sessionRow.id);

    req.userAuth = {
      sessionId: sessionRow.id,
      tokenHash,
      user,
      accessCode,
    };

    next();
  } catch (err) {
    console.error('User auth middleware error:', err);
    res.status(500).json({
      code: 'AUTH_ERROR',
      message: 'Unable to validate session at this time.',
    });
  }
}

/**
 * Validates admin Bearer tokens strictly against the isolated `admin_sessions` table.
 * User session tokens are NEVER accepted on admin routes.
 */
export async function requireAdminAuth(
  req: Request,
  res: Response,
  next: NextFunction
): Promise<void> {
  try {
    const rawToken = extractBearerToken(req);
    if (!rawToken) {
      res.status(401).json({
        code: 'ADMIN_UNAUTHORIZED',
        message: 'Admin authentication required.',
      });
      return;
    }

    const tokenHash = hashSecret(rawToken);
    const { data: adminSession, error } = await supabase
      .from('admin_sessions')
      .select('id, admin_email, expires_at, revoked_at')
      .eq('token_hash', tokenHash)
      .is('revoked_at', null)
      .maybeSingle();

    if (error || !adminSession) {
      res.status(401).json({
        code: 'ADMIN_SESSION_INVALID',
        message: 'Invalid or revoked admin session.',
      });
      return;
    }

    if (new Date(adminSession.expires_at).getTime() <= Date.now()) {
      await supabase
        .from('admin_sessions')
        .update({ revoked_at: new Date().toISOString() })
        .eq('id', adminSession.id);
      res.status(401).json({
        code: 'ADMIN_SESSION_EXPIRED',
        message: 'Admin session expired.',
      });
      return;
    }

    req.adminAuth = {
      sessionId: adminSession.id,
      adminEmail: adminSession.admin_email,
    };

    next();
  } catch (err) {
    console.error('Admin auth middleware error:', err);
    res.status(500).json({
      code: 'AUTH_ERROR',
      message: 'Unable to validate admin session.',
    });
  }
}
