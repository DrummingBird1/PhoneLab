import "./style.css";
import { renderSection, setStatus } from "./ui";
import { SECTIONS } from "./cards";
import { initMotion } from "./motion";
import { initOrientation } from "./orientation";
import { initGeo, initLight, initAudio, initHeartRate, initProximity } from "./environment";
import { initDeviceInfo } from "./deviceinfo";
import { initSpeedTest } from "./speedtest";
import { initDataLog, exportCsv } from "./datalog";
import { exportShareCard } from "./sharecard";
import { initWakeLock } from "./wakelock";
import { initUsb } from "./usb";
import { initFocusMode } from "./focusmode";
import { initReorder } from "./reorder";
import { initSearch } from "./search";
import { initA11yControls } from "./a11y";
import { initInstallPrompt } from "./pwa";
import { copyPermalink, initPermalinkBanner } from "./permalink";

const DISPLAY_MODES = ["text", "visual", "compact"] as const;
type DisplayMode = (typeof DISPLAY_MODES)[number];
const DISPLAY_LABEL: Record<DisplayMode, string> = {
  text: "\u{1F522} Text",
  visual: "\u{1F3A8} Visual",
  compact: "\u{1F4CB} Compact",
};

function detectDeviceKind(): string {
  const coarse = window.matchMedia("(pointer: coarse)").matches;
  const narrow = window.matchMedia("(max-width: 900px)").matches;
  return coarse && narrow ? "Mobile" : coarse ? "Tablet" : "Desktop";
}

function render() {
  const app = document.getElementById("app");
  if (!app) return;

  app.innerHTML = `
    <a class="skip-link" href="#main-content">Skip to sensor list</a>
    <div class="top">
      <div class="brand">
        <div class="brand-mark"><span>\u{1F4E1}</span></div>
        <div>
          <h1>SensoLab Web</h1>
          <p>Every sensor your browser will give up &mdash; live, in one dashboard.</p>
        </div>
      </div>
      <div class="top-actions">
        <button class="mode-btn" id="install-btn" hidden>⬇️ Install App</button>
        <button class="mode-btn" id="display-toggle" title="Cycle Text / Visual / Compact mode">\u{1F522} Text</button>
        <div class="a11y-controls" role="group" aria-label="Text size and contrast">
          <button class="icon-btn" id="text-smaller" aria-label="Decrease text size">A&minus;</button>
          <span id="text-scale-label" class="scale-label">100%</span>
          <button class="icon-btn" id="text-bigger" aria-label="Increase text size">A+</button>
          <button class="icon-btn" id="contrast-toggle" aria-label="Toggle high contrast" aria-pressed="false">◐</button>
        </div>
        <button class="icon-btn" id="theme-toggle" title="Toggle theme" aria-label="Toggle theme">\u{1F319}</button>
      </div>
    </div>

    <div class="toolbar">
      <label class="search-wrap">
        <span class="visually-hidden">Search sensors</span>
        <input type="search" id="card-search" placeholder="\u{1F50D} Search sensors&hellip;" autocomplete="off">
      </label>
      <span id="search-count" class="search-count"></span>
    </div>

    <div class="summary-bar" id="summary-bar">
      <span class="summary-chip"><span class="dot ok"></span>Live: <b id="count-live">0</b></span>
      <span class="summary-chip"><span class="dot warn"></span>Needs permission: <b id="count-permission">0</b></span>
      <span class="summary-chip"><span class="dot bad"></span>Not available here: <b id="count-unavailable">0</b></span>
      <span class="summary-chip">Device: <b id="device-kind">${detectDeviceKind()}</b></span>
    </div>

    <main id="main-content">
    ${SECTIONS.map(renderSection).join("")}

    <section class="section">
      <div class="section-head">
        <h2>Export &amp; Share</h2>
        <span class="section-desc">A snapshot is logged every second, starting now</span>
      </div>
      <div class="export-panel">
        <div class="export-info">
          <span class="export-count"><b id="log-count">0</b> snapshots logged</span>
          <span class="export-hint">CSV opens cleanly in Google Sheets &mdash; one column per sensor reading.</span>
        </div>
        <div class="export-actions">
          <button class="enable-btn" id="export-csv">\u{1F4E5} Export CSV Log</button>
          <button class="enable-btn" id="export-image">\u{1F5BC}\u{FE0F} Export Summary Image</button>
          <button class="enable-btn" id="export-permalink">\u{1F517} Copy Snapshot Link</button>
        </div>
      </div>
    </section>
    </main>

    <div class="foot">
      <span>Mirrors the 24 hardware sensors in the PhoneLab Android app — mapped to what the Web Platform actually exposes — plus a GPS-derived trip computer.</span>
      <span>Everything runs locally in your browser &mdash; no readings are sent anywhere.</span>
      <span><a href="https://drummingbird1.github.io/PhoneLab/">Get the PhoneLab Android app &rarr;</a></span>
    </div>
  `;
}

function setupDisplayMode() {
  const btn = document.getElementById("display-toggle");
  const root = document.body;
  const stored = localStorage.getItem("sensolab-display");
  const mode: DisplayMode = (DISPLAY_MODES as readonly string[]).includes(stored ?? "")
    ? (stored as DisplayMode)
    : "text";
  root.dataset.display = mode;

  function paint() {
    const current = (root.dataset.display as DisplayMode) ?? "text";
    if (btn) btn.textContent = DISPLAY_LABEL[current];
  }
  paint();
  btn?.addEventListener("click", () => {
    const current = (root.dataset.display as DisplayMode) ?? "text";
    const next = DISPLAY_MODES[(DISPLAY_MODES.indexOf(current) + 1) % DISPLAY_MODES.length];
    root.dataset.display = next;
    localStorage.setItem("sensolab-display", next);
    paint();
  });
}

function setupTheme() {
  const btn = document.getElementById("theme-toggle");
  const root = document.documentElement;
  const stored = localStorage.getItem("sensolab-theme");
  if (stored === "light" || stored === "dark") root.dataset.theme = stored;

  function effective(): "light" | "dark" {
    if (root.dataset.theme === "light" || root.dataset.theme === "dark") return root.dataset.theme;
    return window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
  }
  function paintIcon() {
    if (btn) btn.textContent = effective() === "light" ? "\u{2600}\u{FE0F}" : "\u{1F319}";
  }
  paintIcon();
  btn?.addEventListener("click", () => {
    const next = effective() === "light" ? "dark" : "light";
    root.dataset.theme = next;
    localStorage.setItem("sensolab-theme", next);
    paintIcon();
  });
}

function setupSummary() {
  const liveEl = document.getElementById("count-live");
  const permEl = document.getElementById("count-permission");
  const unavailEl = document.getElementById("count-unavailable");

  function tick() {
    const cards = document.querySelectorAll<HTMLElement>(".card");
    let live = 0,
      perm = 0,
      unavail = 0;
    cards.forEach((c) => {
      const s = c.dataset.status;
      if (s === "live") live++;
      else if (s === "permission" || s === "waiting") perm++;
      else if (s === "unavailable" || s === "denied" || s === "error") unavail++;
    });
    if (liveEl) liveEl.textContent = String(live);
    if (permEl) permEl.textContent = String(perm);
    if (unavailEl) unavailEl.textContent = String(unavail);
  }
  tick();
  window.setInterval(tick, 1000);
}

function setupExport() {
  document.getElementById("export-csv")?.addEventListener("click", exportCsv);
  document.getElementById("export-image")?.addEventListener("click", exportShareCard);
  document.getElementById("export-permalink")?.addEventListener("click", async (e) => {
    const btn = e.currentTarget as HTMLButtonElement;
    const original = btn.textContent;
    const ok = await copyPermalink();
    btn.textContent = ok ? "✅ Link copied!" : "Link ready — see prompt";
    window.setTimeout(() => (btn.textContent = original), 2000);
  });
}

const NOT_AVAILABLE_CARDS = ["motion-na", "orient-na", "env-na"];

render();
for (const id of NOT_AVAILABLE_CARDS) setStatus(id, "unavailable", "By design");
initPermalinkBanner();
setupDisplayMode();
setupTheme();
setupSummary();
setupExport();
initFocusMode();
initReorder();
initSearch();
initA11yControls();
initInstallPrompt();
initMotion();
initOrientation();
initGeo();
initLight();
initProximity();
initAudio();
initHeartRate();
initDeviceInfo();
initWakeLock();
initUsb();
initSpeedTest();
initDataLog();
