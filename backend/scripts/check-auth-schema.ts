/** Prints Better Auth's schema-mismatch findings (no secrets). Run: npx vite-node scripts/check-auth-schema.ts */
import { config } from 'dotenv';
config({ path: '.env.local' });
config({ path: '.env' });

async function main() {
  const { auth } = await import('../src/config/auth');
  try {
    await auth.$context;
    await auth.api.getSession({ headers: new Headers({ authorization: 'Bearer x' }) });
    console.log('OK: no mismatch reported');
  } catch (e) {
    const err = e as { message?: string; findings?: unknown };
    console.log('ERROR:', err.message?.slice(0, 600));
    console.log(JSON.stringify(err.findings ?? null, null, 1));
  }
  process.exit(0);
}
void main();
