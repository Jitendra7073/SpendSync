import { searchHelp, type ScreenId } from './knowledge';
import { findTool, type ToolContext } from './tools';

type Lang = 'English' | 'Hindi' | 'Spanish' | 'French' | 'German';

/**
 * Two safety nets for when the AI models can't be used.
 *
 *  1. `prefetchData` — for a model that can't call functions, the data is looked up first and put
 *     in the prompt ("context mode").
 *  2. `offlineAnswer` — if EVERY model fails, the user still gets a real answer (the matching help
 *     entry and/or their own numbers) instead of an error. No AI involved, so it can't fail the same way.
 */

const ALL_TOOLS = ['search_help', 'get_balance', 'get_spending_summary', 'get_budget_status', 'list_holds'] as const;

async function runTool(ctx: ToolContext, name: string, input: unknown): Promise<unknown | undefined> {
  const tool = findTool(name);
  if (!tool) return undefined;
  const parsed = tool.input.safeParse(input);
  if (!parsed.success) return undefined;
  try {
    return (await tool.run(ctx, parsed.data)).data;
  } catch {
    return undefined;
  }
}

/** Looks up the usual facts and returns them as one prompt-ready block. */
export async function prefetchData(ctx: ToolContext, question: string, disabled: ReadonlySet<string>): Promise<string> {
  const wanted = ALL_TOOLS.filter((t) => !disabled.has(t));
  const results = await Promise.all(
    wanted.map(async (name) => {
      const input = name === 'search_help' ? { query: question.slice(0, 200) } : name === 'list_holds' ? { status: 'pending' } : {};
      return [name, await runTool(ctx, name, input)] as const;
    }),
  );
  const body = results
    .filter(([, data]) => data !== undefined)
    .map(([name, data]) => `${name}: ${JSON.stringify(data)}`)
    .join('\n');
  return `<data>\n${body.slice(0, 6000)}\n</data>`;
}

// ── Offline answer ───────────────────────────────────────────────────────────

interface Words {
  helpIntro: string;
  dataIntro: string;
  busy: string;
  balance: string;
  spent: string;
  earned: string;
  net: string;
  budget: string;
  owedToYou: string;
  youOwe: string;
  over: string;
  nearLimit: string;
  followups: string[];
}

const WORDS: Record<Lang, Words> = {
  English: {
    helpIntro: 'My AI helpers are busy right now, but here is what the app guide says:',
    dataIntro: 'My AI helpers are busy right now, but here are your numbers:',
    busy: 'My AI helpers are busy right now. Please try again in a minute — meanwhile I can still show your balance or spending.',
    balance: 'Net balance', spent: 'Spent this month', earned: 'Earned this month', net: 'Net this month',
    budget: 'Budgets', owedToYou: 'Owed to you', youOwe: 'You owe', over: 'over', nearLimit: 'near limit',
    followups: ["What's my balance?", 'How much did I spend this month?', 'Am I over any budget?'],
  },
  Hindi: {
    helpIntro: 'मेरे AI सहायक अभी व्यस्त हैं, लेकिन ऐप गाइड यह कहता है:',
    dataIntro: 'मेरे AI सहायक अभी व्यस्त हैं, लेकिन ये रहे आपके आँकड़े:',
    busy: 'मेरे AI सहायक अभी व्यस्त हैं। कृपया एक मिनट में फिर कोशिश करें — तब तक मैं आपका बैलेंस या खर्च दिखा सकता हूँ।',
    balance: 'कुल बैलेंस', spent: 'इस महीने का खर्च', earned: 'इस महीने की कमाई', net: 'इस महीने का शुद्ध',
    budget: 'बजट', owedToYou: 'आपको मिलने हैं', youOwe: 'आपको देना है', over: 'सीमा पार', nearLimit: 'सीमा के पास',
    followups: ['मेरा बैलेंस कितना है?', 'इस महीने मैंने कितना खर्च किया?', 'क्या मैं किसी बजट से ज़्यादा खर्च कर चुका हूँ?'],
  },
  Spanish: {
    helpIntro: 'Mis asistentes de IA están ocupados ahora, pero esto dice la guía de la app:',
    dataIntro: 'Mis asistentes de IA están ocupados ahora, pero aquí están tus cifras:',
    busy: 'Mis asistentes de IA están ocupados. Inténtalo de nuevo en un minuto; mientras tanto puedo mostrarte tu saldo o tus gastos.',
    balance: 'Saldo neto', spent: 'Gastado este mes', earned: 'Ingresado este mes', net: 'Neto este mes',
    budget: 'Presupuestos', owedToYou: 'Te deben', youOwe: 'Debes', over: 'excedido', nearLimit: 'cerca del límite',
    followups: ['¿Cuál es mi saldo?', '¿Cuánto gasté este mes?', '¿Me he pasado de algún presupuesto?'],
  },
  French: {
    helpIntro: "Mes assistants IA sont occupés, mais voici ce que dit le guide de l'application :",
    dataIntro: 'Mes assistants IA sont occupés, mais voici vos chiffres :',
    busy: "Mes assistants IA sont occupés. Réessayez dans une minute ; en attendant, je peux vous montrer votre solde ou vos dépenses.",
    balance: 'Solde net', spent: 'Dépensé ce mois-ci', earned: 'Gagné ce mois-ci', net: 'Net ce mois-ci',
    budget: 'Budgets', owedToYou: 'On vous doit', youOwe: 'Vous devez', over: 'dépassé', nearLimit: 'proche de la limite',
    followups: ['Quel est mon solde ?', 'Combien ai-je dépensé ce mois-ci ?', 'Ai-je dépassé un budget ?'],
  },
  German: {
    helpIntro: 'Meine KI-Helfer sind gerade beschäftigt, aber das sagt der App-Leitfaden:',
    dataIntro: 'Meine KI-Helfer sind gerade beschäftigt, aber hier sind deine Zahlen:',
    busy: 'Meine KI-Helfer sind gerade beschäftigt. Versuche es in einer Minute erneut – bis dahin kann ich dir Kontostand oder Ausgaben zeigen.',
    balance: 'Nettosaldo', spent: 'Diesen Monat ausgegeben', earned: 'Diesen Monat eingenommen', net: 'Netto diesen Monat',
    budget: 'Budgets', owedToYou: 'Dir geschuldet', youOwe: 'Du schuldest', over: 'überschritten', nearLimit: 'nahe am Limit',
    followups: ['Wie hoch ist mein Kontostand?', 'Wie viel habe ich diesen Monat ausgegeben?', 'Habe ich ein Budget überschritten?'],
  },
};

const inr = (n: unknown) => {
  const v = Number(n) || 0;
  return `₹${v.toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;
};

const INTENT = {
  balance: /balance|saldo|solde|kontostand|बैलेंस|शेष|how much (money )?do i have/i,
  spending: /spen[dt]|expens|kharch|खर्च|gast|dépens|depens|ausgab|this month|इस महीने|este mes|ce mois|diesen monat/i,
  budget: /budget|बजट|presupuesto|limit/i,
  holds: /\bowe|\bowed|hold|udhaar|उधार|debe|doit|schulde|lent|borrow/i,
};

export interface OfflineAnswer {
  text: string;
  actions: ScreenId[];
  followups: string[];
}

/** A real answer built from the help guide and the user's own numbers — no AI required. */
export async function offlineAnswer(
  ctx: ToolContext,
  question: string,
  language: string,
  disabled: ReadonlySet<string>,
): Promise<OfflineAnswer> {
  const w = WORDS[(language in WORDS ? language : 'English') as Lang];
  const lines: string[] = [];
  const actions: ScreenId[] = [];

  const data = async (name: string, input: unknown = {}) => (disabled.has(name) ? undefined : await runTool(ctx, name, input));

  if (INTENT.balance.test(question)) {
    const d = (await data('get_balance')) as Record<string, number> | undefined;
    if (d) {
      lines.push(`${w.balance}: ${inr(d.net_balance)}`);
      if (d.pending_owed_to_user) lines.push(`${w.owedToYou}: ${inr(d.pending_owed_to_user)}`);
      if (d.pending_user_owes) lines.push(`${w.youOwe}: ${inr(d.pending_user_owes)}`);
      actions.push('home');
    }
  }
  if (INTENT.spending.test(question)) {
    const d = (await data('get_spending_summary')) as Record<string, number> | undefined;
    if (d) {
      lines.push(`${w.spent}: ${inr(d.total_spent)}`, `${w.earned}: ${inr(d.total_earned)}`, `${w.net}: ${inr(d.net)}`);
      actions.push('analytics');
    }
  }
  if (INTENT.budget.test(question)) {
    const d = (await data('get_budget_status')) as { budgets?: Array<{ category: string; percent_used: number; status: string }> } | undefined;
    const risky = d?.budgets?.filter((b) => b.status !== 'on_track') ?? [];
    if (d?.budgets?.length) {
      lines.push(
        `${w.budget}: ` +
          (risky.length ? risky.map((b) => `${b.category} ${b.percent_used}% (${b.status === 'over' ? w.over : w.nearLimit})`).join(', ') : `${d.budgets.length} ✓`),
      );
      actions.push('budget');
    }
  }
  if (INTENT.holds.test(question)) {
    const d = (await data('list_holds', { status: 'pending' })) as Record<string, number> | undefined;
    if (d) {
      lines.push(`${w.owedToYou}: ${inr(d.pending_owed_to_user)}`, `${w.youOwe}: ${inr(d.pending_user_owes)}`);
      actions.push('holds');
    }
  }

  if (lines.length) return { text: `${w.dataIntro}\n${lines.map((l) => `- ${l}`).join('\n')}`, actions, followups: w.followups };

  const hit = disabled.has('search_help') ? undefined : searchHelp(question, 1)[0];
  if (hit) {
    return {
      text: `${w.helpIntro}\n${hit.entry.title}: ${hit.entry.text}`,
      actions: hit.entry.screen ? [hit.entry.screen] : [],
      followups: w.followups,
    };
  }
  return { text: w.busy, actions: [], followups: w.followups };
}
