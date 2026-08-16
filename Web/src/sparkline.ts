export class Sparkline {
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private values: number[] = [];
  private readonly max: number;
  private dpr: number;

  constructor(canvas: HTMLCanvasElement, capacity = 60) {
    const ctx = canvas.getContext("2d");
    if (!ctx) throw new Error("2d context unavailable");
    this.canvas = canvas;
    this.ctx = ctx;
    this.max = capacity;
    this.dpr = Math.min(window.devicePixelRatio || 1, 2);
    this.resize();
    // The card's CSS may not have laid out yet on the very first synchronous
    // pass (constructed immediately on module load) — correct once after the
    // browser has painted, so the canvas doesn't stay stuck at a 0/1px buffer.
    requestAnimationFrame(() => this.resize());
    window.addEventListener("resize", () => this.resize());
    window.addEventListener("orientationchange", () => this.resize());
  }

  resize() {
    const rect = this.canvas.getBoundingClientRect();
    const w = Math.max(rect.width, 1);
    const h = Math.max(rect.height || 44, 1);
    this.canvas.width = Math.round(w * this.dpr);
    this.canvas.height = Math.round(h * this.dpr);
    this.ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    if (this.values.length) this.draw();
  }

  push(value: number) {
    if (!Number.isFinite(value)) return;
    this.values.push(value);
    if (this.values.length > this.max) this.values.shift();
    this.draw();
  }

  private draw() {
    const rect = this.canvas.getBoundingClientRect();
    const w = rect.width || 240;
    const h = rect.height || 44;
    const ctx = this.ctx;
    ctx.clearRect(0, 0, w, h);
    if (this.values.length < 2) return;

    let lo = Math.min(...this.values);
    let hi = Math.max(...this.values);
    if (hi - lo < 1e-6) {
      lo -= 1;
      hi += 1;
    }
    const pad = (hi - lo) * 0.12;
    lo -= pad;
    hi += pad;

    const stepX = w / (this.max - 1);
    const startIdx = this.max - this.values.length;

    const grad = ctx.createLinearGradient(0, 0, 0, h);
    grad.addColorStop(0, "rgba(110, 231, 255, 0.35)");
    grad.addColorStop(1, "rgba(110, 231, 255, 0)");

    ctx.beginPath();
    this.values.forEach((v, i) => {
      const x = (startIdx + i) * stepX;
      const y = h - ((v - lo) / (hi - lo)) * h;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    const lastX = (startIdx + this.values.length - 1) * stepX;
    ctx.lineTo(lastX, h);
    ctx.lineTo(startIdx * stepX, h);
    ctx.closePath();
    ctx.fillStyle = grad;
    ctx.fill();

    ctx.beginPath();
    this.values.forEach((v, i) => {
      const x = (startIdx + i) * stepX;
      const y = h - ((v - lo) / (hi - lo)) * h;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.strokeStyle = "#6ee7ff";
    ctx.lineWidth = 1.6;
    ctx.lineJoin = "round";
    ctx.stroke();
  }
}
