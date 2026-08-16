export type Status = "idle" | "waiting" | "live" | "permission" | "denied" | "unavailable" | "error";

const STATUS_LABEL: Record<Status, string> = {
  idle: "Idle",
  waiting: "Waiting",
  live: "Live",
  permission: "Tap to enable",
  denied: "Denied",
  unavailable: "Unavailable",
  error: "Error",
};

export interface FieldDef {
  key: string;
  label: string;
  unit?: string;
}

export interface CardDef {
  id: string;
  icon: string;
  title: string;
  fields?: FieldDef[];
  sparkline?: boolean;
  compass?: boolean;
  level?: boolean;
  meter?: boolean;
  triaxis?: boolean;
  gauge?: boolean;
  stateViz?: boolean;
  rotateIcon?: boolean;
  pulse?: boolean;
  gpsSignal?: boolean;
  note?: string;
}

export interface SectionDef {
  id: string;
  title: string;
  desc: string;
  gateLabel?: string;
  toggleable?: boolean;
  cards: CardDef[];
}

function fieldRow(f: FieldDef): string {
  return `<div class="metric-row">
    <dt>${f.label}</dt>
    <dd data-field="${f.key}">&mdash;${f.unit ? ` <span class="unit">${f.unit}</span>` : ""}</dd>
  </div>`;
}

function renderCard(def: CardDef): string {
  const parts: string[] = [];
  parts.push(`<article class="card" id="card-${def.id}" data-status="idle">`);
  parts.push(`<div class="card-head">
    <span class="card-icon">${def.icon}</span>
    <h3>${def.title}</h3>
    <span class="status-pill" data-role="status" data-s="idle">Idle</span>
  </div>`);

  if (def.compass) {
    parts.push(`<div class="compass-wrap"><div class="compass-dial">
      <div class="compass-needle" data-role="needle"></div>
      <div class="compass-center"></div>
    </div></div>`);
  }
  if (def.level) {
    parts.push(`<div class="level-wrap"><div class="level-dial">
      <div class="level-bubble" data-role="bubble"></div>
    </div></div>`);
  }
  if (def.meter) {
    parts.push(`<div class="meter-wrap">
      <div class="meter-track"><div class="meter-fill" data-role="meter"></div></div>
    </div>`);
  }
  if (def.fields?.length) {
    parts.push(`<dl class="metric-list">${def.fields.map(fieldRow).join("")}</dl>`);
  }

  const vizParts: string[] = [];
  if (def.triaxis) {
    vizParts.push(`<div class="triaxis">
      <div class="triaxis-row"><b>X</b><div class="triaxis-track"><div class="triaxis-fill" data-axis="x"></div></div></div>
      <div class="triaxis-row"><b>Y</b><div class="triaxis-track"><div class="triaxis-fill" data-axis="y"></div></div></div>
      <div class="triaxis-row"><b>Z</b><div class="triaxis-track"><div class="triaxis-fill" data-axis="z"></div></div></div>
    </div>`);
  }
  if (def.gauge) {
    vizParts.push(`<div class="gauge">
      <div class="gauge-ring" data-role="gauge-ring">
        <div class="gauge-inner"><span data-role="gauge-label">—</span></div>
      </div>
    </div>`);
  }
  if (def.stateViz) {
    vizParts.push(`<div class="state-badge" data-role="state">
      <span class="state-icon" data-role="state-icon">—</span>
      <span class="state-label" data-role="state-label">—</span>
    </div>`);
  }
  if (def.rotateIcon) {
    vizParts.push(`<div class="rotate-wrap"><span class="rotate-icon" data-role="rotate">📱</span></div>`);
  }
  if (def.pulse) {
    vizParts.push(`<div class="pulse-wrap">
      <span class="pulse-icon" data-role="pulse">💓</span>
      <span class="pulse-bpm" data-role="pulse-bpm">— BPM</span>
    </div>`);
  }
  if (def.gpsSignal) {
    vizParts.push(`<div class="signal-wrap">
      <div class="signal-bars" data-role="signal">
        <span class="bar" data-i="1"></span><span class="bar" data-i="2"></span><span class="bar" data-i="3"></span><span class="bar" data-i="4"></span>
      </div>
      <span class="signal-label" data-role="signal-label">No fix</span>
    </div>`);
  }
  if (vizParts.length) {
    parts.push(`<div class="visual-view">${vizParts.join("")}</div>`);
  }

  if (def.sparkline) {
    parts.push(`<canvas class="sparkline" data-role="sparkline"></canvas>`);
  }
  if (def.note) {
    parts.push(`<p class="card-note">${def.note}</p>`);
  }
  parts.push(`<div class="card-actions" data-role="actions"></div>`);
  parts.push(`</article>`);
  return parts.join("");
}

export function renderSection(def: SectionDef): string {
  const gate = def.gateLabel
    ? `<div class="section-gate" id="gate-${def.id}" data-role="gate">
        <span>${def.gateLabel}</span>
        <button class="enable-btn" data-role="gate-btn">Enable</button>
      </div>`
    : "";
  return `<section class="section" id="section-${def.id}"${def.toggleable ? ' data-toggleable="true"' : ""}>
    <div class="section-head">
      <h2>${def.title}</h2>
      <span class="section-desc">${def.desc}</span>
    </div>
    ${gate}
    <div class="grid">${def.cards.map(renderCard).join("")}</div>
  </section>`;
}

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
  ([["x", x], ["y", y], ["z", z]] as const).forEach(([axis, v]) => {
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
  btn?.addEventListener("click", () => {
    handler();
  }, { once: true });
}
