import { renderEmail } from '../support/templates';
import { CODE_TTL_MS } from './code';

type Lang = 'English' | 'Hindi' | 'Spanish' | 'French' | 'German';

interface Words {
  subject: string;
  title: string;
  hi: (name: string) => string;
  intro: string;
  codeLabel: string;
  expires: (minutes: number) => string;
  ignore: string;
  footer: string;
}

const WORDS: Record<Lang, Words> = {
  English: {
    subject: 'Your SpendSync password reset code',
    title: 'Reset your password',
    hi: (n) => `Hi ${n},`,
    intro: 'Enter this code in the SpendSync app to choose a new password.',
    codeLabel: 'Your code',
    expires: (m) => `The code works for ${m} minutes and can be used once.`,
    ignore: "If you didn't ask for this, you can ignore this email. Your password has not changed.",
    footer: 'Never share this code with anyone. SpendSync will never ask you for it.',
  },
  Hindi: {
    subject: 'आपका SpendSync पासवर्ड रीसेट कोड',
    title: 'अपना पासवर्ड रीसेट करें',
    hi: (n) => `नमस्ते ${n},`,
    intro: 'नया पासवर्ड चुनने के लिए यह कोड SpendSync ऐप में डालें।',
    codeLabel: 'आपका कोड',
    expires: (m) => `यह कोड ${m} मिनट तक चलेगा और सिर्फ़ एक बार इस्तेमाल हो सकता है।`,
    ignore: 'अगर आपने यह नहीं माँगा था, तो इस ईमेल को अनदेखा करें। आपका पासवर्ड नहीं बदला है।',
    footer: 'यह कोड किसी को न बताएँ। SpendSync कभी आपसे यह नहीं माँगेगा।',
  },
  Spanish: {
    subject: 'Tu código para restablecer la contraseña de SpendSync',
    title: 'Restablece tu contraseña',
    hi: (n) => `Hola ${n}:`,
    intro: 'Introduce este código en la app SpendSync para elegir una contraseña nueva.',
    codeLabel: 'Tu código',
    expires: (m) => `El código dura ${m} minutos y solo se puede usar una vez.`,
    ignore: 'Si no lo pediste tú, ignora este correo. Tu contraseña no ha cambiado.',
    footer: 'No compartas este código con nadie. SpendSync nunca te lo pedirá.',
  },
  French: {
    subject: 'Votre code de réinitialisation du mot de passe SpendSync',
    title: 'Réinitialisez votre mot de passe',
    hi: (n) => `Bonjour ${n},`,
    intro: "Saisissez ce code dans l'application SpendSync pour choisir un nouveau mot de passe.",
    codeLabel: 'Votre code',
    expires: (m) => `Le code est valable ${m} minutes et ne peut servir qu'une fois.`,
    ignore: "Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail. Votre mot de passe n'a pas changé.",
    footer: 'Ne partagez ce code avec personne. SpendSync ne vous le demandera jamais.',
  },
  German: {
    subject: 'Dein SpendSync-Code zum Zurücksetzen des Passworts',
    title: 'Passwort zurücksetzen',
    hi: (n) => `Hallo ${n},`,
    intro: 'Gib diesen Code in der SpendSync-App ein, um ein neues Passwort zu wählen.',
    codeLabel: 'Dein Code',
    expires: (m) => `Der Code gilt ${m} Minuten und kann einmal verwendet werden.`,
    ignore: 'Wenn du das nicht angefordert hast, ignoriere diese E-Mail. Dein Passwort hat sich nicht geändert.',
    footer: 'Gib diesen Code niemals weiter. SpendSync fragt nie danach.',
  },
};

export function composeResetEmail(input: { code: string; name?: string | null; language?: string }): { subject: string; text: string; html: string } {
  const w = WORDS[(input.language && input.language in WORDS ? input.language : 'English') as Lang];
  const minutes = Math.round(CODE_TTL_MS / 60000);
  const first = input.name?.trim().split(/\s+/)[0] ?? '';
  const greeting = first ? w.hi(first) : '';
  const text = [greeting, w.intro, '', `${w.codeLabel}: ${input.code}`, '', w.expires(minutes), w.ignore, '', w.footer].filter((l, i) => l || i > 0).join('\n');
  const html = renderEmail({
    title: w.title,
    preheader: `${w.codeLabel}: ${input.code}`,
    intro: [greeting, w.intro].filter(Boolean).join(' '),
    highlight: { label: w.codeLabel, value: input.code },
    sections: [{ heading: '', body: `${w.expires(minutes)}\n${w.ignore}` }],
    footer: w.footer,
  });
  return { subject: w.subject, text, html };
}
