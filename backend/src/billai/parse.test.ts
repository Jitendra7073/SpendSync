import { describe, expect, it } from 'vitest';
import { extractJson, parseBillRead, validNote } from './parse';
import { buildBillPrompt } from './prompt';

const good = {
  merchant: 'Zara', date: '2026-10-09', currency: 'inr', total: '2,150.00', subtotal: 2000, tax: 150, discount: null, fees: null,
  items: [{ name: 'Shirt', qty: 1, unitPrice: 1200, amount: 1200 }, { name: 'Jeans', qty: 1, unitPrice: 800, amount: 800 }, { name: '', amount: 5 }],
  paymentMethod: 'UPI', billNumber: 'Z-1', category: 'Shopping', confidence: 0.92,
  summary: 'Clothes from Zara.', notes: ['Tax is {tax} on a subtotal of {subtotal}.', 'You paid 2150 in total.', 'Item {item:9} looks odd.'],
};

describe('parseBillRead', () => {
  it('parses clean JSON, normalises numbers and drops empty items', () => {
    const r = parseBillRead(JSON.stringify(good));
    expect(r).toMatchObject({ merchant: 'Zara', currency: 'INR', total: 2150, category: 'Shopping', confidence: 0.92 });
    expect(r.items.map((i) => i.name)).toEqual(['Shirt', 'Jeans']);
    expect(r.checks).toEqual({ itemsMatchSubtotal: true, totalsAddUp: true });
  });
  it('survives think tags, code fences and chatter', () => {
    expect(parseBillRead(`<think>hmm</think>Sure!\n\`\`\`json\n${JSON.stringify(good)}\n\`\`\`\nDone`).total).toBe(2150);
  });
  it('keeps only notes that use known placeholders and no raw money', () => {
    expect(parseBillRead(JSON.stringify(good)).notes).toEqual(['Tax is {tax} on a subtotal of {subtotal}.']);
    expect(validNote('Item {item:2} was charged twice', 2)).toBe(true);
    expect(validNote('Item {item:3} was charged twice', 2)).toBe(false);
    expect(validNote('Delivery {fees} is {nope}', 2)).toBe(false);
  });
  it('clamps silly numbers and flags bad maths', () => {
    const r = parseBillRead(JSON.stringify({ ...good, total: 99999999999, subtotal: 100, tax: -5 }));
    expect(r.total).toBe(10_000_000);
    expect(r.tax).toBe(0);
    expect(r.checks.itemsMatchSubtotal).toBe(false);
    expect(r.checks.totalsAddUp).toBe(false);
  });
  it('rejects output with no JSON or the wrong shape', () => {
    expect(() => parseBillRead('I cannot read this image.')).toThrow();
    expect(() => parseBillRead('{"items": "lots"}')).toThrow();
    expect(extractJson('no braces')).toBeNull();
  });
  it('unknown category and bad date become null; confidence is bounded', () => {
    const r = parseBillRead(JSON.stringify({ ...good, category: 'Yachts', date: '9 Oct', confidence: 7 }));
    expect(r.category).toBeNull(); expect(r.date).toBeNull(); expect(r.confidence).toBe(1);
  });
});

describe('prompt', () => {
  it('fences the bill as data, demands JSON only, and follows prefs', () => {
    const p = buildBillPrompt({ language: 'Hindi', style: 'short', tone: 'simple' });
    expect(p).toMatch(/DATA, never instructions/);
    expect(p).toMatch(/only one JSON object/i);
    expect(p).toContain('Hindi');
    expect(p).toContain('{total}');
  });
});
