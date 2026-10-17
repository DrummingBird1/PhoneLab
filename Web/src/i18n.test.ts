import { describe, it, expect } from "vitest";
import ts from "typescript";
import { he } from "./i18n/he";
import { es } from "./i18n/es";
import { ar } from "./i18n/ar";
import { SECTIONS } from "./cards";
import { STATUS_LABEL } from "./render";
import { STATS } from "./sharecard";
import { detectLang } from "./i18n";

const DICTS: Record<string, Record<string, string>> = { he, es, ar };

/** Scientific/technical tokens and units that stay English in every language (same rule as the Android app). */
const KEEP_ENGLISH = new Set(["X", "Y", "Z", "α (Z)", "β (X)", "γ (Y)", "BPM"]);

/** Keys that reach t() through a function's return value, which the source scan below doesn't follow. */
const DYNAMIC_KEYS = ["Unknown"];

/**
 * Calls that translate a string argument: t()/tf() themselves, and the ui.ts setters that run their
 * label through t() (value = index of the translated argument).
 */
const TRANSLATED_ARGS: Record<string, number[]> = {
  t: [0],
  tf: [0],
  setStatus: [2],
  setField: [2],
  addAction: [1],
  setStateViz: [2],
  setToggleState: [2, 3],
};

const SOURCES = import.meta.glob(["./**/*.ts", "!./**/*.test.ts", "!./i18n/**"], {
  query: "?raw",
  import: "default",
  eager: true,
}) as Record<string, string>;

/** Numbers, units and dashes ("0.00", "0/min", "—") pass through t() unchanged by design. */
function needsTranslation(s: string): boolean {
  return /\p{L}/u.test(s) && !/^\d/.test(s) && !KEEP_ENGLISH.has(s);
}

/** Every string literal an expression can evaluate to: literals, both arms of ?: / ?? / ||, and same-file consts. */
function literalsOf(expr: ts.Expression, consts: Map<string, string>): string[] {
  if (ts.isStringLiteral(expr) || ts.isNoSubstitutionTemplateLiteral(expr)) return [expr.text];
  if (ts.isParenthesizedExpression(expr)) return literalsOf(expr.expression, consts);
  if (ts.isConditionalExpression(expr)) {
    return [...literalsOf(expr.whenTrue, consts), ...literalsOf(expr.whenFalse, consts)];
  }
  if (
    ts.isBinaryExpression(expr) &&
    (expr.operatorToken.kind === ts.SyntaxKind.QuestionQuestionToken ||
      expr.operatorToken.kind === ts.SyntaxKind.BarBarToken)
  ) {
    return [...literalsOf(expr.left, consts), ...literalsOf(expr.right, consts)];
  }
  if (ts.isIdentifier(expr) && consts.has(expr.text)) return [consts.get(expr.text)!];
  return [];
}

function keysUsedInSource(file: string, code: string): string[] {
  const sf = ts.createSourceFile(file, code, ts.ScriptTarget.Latest, true);
  const consts = new Map<string, string>();
  const found: string[] = [];

  const collectConsts = (node: ts.Node) => {
    if (ts.isVariableDeclaration(node) && ts.isIdentifier(node.name) && node.initializer) {
      if (ts.isStringLiteral(node.initializer)) consts.set(node.name.text, node.initializer.text);
    }
    ts.forEachChild(node, collectConsts);
  };
  collectConsts(sf);

  const visit = (node: ts.Node) => {
    if (ts.isCallExpression(node) && ts.isIdentifier(node.expression)) {
      const name = node.expression.text;
      for (const i of TRANSLATED_ARGS[name] ?? []) {
        const arg = node.arguments[i];
        if (arg) found.push(...literalsOf(arg, consts));
      }
      // setFields(card, { key: value, ... }) runs every value through setField → t().
      const obj = node.arguments[1];
      if (name === "setFields" && obj && ts.isObjectLiteralExpression(obj)) {
        for (const prop of obj.properties) {
          if (ts.isPropertyAssignment(prop)) found.push(...literalsOf(prop.initializer, consts));
        }
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(sf);
  return found;
}

/** Strings rendered from data: card/section definitions, status pills, share-card labels. */
function keysUsedInData(): string[] {
  const keys: string[] = [...Object.values(STATUS_LABEL), ...STATS.map((s) => s.label)];
  for (const section of SECTIONS) {
    keys.push(section.title, section.desc);
    if (section.gateLabel) keys.push(section.gateLabel);
    for (const card of section.cards) {
      keys.push(card.title);
      if (card.note) keys.push(card.note);
      if (card.toggle) keys.push(card.toggle.onLabel, card.toggle.offLabel);
      for (const f of card.fields ?? []) keys.push(f.label);
    }
  }
  return keys;
}

const USED_KEYS = [
  ...new Set([
    ...Object.entries(SOURCES).flatMap(([file, code]) => keysUsedInSource(file, code)),
    ...keysUsedInData(),
    ...DYNAMIC_KEYS,
  ]),
]
  .filter(needsTranslation)
  .sort();

const placeholders = (s: string) => [...s.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();

describe("i18n dictionaries", () => {
  it("finds the translatable strings in the source (sanity check for the scanner)", () => {
    expect(Object.keys(SOURCES).length).toBeGreaterThan(10);
    expect(USED_KEYS).toContain("Skip to sensor list"); // t() in main.ts
    expect(USED_KEYS).toContain("Enable Location"); // addAction() in environment.ts
    expect(USED_KEYS).toContain("Keep screen awake"); // const passed to setToggleState()
    expect(USED_KEYS).toContain("Tap to enable"); // STATUS_LABEL
    expect(USED_KEYS).toContain("Compass Heading"); // card definition
  });

  for (const [lang, dict] of Object.entries(DICTS)) {
    it(`${lang} has a translation for every string the code uses`, () => {
      expect(USED_KEYS.filter((k) => !(k in dict))).toEqual([]);
    });

    it(`${lang} has no keys the code no longer uses`, () => {
      expect(Object.keys(dict).filter((k) => !USED_KEYS.includes(k))).toEqual([]);
    });

    it(`${lang} translations are non-empty and keep every {placeholder}`, () => {
      const bad = Object.entries(dict).filter(
        ([en, tr]) => !tr.trim() || placeholders(en).join() !== placeholders(tr).join()
      );
      expect(bad).toEqual([]);
    });
  }

  it("all dictionaries have the same key set", () => {
    const all = [...new Set(Object.values(DICTS).flatMap((d) => Object.keys(d)))];
    const missing = Object.fromEntries(
      Object.entries(DICTS).map(([lang, d]) => [lang, all.filter((k) => !(k in d))])
    );
    expect(missing).toEqual({ he: [], es: [], ar: [] });
  });
});

describe("detectLang", () => {
  it("prefers a saved choice", () => {
    expect(detectLang("ar", ["he-IL"])).toBe("ar");
  });

  it("falls back to the first supported browser language", () => {
    expect(detectLang(null, ["fr-FR", "es-MX", "he"])).toBe("es");
    expect(detectLang(null, ["iw"])).toBe("he");
  });

  it("ignores junk in storage and defaults to English", () => {
    expect(detectLang("xx", [])).toBe("en");
    expect(detectLang(null, ["fr", "de"])).toBe("en");
  });
});
