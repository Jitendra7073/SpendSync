/**
 * Pure duplicate decision for Smart capture: is this bank message a new payment, the same payment as
 * one already stored (a bank's SMS + email for one UPI transfer), or something the user must confirm?
 * No I/O here — service.ts loads the rows and applies the result.
 */

export type Source = 'sms' | 'email' | 'upi';

export interface Incoming {
  source: Source;
  amount: number;
  type: 'debit' | 'credit';
  at: Date;
  refNo?: string | null;
  last4?: string | null;
  payee?: string | null;
}

/** A live transaction with the same amount and type near the message time. */
export interface Candidate {
  transactionId: string;
  at: Date;
  /** The captured message that created it; null for a manual entry. */
  messageId: string | null;
  source: Source | null;
  refNo: string | null;
  last4: string | null;
  payee: string | null;
}

export interface Prefs {
  mode: 'auto' | 'ask';
  duplicates: 'ask' | 'keep';
}

export type Decision =
  | { status: 'added' }
  | { status: 'duplicate'; transactionId: string | null; duplicateOf: string | null }
  | { status: 'review'; reason: 'possible_duplicate' | 'ask_first'; transactionId: string | null; duplicateOf: string | null };

const MIN = 60_000;
/** Two SMS for one payment arrive together; a bank's email can come many hours later. */
export function windowMs(a: Source | null, b: Source | null): number {
  return a === 'email' || b === 'email' ? 24 * 60 * MIN : 30 * MIN;
}

const letters = (s: string | null | undefined) => (s ?? '').toLowerCase().replace(/[^a-z]/g, '');

/** "ZOMATO LTD" vs "zomato@icici" → same. Too-short names never match. */
export function samePayee(a?: string | null, b?: string | null): boolean {
  const x = letters(a?.split('@')[0]);
  const y = letters(b?.split('@')[0]);
  if (x.length < 3 || y.length < 3) return false;
  return x.includes(y) || y.includes(x);
}

/**
 * @param refMatch a stored message with the same reference number (last 7 days), if any.
 * @param candidates transactions with the same amount + type inside the widest window.
 */
export function decide(
  msg: Incoming,
  refMatch: { messageId: string; transactionId: string | null } | null,
  candidates: Candidate[],
  prefs: Prefs,
): Decision {
  // 1. The bank's reference number is the payment's identity.
  if (refMatch) return { status: 'duplicate', transactionId: refMatch.transactionId, duplicateOf: refMatch.messageId };

  // 2. Same amount and type, close in time, and nothing that proves they differ.
  const near = candidates.filter((c) => {
    if (Math.abs(c.at.getTime() - msg.at.getTime()) > windowMs(msg.source, c.source)) return false;
    if (msg.refNo && c.refNo && msg.refNo !== c.refNo) return false; // two different payments
    if (msg.last4 && c.last4 && msg.last4 !== c.last4) return false; // different account/card
    return true;
  });

  if (near.length === 1) {
    const c = near[0];
    const strong = c.messageId !== null && ((msg.last4 != null && msg.last4 === c.last4) || samePayee(msg.payee, c.payee));
    if (strong) return { status: 'duplicate', transactionId: c.transactionId, duplicateOf: c.messageId };
  }
  // ponytail: several same-amount payments in one day all go to review; a ref number or last 4 settles most.
  if (near.length > 0 && prefs.duplicates === 'ask') {
    const best = [...near].sort((a, b) => Math.abs(a.at.getTime() - msg.at.getTime()) - Math.abs(b.at.getTime() - msg.at.getTime()))[0];
    return { status: 'review', reason: 'possible_duplicate', transactionId: best.transactionId, duplicateOf: best.messageId };
  }

  // 3. A new payment.
  if (prefs.mode === 'ask') return { status: 'review', reason: 'ask_first', transactionId: null, duplicateOf: null };
  return { status: 'added' };
}

/** Account and card numbers keep their last 4 digits; shorter numbers (amounts, dates, OTP-free refs) stay. */
export function maskBody(body: string): string {
  return body.replace(/\d{9,}/g, (d) => '•'.repeat(d.length - 4) + d.slice(-4));
}

/** One-time codes are never stored, even if the phone sent one by mistake. */
export const OTP = /\b(otp|one[\s-]?time\s+pass(word|code)?|verification\s+code|security\s+code)\b/i;
