import type { ToolSpec } from './llm/types';
import { and, desc, eq, gte, ilike, lte, or, sql } from 'drizzle-orm';
import { z } from 'zod';
import { db } from '../db/index';
import { transactions } from '../db/schema/index';
import { liveTx } from '../lib/live';
import { budgetService } from '../services/budget.service';
import { loadPlan } from '../planify/service';
import { dashboardService } from '../services/dashboard.service';
import { holdService } from '../services/hold.service';
import { settingsService } from '../services/settings.service';
import { searchHelp, type ScreenId } from './knowledge';
import { canRun, looksBlocked, type ToolTier } from './permissions';

export interface ToolContext {
  userId: string;
  /** Today in the user's timezone, YYYY-MM-DD. */
  today: string;
}

/** An instruction for the phone, streamed to the app (e.g. show an "Open Budget" button). */
export interface ProposedEntry {
  kind: 'expense' | 'income';
  amount: number;
  category: string;
  note?: string;
  /** Set when money is lent/borrowed and will come back: makes a hold on the person. */
  person?: string;
  returnDate?: string;
  date: string;
}

export interface ProposedFollowup {
  person: string;
  direction: 'owed_to_me' | 'owed_by_me';
  amount: number;
  dueDate: string;
  overdueDays: number;
  channel?: 'whatsapp' | 'sms' | 'email';
  tone?: 'gentle' | 'friendly' | 'firm';
  context?: string;
}

export interface ProposedMessage {
  message: string;
  channel: 'whatsapp' | 'sms' | 'email';
  toName?: string;
  subject?: string;
}

export type UiAction =
  | { type: 'compose'; compose: ProposedMessage }
  | { type: 'open_screen'; screen: ScreenId }
  | { type: 'propose_entry'; entry: ProposedEntry }
  | { type: 'followup'; followup: ProposedFollowup };

export interface ToolResult {
  data: unknown;
  uiAction?: UiAction;
}

export interface AssistantTool<I = unknown> {
  name: string;
  description: string;
  tier: ToolTier;
  input: z.ZodType<I>;
  jsonSchema: { type: 'object'; properties?: Record<string, unknown>; required?: string[]; additionalProperties?: boolean };
  run: (ctx: ToolContext, input: I) => Promise<ToolResult>;
}

const SCREENS = ['home', 'analytics', 'budget', 'planify', 'profile', 'holds', 'add_transaction', 'support'] as const;
const ISO_DAY = /^\d{4}-\d{2}-\d{2}$/;
const MONTH = /^\d{4}-(0[1-9]|1[0-2])$/;

const num = (v: unknown) => (v === null || v === undefined ? 0 : Number(v));
const money = (v: unknown) => Math.round(num(v) * 100) / 100;

/** Defines a tool and checks (at import time) that its name is not a blocked action. */
function tool<I>(t: AssistantTool<I>): AssistantTool<I> {
  if (looksBlocked(t.name)) throw new Error(`Tool "${t.name}" looks like a blocked action`);
  return t;
}

// ── Read tools ───────────────────────────────────────────────────────────────

const getBalance = tool({
  name: 'get_balance',
  tier: 'read',
  description:
    "The user's all-time net balance (everything earned minus everything spent), plus money currently on hold: what others still owe the user and what the user still owes others. Use for 'what is my balance'.",
  input: z.object({}),
  jsonSchema: { type: 'object', properties: {}, additionalProperties: false },
  async run(ctx) {
    const [row] = await db
      .select({
        credit: sql<string>`COALESCE(SUM(CASE WHEN ${transactions.type} = 'credit' THEN ${transactions.amount}::numeric ELSE 0 END), 0)`,
        debit: sql<string>`COALESCE(SUM(CASE WHEN ${transactions.type} = 'debit' THEN ${transactions.amount}::numeric ELSE 0 END), 0)`,
      })
      .from(transactions)
      .where(and(eq(transactions.userId, ctx.userId), liveTx));
    const pending = await holdService.getAll(ctx.userId, { status: 'pending' });
    const owedToMe = pending.filter((h) => h.direction === 'owed_to_me').reduce((s, h) => s + num(h.amount), 0);
    const owedByMe = pending.filter((h) => h.direction === 'owed_by_me').reduce((s, h) => s + num(h.amount), 0);
    const net = money(num(row.credit) - num(row.debit));
    return {
      data: {
        currency: 'INR',
        total_earned: money(row.credit),
        total_spent: money(row.debit),
        net_balance: net,
        pending_owed_to_user: money(owedToMe),
        pending_user_owes: money(owedByMe),
        net_balance_including_holds: money(net + owedToMe - owedByMe),
      },
    };
  },
});

const getSpendingSummary = tool({
  name: 'get_spending_summary',
  tier: 'read',
  description:
    "Income, spending, net and a per-category breakdown (with any budget) for one calendar month, plus the last 6 months' trend. Defaults to the current month. Use for 'how much did I spend in <month>', 'where does my money go', category totals.",
  input: z.object({ month: z.string().regex(MONTH).optional() }),
  jsonSchema: {
    type: 'object',
    properties: { month: { type: 'string', description: 'Month as YYYY-MM. Omit for the current month.' } },
    additionalProperties: false,
  },
  async run(ctx, input) {
    const month = input.month ?? ctx.today.slice(0, 7);
    const s = await dashboardService.getSummary(ctx.userId, month);
    return {
      data: {
        currency: 'INR',
        month: s.month,
        total_spent: money(s.totals.totalSpent),
        total_earned: money(s.totals.totalEarned),
        net: money(s.totals.netAmount),
        transaction_count: s.totals.totalTransactions,
        categories: s.categoryBreakdown
          .map((c) => ({
            category: c.category,
            spent: money(c.spent),
            earned: money(c.earned),
            transactions: Number(c.transactionCount),
            budget: c.budget === null ? null : money(c.budget),
            percent_of_budget_used: c.percentageUsed === null ? null : Math.round(c.percentageUsed),
          }))
          .sort((a, b) => b.spent - a.spent),
        recent_months: s.monthlyTrend.map((m) => ({
          month: m.month,
          spent: money(m.spent),
          earned: money(m.earned),
          net: money(m.net),
        })),
      },
    };
  },
});

const searchTransactionsInput = z.object({
  query: z.string().max(100).optional(),
  category: z.string().max(100).optional(),
  type: z.enum(['debit', 'credit']).optional(),
  start_date: z.string().regex(ISO_DAY).optional(),
  end_date: z.string().regex(ISO_DAY).optional(),
  min_amount: z.number().nonnegative().optional(),
  max_amount: z.number().nonnegative().optional(),
  sort: z.enum(['newest', 'largest']).optional(),
  limit: z.number().int().min(1).max(20).optional(),
});

const searchTransactions = tool({
  name: 'search_transactions',
  tier: 'read',
  description:
    "Find the user's transactions. Returns up to `limit` rows PLUS exact totals (count, total_spent, total_earned) over ALL matches — always quote those totals instead of adding rows yourself. type 'debit' = money out (expense), 'credit' = money in (income). Dates are YYYY-MM-DD, inclusive. `query` matches merchant, note and category text.",
  input: searchTransactionsInput,
  jsonSchema: {
    type: 'object',
    properties: {
      query: { type: 'string', description: 'Text to look for in merchant, note or category.' },
      category: { type: 'string', description: 'Exact category name, e.g. Food.' },
      type: { type: 'string', enum: ['debit', 'credit'] },
      start_date: { type: 'string', description: 'YYYY-MM-DD, inclusive.' },
      end_date: { type: 'string', description: 'YYYY-MM-DD, inclusive.' },
      min_amount: { type: 'number' },
      max_amount: { type: 'number' },
      sort: { type: 'string', enum: ['newest', 'largest'], description: 'Default newest.' },
      limit: { type: 'integer', description: 'Rows to return, 1-20. Default 10.' },
    },
    additionalProperties: false,
  },
  async run(ctx, input) {
    const conditions = [eq(transactions.userId, ctx.userId), liveTx];
    if (input.query) {
      // Escape LIKE wildcards so user text is matched literally.
      const like = `%${input.query.replace(/[\\%_]/g, (c) => `\\${c}`)}%`;
      conditions.push(
        or(ilike(transactions.merchant, like), ilike(transactions.note, like), ilike(transactions.category, like))!,
      );
    }
    if (input.category) conditions.push(eq(transactions.category, input.category));
    if (input.type) conditions.push(eq(transactions.type, input.type));
    if (input.start_date) conditions.push(gte(transactions.createdAt, new Date(`${input.start_date}T00:00:00.000Z`)));
    if (input.end_date) conditions.push(lte(transactions.createdAt, new Date(`${input.end_date}T23:59:59.999Z`)));
    if (input.min_amount !== undefined) conditions.push(sql`${transactions.amount}::numeric >= ${input.min_amount}`);
    if (input.max_amount !== undefined) conditions.push(sql`${transactions.amount}::numeric <= ${input.max_amount}`);
    const where = and(...conditions);

    const rows = await db
      .select()
      .from(transactions)
      .where(where)
      .orderBy(input.sort === 'largest' ? sql`${transactions.amount}::numeric DESC` : desc(transactions.createdAt))
      .limit(input.limit ?? 10);

    const [agg] = await db
      .select({
        count: sql<number>`COUNT(*)`,
        debit: sql<string>`COALESCE(SUM(CASE WHEN ${transactions.type} = 'debit' THEN ${transactions.amount}::numeric ELSE 0 END), 0)`,
        credit: sql<string>`COALESCE(SUM(CASE WHEN ${transactions.type} = 'credit' THEN ${transactions.amount}::numeric ELSE 0 END), 0)`,
      })
      .from(transactions)
      .where(where);

    return {
      data: {
        currency: 'INR',
        matching_count: Number(agg.count),
        total_spent: money(agg.debit),
        total_earned: money(agg.credit),
        showing: rows.length,
        transactions: rows.map((t) => ({
          date: t.createdAt.toISOString().slice(0, 10),
          type: t.type,
          amount: money(t.amount),
          category: t.category,
          merchant: t.merchant,
          note: t.note,
        })),
      },
    };
  },
});

const getBudgetStatus = tool({
  name: 'get_budget_status',
  tier: 'read',
  description:
    "Each category budget for a month with the limit, amount spent, remaining and status (on_track under 75% used, near_limit 75-100%, over above 100%). Defaults to the current month. Use for 'am I over budget', 'how is my food budget'.",
  input: z.object({ month: z.string().regex(MONTH).optional() }),
  jsonSchema: {
    type: 'object',
    properties: { month: { type: 'string', description: 'YYYY-MM. Omit for the current month.' } },
    additionalProperties: false,
  },
  async run(ctx, input) {
    const month = input.month ?? ctx.today.slice(0, 7);
    const [budgets, summary] = await Promise.all([
      budgetService.getAll(ctx.userId, { month }),
      dashboardService.getSummary(ctx.userId, month),
    ]);
    const spentBy = new Map(summary.categoryBreakdown.map((c) => [c.category, c.spent]));
    const items = budgets.map((b) => {
      const limit = num(b.limitAmount);
      const spent = spentBy.get(b.category) ?? 0;
      const pct = limit > 0 ? (spent / limit) * 100 : 0;
      return {
        category: b.category,
        limit: money(limit),
        spent: money(spent),
        remaining: money(limit - spent),
        percent_used: Math.round(pct),
        status: pct > 100 ? 'over' : pct >= 75 ? 'near_limit' : 'on_track',
      };
    });
    return {
      data: {
        currency: 'INR',
        month,
        total_budget: money(items.reduce((s, i) => s + i.limit, 0)),
        total_spent_on_budgeted: money(items.reduce((s, i) => s + i.spent, 0)),
        budgets: items.sort((a, b) => b.percent_used - a.percent_used),
      },
    };
  },
});

const getPlanStatus = tool({
  name: 'get_plan_status',
  tier: 'read',
  description:
    "The user's Planify monthly plan for a month: money planned, what is left to plan, how much is safe to spend today, days left, and every bucket (fixed, spend or savings) with its limit, spent, remaining, percent, state (ok, close, over, paid, saved) and the day it would run out at the current pace, plus spending outside the plan and any spending the app is waiting for the user to confirm. Defaults to the current month. Use for 'how is my plan', 'can I spend X today', 'which bucket am I over', 'what is safe to spend'.",
  input: z.object({ month: z.string().regex(MONTH).optional() }),
  jsonSchema: {
    type: 'object',
    properties: { month: { type: 'string', description: 'YYYY-MM. Omit for the current month.' } },
    additionalProperties: false,
  },
  async run(ctx, input) {
    const month = input.month ?? ctx.today.slice(0, 7);
    const plan = await loadPlan(ctx.userId, month, ctx.today);
    if (!plan.exists) {
      return { data: { currency: 'INR', month, has_plan: false, note: 'There is no plan for this month yet. The user can build one on the Planify tab.' } };
    }
    const s = plan.status;
    return {
      data: {
        currency: 'INR',
        month,
        has_plan: true,
        money_to_plan: money(s.available),
        planned: money(s.planned),
        left_to_plan: money(s.leftToPlan),
        spent_in_plan: money(s.spentInPlan),
        safe_to_spend_today: money(s.safeToSpendToday),
        days_left: s.daysLeft,
        buckets_ok: s.counts.ok,
        buckets_close: s.counts.close,
        buckets_over: s.counts.over,
        buckets: s.buckets.map((b) => ({
          name: b.name,
          kind: b.kind,
          limit: money(b.limit),
          spent: money(b.spent),
          remaining: money(b.remaining),
          percent_used: Math.round(b.percent),
          state: b.state,
          runs_out_on_day: b.runsOutOnDay ?? null,
        })),
        spent_outside_plan: money(s.unplannedTotal),
        outside_plan: s.unplanned.slice(0, 5).map((u) => ({ category: u.category, spent: money(u.spent) })),
        waiting_for_confirmation: plan.matches.map((m) => ({ bucket: m.bucket, looks_like: m.label, spends: m.count })),
      },
    };
  },
});

const listHolds = tool({
  name: 'list_holds',
  tier: 'read',
  description:
    "Money the user lent (direction owed_to_me) or borrowed (owed_by_me), per person with amount, due date and status (pending or settled), plus pending totals. Use for 'who owes me', 'what do I owe'.",
  input: z.object({
    status: z.enum(['pending', 'settled']).optional(),
    direction: z.enum(['owed_to_me', 'owed_by_me']).optional(),
  }),
  jsonSchema: {
    type: 'object',
    properties: {
      status: { type: 'string', enum: ['pending', 'settled'] },
      direction: { type: 'string', enum: ['owed_to_me', 'owed_by_me'] },
    },
    additionalProperties: false,
  },
  async run(ctx, input) {
    const rows = await holdService.getAll(ctx.userId, input);
    const pending = rows.filter((h) => h.status === 'pending');
    return {
      data: {
        currency: 'INR',
        pending_owed_to_user: money(pending.filter((h) => h.direction === 'owed_to_me').reduce((s, h) => s + num(h.amount), 0)),
        pending_user_owes: money(pending.filter((h) => h.direction === 'owed_by_me').reduce((s, h) => s + num(h.amount), 0)),
        holds: rows.slice(0, 30).map((h) => ({
          person: h.personName,
          direction: h.direction,
          amount: money(h.amount),
          due: h.expectedReturnDate.toISOString().slice(0, 10),
          status: h.status,
        })),
      },
    };
  },
});

const getTopMerchants = tool({
  name: 'get_top_merchants',
  tier: 'read',
  description: "The merchants the user has spent the most at, all time, with total and number of transactions.",
  input: z.object({ limit: z.number().int().min(1).max(10).optional() }),
  jsonSchema: {
    type: 'object',
    properties: { limit: { type: 'integer', description: '1-10. Default 5.' } },
    additionalProperties: false,
  },
  async run(ctx, input) {
    const rows = await dashboardService.getTopMerchants(ctx.userId, input.limit ?? 5);
    return {
      data: {
        currency: 'INR',
        merchants: rows.map((r) => ({ merchant: r.merchant, total_spent: money(r.totalSpent), transactions: Number(r.count) })),
      },
    };
  },
});

const getSettings = tool({
  name: 'get_settings',
  tier: 'read',
  description:
    "The user's current app settings: language, theme, accent colour, date format, notification switches, auto backup, whether amounts are hidden behind a PIN and for how long, and whether auto-capture is on. Use for 'what language am I using', 'is auto-capture on'. Never includes the PIN.",
  input: z.object({}),
  jsonSchema: { type: 'object', properties: {}, additionalProperties: false },
  async run(ctx) {
    const s = await settingsService.getSettings(ctx.userId);
    return {
      data: {
        language: s.language,
        theme: s.themeMode,
        accent_colour: s.accentColor,
        date_format: s.dateFormat,
        push_notifications: s.pushNotifications,
        email_notifications: s.emailNotifications,
        auto_backup: s.autoBackup,
        hide_large_amounts_with_pin: s.amountMaskingEnabled,
        amounts_visible_for_seconds_after_unlock: s.amountVisibilitySeconds,
        auto_capture_payments: s.autoCaptureEnabled,
        auto_capture_apps: s.autoCapturePackages ? s.autoCapturePackages.split(',').filter(Boolean).length : 0,
      },
    };
  },
});

const searchHelpTool = tool({
  name: 'search_help',
  tier: 'read',
  description:
    "Look up how SpendSync works: features, where a setting lives, step-by-step instructions, troubleshooting. ALWAYS use this for 'how do I…', 'where is…', 'what does … do' questions about the app, and answer only from what it returns. Returns nothing when the question is not about SpendSync.",
  input: z.object({ query: z.string().min(2).max(200) }),
  jsonSchema: {
    type: 'object',
    properties: { query: { type: 'string', description: 'The topic, in English keywords.' } },
    required: ['query'],
    additionalProperties: false,
  },
  async run(_ctx, input) {
    const hits = searchHelp(input.query, 3);
    if (hits.length === 0) {
      return { data: { found: false, note: 'No help entry matches. The question may be outside SpendSync.' } };
    }
    return {
      data: {
        found: true,
        entries: hits.map((h) => ({ title: h.entry.title, text: h.entry.text, screen: h.entry.screen ?? null })),
      },
    };
  },
});

// ── Navigate ─────────────────────────────────────────────────────────────────

const openScreen = tool({
  name: 'open_screen',
  tier: 'navigate',
  description:
    "Offer the user an 'Open <screen>' button. Use after answering when the user would act on a screen: home, analytics, planify (the monthly plan, also used for budgets), budget, profile (settings and account), holds, add_transaction, support (report a problem to the support team). Only offers; the user taps it.",
  input: z.object({ screen: z.enum(SCREENS) }),
  jsonSchema: {
    type: 'object',
    properties: { screen: { type: 'string', enum: [...SCREENS] } },
    required: ['screen'],
    additionalProperties: false,
  },
  async run(_ctx, input) {
    return { data: { offered: input.screen }, uiAction: { type: 'open_screen', screen: input.screen } };
  },
});


const EXPENSE_CATEGORIES = ['Food', 'Transport', 'Shopping', 'Housing', 'Health', 'Education', 'Travel', 'Bills', 'Fitness', 'Movies'];
const INCOME_CATEGORIES = ['Salary', 'Freelance', 'Business', 'Gift', 'Investment'];

const proposeInput = z
  .object({
    kind: z.enum(['expense', 'income']),
    amount: z.number().positive().max(1_000_000_000),
    category: z.string().trim().min(1).max(60),
    note: z.string().trim().max(200).optional(),
    person_name: z.string().trim().min(1).max(100).optional(),
    return_date: z.string().regex(ISO_DAY).optional(),
    date: z.string().regex(ISO_DAY).optional(),
  })
  .refine((v) => !!v.person_name === !!v.return_date, { message: 'person_name and return_date must be given together' });

const proposeEntry = tool({
  name: 'propose_entry',
  tier: 'propose',
  description:
    "Prepare a NEW expense or income for the user and show a confirm card. NOTHING is saved until the user taps Confirm in the app, so never say it is saved or added; say you have prepared it and ask them to confirm. " +
    "kind 'expense' = money went out, 'income' = money came in. amount is a plain number in rupees. " +
    `category: for expenses prefer one of ${EXPENSE_CATEGORIES.join(', ')} (or 'Other'); for income one of ${INCOME_CATEGORIES.join(', ')} (or 'Other'). Always English. note: what it was for, a few words. date: YYYY-MM-DD when it happened (default today, never in the future). ` +
    "MONEY LENT OR BORROWED THAT WILL COME BACK: give person_name and return_date (YYYY-MM-DD, today or later) and the app also creates a hold. " +
    "'I gave/sent/lent X rupees to <person>, they will return it on <date>' => kind expense + person_name + return_date. 'I took/borrowed X from <person>, I will return it on <date>' => kind income + person_name + return_date. " +
    "If the day is only given as a day number (e.g. 'the 5th'), use the next such day on or after today. If the amount or the person is missing, ask ONE short question instead of calling this.",
  input: proposeInput,
  jsonSchema: {
    type: 'object',
    properties: {
      kind: { type: 'string', enum: ['expense', 'income'] },
      amount: { type: 'number' },
      category: { type: 'string' },
      note: { type: 'string' },
      person_name: { type: 'string' },
      return_date: { type: 'string' },
      date: { type: 'string' },
    },
    required: ['kind', 'amount', 'category'],
    additionalProperties: false,
  },
  async run(ctx, input) {
    const date = input.date ?? ctx.today;
    if (date > ctx.today) throw new Error('The date cannot be in the future. Use today or an earlier day.');
    if (input.return_date && input.return_date < ctx.today) throw new Error('return_date must be today or later.');
    const entry: ProposedEntry = {
      kind: input.kind,
      amount: Math.round(input.amount * 100) / 100,
      category: input.category,
      ...(input.note ? { note: input.note } : {}),
      ...(input.person_name ? { person: input.person_name, returnDate: input.return_date } : {}),
      date,
    };
    return {
      data: { status: 'confirm_card_shown', saved: false, message: 'The user must tap Confirm in the app. Nothing has been saved yet.' },
      uiAction: { type: 'propose_entry', entry },
    };
  },
});

const shareMessage = tool({
  name: 'share_message',
  tier: 'propose',
  description:
    "Prepare a message for the user to send through WhatsApp, SMS or email (to a person they name, or to anyone they pick). Use when the user says 'send this on WhatsApp', 'message this to Asha', 'email this', 'share this with my wife', including when they reply to one of your earlier answers (the <replying_to> block is then the content to send). " +
    "You CAN do this: it shows a small card where the user checks the message, picks or confirms the contact and taps Send; the app then opens WhatsApp/SMS/email with the text ready and the user taps Send there. NOTHING is sent by you, so say you prepared it for them to check, and NEVER say you cannot send messages. " +
    "message: the exact text to send, as plain text without markdown (copy it from the quoted answer when replying; keep numbers exactly). Write it in the voice of the user. Do not add facts. channel: whatsapp unless the user says sms or email. to_name: the person's name only if the user said it. subject: only for email, a few words. " +
    "For a reminder about money on hold use prepare_followup instead.",
  input: z.object({
    message: z.string().min(1).max(1500),
    channel: z.enum(['whatsapp', 'sms', 'email']).default('whatsapp'),
    to_name: z.string().max(80).optional(),
    subject: z.string().max(120).optional(),
  }),
  jsonSchema: {
    type: 'object',
    properties: {
      message: { type: 'string' },
      channel: { type: 'string', enum: ['whatsapp', 'sms', 'email'] },
      to_name: { type: 'string' },
      subject: { type: 'string' },
    },
    required: ['message'],
    additionalProperties: false,
  },
  async run(_ctx, input) {
    const compose: ProposedMessage = {
      message: input.message.trim(),
      channel: input.channel ?? 'whatsapp',
      ...(input.to_name ? { toName: input.to_name.trim() } : {}),
      ...(input.subject ? { subject: input.subject.trim() } : {}),
    };
    return {
      data: { status: 'message_card_shown', sent: false, message: 'A card was shown in the chat. The user checks the message, picks the contact and taps Send. Nothing has been sent by you.' },
      uiAction: { type: 'compose', compose },
    };
  },
});

const prepareFollowup = tool({
  name: 'prepare_followup',
  tier: 'propose',
  description:
    "Start a follow-up message to a person the user has money on hold with (they lent them money, or borrowed from them): a reminder over WhatsApp, SMS or email. " +
    "Use when the user says things like 'remind Asha about the money', 'message Uttam', 'send a follow up to Ravi on WhatsApp'. It shows a card in the chat where the app writes the message and the user must confirm before anything is sent. " +
    "NOTHING is sent by you: never say it was sent; say you started a message for them to check. person_name is the name as the user said it. channel and tone are optional (use only if the user said so). " +
    "context: extra detail the user asked to mention, in a few words. If the user did not say who, ask ONE short question instead of calling this.",
  input: z.object({
    person_name: z.string().min(1).max(80),
    channel: z.enum(['whatsapp', 'sms', 'email']).optional(),
    tone: z.enum(['gentle', 'friendly', 'firm']).optional(),
    context: z.string().max(300).optional(),
  }),
  jsonSchema: {
    type: 'object',
    properties: {
      person_name: { type: 'string' },
      channel: { type: 'string', enum: ['whatsapp', 'sms', 'email'] },
      tone: { type: 'string', enum: ['gentle', 'friendly', 'firm'] },
      context: { type: 'string' },
    },
    required: ['person_name'],
    additionalProperties: false,
  },
  async run(ctx, input) {
    const wanted = input.person_name.trim().toLowerCase();
    const all = (await holdService.getAll(ctx.userId, { status: 'pending' })).filter((h) => h.status === 'pending');
    const same = all.filter((h) => h.personName.trim().toLowerCase() === wanted);
    const mine = same.length ? same : all.filter((h) => h.personName.toLowerCase().includes(wanted) || wanted.includes(h.personName.trim().toLowerCase()));
    if (!mine.length) throw new Error(`There is no pending hold with ${input.person_name}. Tell the user, and offer to open the Holds screen.`);
    const people = new Set(mine.map((h) => h.personName.trim().toLowerCase()));
    if (people.size > 1) throw new Error(`More than one person matches: ${[...new Set(mine.map((h) => h.personName))].join(', ')}. Ask the user which one.`);
    const directions = new Set(mine.map((h) => h.direction));
    if (directions.size > 1) throw new Error('This person both owes the user and is owed by the user. Ask which one the message is about.');
    const direction = mine[0].direction as 'owed_to_me' | 'owed_by_me';
    const due = mine.map((h) => h.expectedReturnDate.toISOString().slice(0, 10)).sort()[0];
    const overdueDays = Math.max(0, Math.floor((Date.parse(ctx.today) - Date.parse(due)) / 86_400_000));
    const followup: ProposedFollowup = {
      person: mine[0].personName,
      direction,
      amount: Math.round(mine.reduce((s, h) => s + num(h.amount), 0) * 100) / 100,
      dueDate: due,
      overdueDays,
      ...(input.channel ? { channel: input.channel } : {}),
      ...(input.tone ? { tone: input.tone } : {}),
      ...(input.context ? { context: input.context } : {}),
    };
    return {
      data: { status: 'followup_card_shown', sent: false, message: 'A card was shown in the chat. The app writes the message and the user must confirm before it is sent. Nothing has been sent.' },
      uiAction: { type: 'followup', followup },
    };
  },
});

// ── Registry ─────────────────────────────────────────────────────────────────

/** Order is fixed on purpose: a stable tool list keeps the prompt cache warm. */
export const ASSISTANT_TOOLS: AssistantTool<any>[] = [
  searchHelpTool,
  getBalance,
  getSpendingSummary,
  searchTransactions,
  getBudgetStatus,
  getPlanStatus,
  listHolds,
  getTopMerchants,
  getSettings,
  proposeEntry,
  prepareFollowup,
  shareMessage,
  openScreen,
];

const byName = new Map(ASSISTANT_TOOLS.map((t) => [t.name, t]));

export function findTool(name: string): AssistantTool<any> | undefined {
  return byName.get(name);
}

/** The tools a request may use: runnable tiers only, minus anything the user turned off in Settings → Assistant. */
export function toolSpecs(disabled: ReadonlySet<string> = new Set()): ToolSpec[] {
  return ASSISTANT_TOOLS.filter((t) => canRun(t.tier) && !disabled.has(t.name)).map((t) => ({
    name: t.name,
    description: t.description,
    parameters: t.jsonSchema as ToolSpec['parameters'],
  }));
}

/** What the Settings screen lists: every tool the assistant has, with its tier. */
export function toolCatalog(): Array<{ name: string; tier: ToolTier }> {
  return ASSISTANT_TOOLS.map((t) => ({ name: t.name, tier: t.tier }));
}
