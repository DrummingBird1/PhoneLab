import { readValue } from "./datalog";
import { STATS } from "./sharecard";

export interface SnapshotPayload {
  t: number;
  values: [string, string, string][]; // [cardId, key, value]
}

export function encode(payload: SnapshotPayload): string {
  const json = JSON.stringify(payload);
  const bytes = new TextEncoder().encode(json);
  let binary = "";
  bytes.forEach((b) => (binary += String.fromCharCode(b)));
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function decode(s: string): SnapshotPayload | null {
  try {
    const b64 = s.replace(/-/g, "+").replace(/_/g, "/");
    const binary = atob(b64);
    const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
    const json = new TextDecoder().decode(bytes);
    return JSON.parse(json);
  } catch {
    return null;
  }
}

export async function copyPermalink(): Promise<boolean> {
  const values: [string, string, string][] = STATS.map((s) => [s.cardId, s.key, readValue(s.cardId, s.key)]);
  const payload: SnapshotPayload = { t: Date.now(), values };
  const url = `${location.origin}${location.pathname}#s=${encode(payload)}`;
  try {
    await navigator.clipboard.writeText(url);
    return true;
  } catch {
    try {
      window.prompt("Copy this link:", url);
    } catch {
      // Some embedded/automated browser contexts disable window.prompt too — nothing more we can do.
    }
    return false;
  }
}

export function initPermalinkBanner() {
  const hash = location.hash;
  const match = /#s=([^&]+)/.exec(hash);
  if (!match) return;
  const payload = decode(match[1]);
  if (!payload) return;

  const labelFor = (cardId: string, key: string) => STATS.find((s) => s.cardId === cardId && s.key === key);

  const banner = document.createElement("div");
  banner.className = "snapshot-banner";
  const when = new Date(payload.t).toLocaleString();
  const rows = payload.values
    .map(([cardId, key, value]) => {
      const stat = labelFor(cardId, key);
      if (!stat) return "";
      return `<div class="snapshot-row"><span>${stat.icon} ${stat.label}</span><b>${value || "—"}${stat.unit ? ` ${stat.unit}` : ""}</b></div>`;
    })
    .join("");

  banner.innerHTML = `
    <div class="snapshot-head">
      <span>📎 Viewing a snapshot shared ${when}</span>
      <button class="icon-btn" data-role="dismiss-snapshot" aria-label="Dismiss">✕</button>
    </div>
    <div class="snapshot-grid">${rows}</div>
    <p class="snapshot-hint">The live cards below show <em>this</em> device, not the one that was shared.</p>
  `;

  document.getElementById("app")?.prepend(banner);
  banner.querySelector('[data-role="dismiss-snapshot"]')?.addEventListener("click", () => {
    banner.remove();
    history.replaceState(null, "", location.pathname + location.search);
  });
}
