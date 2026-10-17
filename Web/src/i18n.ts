export type Lang = "en" | "he" | "es" | "ar";
type Dict = Record<string, string>;

export const LANGS: { code: Lang; label: string }[] = [
  { code: "en", label: "English" },
  { code: "he", label: "עברית" },
  { code: "es", label: "Español" },
  { code: "ar", label: "العربية" },
];

const STORAGE_KEY = "sensolab-lang";

/**
 * Each dictionary is its own chunk, so English visitors never download one. The service
 * worker still precaches all of them (vite.config.ts) so switching language works offline.
 */
const LOADERS: Record<Exclude<Lang, "en">, () => Promise<Dict>> = {
  he: () => import("./i18n/he").then((m) => m.he),
  es: () => import("./i18n/es").then((m) => m.es),
  ar: () => import("./i18n/ar").then((m) => m.ar),
};

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

/** The UI language. Falls back to "en" if its dictionary can't be loaded. */
export let lang: Lang = detectLang(
  readStored(),
  typeof navigator !== "undefined" ? (navigator.languages ?? [navigator.language]) : []
);
let dict: Dict | null = null;
let warnedEarly = false;

export function isRtl(): boolean {
  return lang === "he" || lang === "ar";
}

/** Fetches the dictionary for the detected language. Await it once before rendering anything. */
export async function loadDictionary(): Promise<void> {
  if (lang === "en") return;
  try {
    dict = await LOADERS[lang]();
  } catch {
    // Chunk unreachable (e.g. offline before the service worker cached it) — an all-English
    // page beats a half-translated, right-to-left one.
    lang = "en";
  }
}

/**
 * gettext-style lookup keyed by the English text, so card definitions and status labels
 * stay readable English in the source. Anything without a translation (numbers, units,
 * error messages from the browser) falls through unchanged.
 */
export function t(en: string): string {
  if (!dict || !en) {
    if (import.meta.env.DEV && lang !== "en" && !warnedEarly) {
      warnedEarly = true;
      console.warn(`t(${JSON.stringify(en)}) ran before loadDictionary() finished, so it stays English.`);
    }
    return en;
  }
  return dict[en] ?? en;
}

/** t() plus {name} placeholders, e.g. tf("{shown} of {total} sensors", { shown: 3, total: 9 }). */
export function tf(en: string, vars: Record<string, string | number>): string {
  return t(en).replace(/\{(\w+)\}/g, (m, k: string) => (k in vars ? String(vars[k]) : m));
}

/**
 * Locale for formatted dates: the UI language, except English keeps the browser's regional
 * format and Arabic keeps Latin digits to match the sensor readings.
 */
export function dateLocale(): string | undefined {
  if (lang === "en") return undefined;
  return lang === "ar" ? "ar-u-nu-latn" : lang;
}

export function applyDocumentLang() {
  document.documentElement.lang = lang;
  document.documentElement.dir = isRtl() ? "rtl" : "ltr";
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
