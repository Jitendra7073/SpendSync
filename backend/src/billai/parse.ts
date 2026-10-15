import type { z } from 'zod';
import { billReadSchema } from './schema';

export type BillRead = z.infer<typeof billReadSchema> & { checks: { itemsMatchSubtotal: boolean | null; totalsAddUp: boolean | null } };

const MAX_MONEY = 10_000_000;
const PLACEHOLDER = /\{(total|subtotal|tax|discount|fees|item:(\d+))\}/g;
const FENCE = new RegExp('`{3}(?:json)?', 'gi');

/** The first {...} block, after removing <think> sections and code fences. */
export function extractJson(raw: string): string | null {
  const s = raw.replace(/<think>[\s\S]*?<\/think>/gi, '').replace(FENCE, '');
  const a = s.indexOf('{');
  const b = s.lastIndexOf('}');
  return a === -1 || b <= a ? null : s.slice(a, b + 1);
}

/** Notes may mention money only through placeholders; any other digit is a made-up number. */
export function validNote(note: string, itemCount: number): boolean {
  let ok = true;
  const rest = note.replace(PLACEHOLDER, (_m, _k, n) => {
    if (n !== undefined && (Number(n) < 1 || Number(n) > itemCount)) ok = false;
    return '';
  });
  if (/\{[^}]*\}/.test(rest)) return false; // unknown placeholder
  // Any digit, in any script (४९ too), outside a placeholder is a number the app did not supply.
  return ok && !/\p{Nd}/u.test(rest);
}

const clamp = (n: number | null) => (n === null ? null : Math.round(Math.min(MAX_MONEY, Math.max(0, n)) * 100) / 100);
const near = (a: number, b: number) => Math.abs(a - b) <= 1;

export function parseBillRead(raw: string): BillRead {
  const json = extractJson(raw);
  if (!json) throw new Error('no JSON in reply');
  let obj: unknown;
  try {
    obj = JSON.parse(json);
  } catch {
    throw new Error('reply is not valid JSON');
  }
  const r = billReadSchema.parse(obj);
  const items = r.items
    .filter((i) => i.name)
    .slice(0, 100)
    .map((i) => ({ name: i.name, qty: i.qty === null ? null : Math.max(0, Math.min(i.qty, 10_000)), unitPrice: clamp(i.unitPrice), amount: clamp(i.amount) }));
  const total = clamp(r.total);
  const subtotal = clamp(r.subtotal);
  const tax = clamp(r.tax);
  const discount = clamp(r.discount);
  const fees = clamp(r.fees);
  const amounts = items.map((i) => i.amount);
  const base = subtotal ?? total;
  const itemsMatchSubtotal =
    items.length && base !== null && amounts.every((a) => a !== null) ? near(amounts.reduce((s, a) => s + (a as number), 0), base) : null;
  const totalsAddUp = subtotal !== null && total !== null ? near(subtotal + (tax ?? 0) + (fees ?? 0) - (discount ?? 0), total) : null;
  return {
    ...r,
    items,
    total,
    subtotal,
    tax,
    discount,
    fees,
    notes: r.notes.map((n) => n.trim().slice(0, 200)).filter((n) => n && validNote(n, items.length)).slice(0, 3),
    checks: { itemsMatchSubtotal, totalsAddUp },
  };
}
