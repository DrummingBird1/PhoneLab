const SCALE_KEY = "sensolab-text-scale";
const CONTRAST_KEY = "sensolab-high-contrast";
const SCALES = [0.9, 1, 1.1, 1.25];

export function initA11yControls() {
  const root = document.documentElement;
  const smaller = document.getElementById("text-smaller");
  const bigger = document.getElementById("text-bigger");
  const scaleLabel = document.getElementById("text-scale-label");
  const contrastBtn = document.getElementById("contrast-toggle");

  let scaleIdx = SCALES.indexOf(Number(localStorage.getItem(SCALE_KEY)) || 1);
  if (scaleIdx === -1) scaleIdx = 1;
  let highContrast = localStorage.getItem(CONTRAST_KEY) === "true";

  function paint() {
    root.style.setProperty("--user-scale", String(SCALES[scaleIdx]));
    if (scaleLabel) scaleLabel.textContent = `${Math.round(SCALES[scaleIdx] * 100)}%`;
    root.dataset.contrast = highContrast ? "high" : "normal";
    contrastBtn?.setAttribute("aria-pressed", String(highContrast));
  }
  paint();

  smaller?.addEventListener("click", () => {
    scaleIdx = Math.max(0, scaleIdx - 1);
    localStorage.setItem(SCALE_KEY, String(SCALES[scaleIdx]));
    paint();
  });
  bigger?.addEventListener("click", () => {
    scaleIdx = Math.min(SCALES.length - 1, scaleIdx + 1);
    localStorage.setItem(SCALE_KEY, String(SCALES[scaleIdx]));
    paint();
  });
  contrastBtn?.addEventListener("click", () => {
    highContrast = !highContrast;
    localStorage.setItem(CONTRAST_KEY, String(highContrast));
    paint();
  });
}
