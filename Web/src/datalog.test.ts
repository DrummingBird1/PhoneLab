import { describe, it, expect } from "vitest";
import { csvEscape } from "./datalog";

describe("csvEscape", () => {
  it("leaves plain values untouched", () => {
    expect(csvEscape("42.5")).toBe("42.5");
    expect(csvEscape("Live")).toBe("Live");
  });

  it("quotes values containing a comma", () => {
    expect(csvEscape("Chrome, Edge")).toBe('"Chrome, Edge"');
  });

  it("quotes and escapes values containing a double quote", () => {
    expect(csvEscape('12" screen')).toBe('"12"" screen"');
  });

  it("quotes values containing a newline", () => {
    expect(csvEscape("line1\nline2")).toBe('"line1\nline2"');
  });

  it("handles empty strings", () => {
    expect(csvEscape("")).toBe("");
  });
});
