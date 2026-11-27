import assert from 'node:assert/strict';
import test from 'node:test';
import { parseHttpRange } from '../src/lib/range.js';

// Set minimal non-secret test env vars before importing crypto helpers
process.env.SUPABASE_URL = 'https://test-project.supabase.co';
process.env.SUPABASE_SERVICE_ROLE_KEY = 'test-service-role-key-placeholder-000000';
process.env.TOKEN_PEPPER = 'test-pepper-at-least-32-characters-long-123456';

const {
  generateAccessCode,
  hashAdminPassword,
  hashSecret,
  maskAccessCode,
  normalizeAccessCode,
  verifyAdminPassword,
} = await import('../src/lib/crypto.js');

test('access code generation, normalization, and masking', () => {
  const code = generateAccessCode();
  assert.match(code, /^AXN-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$/);
  assert.equal(normalizeAccessCode(`  ${code.toLowerCase()}  `), code);

  const masked = maskAccessCode(code);
  assert.equal(masked, `AXN-****-${code.slice(-4)}`);
});

test('HMAC secret hashing is deterministic and distinct for different inputs', () => {
  const h1 = hashSecret('AXN-TEST-1111-2222');
  const h2 = hashSecret('AXN-TEST-1111-2222');
  const h3 = hashSecret('AXN-TEST-1111-3333');
  assert.equal(h1, h2);
  assert.notEqual(h1, h3);
  assert.equal(h1.length, 64);
});

test('scrypt admin password hashing and timing-safe verification', () => {
  const hash = hashAdminPassword('Sup3r-Str0ng-Test-Pass!');
  assert.ok(hash.startsWith('scrypt$'));
  assert.equal(verifyAdminPassword('Sup3r-Str0ng-Test-Pass!', hash), true);
  assert.equal(verifyAdminPassword('wrong-password', hash), false);
  assert.equal(verifyAdminPassword('Sup3r-Str0ng-Test-Pass!', 'invalid-hash-format'), false);
});

test('HTTP Range parser supports partial, open-ended, suffix, and invalid ranges', () => {
  const total = 200_000_000;

  assert.deepEqual(parseHttpRange('bytes=0-1', total), {
    start: 0,
    end: 1,
    isPartial: true,
  });

  assert.deepEqual(parseHttpRange('bytes=1024-', total), {
    start: 1024,
    end: total - 1,
    isPartial: true,
  });

  assert.deepEqual(parseHttpRange('bytes=-4096', total), {
    start: total - 4096,
    end: total - 1,
    isPartial: true,
  });

  assert.deepEqual(parseHttpRange(undefined, total), {
    start: 0,
    end: total - 1,
    isPartial: false,
  });

  assert.equal(parseHttpRange(`bytes=${total}-`, total), 'INVALID');
  assert.equal(parseHttpRange('bytes=500-100', total), 'INVALID');
  assert.equal(parseHttpRange('items=0-10', total), 'INVALID');
});
