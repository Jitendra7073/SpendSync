/**
 * The assistant's help knowledge: what SpendSync does and where things live. The assistant answers
 * "how do I…" questions ONLY from these entries (retrieved with a small BM25 index), so it can't
 * invent features. Keep entries short, true to the app, and in plain English — the assistant
 * translates to the user's language when it answers.
 *
 * `screen` is the screen the answer is about; the app turns it into an "Open …" button.
 */
export type ScreenId = 'home' | 'analytics' | 'budget' | 'planify' | 'profile' | 'holds' | 'add_transaction' | 'support';

export interface HelpEntry {
  id: string;
  title: string;
  text: string;
  screen?: ScreenId;
}

export const HELP_ENTRIES: HelpEntry[] = [
  {
    id: 'home',
    title: 'Home screen',
    screen: 'home',
    text:
      'Home shows your net balance (everything earned minus everything spent, all time), money on hold, a card for the selected month with income and expenses, and your newest transactions grouped by day. Tap the balance card for a full breakdown, tap Income or Expenses to see just those, and tap See all for the whole month. Pull down to refresh. The calendar icon in the top bar changes the month and the magnifier searches transactions and settings.',
  },
  {
    id: 'add_transaction',
    title: 'Add a transaction',
    screen: 'add_transaction',
    text:
      'Tap the + button in the bottom bar and choose Money in (income) or Money out (expense). Enter the amount, an optional note about what it was for, pick a date (never in the future) and a category, then tap Add. Typing a note can suggest a category from what you picked before. Turn on "Expect this back?" to also track the money as a hold.',
  },
  {
    id: 'edit_delete_transaction',
    title: 'Edit, view or delete a transaction',
    screen: 'home',
    text:
      'On Home or the Transactions list, tap a transaction to edit it, swipe it right to see its details, or swipe it left to delete it (you are asked to confirm and it cannot be undone).',
  },
  {
    id: 'categories',
    title: 'Categories',
    screen: 'add_transaction',
    text:
      'Built-in categories cover things like Food, Transport, Shopping, Bills, Salary and Gift. When adding a transaction tap "+ Add" at the end of the category grid to create your own: give it a name and search an icon. With many categories use the filter button to jump by letter. Built-in category names are shown in your app language; custom ones appear as you typed them.',
  },
  {
    id: 'holds',
    title: 'Holds (money lent or borrowed)',
    screen: 'holds',
    text:
      'A hold tracks money you expect back (you lent it) or money you need to pay back (you borrowed it). Create one by turning on "Expect this back?" or "Need to pay this back?" when adding a transaction, then choose the person and the date it is due. Open Holds from the "On hold" chip on Home to see each person and the net amount. Open a person to edit a hold, mark it as settled or delete it. You get a reminder notification on the due date.',
  },
  {
    id: 'budgets',
    title: 'Planify and budgets',
    screen: 'planify',
    text:
      'Planify (the Planify tab) is the monthly plan, and it replaces the old Budget page. Tap "Plan this month", enter the money you have (your salary is read from your own credits) and split it into buckets, each with a limit, like 2,000 for eating out. "Let AI guide me" asks a few multiple-choice questions and builds the buckets from your history, and shows whether the plan would have held in your past months. Limits are soft: nothing is blocked, you are only warned. Each bucket shows On track, Near limit (80%) or Over, how much is left, and the pace. The top shows how much is safe to spend today. If a bucket goes over, "What now?" lets you move money from another bucket or raise the limit. A bucket can also count other categories or shops you confirm (for example Food in Eating out) and the app asks when it is unsure. Alerts and a daily summary are in Settings → Planify.',
  },
  {
    id: 'analytics',
    title: 'Analytics',
    screen: 'analytics',
    text:
      'The Analytics tab turns your transactions into charts for a period you choose (this week, last month, this year, a custom month and more). You get net flow with savings rate, a spending trend line (daily or cumulative), income versus expenses, where your money goes by category (a ring and ranked bars, for spending or income), spending by day of the week and a day-by-day calendar heatmap, top merchants and your largest transactions, plus insights like savings rate and average daily spend. Tap or drag any chart to read exact values.',
  },
  {
    id: 'search',
    title: 'Search',
    screen: 'home',
    text:
      'Tap the magnifier in the top bar of Home, Analytics or Budget to search your transactions by merchant, category or note, and to jump to a setting. Inside Settings there is also a search box at the top.',
  },
  {
    id: 'settings_overview',
    title: 'Settings overview',
    screen: 'profile',
    text:
      'Open the Profile tab. It shows your name, email and this month\'s stats, with Settings below it in seven groups: Account, Appearance & Format, Notifications, Privacy & Security, Auto-capture, Data & Backup, and Help & About. Use the search box at the top to find any setting quickly.',
  },
  {
    id: 'theme_accent',
    title: 'Theme and accent colour',
    screen: 'profile',
    text:
      'In Settings → Appearance & Format choose Theme: System (follows your phone), Light or Dark. Pick an accent colour from five choices (Brand Blue, Emerald Green, Kakariki Green, Crimson Red, Coral Orange); it changes buttons, cards and charts everywhere. These settings sync to your account.',
  },
  {
    id: 'language',
    title: 'Language',
    screen: 'profile',
    text:
      'In Settings → Appearance & Format tap Language and choose English, हिन्दी (Hindi), Español, Français or Deutsch. The whole app switches immediately, including dates and notifications. The choice syncs to your account so it follows you to another phone.',
  },
  {
    id: 'date_format',
    title: 'Date format',
    screen: 'profile',
    text:
      'In Settings → Appearance & Format tap Date format and choose DD / MM / YYYY, MM / DD / YYYY or YYYY - MM - DD. A preview shows today\'s date in each style.',
  },
  {
    id: 'notifications',
    title: 'Notifications',
    screen: 'profile',
    text:
      'In Settings → Notifications switch Push notifications (hold reminders and spending alerts on your phone) and Email notifications (summaries and account notices) on or off. Both sync to your account.',
  },
  {
    id: 'privacy_pin',
    title: 'Hide amounts with a PIN',
    screen: 'profile',
    text:
      'In Settings → Privacy & Security turn on "Hide large amounts". Amounts over ₹1,000 then show as ••••• everywhere (Home, Analytics, Budget, Holds and this chat) until you tap one and enter your 4-digit PIN. After entering it, amounts stay visible for the time you chose (30 seconds, 1, 5 or 15 minutes) and then hide again. You can change the PIN or the duration there. Your PIN stays on your phone and is never sent to our servers, so it cannot be recovered — if you forget it, turn the setting off and set a new PIN.',
  },
  {
    id: 'auto_capture',
    title: 'Auto-capture payments',
    screen: 'profile',
    text:
      'Settings → Auto-capture can turn payment notifications into transactions automatically. Turn on "Auto-detect transactions", allow notification access when Android asks, and choose which apps to watch (such as Google Pay, PhonePe, Paytm and messaging apps). Only the apps you choose are read, and only the transaction you confirm leaves your phone. If a warning says notification access is off, tap Fix and enable SpendSync in Android settings. When a payment is captured you can reply to the notification to add a description.',
  },
  {
    id: 'backup_sync',
    title: 'Backup and sync',
    screen: 'profile',
    text:
      'When you are signed in, your transactions, budgets, holds and settings are stored in your account, so they appear on any phone you sign in on. Auto backup (Settings → Data & Backup) keeps this on a daily schedule. A setting changed while offline is saved on the phone and syncs automatically when you are back online. Guest mode keeps everything on the phone only and is not synced.',
  },
  {
    id: 'export',
    title: 'Export your data',
    screen: 'profile',
    text:
      'Settings → Data & Backup → Export data lets you download your transactions as a CSV file (opens in Excel or Google Sheets) or a PDF (easy to read and print), then save or share it.',
  },
  {
    id: 'clear_data_steps',
    title: 'Clear local data (you do this yourself)',
    screen: 'profile',
    text:
      'Settings → Data & Backup → Clear local data removes saved custom categories and cached data from this phone only; your account and online data are untouched. The assistant cannot do this for you — it must be confirmed in the app.',
  },
  {
    id: 'sign_out_steps',
    title: 'Sign out (you do this yourself)',
    screen: 'profile',
    text:
      'Settings → Account → Sign out signs you out of this phone; you can sign back in any time. The assistant cannot sign you out — you do it in the app.',
  },
  {
    id: 'delete_account_steps',
    title: 'Delete your account (you do this yourself)',
    screen: 'profile',
    text:
      'Settings → Account → Delete account permanently erases your account and all records from our servers. You must type DELETE to confirm and it cannot be undone. The assistant cannot delete your account — you do it in the app.',
  },
  {
    id: 'account',
    title: 'Your account and profile',
    screen: 'profile',
    text:
      'Tap Edit profile on the Profile tab (or Settings → Account) to change your name or email. The Account page also shows your member-since date, whether cloud sync is active and your user ID. Signed-in users sync across phones; guests are local only.',
  },
  {
    id: 'currency',
    title: 'Currency',
    text:
      'SpendSync works in Indian rupees (₹) only; there is no currency setting.',
  },
  {
    id: 'calendar',
    title: 'Changing the month or date',
    screen: 'home',
    text:
      'The calendar button in the top bar picks the month that Home and Budget show. In the date picker tap the month/year title to jump to another month or year, and use Today to return. Future dates are greyed out when adding a transaction.',
  },
  {
    id: 'privacy_policy',
    title: 'Privacy',
    screen: 'profile',
    text:
      'SpendSync keeps your transactions and settings in your account so they follow you across phones. Only you can see them and they are never sold or shared. Your PIN never leaves your phone. You can clear data on your phone or delete your account at any time. The full text is in Settings → Help & About → Privacy policy.',
  },
  {
    id: 'troubleshooting_numbers',
    title: 'Numbers look wrong or out of date',
    screen: 'home',
    text:
      'Pull down on the screen to refresh. Check the month selected in the top bar and, on Analytics, the period pill. Amounts above ₹1,000 are hidden if "Hide large amounts" is on — tap one and enter your PIN to reveal it.',
  },
  {
    id: 'troubleshooting_capture',
    title: 'Auto-capture is not adding payments',
    screen: 'profile',
    text:
      'Check Settings → Auto-capture: the switch must be on, the app that sends the payment alert must be ticked, and notification access must be enabled for SpendSync in Android settings (the Fix button opens it). Only common payment alert formats are recognised.',
  },
  {
    id: 'troubleshooting_sync',
    title: 'Settings or data are not syncing',
    screen: 'profile',
    text:
      'Make sure you are signed in (Settings → Account shows "Cloud sync: Active") and online. Changes made offline wait on the phone and sync on the next launch or sign-in. Guests are never synced.',
  },
  {
    id: 'assistant',
    title: 'About this assistant',
    text:
      'The assistant answers questions about SpendSync and about your own transactions, budgets, holds and settings, and can open the right screen for you. It cannot change anything yet, and it will never clear data, sign you out or delete your account — you do those yourself in Settings. It only talks about SpendSync.',
  },
];

// ── Tiny BM25 index ──────────────────────────────────────────────────────────

const STOP = new Set(
  'a an the is are was were be to of in on for and or it my me i you your how do does can what where when which with at as by this that from'.split(' '),
);

function tokenize(text: string): string[] {
  return text
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[^\p{L}\p{N}₹]+/gu, ' ')
    .split(' ')
    .filter((t) => t.length > 1 && !STOP.has(t))
    .map((t) => (t.length > 4 && t.endsWith('s') ? t.slice(0, -1) : t));
}

interface Doc {
  entry: HelpEntry;
  tf: Map<string, number>;
  len: number;
}

const docs: Doc[] = HELP_ENTRIES.map((entry) => {
  // Title words count three times so "budget" finds the Budgets entry first.
  const tokens = [...tokenize(entry.title), ...tokenize(entry.title), ...tokenize(entry.title), ...tokenize(entry.text)];
  const tf = new Map<string, number>();
  tokens.forEach((t) => tf.set(t, (tf.get(t) ?? 0) + 1));
  return { entry, tf, len: tokens.length };
});
const avgLen = docs.reduce((s, d) => s + d.len, 0) / docs.length;
const df = new Map<string, number>();
docs.forEach((d) => d.tf.forEach((_, t) => df.set(t, (df.get(t) ?? 0) + 1)));

export interface HelpHit {
  entry: HelpEntry;
  score: number;
}

/** Best matching help entries for a question. Empty when nothing is relevant (→ "outside SpendSync"). */
export function searchHelp(query: string, limit = 3, minScore = 1.2): HelpHit[] {
  const q = tokenize(query);
  if (q.length === 0) return [];
  const k1 = 1.4;
  const b = 0.75;
  const N = docs.length;
  const hits = docs.map((d) => {
    let score = 0;
    for (const t of new Set(q)) {
      const f = d.tf.get(t);
      if (!f) continue;
      const n = df.get(t) ?? 0;
      const idf = Math.log(1 + (N - n + 0.5) / (n + 0.5));
      score += idf * ((f * (k1 + 1)) / (f + k1 * (1 - b + (b * d.len) / avgLen)));
    }
    return { entry: d.entry, score };
  });
  return hits
    .filter((h) => h.score >= minScore)
    .sort((a, z) => z.score - a.score)
    .slice(0, limit);
}
