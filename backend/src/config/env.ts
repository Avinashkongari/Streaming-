import 'dotenv/config';
import crypto from 'node:crypto';
import { z } from 'zod';

const envSchema = z.object({
  PORT: z.coerce.number().int().min(1).max(65535).default(8080),
  NODE_ENV: z.enum(['development', 'production', 'test']).default('development'),
  CORS_ALLOWED_ORIGINS: z.string().default(''),
  PUBLIC_BASE_URL: z.string().optional().default(''),

  SUPABASE_URL: z.string().url('SUPABASE_URL must be a valid HTTPS URL'),
  SUPABASE_SERVICE_ROLE_KEY: z.string().min(20, 'SUPABASE_SERVICE_ROLE_KEY is required'),

  TOKEN_PEPPER: z.string().optional().default(''),
  USER_SESSION_TTL_HOURS: z.coerce.number().int().min(1).max(8760).default(720),
  ADMIN_SESSION_TTL_HOURS: z.coerce.number().int().min(1).max(168).default(12),

  ADMIN_EMAIL: z.string().email().optional().or(z.literal('')).default(''),
  ADMIN_PASSWORD_HASH: z.string().optional().default(''),

  TELEGRAM_API_ID: z.coerce.number().int().optional(),
  TELEGRAM_API_HASH: z.string().optional().default(''),
  TELEGRAM_SESSION_STRING: z.string().optional().default(''),
  TELEGRAM_CHANNEL_ID: z.string().optional().default(''),

  // Default ~220 MB ceiling to comfortably allow ~200 MB videos
  MAX_VIDEO_SIZE_BYTES: z.coerce.number().int().positive().default(230_686_720),
  // 512 KB chunk size (multiple of 4 KB required by Telegram MTProto upload.getFile)
  STREAM_CHUNK_SIZE_BYTES: z.coerce.number().int().min(65_536).max(1_048_576).default(524_288),
});

const parsed = envSchema.safeParse(process.env);
if (!parsed.success) {
  console.error('Invalid server environment configuration:');
  for (const issue of parsed.error.issues) {
    console.error(`  - ${issue.path.join('.')}: ${issue.message}`);
  }
  process.exit(1);
}

const raw = parsed.data;

if (raw.NODE_ENV === 'production' && raw.TOKEN_PEPPER.trim().length < 32) {
  console.error('TOKEN_PEPPER must be at least 32 characters in production.');
  process.exit(1);
}

const resolvedTokenPepper =
  raw.TOKEN_PEPPER.trim().length >= 16
    ? raw.TOKEN_PEPPER.trim()
    : crypto.randomBytes(32).toString('hex');

const corsOrigins = raw.CORS_ALLOWED_ORIGINS.split(',')
  .map((o) => o.trim())
  .filter((o) => o.length > 0);

export const env = {
  ...raw,
  TOKEN_PEPPER: resolvedTokenPepper,
  corsOrigins,
  isAdminConfigured: Boolean(raw.ADMIN_EMAIL && raw.ADMIN_PASSWORD_HASH),
  isTelegramConfigured: Boolean(
    raw.TELEGRAM_API_ID &&
      raw.TELEGRAM_API_HASH &&
      raw.TELEGRAM_SESSION_STRING &&
      raw.TELEGRAM_CHANNEL_ID
  ),
} as const;
