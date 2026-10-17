import { readValue } from "./datalog";
import { t, isRtl, dateLocale } from "./i18n";

export interface Stat {
  cardId: string;
  key: string;
  icon: string;
  label: string;
  unit?: string;
}

export const STATS: Stat[] = [
  { cardId: "accel", key: "mag", icon: "📡", label: "Acceleration", unit: "m/s²" },
  { cardId: "compass", key: "heading", icon: "🧭", label: "Compass Heading", unit: "°" },
  { cardId: "trip", key: "speed", icon: "🚗", label: "Speed", unit: "km/h" },
  { cardId: "sound", key: "db", icon: "🎤", label: "Sound Level", unit: "dB" },
  { cardId: "battery", key: "level", icon: "🔋", label: "Battery" },
  { cardId: "network", key: "type", icon: "📶", label: "Network" },
  { cardId: "cpupressure", key: "state", icon: "🌡️", label: "CPU Pressure" },
  { cardId: "platform", key: "browser", icon: "🌐", label: "Browser" },
];

function drawRadarMark(ctx: CanvasRenderingContext2D, cx: number, cy: number, r: number) {
  ctx.strokeStyle = "#6ee7ff";
  ctx.lineWidth = r * 0.1;
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.stroke();
  ctx.strokeStyle = "#a78bfa";
  ctx.globalAlpha = 0.6;
  ctx.lineWidth = r * 0.06;
  ctx.beginPath();
  ctx.arc(cx, cy, r * 0.6, 0, Math.PI * 2);
  ctx.stroke();
  ctx.globalAlpha = 1;
  ctx.fillStyle = "#6ee7ff";
  ctx.beginPath();
  ctx.arc(cx, cy, r * 0.18, 0, Math.PI * 2);
  ctx.fill();
}

export function exportShareCard() {
  const W = 1080;
  const H = 1350;
  const canvas = document.createElement("canvas");
  canvas.width = W;
  canvas.height = H;
  const ctx = canvas.getContext("2d");
  if (!ctx) return;

  const bg = ctx.createLinearGradient(0, 0, W, H);
  bg.addColorStop(0, "#0d1220");
  bg.addColorStop(1, "#090c15");
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, W, H);

  const glow = ctx.createRadialGradient(W * 0.18, H * 0.06, 0, W * 0.18, H * 0.06, 620);
  glow.addColorStop(0, "rgba(110,231,255,0.15)");
  glow.addColorStop(1, "rgba(110,231,255,0)");
  ctx.fillStyle = glow;
  ctx.fillRect(0, 0, W, H);
  const glow2 = ctx.createRadialGradient(W * 0.95, H * 0.9, 0, W * 0.95, H * 0.9, 500);
  glow2.addColorStop(0, "rgba(167,139,250,0.14)");
  glow2.addColorStop(1, "rgba(167,139,250,0)");
  ctx.fillStyle = glow2;
  ctx.fillRect(0, 0, W, H);

  // Hebrew/Arabic mirror the layout: x positions below are measured from the reading-start edge.
  const rtl = isRtl();
  const dir: CanvasDirection = rtl ? "rtl" : "ltr";
  const fromStart = (x: number) => (rtl ? W - x : x);
  ctx.direction = dir;

  drawRadarMark(ctx, fromStart(90), 96, 38);
  ctx.textBaseline = "middle";
  ctx.textAlign = "start";
  ctx.fillStyle = "#eef2ff";
  ctx.font = "800 46px -apple-system, 'Segoe UI', Arial, sans-serif";
  ctx.fillText("SensoLab", fromStart(148), 84);
  ctx.fillStyle = "#6ee7ff";
  ctx.font = "600 22px -apple-system, 'Segoe UI', Arial, sans-serif";
  ctx.fillText(t("Device Snapshot"), fromStart(150), 122);

  ctx.textAlign = "end";
  ctx.fillStyle = "#5c6685";
  ctx.font = "500 19px ui-monospace, Consolas, monospace";
  const now = new Date();
  ctx.fillText(now.toLocaleDateString(dateLocale()), fromStart(W - 60), 78);
  ctx.fillText(now.toLocaleTimeString(dateLocale()), fromStart(W - 60), 104);
  ctx.textAlign = "start";

  const gridTop = 200;
  const marginX = 60;
  const gap = 22;
  const cols = 2;
  const tileW = (W - marginX * 2 - gap * (cols - 1)) / cols;
  const tileH = 190;
  const rowsCount = Math.ceil(STATS.length / cols);

  STATS.forEach((stat, i) => {
    const col = i % cols;
    const row = Math.floor(i / cols);
    const x = rtl ? W - marginX - tileW - col * (tileW + gap) : marginX + col * (tileW + gap);
    const y = gridTop + row * (tileH + gap);
    const textX = rtl ? x + tileW - 26 : x + 26;

    ctx.fillStyle = "rgba(255,255,255,0.045)";
    ctx.strokeStyle = "rgba(255,255,255,0.09)";
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.roundRect(x, y, tileW, tileH, 20);
    ctx.fill();
    ctx.stroke();

    ctx.font = "40px -apple-system, 'Segoe UI', Arial, sans-serif";
    ctx.fillStyle = "#eef2ff";
    ctx.textAlign = "start";
    ctx.fillText(stat.icon, textX, y + 52);

    ctx.font = "600 20px -apple-system, 'Segoe UI', Arial, sans-serif";
    ctx.fillStyle = "#9aa4c2";
    ctx.fillText(t(stat.label).toUpperCase(), textX, y + 96);

    // "12.3 m/s²" reads left-to-right in every language; in RTL the pair just sits against the right edge.
    const value = readValue(stat.cardId, stat.key) || "—";
    const truncated = value.length > 12 ? value.slice(0, 12) + "…" : value;
    const valueFont = "700 40px ui-monospace, Consolas, monospace";
    const unitFont = "500 20px -apple-system, 'Segoe UI', Arial, sans-serif";
    ctx.direction = "ltr";
    ctx.textAlign = "left";
    ctx.font = valueFont;
    const valueW = ctx.measureText(truncated).width;
    ctx.font = unitFont;
    const unitW = stat.unit ? 10 + ctx.measureText(stat.unit).width : 0;
    const valueX = rtl ? textX - valueW - unitW : textX;

    ctx.font = valueFont;
    ctx.fillStyle = "#eef2ff";
    ctx.fillText(truncated, valueX, y + 142);
    if (stat.unit) {
      ctx.font = unitFont;
      ctx.fillStyle = "#6ee7ff";
      ctx.fillText(stat.unit, valueX + valueW + 10, y + 142);
    }
    ctx.direction = dir;
  });

  const footerY = gridTop + rowsCount * (tileH + gap) + 30;
  ctx.textAlign = "center";
  ctx.fillStyle = "#5c6685";
  ctx.font = "500 20px -apple-system, 'Segoe UI', Arial, sans-serif";
  ctx.fillText(`sensolab-web.vercel.app · ${t("everything stays on your device")}`, W / 2, Math.min(footerY, H - 40));

  canvas.toBlob(async (blob) => {
    if (!blob) return;
    const filename = `SensoLab-snapshot-${Date.now()}.png`;
    const file = new File([blob], filename, { type: "image/png" });

    const nav = navigator as any;
    if (nav.share && nav.canShare?.({ files: [file] })) {
      try {
        await nav.share({ files: [file], title: t("SensoLab snapshot") });
        return;
      } catch {
        // User cancelled the share sheet, or it failed — fall through to a plain download.
      }
    }

    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }, "image/png");
}
