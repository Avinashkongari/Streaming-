import readline from 'node:readline/promises';
import { stdin as input, stdout as output } from 'node:process';
import { TelegramClient } from 'telegram';
import { StringSession } from 'telegram/sessions/index.js';

/**
 * Interactive one-time local helper to generate TELEGRAM_SESSION_STRING
 * for headless server-side private-channel streaming via GramJS MTProto.
 */
async function main() {
  const rl = readline.createInterface({ input, output });
  try {
    const apiIdStr = (await rl.question('Telegram API ID (from https://my.telegram.org): ')).trim();
    const apiHash = (await rl.question('Telegram API Hash: ')).trim();
    const apiId = Number.parseInt(apiIdStr, 10);

    if (!Number.isFinite(apiId) || !apiHash) {
      console.error('Valid numeric API ID and API Hash are required.');
      process.exit(1);
    }

    const client = new TelegramClient(new StringSession(''), apiId, apiHash, {
      connectionRetries: 5,
    });

    await client.start({
      phoneNumber: async () => (await rl.question('Phone number (with country code, e.g. +1...): ')).trim(),
      password: async () => (await rl.question('Telegram 2FA password (leave blank if none): ')).trim(),
      phoneCode: async () => (await rl.question('Verification code sent by Telegram: ')).trim(),
      onError: (err) => console.error('Telegram login error:', err),
    });

    const sessionString = client.session.save() as unknown as string;
    console.log('\n=== SAVE THIS SECRET IN YOUR BACKEND HOST AS TELEGRAM_SESSION_STRING ===\n');
    console.log(sessionString);
    console.log('\nNEVER put this string in your Android app or commit it to Git.\n');

    await client.disconnect();
  } finally {
    rl.close();
  }
}

void main();
