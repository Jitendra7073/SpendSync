/** Asks the real models for a follow-up draft. Usage: npx vite-node scripts/probe-followup.ts [gentle|friendly|firm] [Language] */
import 'dotenv/config';
import { draftMessage } from '../src/holds/message.service';
import { messageRequestSchema } from '../src/holds/message';

(async () => {
  const tone = process.argv[2] ?? 'friendly';
  const language = process.argv[3] ?? 'English';
  for (const direction of ['owed_to_me', 'owed_by_me'] as const) {
    const r = messageRequestSchema.parse({ personName: 'Asha Verma', direction, amount: 1500, dueDate: '2026-10-05', overdueDays: direction === 'owed_to_me' ? 3 : 0, tone, language, context: 'it was for the Goa trip', userName: 'Jitendra' });
    const t0 = Date.now();
    const out = await draftMessage(r);
    console.log(`\n[${direction}] ${Date.now() - t0}ms`, out.source === 'ai' ? out.model : out);
    if (out.source === 'ai') console.log(out.text);
  }
  process.exit(0);
})();
