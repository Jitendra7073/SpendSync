import type { CreateTicketInput, TicketCategory } from './ticket';

export const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

export interface EmailSection {
  heading: string;
  /** Plain text; escaped and line breaks kept. */
  body: string;
}

export interface LayoutInput {
  title: string;
  /** Short line shown next to the inbox subject in most mail apps. */
  preheader: string;
  intro?: string;
  /** A big, easy-to-copy value such as a one-time code. */
  highlight?: { label: string; value: string };
  rows?: Array<[string, string]>;
  sections?: EmailSection[];
  footer?: string;
}

/** One clean, single-column HTML email (inline styles only: mail apps ignore everything else). */
export function renderEmail(l: LayoutInput): string {
  const rows = (l.rows ?? [])
    .map(
      ([k, v]) =>
        `<tr><td style="padding:7px 0;color:#5b6b73;font-size:13px;width:120px;vertical-align:top">${esc(k)}</td>` +
        `<td style="padding:7px 0;color:#12222a;font-size:14px;font-weight:600">${esc(v)}</td></tr>`,
    )
    .join('');
  const sections = (l.sections ?? [])
    .map(
      (s) =>
        `<div style="margin-top:22px">${s.heading ? `<div style="font-size:12px;letter-spacing:.06em;text-transform:uppercase;color:#5b6b73;font-weight:700;margin-bottom:8px">${esc(s.heading)}</div>` : ''}` +
        `<div style="background:#f3f6f7;border-radius:10px;padding:14px 16px;font-size:14px;line-height:1.55;color:#12222a;white-space:pre-wrap">${esc(s.body)}</div></div>`,
    )
    .join('');
  return (
    `<!doctype html><html><body style="margin:0;background:#e9eef0;font-family:-apple-system,Segoe UI,Roboto,Arial,sans-serif">` +
    `<span style="display:none;max-height:0;overflow:hidden;opacity:0">${esc(l.preheader)}</span>` +
    `<table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:24px 12px">` +
    `<table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%;background:#ffffff;border-radius:16px;overflow:hidden">` +
    `<tr><td style="background:#0b7a75;padding:22px 28px"><div style="color:#bfeeea;font-size:12px;letter-spacing:.08em;text-transform:uppercase;font-weight:700">SpendSync</div>` +
    `<div style="color:#ffffff;font-size:22px;font-weight:700;margin-top:4px">${esc(l.title)}</div></td></tr>` +
    `<tr><td style="padding:24px 28px 28px">` +
    (l.intro ? `<p style="margin:0 0 16px;font-size:15px;line-height:1.55;color:#12222a">${esc(l.intro)}</p>` : '') +
    (l.highlight
      ? `<div style="margin:4px 0 20px;text-align:center"><div style="font-size:12px;letter-spacing:.06em;text-transform:uppercase;color:#5b6b73;font-weight:700">${esc(l.highlight.label)}</div>` +
        `<div style="display:inline-block;margin-top:8px;padding:14px 26px;border-radius:12px;background:#e6f4f3;color:#0b5f5a;font-family:Consolas,Menlo,monospace;font-size:34px;font-weight:700;letter-spacing:.35em;padding-left:calc(26px + .35em)">${esc(l.highlight.value)}</div></div>`
      : '') +
    (rows ? `<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="border-top:1px solid #e3eaed;border-bottom:1px solid #e3eaed">${rows}</table>` : '') +
    sections +
    (l.footer ? `<p style="margin:24px 0 0;font-size:12px;line-height:1.5;color:#7a8a92">${esc(l.footer)}</p>` : '') +
    `</td></tr></table></td></tr></table></body></html>`
  );
}

// ── Confirmation email to the person who sent the report ─────────────────────

type Lang = 'English' | 'Hindi' | 'Spanish' | 'French' | 'German';

interface Words {
  subject: (ref: string) => string;
  title: string;
  hi: (name: string) => string;
  intro: string;
  ref: string;
  type: string;
  sent: string;
  wrote: string;
  next: string;
  nextBody: string;
  footer: string;
  categories: Record<TicketCategory, string>;
}

const WORDS: Record<Lang, Words> = {
  English: {
    subject: (r) => `We got your report ${r} - SpendSync Support`,
    title: 'We got your report',
    hi: (n) => `Hi ${n},`,
    intro: 'Thanks for telling us. Your report is with our team and is saved in the app under Support > Your reports.',
    ref: 'Reference', type: 'Type', sent: 'Sent', wrote: 'What you wrote',
    next: 'What happens next',
    nextBody: 'A person on the team reads every report. If we need more details, or when it is fixed, we will email you at this address. You can reply to this email to add anything.',
    footer: 'You are receiving this because you sent a report from the SpendSync app.',
    categories: { bug: 'Something broke', wrong_data: 'Wrong numbers', assistant: 'The assistant', account: 'Account or sign-in', feature: 'An idea', other: 'Something else' },
  },
  Hindi: {
    subject: (r) => `हमें आपकी रिपोर्ट ${r} मिल गई - SpendSync सहायता`,
    title: 'हमें आपकी रिपोर्ट मिल गई',
    hi: (n) => `नमस्ते ${n},`,
    intro: 'हमें बताने के लिए धन्यवाद। आपकी रिपोर्ट हमारी टीम के पास है और ऐप में सहायता > आपकी रिपोर्ट में सहेजी गई है।',
    ref: 'संदर्भ', type: 'प्रकार', sent: 'भेजी गई', wrote: 'आपने क्या लिखा',
    next: 'आगे क्या होगा',
    nextBody: 'टीम का कोई व्यक्ति हर रिपोर्ट पढ़ता है। अगर और जानकारी चाहिए, या समस्या सुलझ जाए, तो हम इसी पते पर ईमेल करेंगे। कुछ जोड़ना हो तो इस ईमेल का जवाब दें।',
    footer: 'आपको यह ईमेल इसलिए मिला क्योंकि आपने SpendSync ऐप से रिपोर्ट भेजी थी।',
    categories: { bug: 'कुछ टूट गया', wrong_data: 'गलत आँकड़े', assistant: 'सहायक', account: 'खाता या साइन-इन', feature: 'कोई सुझाव', other: 'कुछ और' },
  },
  Spanish: {
    subject: (r) => `Recibimos tu informe ${r} - Soporte de SpendSync`,
    title: 'Recibimos tu informe',
    hi: (n) => `Hola ${n}:`,
    intro: 'Gracias por avisarnos. Tu informe está con nuestro equipo y guardado en la app en Soporte > Tus informes.',
    ref: 'Referencia', type: 'Tipo', sent: 'Enviado', wrote: 'Lo que escribiste',
    next: 'Qué pasa ahora',
    nextBody: 'Una persona del equipo lee cada informe. Si necesitamos más detalles, o cuando se resuelva, te escribiremos a este correo. Puedes responder a este mensaje para añadir algo.',
    footer: 'Recibes esto porque enviaste un informe desde la app SpendSync.',
    categories: { bug: 'Algo falló', wrong_data: 'Números incorrectos', assistant: 'El asistente', account: 'Cuenta o inicio de sesión', feature: 'Una idea', other: 'Otra cosa' },
  },
  French: {
    subject: (r) => `Nous avons reçu votre signalement ${r} - Support SpendSync`,
    title: 'Nous avons reçu votre signalement',
    hi: (n) => `Bonjour ${n},`,
    intro: "Merci de nous avoir prévenus. Votre signalement est chez notre équipe et enregistré dans l'application sous Support > Vos signalements.",
    ref: 'Référence', type: 'Type', sent: 'Envoyé', wrote: "Ce que vous avez écrit",
    next: 'Et ensuite ?',
    nextBody: "Une personne de l'équipe lit chaque signalement. Si nous avons besoin de précisions, ou une fois le problème réglé, nous vous écrirons à cette adresse. Vous pouvez répondre à ce message pour ajouter quelque chose.",
    footer: "Vous recevez ce message car vous avez envoyé un signalement depuis l'application SpendSync.",
    categories: { bug: 'Quelque chose a planté', wrong_data: 'Chiffres erronés', assistant: "L'assistant", account: 'Compte ou connexion', feature: 'Une idée', other: 'Autre chose' },
  },
  German: {
    subject: (r) => `Wir haben deine Meldung ${r} erhalten - SpendSync Support`,
    title: 'Wir haben deine Meldung erhalten',
    hi: (n) => `Hallo ${n},`,
    intro: 'Danke, dass du uns Bescheid gegeben hast. Deine Meldung liegt beim Team und ist in der App unter Support > Deine Meldungen gespeichert.',
    ref: 'Referenz', type: 'Art', sent: 'Gesendet', wrote: 'Was du geschrieben hast',
    next: 'Wie es weitergeht',
    nextBody: 'Eine Person im Team liest jede Meldung. Brauchen wir mehr Details, oder ist es behoben, melden wir uns unter dieser Adresse. Du kannst auf diese E-Mail antworten, um etwas zu ergänzen.',
    footer: 'Du erhältst diese Nachricht, weil du eine Meldung aus der SpendSync-App gesendet hast.',
    categories: { bug: 'Etwas ist kaputt', wrong_data: 'Falsche Zahlen', assistant: 'Der Assistent', account: 'Konto oder Anmeldung', feature: 'Eine Idee', other: 'Etwas anderes' },
  },
};

export interface ReceiptInput {
  ref: string;
  category: TicketCategory;
  message: string;
  context: CreateTicketInput['context'];
  user: { name?: string | null; email?: string | null };
  createdAt: Date;
}

/** The "we got it" email for the person who sent the report, in the language they use in the app. */
export function composeReceiptEmail(t: ReceiptInput): { subject: string; text: string; html: string } {
  const w = WORDS[(t.context.language && t.context.language in WORDS ? t.context.language : 'English') as Lang];
  const first = t.user.name?.trim().split(/\s+/)[0] || '';
  const greeting = w.hi(first || (t.user.email ?? ''));
  const sent = t.createdAt.toUTCString();
  const rows: Array<[string, string]> = [
    [w.ref, t.ref],
    [w.type, w.categories[t.category]],
    [w.sent, sent],
  ];
  const text = [greeting, '', w.intro, '', ...rows.map(([k, v]) => `${k}: ${v}`), '', `${w.wrote}:`, t.message, '', `${w.next}:`, w.nextBody, '', w.footer].join('\n');
  const html = renderEmail({
    title: w.title,
    preheader: `${w.ref} ${t.ref}`,
    intro: `${greeting} ${w.intro}`,
    rows,
    sections: [
      { heading: w.wrote, body: t.message },
      { heading: w.next, body: w.nextBody },
    ],
    footer: w.footer,
  });
  return { subject: w.subject(t.ref), text, html };
}
