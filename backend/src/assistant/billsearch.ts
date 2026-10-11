export type MatchRow = { id: string; date: Date; amount: number; merchant: string; category: string; note: string | null; billPages: number; read: unknown | null };

/** "₹2,000" matches 1,960–2,040; small amounts get at least ±₹20. */
export function amountWindow(amount: number) {
  const d = Math.max(amount * 0.02, 20);
  return { lo: Math.round((amount - d) * 100) / 100, hi: Math.round((amount + d) * 100) / 100 };
}

const text = (r: MatchRow) => [r.merchant, r.note ?? '', r.category, JSON.stringify(r.read ?? '')].join(' ').toLowerCase();

/** Closest amount first (when asked), then rows whose text matched, then newest. */
export function rankMatches(rows: MatchRow[], q: { amount?: number; query?: string }): MatchRow[] {
  const needle = q.query?.trim().toLowerCase();
  return [...rows].sort((a, b) => {
    if (q.amount !== undefined) {
      const d = Math.abs(a.amount - q.amount) - Math.abs(b.amount - q.amount);
      if (Math.abs(d) > 0.005) return d;
    }
    if (needle) {
      const t = Number(text(b).includes(needle)) - Number(text(a).includes(needle));
      if (t !== 0) return t;
    }
    return b.date.getTime() - a.date.getTime();
  });
}

/** What the model may see from a bill: short, capped, labelled as data by the caller. */
export function fenceBill(read: unknown): { merchant: string | null; total: number | null; items: string[] } | null {
  if (!read || typeof read !== 'object') return null;
  const r = read as { merchant?: unknown; total?: unknown; items?: unknown };
  const items = Array.isArray(r.items) ? r.items : [];
  return {
    merchant: typeof r.merchant === 'string' ? r.merchant.slice(0, 60) : null,
    total: typeof r.total === 'number' && Number.isFinite(r.total) ? r.total : null,
    items: items
      .map((i) => (i && typeof i === 'object' && typeof (i as { name?: unknown }).name === 'string' ? (i as { name: string }).name.slice(0, 60) : null))
      .filter((n): n is string => !!n)
      .slice(0, 5),
  };
}
