export * from "./render";
import { STATUS_LABEL, type Status } from "./render";

export function setStatus(cardId: string, status: Status, label?: string) {
  const card = document.getElementById(`card-${cardId}`);
  if (!card) return;
  card.dataset.status = status;
  const pill = card.querySelector<HTMLElement>('[data-role="status"]');
  if (pill) {
    pill.dataset.s = status;
    pill.textContent = label ?? STATUS_LABEL[status];
  }
}

export function setField(cardId: string, key: string, value: string) {
  const card = document.getElementById(`card-${cardId}`);
  const el = card?.querySelector<HTMLElement>(`[data-field="${key}"]`);
  if (!el) return;
  const unit = el.querySelector(".unit");
  el.textContent = value;
  if (unit) el.appendChild(unit);
}

export function setFields(cardId: string, values: Record<string, string>) {
  for (const [k, v] of Object.entries(values)) setField(cardId, k, v);
}

export function addAction(cardId: string, label: string, onClick: () => void, once = true) {
  const card = document.getElementById(`card-${cardId}`);
  const actions = card?.querySelector<HTMLElement>('[data-role="actions"]');
  if (!actions) return;
  const btn = document.createElement("button");
  btn.className = "enable-btn";
  btn.textContent = label;
  btn.addEventListener("click", onClick, { once });
  actions.appendChild(btn);
}

export function clearActions(cardId: string) {
  const card = document.getElementById(`card-${cardId}`);
  const actions = card?.querySelector<HTMLElement>('[data-role="actions"]');
  if (actions) actions.innerHTML = "";
}

export function getCanvas(cardId: string): HTMLCanvasElement | null {
  const card = document.getElementById(`card-${cardId}`);
  return card?.querySelector<HTMLCanvasElement>('[data-role="sparkline"]') ?? null;
}

export function getTrailCanvas(cardId: string): HTMLCanvasElement | null {
  const card = document.getElementById(`card-${cardId}`);
  return card?.querySelector<HTMLCanvasElement>('[data-role="trail"]') ?? null;
}

export function setNeedle(cardId: string, degrees: number) {
  const card = document.getElementById(`card-${cardId}`);
  const needle = card?.querySelector<HTMLElement>('[data-role="needle"]');
  if (needle) needle.style.transform = `rotate(${degrees}deg)`;
}

export function setBubble(cardId: string, xPct: number, yPct: number, hot: boolean) {
  const card = document.getElementById(`card-${cardId}`);
  const bubble = card?.querySelector<HTMLElement>('[data-role="bubble"]');
  if (!bubble) return;
  bubble.style.left = `${50 + xPct}%`;
  bubble.style.top = `${50 + yPct}%`;
  bubble.style.background = hot
    ? "radial-gradient(circle at 35% 30%, #fff, #f472b6 60%, #a78bfa)"
    : "radial-gradient(circle at 35% 30%, #fff, #6ee7ff 60%, #a78bfa)";
}

export function setMeter(cardId: string, pct: number) {
  const card = document.getElementById(`card-${cardId}`);
  const meter = card?.querySelector<HTMLElement>('[data-role="meter"]');
  if (meter) meter.style.width = `${Math.max(0, Math.min(100, pct))}%`;
}

export function showGate(sectionId: string, show: boolean) {
  const gate = document.getElementById(`gate-${sectionId}`);
  gate?.classList.toggle("show", show);
}

export function setTriaxis(cardId: string, x: number, y: number, z: number, range: number) {
  const card = document.getElementById(`card-${cardId}`);
  (
    [
      ["x", x],
      ["y", y],
      ["z", z],
    ] as const
  ).forEach(([axis, v]) => {
    const el = card?.querySelector<HTMLElement>(`[data-axis="${axis}"]`);
    if (!el) return;
    const pct = Math.max(0, Math.min(50, (Math.abs(v) / range) * 50));
    if (v >= 0) {
      el.style.left = "50%";
      el.style.width = `${pct}%`;
    } else {
      el.style.left = `${50 - pct}%`;
      el.style.width = `${pct}%`;
    }
  });
}

export function setGauge(cardId: string, pct: number, label: string) {
  const card = document.getElementById(`card-${cardId}`);
  const ring = card?.querySelector<HTMLElement>('[data-role="gauge-ring"]');
  const lbl = card?.querySelector<HTMLElement>('[data-role="gauge-label"]');
  const clamped = Math.max(0, Math.min(100, pct));
  if (ring) ring.style.background = `conic-gradient(var(--accent) ${clamped}%, rgba(255,255,255,0.1) 0)`;
  if (lbl) lbl.textContent = label;
}

export function setStateViz(cardId: string, icon: string, label: string, tone: "idle" | "active") {
  const card = document.getElementById(`card-${cardId}`);
  const wrap = card?.querySelector<HTMLElement>('[data-role="state"]');
  const iconEl = card?.querySelector<HTMLElement>('[data-role="state-icon"]');
  const labelEl = card?.querySelector<HTMLElement>('[data-role="state-label"]');
  if (wrap) wrap.dataset.tone = tone;
  if (iconEl) iconEl.textContent = icon;
  if (labelEl) labelEl.textContent = label;
}

export function setRotateIcon(cardId: string, degrees: number) {
  const card = document.getElementById(`card-${cardId}`);
  const el = card?.querySelector<HTMLElement>('[data-role="rotate"]');
  if (el) el.style.transform = `rotate(${degrees}deg)`;
}

export function setPulse(cardId: string, bpm: number) {
  const card = document.getElementById(`card-${cardId}`);
  const icon = card?.querySelector<HTMLElement>('[data-role="pulse"]');
  const label = card?.querySelector<HTMLElement>('[data-role="pulse-bpm"]');
  if (icon) {
    icon.style.animationDuration = `${60 / Math.max(30, bpm)}s`;
    icon.dataset.beating = "true";
  }
  if (label) label.textContent = `${bpm} BPM`;
}

export function setGpsSignal(cardId: string, accuracy: number) {
  const card = document.getElementById(`card-${cardId}`);
  const bars = card?.querySelector<HTMLElement>('[data-role="signal"]');
  const label = card?.querySelector<HTMLElement>('[data-role="signal-label"]');
  const level = accuracy <= 10 ? 4 : accuracy <= 20 ? 3 : accuracy <= 50 ? 2 : 1;
  if (bars) bars.dataset.level = String(level);
  if (label) label.textContent = `±${accuracy.toFixed(0)} m`;
}

export function onGateClick(sectionId: string, handler: () => void) {
  const gate = document.getElementById(`gate-${sectionId}`);
  const btn = gate?.querySelector<HTMLButtonElement>('[data-role="gate-btn"]');
  btn?.addEventListener(
    "click",
    () => {
      handler();
    },
    { once: true }
  );
}

export function onToggle(cardId: string, handler: (on: boolean) => void) {
  const card = document.getElementById(`card-${cardId}`);
  const btn = card?.querySelector<HTMLButtonElement>('[data-role="toggle-btn"]');
  btn?.addEventListener("click", () => {
    const next = btn.dataset.on !== "true";
    handler(next);
  });
}

export function setToggleState(cardId: string, on: boolean, onLabel: string, offLabel: string) {
  const card = document.getElementById(`card-${cardId}`);
  const btn = card?.querySelector<HTMLButtonElement>('[data-role="toggle-btn"]');
  if (!btn) return;
  btn.dataset.on = String(on);
  btn.setAttribute("aria-pressed", String(on));
  btn.textContent = on ? onLabel : offLabel;
}

/** Marks a card's metric values as live-announced (used only while the card is open in Focus mode). */
export function setCardAriaLive(cardId: string, live: boolean) {
  const card = document.getElementById(`card-${cardId}`);
  card?.querySelectorAll<HTMLElement>("[data-field]").forEach((el) => {
    el.setAttribute("aria-live", live ? "polite" : "off");
  });
}
