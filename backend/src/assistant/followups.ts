/**
 * The model ends a reply with `<followups>a|b|c</followups>`. This streams the visible text through
 * untouched but holds back anything that could be the start of that tag, so the user never sees
 * half a tag flash on screen, then returns the parsed suggestions at the end.
 */
const TAG = '<followups>';
const CLOSE = '</followups>';

export class FollowupFilter {
  private pending = '';
  private tail = '';
  private inTag = false;

  /** Feed a streamed text delta; returns the part that is safe to show now. */
  push(delta: string): string {
    if (this.inTag) {
      this.tail += delta;
      return '';
    }
    const text = this.pending + delta;
    const at = text.indexOf(TAG);
    if (at !== -1) {
      this.inTag = true;
      this.tail = text.slice(at + TAG.length);
      this.pending = '';
      return text.slice(0, at).replace(/\s+$/, '');
    }
    // Hold back the longest suffix that is still a prefix of the tag.
    let keep = 0;
    for (let n = Math.min(TAG.length - 1, text.length); n > 0; n--) {
      if (TAG.startsWith(text.slice(text.length - n))) {
        keep = n;
        break;
      }
    }
    // Trailing whitespace is held back too: it may be the newline just before the tag.
    let visible = text.slice(0, text.length - keep);
    const ws = visible.match(/\s*$/)![0];
    visible = visible.slice(0, visible.length - ws.length);
    this.pending = ws + text.slice(text.length - keep);
    return visible;
  }

  /** Call once at the end: remaining visible text (if no tag arrived) and the follow-up questions. */
  finish(): { text: string; followups: string[] } {
    const text = this.inTag ? '' : this.pending.trimEnd();
    this.pending = '';
    const body = this.tail.includes(CLOSE) ? this.tail.slice(0, this.tail.indexOf(CLOSE)) : this.tail;
    const followups = this.inTag
      ? body
          .split('|')
          .map((s) => s.trim())
          .filter((s) => s.length > 0 && s.length <= 120)
          .slice(0, 3)
      : [];
    return { text, followups };
  }
}
