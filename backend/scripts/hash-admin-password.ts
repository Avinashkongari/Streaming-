import crypto from 'node:crypto';
import readline from 'node:readline/promises';
import { stdin as input, stdout as output } from 'node:process';

async function main() {
  const rl = readline.createInterface({ input, output });
  try {
    const password = (await rl.question('Enter admin password (min 12 chars recommended): ')).trim();
    if (password.length < 8) {
      console.error('Password must be at least 8 characters.');
      process.exit(1);
    }
    const salt = crypto.randomBytes(16).toString('hex');
    const derived = crypto.scryptSync(password, salt, 64, {
      N: 16384,
      r: 8,
      p: 1,
    });
    const hash = `scrypt$${salt}$${derived.toString('hex')}`;
    console.log('\nAdd this to your server environment variables as ADMIN_PASSWORD_HASH:\n');
    console.log(hash);
  } finally {
    rl.close();
  }
}

void main();
