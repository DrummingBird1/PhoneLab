import { t, tf } from "./i18n";

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
  trail?: boolean;
  compass?: boolean;
  level?: boolean;
  meter?: boolean;
  triaxis?: boolean;
  gauge?: boolean;
  stateViz?: boolean;
  rotateIcon?: boolean;
  pulse?: boolean;
  gpsSignal?: boolean;
  toggle?: { onLabel: string; offLabel: string };
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
    <dt>${t(f.label)}</dt>
    <dd data-field="${f.key}" aria-live="off">&mdash;${f.unit ? ` <span class="unit">${f.unit}</span>` : ""}</dd>
  </div>`;
}

export function renderCard(def: CardDef): string {
  const parts: string[] = [];
  parts.push(`<article class="card" id="card-${def.id}" data-status="idle" data-card-id="${def.id}">`);
  parts.push(`<div class="card-head">
    <span class="card-icon" aria-hidden="true">${def.icon}</span>
    <h3>${t(def.title)}</h3>
    <div class="card-head-end">
      <span class="status-pill" data-role="status" data-s="idle" role="status">${t("Idle")}</span>
      <button class="focus-btn" data-role="focus-btn" title="${t("Expand this card")}" aria-label="${tf("Expand {title}", { title: t(def.title) })}">&#10021;</button>
    </div>
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
      <span class="signal-label" data-role="signal-label">${t("No fix")}</span>
    </div>`);
  }
  if (vizParts.length) {
    parts.push(`<div class="visual-view">${vizParts.join("")}</div>`);
  }

  if (def.sparkline) {
    parts.push(
      `<canvas class="sparkline" data-role="sparkline" role="img" aria-label="${tf("{title} history graph", { title: t(def.title) })}"></canvas>`
    );
  }
  if (def.trail) {
    parts.push(
      `<canvas class="trail-canvas" data-role="trail" role="img" aria-label="${tf("{title} path trail", { title: t(def.title) })}"></canvas>`
    );
  }
  if (def.toggle) {
    parts.push(
      `<button class="toggle-btn" data-role="toggle-btn" data-on="false" aria-pressed="false">${t(def.toggle.offLabel)}</button>`
    );
  }
  if (def.note) {
    parts.push(`<p class="card-note">${t(def.note)}</p>`);
  }
  parts.push(`<div class="card-actions" data-role="actions"></div>`);
  parts.push(`</article>`);
  return parts.join("");
}

export function renderSection(def: SectionDef): string {
  const gate = def.gateLabel
    ? `<div class="section-gate" id="gate-${def.id}" data-role="gate">
        <span>${t(def.gateLabel)}</span>
        <button class="enable-btn" data-role="gate-btn">${t("Enable")}</button>
      </div>`
    : "";
  return `<section class="section" id="section-${def.id}"${def.toggleable ? ' data-toggleable="true"' : ""}>
    <div class="section-head">
      <h2>${t(def.title)}</h2>
      <span class="section-desc">${t(def.desc)}</span>
    </div>
    ${gate}
    <div class="grid">${def.cards.map(renderCard).join("")}</div>
  </section>`;
}

export { STATUS_LABEL };
