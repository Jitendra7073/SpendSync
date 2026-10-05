import { describe, expect, it } from 'vitest';
import { checkDraft, messageRequestSchema, userPrompt } from './message';

const req = (over: object = {}) => messageRequestSchema.parse({ personName: 'Asha Verma', direction: 'owed_to_me', amount: 1500, dueDate: '2026-10-05', ...over });

describe('follow-up draft checks', () => {
  it('accepts a draft with the person, the amount and no placeholders', () => {
    expect(checkDraft('Hi Asha, a gentle reminder about the ₹1,500 due on 5 Oct. Thank you!', req())).toContain('1,500');
    expect(checkDraft('"Hi Asha, could you send the 1500 by Friday? Thanks"', req())).toBe('Hi Asha, could you send the 1500 by Friday? Thanks');
  });

  it('rejects wrong amounts, missing name, placeholders, links and the wrong size', () => {
    expect(checkDraft('Hi Asha, please return ₹1,800 soon, thanks a lot.', req())).toBeNull();
    expect(checkDraft('Hello, please return ₹1,500 soon, thanks a lot.', req())).toBeNull();
    expect(checkDraft('Hi Asha, please return ₹1,500 soon [Your Name]', req())).toBeNull();
    expect(checkDraft('Hi Asha, please pay ₹1,500 at https://pay.me/x now', req())).toBeNull();
    expect(checkDraft('Hi Asha ₹1500', req())).toBeNull();
    expect(checkDraft('Hi Asha, ₹1,500 ' + 'x'.repeat(500), req({ channel: 'sms' }))).toBeNull();
  });

  it('accepts Hindi drafts with Devanagari digits and a transliterated name', () => {
    const hi = req({ language: 'Hindi' });
    expect(checkDraft('नमस्ते आशा, ₹१,५०० की एक छोटी सी याद दिलाई, ५ अक्टूबर तक। धन्यवाद!', hi)).not.toBeNull();
    expect(checkDraft('नमस्ते आशा, ₹१,८०० की याद दिलाई, ५ अक्टूबर तक। धन्यवाद!', hi)).toBeNull(); // still the wrong amount
  });

  it('validates the request and passes only facts to the model, never contact details', () => {
    expect(() => messageRequestSchema.parse({ personName: '', direction: 'owed_to_me', amount: 1, dueDate: '2026-10-05' })).toThrow();
    expect(() => messageRequestSchema.parse({ personName: 'A', direction: 'owed_to_me', amount: -1, dueDate: '2026-10-05' })).toThrow();
    const p = userPrompt(req({ context: 'it was for the trip', previous: 'old draft' }));
    expect(p).toContain('1500');
    expect(p).toContain('it was for the trip');
    expect(p).toContain('old draft');
    expect(p).not.toMatch(/phone|email|@/i);
  });
});
