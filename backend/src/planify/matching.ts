/**
 * Which spending belongs in which bucket, beyond the exact category name. A bucket "Eating out" should also count
 * a "Food" category, or the merchant "Zomato" filed under "Other", but only when it is a sensible match AND the
 * user said so. Nothing is guessed silently:
 *  - a pair that looks related (same word group, or shared words) is only SUGGESTED, as a Yes/No question;
 *  - Yes makes it count from then on, in every month, past and future; No is never asked again;
 *  - what a person confirmed teaches the next suggestions (a confirmed "Food court" makes "Food truck" look related).
 * Everything here is plain code, so it works offline and every decision can be explained.
 */

export interface TxLite {
  category: string;
  merchant: string;
  type: string; // 'debit' | 'credit'
  amount: number;
}

export interface BucketLite {
  category: string;
  name: string;
  kind: string;
}

export type MatchKind = 'category' | 'merchant';

export interface Alias {
  bucket: string;
  kind: MatchKind;
  key: string;
  label: string;
}

export interface Feedback extends Alias {
  verdict: 'yes' | 'no';
}

export interface Candidate {
  bucket: string;
  kind: MatchKind;
  key: string;
  label: string;
  count: number;
  total: number;
}

export const normalize = (s: string) =>
  s.toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, ' ').replace(/\s+/g, ' ').trim();

const CLUSTERS: Record<string, string[]> = {
  food: ['food', 'eating', 'eat', 'dining', 'dine', 'restaurant', 'cafe', 'lunch', 'dinner', 'breakfast', 'snack', 'meal', 'swiggy', 'zomato', 'canteen', 'takeaway', 'pizza', 'burger', 'biryani', 'coffee', 'tea', 'bakery', 'dominos', 'mcdonalds', 'kfc', 'starbucks'],
  groceries: ['grocery', 'groceries', 'vegetable', 'fruit', 'supermarket', 'kirana', 'mart', 'bigbasket', 'blinkit', 'zepto', 'milk', 'dmart', 'ration'],
  transport: ['transport', 'fuel', 'petrol', 'diesel', 'cab', 'uber', 'ola', 'rapido', 'metro', 'bus', 'auto', 'taxi', 'train', 'commute', 'parking', 'toll', 'fastag'],
  shopping: ['shopping', 'clothes', 'clothing', 'amazon', 'flipkart', 'myntra', 'fashion', 'shoes', 'apparel', 'mall', 'meesho'],
  health: ['health', 'medical', 'medicine', 'pharmacy', 'doctor', 'hospital', 'clinic', 'dental', 'apollo', 'pharmeasy'],
  bills: ['bills', 'electricity', 'power', 'water', 'gas', 'internet', 'wifi', 'broadband', 'recharge', 'mobile', 'phone', 'postpaid', 'utility', 'utilities', 'airtel', 'jio'],
  housing: ['rent', 'housing', 'maintenance', 'society', 'mortgage'],
  loan: ['emi', 'loan', 'installment', 'instalment'],
  entertainment: ['entertainment', 'movie', 'cinema', 'netflix', 'hotstar', 'spotify', 'prime', 'game', 'gaming', 'subscription', 'ott', 'concert'],
  travel: ['travel', 'trip', 'flight', 'hotel', 'holiday', 'vacation', 'irctc', 'makemytrip', 'booking'],
  education: ['education', 'school', 'college', 'course', 'tuition', 'books', 'udemy', 'fees'],
  fitness: ['fitness', 'gym', 'yoga', 'sports', 'workout'],
  gifts: ['gift', 'donation', 'charity', 'birthday'],
  care: ['salon', 'haircut', 'grooming', 'spa', 'beauty', 'cosmetics'],
  insurance: ['insurance', 'premium', 'lic'],
};

/** Names too vague to say anything about what the money was for. */
const VAGUE = new Set(['other', 'others', 'misc', 'miscellaneous', 'general', 'unknown', 'uncategorized', 'uncategorised']);
const STOP = new Set(['and', 'the', 'for', 'out', 'money', 'last', 'month', 'fund']);

const words = (s: string) => normalize(s).split(' ').filter(Boolean);
const hits = (w: string, kw: string) => w === kw || (kw.length >= 4 && w.startsWith(kw));

function clustersOf(text: string): Set<string> {
  const out = new Set<string>();
  const ws = words(text);
  for (const [id, kws] of Object.entries(CLUSTERS)) if (ws.some((w) => kws.some((k) => hits(w, k)))) out.add(id);
  return out;
}

/** 0.9 = same word group; 0.6 = they share a real word; 0 = unrelated. Vague names never match. */
export function similarity(a: string, b: string): number {
  if (VAGUE.has(normalize(a)) || VAGUE.has(normalize(b))) return 0;
  const ca = clustersOf(a);
  for (const c of clustersOf(b)) if (ca.has(c)) return 0.9;
  const wa = new Set(words(a).filter((w) => w.length >= 4 && !STOP.has(w)));
  return words(b).some((w) => wa.has(w)) ? 0.6 : 0;
}

/** What a bucket is "about": its own name plus everything the user confirmed belongs in it. */
function bucketTexts(b: BucketLite, aliases: Alias[]): string[] {
  return [b.name, b.category, ...aliases.filter((a) => a.bucket === b.category).map((a) => a.label)];
}

const best = (texts: string[], candidate: string) => Math.max(0, ...texts.map((t) => similarity(t, candidate)));

/**
 * What to ask the user about. [txs] covers a window around the month (past and future); [feedback] is every Yes/No
 * given so far. At most one question per bucket, the biggest first, and never a pair already answered.
 */
export function findCandidates(txs: TxLite[], buckets: BucketLite[], feedback: Feedback[]): Candidate[] {
  const spendBuckets = buckets.filter((b) => b.kind !== 'savings');
  const bucketCats = new Set(buckets.map((b) => normalize(b.category)));
  const answered = new Set(feedback.map((f) => `${f.bucket}|${f.kind}|${f.key}`));
  const aliases = feedback.filter((f) => f.verdict === 'yes');
  const aliasedCategories = new Set(aliases.filter((a) => a.kind === 'category').map((a) => a.key));

  const groups = new Map<string, Candidate & { score: number }>();
  const note = (bucket: BucketLite, kind: MatchKind, label: string, amount: number, score: number) => {
    const key = normalize(label);
    if (!key || answered.has(`${bucket.category}|${kind}|${key}`)) return;
    const id = `${bucket.category}|${kind}|${key}`;
    const g = groups.get(id) ?? { bucket: bucket.category, kind, key, label, count: 0, total: 0, score };
    g.count += 1;
    g.total += amount;
    groups.set(id, g);
  };

  for (const t of txs) {
    if (t.type !== 'debit') continue;
    const cat = normalize(t.category);
    if (bucketCats.has(cat)) continue; // already counted by its own bucket
    for (const b of spendBuckets) {
      const texts = bucketTexts(b, aliases);
      // Category-level first: "Food" for an "Eating out" bucket.
      if (!aliasedCategories.has(cat) && !VAGUE.has(cat)) {
        const s = best(texts, t.category);
        if (s >= 0.6) { note(b, 'category', t.category, t.amount, s); continue; }
      }
      // Then the merchant: "Zomato" filed under "Other". Stricter, because merchants are noisier.
      if (!aliasedCategories.has(cat) && t.merchant && best(texts, t.merchant) >= 0.9) note(b, 'merchant', t.merchant, t.amount, 0.9);
    }
  }

  const perBucket = new Map<string, Candidate & { score: number }>();
  for (const g of groups.values()) {
    const cur = perBucket.get(g.bucket);
    if (!cur || g.total > cur.total) perBucket.set(g.bucket, g);
  }
  return [...perBucket.values()]
    .sort((a, b) => b.total - a.total)
    .slice(0, 4)
    .map(({ score: _score, ...c }) => c);
}

/**
 * Net spend per bucket category for the given transactions, honouring what the user confirmed. A transaction counts
 * once: its own bucket first, then a confirmed merchant, then a confirmed category. Refunds reduce the same place.
 * Anything matching nothing stays under its own category (and shows up as "unplanned").
 */
export function allocate(txs: TxLite[], buckets: BucketLite[], aliases: Alias[]): Record<string, number> {
  const own = new Map(buckets.map((b) => [normalize(b.category), b.category]));
  const byMerchant = new Map(aliases.filter((a) => a.kind === 'merchant').map((a) => [a.key, a.bucket]));
  const byCategory = new Map(aliases.filter((a) => a.kind === 'category').map((a) => [a.key, a.bucket]));
  const debit: Record<string, number> = {};
  const credit: Record<string, number> = {};
  for (const t of txs) {
    const cat = normalize(t.category);
    const target = own.get(cat) ?? byMerchant.get(normalize(t.merchant)) ?? byCategory.get(cat) ?? t.category;
    const bag = t.type === 'credit' ? credit : debit;
    bag[target] = (bag[target] ?? 0) + t.amount;
  }
  const out: Record<string, number> = {};
  for (const k of new Set([...Object.keys(debit), ...Object.keys(credit)])) out[k] = Math.max(0, (debit[k] ?? 0) - (credit[k] ?? 0));
  return out;
}
