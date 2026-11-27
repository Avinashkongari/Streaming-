import { Router } from 'express';
import { z } from 'zod';
import { env } from '../config/env.js';
import {
  generateAccessCode,
  generateOpaqueToken,
  hashSecret,
  maskAccessCode,
  verifyAdminPassword,
} from '../lib/crypto.js';
import { supabase } from '../lib/supabase.js';
import { requireAdminAuth } from '../middleware/auth.js';
import { authRateLimiter } from '../middleware/rateLimit.js';
import { telegramService } from '../services/telegramService.js';

export const adminRouter = Router();

const uuidSchema = z.string().uuid();

const adminLoginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8).max(128),
});

const createAccessCodeSchema = z.object({
  count: z.number().int().min(1).max(50).optional().default(1),
  label: z.string().trim().max(80).optional(),
  expires_at: z.string().datetime().nullable().optional(),
});

const patchAccessCodeSchema = z.object({
  status: z.enum(['active', 'disabled', 'expired']).optional(),
  label: z.string().trim().max(80).nullable().optional(),
  expires_at: z.string().datetime().nullable().optional(),
});

const patchUserSchema = z.object({
  display_name: z.string().trim().min(1).max(80).optional(),
  status: z.enum(['active', 'suspended', 'revoked']).optional(),
});

const createCategorySchema = z.object({
  name: z.string().trim().min(1).max(60),
  slug: z
    .string()
    .trim()
    .min(1)
    .max(60)
    .regex(/^[a-z0-9-]+$/),
  sort_order: z.number().int().min(0).max(1000).optional().default(0),
});

const createMovieSchema = z.object({
  title: z.string().trim().min(1).max(200),
  description: z.string().trim().max(4000).nullable().optional(),
  category_id: z.string().uuid().nullable().optional(),
  extra_categories: z.array(z.string().trim().min(1).max(50)).max(8).optional().default([]),
  release_year: z.number().int().min(1900).max(2100).nullable().optional(),
  duration_seconds: z.number().int().min(0).optional(),
  file_size_bytes: z.number().int().positive().nullable().optional(),
  mime_type: z.string().trim().max(64).optional(),
  quality_label: z.string().trim().max(24).optional().default('1080p'),
  maturity_rating: z.string().trim().max(16).optional().default('16+'),
  is_featured: z.boolean().optional().default(false),
  is_trending: z.boolean().optional().default(false),
  is_recently_added: z.boolean().optional().default(true),
  is_published: z.boolean().optional().default(true),
  telegram_channel_id: z.string().trim().min(1).optional(),
  telegram_message_id: z.number().int().positive(),
  auto_sync_telegram: z.boolean().optional().default(true),
  poster_override_url: z.string().url().startsWith('https://').nullable().optional(),
  backdrop_override_url: z.string().url().startsWith('https://').nullable().optional(),
});

const patchMovieSchema = createMovieSchema.partial().omit({ auto_sync_telegram: true });

// ---------------------------------------------------------------------------
// Dedicated Admin Authentication (No public registration)
// ---------------------------------------------------------------------------

adminRouter.post('/auth/login', authRateLimiter, async (req, res) => {
  if (!env.isAdminConfigured) {
    res.status(503).json({
      code: 'ADMIN_NOT_CONFIGURED',
      message: 'Admin credentials are not configured in server environment variables.',
    });
    return;
  }

  const parsed = adminLoginSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ code: 'INVALID_REQUEST', message: 'Valid email and password are required.' });
    return;
  }

  const emailMatches =
    parsed.data.email.trim().toLowerCase() === env.ADMIN_EMAIL.trim().toLowerCase();
  const passwordMatches = verifyAdminPassword(parsed.data.password, env.ADMIN_PASSWORD_HASH);

  if (!emailMatches || !passwordMatches) {
    res.status(401).json({ code: 'INVALID_CREDENTIALS', message: 'Invalid admin credentials.' });
    return;
  }

  const rawToken = generateOpaqueToken();
  const tokenHash = hashSecret(rawToken);
  const expiresAt = new Date(
    Date.now() + env.ADMIN_SESSION_TTL_HOURS * 60 * 60 * 1000
  ).toISOString();

  const { error } = await supabase.from('admin_sessions').insert({
    admin_email: env.ADMIN_EMAIL.trim().toLowerCase(),
    token_hash: tokenHash,
    expires_at: expiresAt,
  });

  if (error) {
    res.status(500).json({ code: 'SESSION_CREATE_FAILED', message: 'Failed to create admin session.' });
    return;
  }

  res.status(200).json({
    authenticated: true,
    admin_token: rawToken,
    email: env.ADMIN_EMAIL.trim().toLowerCase(),
    expires_at: expiresAt,
  });
});

adminRouter.get('/auth/me', requireAdminAuth, (req, res) => {
  res.status(200).json({
    authenticated: true,
    email: req.adminAuth!.adminEmail,
  });
});

adminRouter.post('/auth/logout', requireAdminAuth, async (req, res) => {
  await supabase
    .from('admin_sessions')
    .update({ revoked_at: new Date().toISOString() })
    .eq('id', req.adminAuth!.sessionId);
  res.status(204).send();
});

// ---------------------------------------------------------------------------
// Access Code Management (One-time single-user codes)
// ---------------------------------------------------------------------------

adminRouter.get('/access-codes', requireAdminAuth, async (_req, res) => {
  const { data, error } = await supabase
    .from('access_codes')
    .select(
      'id, masked_code, label, status, claimed_by_user_id, claimed_at, expires_at, single_user_locked, created_at'
    )
    .order('created_at', { ascending: false });

  if (error) {
    res.status(500).json({ code: 'DB_ERROR', message: 'Failed to load access codes.' });
    return;
  }
  res.status(200).json({ access_codes: data ?? [] });
});

adminRouter.post('/access-codes', requireAdminAuth, async (req, res) => {
  const parsed = createAccessCodeSchema.safeParse(req.body ?? {});
  if (!parsed.success) {
    res.status(400).json({ code: 'INVALID_REQUEST', message: 'Invalid access code creation payload.' });
    return;
  }

  const generated: Array<{
    plain_code: string;
    code_hash: string;
    masked_code: string;
    label: string | null;
    expires_at: string | null;
  }> = [];

  for (let i = 0; i < parsed.data.count; i++) {
    const plain = generateAccessCode();
    generated.push({
      plain_code: plain,
      code_hash: hashSecret(plain),
      masked_code: maskAccessCode(plain),
      label: parsed.data.label ?? null,
      expires_at: parsed.data.expires_at ?? null,
    });
  }

  const { data, error } = await supabase
    .from('access_codes')
    .insert(
      generated.map((g) => ({
        code_hash: g.code_hash,
        masked_code: g.masked_code,
        label: g.label,
        expires_at: g.expires_at,
        status: 'active',
        single_user_locked: true,
      }))
    )
    .select('id, masked_code, label, status, expires_at, created_at');

  if (error || !data) {
    res.status(500).json({ code: 'DB_ERROR', message: 'Failed to generate access codes.' });
    return;
  }

  // Plaintext codes are returned ONCE at creation time and never stored in plaintext
  res.status(201).json({
    created: data.map((row, idx) => ({
      ...row,
      access_code: generated[idx]?.plain_code,
    })),
  });
});

adminRouter.patch('/access-codes/:id', requireAdminAuth, async (req, res) => {
  const parsedId = uuidSchema.safeParse(req.params.id);
  const parsedBody = patchAccessCodeSchema.safeParse(req.body);
  if (!parsedId.success || !parsedBody.success) {
    res.status(400).json({ code: 'INVALID_REQUEST', message: 'Invalid access code update payload.' });
    return;
  }

  const { data, error } = await supabase
    .from('access_codes')
    .update(parsedBody.data)
    .eq('id', parsedId.data)
    .select('id, masked_code, label, status, claimed_by_user_id, claimed_at, expires_at')
    .maybeSingle();

  if (error || !data) {
    res.status(error ? 500 : 404).json({
      code: error ? 'DB_ERROR' : 'NOT_FOUND',
      message: error ? 'Failed to update access code.' : 'Access code not found.',
    });
    return;
  }

  // Immediately revoke active sessions if code was disabled or expired
  if (parsedBody.data.status === 'disabled' || parsedBody.data.status === 'expired') {
    await supabase
      .from('user_sessions')
      .update({ revoked_at: new Date().toISOString() })
      .eq('access_code_id', parsedId.data)
      .is('revoked_at', null);
  }

  res.status(200).json({ access_code: data });
});

// ---------------------------------------------------------------------------
// User Management
// ---------------------------------------------------------------------------

adminRouter.get('/users', requireAdminAuth, async (_req, res) => {
  const { data, error } = await supabase
    .from('users')
    .select('id, display_name, status, device_bound, created_at, last_seen_at')
    .order('created_at', { ascending: false });

  if (error) {
    res.status(500).json({ code: 'DB_ERROR', message: 'Failed to load users.' });
    return;
  }
  res.status(200).json({ users: data ?? [] });
});

adminRouter.patch('/users/:id', requireAdminAuth, async (req, res) => {
  const parsedId = uuidSchema.safeParse(req.params.id);
  const parsedBody = patchUserSchema.safeParse(req.body);
  if (!parsedId.success || !parsedBody.success) {
    res.status(400).json({ code: 'INVALID_REQUEST', message: 'Invalid user update payload.' });
    return;
  }

  const { data, error } = await supabase
    .from('users')
    .update(parsedBody.data)
    .eq('id', parsedId.data)
    .select('id, display_name, status, device_bound, created_at, last_seen_at')
    .maybeSingle();

  if (error || !data) {
    res.status(error ? 500 : 404).json({
      code: error ? 'DB_ERROR' : 'NOT_FOUND',
      message: error ? 'Failed to update user.' : 'User not found.',
    });
    return;
  }

  if (parsedBody.data.status && parsedBody.data.status !== 'active') {
    await supabase
      .from('user_sessions')
      .update({ revoked_at: new Date().toISOString() })
      .eq('user_id', parsedId.data)
      .is('revoked_at', null);
  }

  res.status(200).json({ user: data });
});

// ---------------------------------------------------------------------------
// Category Management
// ---------------------------------------------------------------------------

adminRouter.get('/categories', requireAdminAuth, async (_req, res) => {
  const { data, error } = await supabase
    .from('categories')
    .select('*')
    .order('sort_order', { ascending: true });
  if (error) {
    res.status(500).json({ code: 'DB_ERROR', message: 'Failed to load categories.' });
    return;
  }
  res.status(200).json({ categories: data ?? [] });
});

adminRouter.post('/categories', requireAdminAuth, async (req, res) => {
  const parsed = createCategorySchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({ code: 'INVALID_REQUEST', message: 'Invalid category payload.' });
    return;
  }

  const { data, error } = await supabase
    .from('categories')
    .insert(parsed.data)
    .select('*')
    .single();

  if (error) {
    res.status(409).json({ code: 'CATEGORY_CONFLICT', message: 'Category name or slug already exists.' });
    return;
  }
  res.status(201).json({ category: data });
});

adminRouter.delete('/categories/:id', requireAdminAuth, async (req, res) => {
  const parsedId = uuidSchema.safeParse(req.params.id);
  if (!parsedId.success) {
    res.status(400).json({ code: 'INVALID_ID', message: 'Invalid category ID.' });
    return;
  }
  const { error } = await supabase.from('categories').delete().eq('id', parsedId.data);
  if (error) {
    res.status(500).json({ code: 'DB_ERROR', message: 'Failed to delete category.' });
    return;
  }
  res.status(204).send();
});

// ---------------------------------------------------------------------------
// Movie Management & Private Telegram Channel Inspection
// ---------------------------------------------------------------------------

adminRouter.get('/telegram/channel-videos', requireAdminAuth, async (req, res) => {
  if (!telegramService.isConfigured()) {
    res.status(503).json({
      code: 'TELEGRAM_UNCONFIGURED',
      message: 'Telegram MTProto credentials are not configured on the server.',
    });
    return;
  }

  try {
    const limitParam = Number.parseInt(String(req.query.limit ?? '30'), 10);
    const limit = Number.isFinite(limitParam) ? limitParam : 30;
    const videos = await telegramService.listChannelVideos(env.TELEGRAM_CHANNEL_ID, limit);
    res.status(200).json({ videos });
  } catch (err) {
    console.error('GET /api/admin/telegram/channel-videos error:', err);
    res.status(502).json({
      code: 'TELEGRAM_FETCH_ERROR',
      message: 'Failed to inspect videos from the private Telegram channel.',
    });
  }
});

adminRouter.get('/movies', requireAdminAuth, async (_req, res) => {
  const { data, error } = await supabase
    .from('movies')
    .select('*')
    .order('created_at', { ascending: false });

  if (error) {
    res.status(500).json({ code: 'DB_ERROR', message: 'Failed to load movies.' });
    return;
  }
  res.status(200).json({ movies: data ?? [] });
});

adminRouter.post('/movies', requireAdminAuth, async (req, res) => {
  const parsed = createMovieSchema.safeParse(req.body);
  if (!parsed.success) {
    res.status(400).json({
      code: 'INVALID_REQUEST',
      message: 'Invalid movie payload.',
      details: parsed.error.flatten().fieldErrors,
    });
    return;
  }

  const input = parsed.data;
  const channelId = input.telegram_channel_id || env.TELEGRAM_CHANNEL_ID;
  if (!channelId) {
    res.status(400).json({
      code: 'MISSING_CHANNEL_ID',
      message: 'Provide telegram_channel_id or set TELEGRAM_CHANNEL_ID on the server.',
    });
    return;
  }

  let durationSeconds = input.duration_seconds ?? 0;
  let fileSizeBytes = input.file_size_bytes ?? null;
  let mimeType = input.mime_type ?? 'video/mp4';
  let hasTelegramThumbnail = true;

  // Automatically inspect real Telegram video metadata & thumbnail availability
  if (input.auto_sync_telegram && telegramService.isConfigured()) {
    try {
      const resolved = await telegramService.resolveVideoMessage(
        channelId,
        input.telegram_message_id
      );
      durationSeconds = input.duration_seconds ?? resolved.metadata.durationSeconds;
      fileSizeBytes = input.file_size_bytes ?? resolved.metadata.fileSizeBytes;
      mimeType = input.mime_type ?? resolved.metadata.mimeType;
      hasTelegramThumbnail = resolved.metadata.hasThumbnail;
    } catch (err) {
      res.status(422).json({
        code: 'TELEGRAM_MEDIA_VALIDATION_FAILED',
        message:
          err instanceof Error
            ? err.message
            : 'Could not validate Telegram video message in private channel.',
      });
      return;
    }
  }

  const { data, error } = await supabase
    .from('movies')
    .insert({
      title: input.title,
      description: input.description ?? null,
      category_id: input.category_id ?? null,
      extra_categories: input.extra_categories,
      release_year: input.release_year ?? null,
      duration_seconds: durationSeconds,
      file_size_bytes: fileSizeBytes,
      mime_type: mimeType,
      quality_label: input.quality_label,
      maturity_rating: input.maturity_rating,
      is_featured: input.is_featured,
      is_trending: input.is_trending,
      is_recently_added: input.is_recently_added,
      is_published: input.is_published,
      telegram_channel_id: channelId,
      telegram_message_id: input.telegram_message_id,
      has_telegram_thumbnail: hasTelegramThumbnail,
      poster_override_url: input.poster_override_url ?? null,
      backdrop_override_url: input.backdrop_override_url ?? null,
    })
    .select('*')
    .single();

  if (error) {
    res.status(409).json({
      code: 'MOVIE_INSERT_FAILED',
      message: 'Failed to create movie (check duplicate Telegram message ID or category ID).',
    });
    return;
  }

  res.status(201).json({ movie: data });
});

adminRouter.patch('/movies/:id', requireAdminAuth, async (req, res) => {
  const parsedId = uuidSchema.safeParse(req.params.id);
  const parsedBody = patchMovieSchema.safeParse(req.body);
  if (!parsedId.success || !parsedBody.success) {
    res.status(400).json({ code: 'INVALID_REQUEST', message: 'Invalid movie update payload.' });
    return;
  }

  const { data, error } = await supabase
    .from('movies')
    .update({
      ...parsedBody.data,
      updated_at: new Date().toISOString(),
    })
    .eq('id', parsedId.data)
    .select('*')
    .maybeSingle();

  if (error || !data) {
    res.status(error ? 500 : 404).json({
      code: error ? 'DB_ERROR' : 'NOT_FOUND',
      message: error ? 'Failed to update movie.' : 'Movie not found.',
    });
    return;
  }

  res.status(200).json({ movie: data });
});

adminRouter.delete('/movies/:id', requireAdminAuth, async (req, res) => {
  const parsedId = uuidSchema.safeParse(req.params.id);
  if (!parsedId.success) {
    res.status(400).json({ code: 'INVALID_ID', message: 'Invalid movie ID.' });
    return;
  }

  const { error } = await supabase.from('movies').delete().eq('id', parsedId.data);
  if (error) {
    res.status(500).json({ code: 'DB_ERROR', message: 'Failed to delete movie.' });
    return;
  }
  res.status(204).send();
});
