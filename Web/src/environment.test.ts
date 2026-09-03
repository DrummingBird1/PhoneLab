import { describe, it, expect } from "vitest";
import { haversineMeters, formatDuration } from "./environment";

describe("haversineMeters", () => {
  it("returns 0 for identical points", () => {
    expect(haversineMeters(32.0853, 34.7818, 32.0853, 34.7818)).toBeCloseTo(0, 6);
  });

  it("matches the known distance between two well-known cities (within 1%)", () => {
    // Tel Aviv to Jerusalem, roughly 54 km as the crow flies.
    const d = haversineMeters(32.0853, 34.7818, 31.7683, 35.2137);
    expect(d).toBeGreaterThan(53000);
    expect(d).toBeLessThan(56000);
  });

  it("is symmetric", () => {
    const a = haversineMeters(10, 20, 15, 25);
    const b = haversineMeters(15, 25, 10, 20);
    expect(a).toBeCloseTo(b, 6);
  });
});

describe("formatDuration", () => {
  it("formats sub-minute durations as mm:ss", () => {
    expect(formatDuration(5)).toBe("00:05");
    expect(formatDuration(59)).toBe("00:59");
  });

  it("formats minutes", () => {
    expect(formatDuration(125)).toBe("02:05");
  });

  it("formats hours as h:mm:ss", () => {
    expect(formatDuration(3661)).toBe("1:01:01");
  });

  it("clamps negative input to zero", () => {
    expect(formatDuration(-5)).toBe("00:00");
  });
});
