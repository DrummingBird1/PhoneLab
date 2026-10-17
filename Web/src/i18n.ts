import { he } from "./i18n/he";
import { es } from "./i18n/es";
import { ar } from "./i18n/ar";

export type Lang = "en" | "he" | "es" | "ar";

export const LANGS: { code: Lang; label: string }[] = [
  { code: "en", label: "English" },
  { code: "he", label: "עברית" },
  { code: "es", label: "Español" },
  { code: "ar", label: "العربية" },
];

const STORAGE_KEY = "sensolab-lang";
const DICTS: Record<Exclude<Lang, "en">, Record<string, string>> = { he, es, ar };

/** Saved choice first, then the browser's preferred languages, then English. */
export function detectLang(stored: string | null, preferred: readonly string[]): Lang {
  if (stored === "en" || stored === "he" || stored === "es" || stored === "ar") return stored;
  for (const tag of preferred) {
    const base = tag.toLowerCase().split("-")[0];
    if (base === "he" || base === "iw") return "he";
    if (base === "es") return "es";
    if (base === "ar") return "ar";
    if (base === "en") return "en";
  }
  return "en";
}

function readStored(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export const lang: Lang = detectLang(
  readStored(),
  typeof navigator !== "undefined" ? (navigator.languages ?? [navigator.language]) : []
);
export const isRtl = lang === "he" || lang === "ar";

/**
 * gettext-style lookup keyed by the English text, so card definitions and status labels
 * stay readable English in the source. Anything without a translation (numbers, units,
 * error messages from the browser) falls through unchanged.
 */
export function t(en: string): string {
  if (lang === "en" || !en) return en;
  return DICTS[lang][en] ?? en;
}

/** t() plus {name} placeholders, e.g. tf("{shown} of {total} sensors", { shown: 3, total: 9 }). */
export function tf(en: string, vars: Record<string, string | number>): string {
  return t(en).replace(/\{(\w+)\}/g, (m, k: string) => (k in vars ? String(vars[k]) : m));
}

export function applyDocumentLang() {
  document.documentElement.lang = lang;
  document.documentElement.dir = isRtl ? "rtl" : "ltr";
}

/** Persist and reload — every card is rendered once at startup, so a reload is the simplest correct switch. */
export function setLang(next: Lang) {
  try {
    localStorage.setItem(STORAGE_KEY, next);
  } catch {
    // storage blocked (private mode) — the choice just won't persist
  }
  location.reload();
}
