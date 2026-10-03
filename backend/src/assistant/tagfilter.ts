import type { ScreenId } from './knowledge';

const SCREEN_IDS = ['home', 'analytics', 'budget', 'profile', 'holds', 'add_transaction', 'support'] as const;

/**
 * Weaker models sometimes write a fake tool call or a thinking block as plain text, e.g.
 * `<open_screen screen="budget"/>` or `<think>…</think>`. A person must never see that. This sits in
 * front of the follow-up filter on the streamed text: it removes such tags (and anything inside
 * think-style blocks), turns a recognisable `open_screen` tag into a real button, and passes
 * everything else — including the `<followups>` tag — through untouched.
 */
const KEEP = new Set(['followups']);
const HIDDEN_BLOCK = new Set(['think', 'thinking', 'reasoning', 'thought']);
const TAG = /^<(\/?)([a-zA-Z_][a-zA-Z0-9_-]*)((?:\s[^<>]*)?)(\/?)>$/;
const MAX_TAG = 300;

export interface FilteredText {
  text: string;
  screens: ScreenId[];
}

export class ToolTagFilter {
  private buf = '';
  private hiddenUntil: string | null = null;

  push(delta: string): FilteredText {
    const out: string[] = [];
    const screens: ScreenId[] = [];
    this.buf += delta;

    while (this.buf.length) {
      if (this.hiddenUntil) {
        const close = this.buf.indexOf(`</${this.hiddenUntil}>`);
        if (close === -1) {
          // keep only a possible partial closing tag
          this.buf = this.buf.slice(Math.max(0, this.buf.length - (this.hiddenUntil.length + 3)));
          break;
        }
        this.buf = this.buf.slice(close + this.hiddenUntil.length + 3);
        this.hiddenUntil = null;
        continue;
      }
      const lt = this.buf.indexOf('<');
      if (lt === -1) {
        out.push(this.buf);
        this.buf = '';
        break;
      }
      if (lt > 0) {
        out.push(this.buf.slice(0, lt));
        this.buf = this.buf.slice(lt);
      }
      const gt = this.buf.indexOf('>');
      const nextLt = this.buf.indexOf('<', 1);
      if (nextLt !== -1 && (gt === -1 || nextLt < gt)) {
        out.push(this.buf.slice(0, nextLt)); // a lone "<" in normal text
        this.buf = this.buf.slice(nextLt);
        continue;
      }
      if (gt === -1) {
        if (this.buf.length > MAX_TAG) {
          out.push(this.buf);
          this.buf = '';
        }
        break; // might still become a tag: wait for more
      }
      const raw = this.buf.slice(0, gt + 1);
      this.buf = this.buf.slice(gt + 1);
      const m = TAG.exec(raw);
      if (!m) {
        out.push(raw);
        continue;
      }
      const [, closing, name, attrs, selfClose] = m;
      const lower = name.toLowerCase();
      if (KEEP.has(lower)) {
        out.push(raw);
      } else if (HIDDEN_BLOCK.has(lower)) {
        if (!closing && !selfClose) this.hiddenUntil = lower;
      } else if (lower === 'open_screen') {
        const screen = /screen\s*=\s*["']?([a-z_]+)/i.exec(attrs)?.[1]?.toLowerCase();
        if (screen && (SCREEN_IDS as readonly string[]).includes(screen)) screens.push(screen as ScreenId);
      }
      // any other tag: dropped
    }
    return { text: out.join(''), screens };
  }

  /** End of stream: whatever is left, unless it is an unfinished tag. */
  finish(): FilteredText {
    const rest = this.hiddenUntil || /^<\/?[a-zA-Z_]/.test(this.buf) ? '' : this.buf;
    this.buf = '';
    this.hiddenUntil = null;
    return { text: rest, screens: [] };
  }
}
