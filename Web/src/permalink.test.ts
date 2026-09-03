import { describe, it, expect } from "vitest";
import { encode, decode, type SnapshotPayload } from "./permalink";

describe("permalink encode/decode", () => {
  it("round-trips a payload", () => {
    const payload: SnapshotPayload = {
      t: 1700000000000,
      values: [
        ["accel", "mag", "9.81"],
        ["platform", "browser", "Chrome"],
      ],
    };
    const encoded = encode(payload);
    const decoded = decode(encoded);
    expect(decoded).toEqual(payload);
  });

  it("produces a URL-safe string (no +, /, or =)", () => {
    const encoded = encode({ t: 1, values: [["a", "b", "value/with+special=chars"]] });
    expect(encoded).not.toMatch(/[+/=]/);
  });

  it("round-trips unicode values", () => {
    const payload: SnapshotPayload = { t: 1, values: [["compass", "heading", "°C — µT"]] };
    expect(decode(encode(payload))).toEqual(payload);
  });

  it("returns null for garbage input", () => {
    expect(decode("not-valid-base64!!!")).toBeNull();
  });
});
