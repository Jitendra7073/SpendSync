/**
 * Permission tiers for everything the assistant can do.
 *
 *  - read      looks something up. Runs automatically.
 *  - navigate  asks the phone to open a screen. Harmless; the app does the opening.
 *  - propose   prepares a change (e.g. a new expense) and shows the user a confirm card. The assistant
 *              writes NOTHING: the app saves only after the user taps Confirm.
 *  - write     changes data directly. NOT enabled — would need a confirm card + undo.
 *  - blocked   never available to the assistant, ever. These have no tool at all; the assistant
 *              can only explain where the user does them by hand.
 */
export type ToolTier = 'read' | 'navigate' | 'propose' | 'write' | 'blocked';

/** Actions the assistant must never perform. Kept as data so tests can prove no tool matches. */
export const BLOCKED_ACTIONS = ['clear_data', 'sign_out', 'delete_account'] as const;

/** Tiers that may execute right now. 'propose' only shows a confirm card. Add 'write' only together with confirm + undo. */
const RUNNABLE_TIERS: ReadonlySet<ToolTier> = new Set<ToolTier>(['read', 'navigate', 'propose']);

export function canRun(tier: ToolTier): boolean {
  return RUNNABLE_TIERS.has(tier);
}

/** True if a tool name looks like one of the blocked actions (belt and braces for the registry test). */
export function looksBlocked(toolName: string): boolean {
  const n = toolName.toLowerCase();
  return (
    BLOCKED_ACTIONS.some((a) => n.includes(a)) ||
    /(^|_)(delete|remove|clear|wipe|erase|sign_?out|log_?out)(_|$)/.test(n)
  );
}
