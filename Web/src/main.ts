import "./style.css";
import { renderSection, setStatus } from "./ui";
import { SECTIONS } from "./cards";
import { initMotion } from "./motion";
import { initOrientation } from "./orientation";
import { initGeo, initLight, initAudio, initHeartRate } from "./environment";
import { initDeviceInfo } from "./deviceinfo";

function detectDeviceKind(): string {
  const coarse = window.matchMedia("(pointer: coarse)").matches;
  const narrow = window.matchMedia("(max-width: 900px)").matches;
  return coarse && narrow ? "Mobile" : coarse ? "Tablet" : "Desktop";
}

function render() {
  const app = document.getElementById("app");
  if (!app) return;

  app.innerHTML = `
    <div class="top">
      <div class="brand">
        <div class="brand-mark"><span>\u{1F4E1}</span></div>
        <div>
          <h1>SensoLab Web</h1>
          <p>Every sensor your browser will give up &mdash; live, in one dashboard.</p>
        </div>
      </div>
      <div class="top-actions">
        <button class="mode-btn" id="display-toggle" title="Switch between text and visual mode">\u{1F522} Text</button>
        <button class="icon-btn" id="theme-toggle" title="Toggle theme" aria-label="Toggle theme">\u{1F319}</button>
      </div>
    </div>

    <div class="summary-bar" id="summary-bar">
      <span class="summary-chip"><span class="dot ok"></span>Live: <b id="count-live">0</b></span>
      <span class="summary-chip"><span class="dot warn"></span>Needs permission: <b id="count-permission">0</b></span>
      <span class="summary-chip"><span class="dot bad"></span>Not available here: <b id="count-unavailable">0</b></span>
      <span class="summary-chip">Device: <b id="device-kind">${detectDeviceKind()}</b></span>
    </div>

    ${SECTIONS.map(renderSection).join("")}

    <div class="foot">
      <span>Mirrors the 23 hardware sensors in the SensoLab Android app — mapped to what the Web Platform actually exposes — plus a GPS-derived trip computer.</span>
      <span>Everything runs locally in your browser &mdash; no readings are sent anywhere.</span>
    </div>
  `;
}

function setupDisplayMode() {
  const btn = document.getElementById("display-toggle");
  const root = document.body;
  const stored = localStorage.getItem("sensolab-display");
  const mode = stored === "visual" ? "visual" : "text";
  root.dataset.display = mode;

  function paint() {
    const current = root.dataset.display;
    if (btn) btn.textContent = current === "visual" ? "\u{1F3A8} Visual" : "\u{1F522} Text";
  }
  paint();
  btn?.addEventListener("click", () => {
    const next = root.dataset.display === "visual" ? "text" : "visual";
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
    let live = 0, perm = 0, unavail = 0;
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

const NOT_AVAILABLE_CARDS = ["motion-na", "orient-na", "env-na"];

render();
for (const id of NOT_AVAILABLE_CARDS) setStatus(id, "unavailable", "By design");
setupDisplayMode();
setupTheme();
setupSummary();
initMotion();
initOrientation();
initGeo();
initLight();
initAudio();
initHeartRate();
initDeviceInfo();
