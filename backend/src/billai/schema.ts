import { z } from 'zod';
import { SUPPORTED_ASSISTANT_LANGUAGES } from '../assistant/request';

export const BILL_CATEGORIES = ['Food', 'Transport', 'Shopping', 'Housing', 'Health', 'Education', 'Travel', 'Bills', 'Fitness', 'Movies', 'Groceries', 'Dining Out', 'Rent', 'Entertainment', 'Café', 'Other'] as const;

const toNum = (v: unknown): number | null => {
  if (typeof v === 'number') return Number.isFinite(v) ? v : null;
  if (typeof v === 'string' && v.trim()) {
    const n = Number(v.replace(/[^0-9.-]/g, ''));
    return Number.isFinite(n) ? n : null;
  }
  return null;
};
const money = z.preprocess(toNum, z.number().nullable());
const text = (max: number) => z.preprocess((v) => (typeof v === 'string' && v.trim() ? v.trim().slice(0, max) : null), z.string().nullable());

export const billReadSchema = z.object({
  merchant: text(120),
  date: z.preprocess((v) => (typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v) ? v : null), z.string().nullable()),
  currency: z.preprocess((v) => (typeof v === 'string' && /^[A-Za-z]{3}$/.test(v) ? v.toUpperCase() : 'INR'), z.string()),
  total: money,
  subtotal: money,
  tax: money,
  discount: money,
  fees: money,
  // A long supermarket bill must not fail every model: extra items are cut, not rejected.
  items: z.preprocess(
    (v) => (Array.isArray(v) ? v.slice(0, 200) : v),
    z.array(z.object({ name: text(120), qty: money, unitPrice: money, amount: money })).default([]),
  ),
  paymentMethod: text(40),
  billNumber: text(60),
  category: z.preprocess((v) => ((BILL_CATEGORIES as readonly unknown[]).includes(v) ? v : null), z.enum(BILL_CATEGORIES).nullable()),
  confidence: z.preprocess((v) => Math.min(1, Math.max(0, toNum(v) ?? 0)), z.number()),
  summary: z.preprocess((v) => (typeof v === 'string' ? v.trim().slice(0, 600) : ''), z.string()),
  notes: z.array(z.string()).max(10).default([]),
});

export const billAiPrefsSchema = z
  .object({
    language: z.enum(SUPPORTED_ASSISTANT_LANGUAGES).default('English'),
    style: z.enum(['short', 'balanced', 'detailed']).default('balanced'),
    tone: z.enum(['simple', 'friendly', 'professional']).default('friendly'),
  })
  .default({});
export type BillAiPrefs = z.infer<typeof billAiPrefsSchema>;
