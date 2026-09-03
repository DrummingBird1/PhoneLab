/** Plots a GPS fix history to scale on a canvas — a local schematic path, not a map. No tiles are ever fetched. */
export class Trail {
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private points: { lat: number; lon: number }[] = [];
  private readonly maxPoints = 300;
  private dpr: number;

  constructor(canvas: HTMLCanvasElement) {
    const ctx = canvas.getContext("2d");
    if (!ctx) throw new Error("2d context unavailable");
    this.canvas = canvas;
    this.ctx = ctx;
    this.dpr = Math.min(window.devicePixelRatio || 1, 2);
    this.resize();
    requestAnimationFrame(() => this.resize());
    window.addEventListener("resize", () => this.resize());
  }

  resize() {
    const rect = this.canvas.getBoundingClientRect();
    const w = Math.max(rect.width, 1);
    const h = Math.max(rect.height || 140, 1);
    this.canvas.width = Math.round(w * this.dpr);
    this.canvas.height = Math.round(h * this.dpr);
    this.ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    this.draw();
  }

  push(lat: number, lon: number) {
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) return;
    this.points.push({ lat, lon });
    if (this.points.length > this.maxPoints) this.points.shift();
    this.draw();
  }

  reset() {
    this.points = [];
    this.draw();
  }

  private draw() {
    const rect = this.canvas.getBoundingClientRect();
    const w = rect.width || 240;
    const h = rect.height || 140;
    const ctx = this.ctx;
    ctx.clearRect(0, 0, w, h);

    if (this.points.length < 2) {
      ctx.fillStyle = "rgba(154, 164, 194, 0.6)";
      ctx.font = "12px -apple-system, sans-serif";
      ctx.textAlign = "center";
      ctx.fillText("Trail will appear as fixes come in", w / 2, h / 2);
      return;
    }

    const lats = this.points.map((p) => p.lat);
    const lons = this.points.map((p) => p.lon);
    const minLat = Math.min(...lats),
      maxLat = Math.max(...lats);
    const minLon = Math.min(...lons),
      maxLon = Math.max(...lons);
    const latMid = (minLat + maxLat) / 2;
    // Longitude degrees shrink in real distance the further from the equator — undo that so the plot is to scale.
    const lonScale = Math.cos((latMid * Math.PI) / 180);

    const spanLat = Math.max(maxLat - minLat, 1e-6);
    const spanLon = Math.max((maxLon - minLon) * lonScale, 1e-6);
    const span = Math.max(spanLat, spanLon) * 1.25;
    const pad = 14;

    const toXY = (lat: number, lon: number) => {
      const x = ((lon - minLon) * lonScale - (span - spanLon) / 2) / span;
      const y = (maxLat - lat - (span - spanLat) / 2) / span;
      return [pad + x * (w - pad * 2), pad + y * (h - pad * 2)];
    };

    ctx.strokeStyle = "#6ee7ff";
    ctx.lineWidth = 2;
    ctx.lineJoin = "round";
    ctx.lineCap = "round";
    ctx.beginPath();
    this.points.forEach((p, i) => {
      const [x, y] = toXY(p.lat, p.lon);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.stroke();

    const [sx, sy] = toXY(this.points[0].lat, this.points[0].lon);
    ctx.fillStyle = "#9aa4c2";
    ctx.beginPath();
    ctx.arc(sx, sy, 3.5, 0, Math.PI * 2);
    ctx.fill();

    const last = this.points[this.points.length - 1];
    const [ex, ey] = toXY(last.lat, last.lon);
    ctx.fillStyle = "#a78bfa";
    ctx.beginPath();
    ctx.arc(ex, ey, 4.5, 0, Math.PI * 2);
    ctx.fill();
  }
}
