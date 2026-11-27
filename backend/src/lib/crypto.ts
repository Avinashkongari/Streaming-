import crypto from 'node:crypto';
import { env } from '../config/env.js';

const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

/**
 * Generates a high-entropy opaque bearer token (256 bits).
 */
export function generateOpaqueToken(): string {
  return crypto.randomBytes(32).toString('base64url');
}

/**
 * Computes a keyed HMAC-SHA256 digest for storing session tokens and access codes
 * without keeping raw secrets in database columns.
 */
export function hashSecret(value: string): string {
  return crypto
    .createHmac('sha256', env.TOKEN_PEPPER)
    .update(value)
    .digest('hex');
}

export function normalizeAccessCode(raw: string): string {
  return raw.trim().toUpperCase().replace(/\s+/g, '');
}

/**
 * Generates a single-user invitation code like AXN-7K9M-4P2Q-8R5T.
 */
export function generateAccessCode(): string {
  const randomSegment = (length: number): string => {
    const bytes = crypto.randomBytes(length);
    let out = '';
    for (let i = 0; i < length; i++) {
      out += CODE_ALPHABET[bytes[i]! % CODE_ALPHABET.length];
    }
    return out;
  };
  return `AXN-${randomSegment(4)}-${randomSegment(4)}-${randomSegment(4)}`;
}

export function maskAccessCode(normalizedCode: string): string {
  const clean = normalizedCode.replace(/[^A-Z0-9]/g, '');
  if (clean.length <= 4) return '****';
  const tail = clean.slice(-4);
  const prefix = normalizedCode.startsWith('AXN-') ? 'AXN-' : '';
  return `${prefix}****-${tail}`;
}

/**
 * Hashes an admin password using scrypt with a random 16-byte salt.
 * Output format: scrypt$<saltHex>$<derivedKeyHex>
 */
export function hashAdminPassword(plainPassword: string): string {
  const salt = crypto.randomBytes(16).toString('hex');
  const derived = crypto.scryptSync(plainPassword, salt, 64, {
    N: 16384,
    r: 8,
    p: 1,
  });
  return `scrypt$${salt}$${derived.toString('hex')}`;
}

/**
 * Timing-safe verification of an admin password against an `scrypt$<salt>$<hash>` string.
 */
export function verifyAdminPassword(plainPassword: string, storedHash: string): boolean {
  const parts = storedHash.split('$');
  if (parts.length !== 3 || parts[0] !== 'scrypt') {
    return false;
  }
  const [, salt, expectedHex] = parts;
  if (!salt || !expectedHex) return false;

  const expectedBuf = Buffer.from(expectedHex, 'hex');
  const actualBuf = crypto.scryptSync(plainPassword, salt, expectedBuf.length, {
    N: 16384,
    r: 8,
    p: 1,
  });

  if (actualBuf.length !== expectedBuf.length) {
    return false;
  }
  return crypto.timingSafeEqual(actualBuf, expectedBuf);
}
