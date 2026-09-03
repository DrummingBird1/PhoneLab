import { readValue } from "./datalog";

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

  drawRadarMark(ctx, 90, 96, 38);
  ctx.textBaseline = "middle";
  ctx.textAlign = "left";
  ctx.fillStyle = "#eef2ff";
  ctx.font = "800 46px -apple-system, 'Segoe UI', Arial, sans-serif";
  ctx.fillText("SensoLab", 148, 84);
  ctx.fillStyle = "#6ee7ff";
  ctx.font = "600 22px -apple-system, 'Segoe UI', Arial, sans-serif";
  ctx.fillText("Device Snapshot", 150, 122);

  ctx.textAlign = "right";
  ctx.fillStyle = "#5c6685";
  ctx.font = "500 19px ui-monospace, Consolas, monospace";
  const now = new Date();
  ctx.fillText(now.toLocaleDateString(), W - 60, 78);
  ctx.fillText(now.toLocaleTimeString(), W - 60, 104);
  ctx.textAlign = "left";

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
    const x = marginX + col * (tileW + gap);
    const y = gridTop + row * (tileH + gap);

    ctx.fillStyle = "rgba(255,255,255,0.045)";
    ctx.strokeStyle = "rgba(255,255,255,0.09)";
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.roundRect(x, y, tileW, tileH, 20);
    ctx.fill();
    ctx.stroke();

    ctx.font = "40px -apple-system, 'Segoe UI', Arial, sans-serif";
    ctx.fillStyle = "#eef2ff";
    ctx.textAlign = "left";
    ctx.fillText(stat.icon, x + 26, y + 52);

    ctx.font = "600 20px -apple-system, 'Segoe UI', Arial, sans-serif";
    ctx.fillStyle = "#9aa4c2";
    ctx.fillText(stat.label.toUpperCase(), x + 26, y + 96);

    const value = readValue(stat.cardId, stat.key) || "—";
    ctx.font = "700 40px ui-monospace, Consolas, monospace";
    ctx.fillStyle = "#eef2ff";
    const truncated = value.length > 12 ? value.slice(0, 12) + "…" : value;
    ctx.fillText(truncated, x + 26, y + 142);
    if (stat.unit) {
      const w = ctx.measureText(truncated).width;
      ctx.font = "500 20px -apple-system, 'Segoe UI', Arial, sans-serif";
      ctx.fillStyle = "#6ee7ff";
      ctx.fillText(stat.unit, x + 26 + w + 10, y + 142);
    }
  });

  const footerY = gridTop + rowsCount * (tileH + gap) + 30;
  ctx.textAlign = "center";
  ctx.fillStyle = "#5c6685";
  ctx.font = "500 20px -apple-system, 'Segoe UI', Arial, sans-serif";
  ctx.fillText("sensolab-web-app.vercel.app · everything stays on your device", W / 2, Math.min(footerY, H - 40));

  canvas.toBlob(async (blob) => {
    if (!blob) return;
    const filename = `SensoLab-snapshot-${Date.now()}.png`;
    const file = new File([blob], filename, { type: "image/png" });

    const nav = navigator as any;
    if (nav.share && nav.canShare?.({ files: [file] })) {
      try {
        await nav.share({ files: [file], title: "SensoLab snapshot" });
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
