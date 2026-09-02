import { setStatus, setFields, addAction, setGauge } from "./ui";

const DOWN_URL = (bytes: number) => `https://speed.cloudflare.com/__down?bytes=${bytes}`;
const UP_URL = "https://speed.cloudflare.com/__up";

async function measurePing(samples = 4): Promise<number> {
  const times: number[] = [];
  for (let i = 0; i < samples; i++) {
    const t0 = performance.now();
    await fetch(DOWN_URL(0), { cache: "no-store" });
    times.push(performance.now() - t0);
  }
  times.sort((a, b) => a - b);
  // drop the slow first (connection warm-up) sample when we have enough data
  const usable = times.length > 2 ? times.slice(1) : times;
  return usable.reduce((a, b) => a + b, 0) / usable.length;
}

async function measureDownload(bytes: number): Promise<number> {
  const t0 = performance.now();
  const res = await fetch(DOWN_URL(bytes), { cache: "no-store" });
  const buf = await res.arrayBuffer();
  const seconds = (performance.now() - t0) / 1000;
  return (buf.byteLength * 8) / seconds / 1_000_000; // Mb/s
}

async function measureUpload(bytes: number): Promise<number> {
  const payload = new Blob([new Uint8Array(bytes)]);
  const t0 = performance.now();
  await fetch(UP_URL, { method: "POST", body: payload, cache: "no-store" });
  const seconds = (performance.now() - t0) / 1000;
  return (bytes * 8) / seconds / 1_000_000; // Mb/s
}

export function initSpeedTest() {
  if (!("fetch" in window)) {
    setStatus("speedtest", "unavailable", "No API");
    return;
  }
  setStatus("speedtest", "permission", "Tap to run");
  addAction(
    "speedtest",
    "Run Speed Test",
    async () => {
      try {
        setStatus("speedtest", "waiting", "Testing ping…");
        const ping = await measurePing();
        setFields("speedtest", { ping: ping.toFixed(0) });

        setStatus("speedtest", "waiting", "Testing download…");
        const download = await measureDownload(15_000_000);
        setFields("speedtest", { download: download.toFixed(1) });
        setGauge("speedtest", Math.min(100, (download / 300) * 100), `${download.toFixed(0)} Mb/s`);

        setStatus("speedtest", "waiting", "Testing upload…");
        const upload = await measureUpload(6_000_000);
        setFields("speedtest", { upload: upload.toFixed(1) });

        setStatus("speedtest", "live");
      } catch (err: any) {
        setStatus("speedtest", "error", err?.message ?? "Test failed");
      }
    },
    false
  );
}
