import { setStatus, setFields, addAction, getCanvas, setMeter, setGpsSignal, setGauge, setPulse } from "./ui";
import { Sparkline } from "./sparkline";

function haversineMeters(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function formatDuration(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const mm = String(m).padStart(2, "0");
  const ss = String(sec).padStart(2, "0");
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

export function initGeo() {
  if (!("geolocation" in navigator)) {
    setStatus("gps", "unavailable", "No API");
    setStatus("trip", "unavailable", "No API");
    return;
  }
  setStatus("gps", "permission");
  setStatus("trip", "permission");

  let tripDistanceM = 0;
  let tripMaxKmh = 0;
  let tripStartAt: number | null = null;
  let lastFix: { lat: number; lon: number } | null = null;
  const tripCanvas = getCanvas("trip");
  const tripSpark = tripCanvas ? new Sparkline(tripCanvas) : null;

  addAction("gps", "Enable Location", () => {
    setStatus("gps", "waiting");
    setStatus("trip", "waiting");
    navigator.geolocation.watchPosition(
      (pos) => {
        const c = pos.coords;
        setStatus("gps", "live");
        setFields("gps", {
          lat: c.latitude.toFixed(5),
          lon: c.longitude.toFixed(5),
          alt: c.altitude != null ? c.altitude.toFixed(1) : "—",
          speed: c.speed != null ? c.speed.toFixed(1) : "—",
          heading: c.heading != null ? c.heading.toFixed(0) : "—",
          acc: c.accuracy.toFixed(0),
        });
        setGpsSignal("gps", c.accuracy);

        const now = Date.now();
        if (tripStartAt == null) tripStartAt = now;
        if (lastFix) {
          const d = haversineMeters(lastFix.lat, lastFix.lon, c.latitude, c.longitude);
          if (d > 2 && c.accuracy <= 50) tripDistanceM += d;
        }
        lastFix = { lat: c.latitude, lon: c.longitude };

        const speedKmh = c.speed != null ? c.speed * 3.6 : 0;
        if (speedKmh > tripMaxKmh) tripMaxKmh = speedKmh;
        const elapsedS = (now - tripStartAt) / 1000;
        const avgKmh = elapsedS > 0 ? tripDistanceM / 1000 / (elapsedS / 3600) : 0;

        setStatus("trip", "live");
        setFields("trip", {
          distance: (tripDistanceM / 1000).toFixed(2),
          speed: speedKmh.toFixed(1),
          avgspeed: avgKmh.toFixed(1),
          maxspeed: tripMaxKmh.toFixed(1),
          duration: formatDuration(elapsedS),
        });
        setGauge("trip", Math.min(100, (speedKmh / 150) * 100), `${speedKmh.toFixed(0)} km/h`);
        tripSpark?.push(speedKmh);
      },
      (err) => {
        const s = err.code === err.PERMISSION_DENIED ? "denied" : "error";
        setStatus("gps", s, err.message);
        setStatus("trip", s, err.message);
      },
      { enableHighAccuracy: true, maximumAge: 2000, timeout: 15000 }
    );

    addAction(
      "trip",
      "Reset Trip",
      () => {
        tripDistanceM = 0;
        tripMaxKmh = 0;
        tripStartAt = Date.now();
        lastFix = null;
        setFields("trip", { distance: "0.00", speed: "0.0", avgspeed: "0.0", maxspeed: "0.0", duration: "00:00" });
        setGauge("trip", 0, "0 km/h");
      },
      false
    );
  });
}

export function initHeartRate() {
  const bt = (navigator as any).bluetooth;
  if (!bt) {
    setStatus("heartrate", "unavailable", "No Web Bluetooth API");
    return;
  }
  setStatus("heartrate", "permission");
  addAction(
    "heartrate",
    "Connect Bluetooth Device",
    async () => {
      setStatus("heartrate", "waiting");
      try {
        const device = await bt.requestDevice({ filters: [{ services: ["heart_rate"] }] });
        setFields("heartrate", { device: device.name ?? "Unknown device", bpm: "—" });
        const server = await device.gatt.connect();
        const service = await server.getPrimaryService("heart_rate");
        const char = await service.getCharacteristic("heart_rate_measurement");
        await char.startNotifications();
        char.addEventListener("characteristicvaluechanged", (ev: any) => {
          const value: DataView = ev.target.value;
          const flags = value.getUint8(0);
          const is16bit = (flags & 0x1) !== 0;
          const bpm = is16bit ? value.getUint16(1, true) : value.getUint8(1);
          setStatus("heartrate", "live");
          setFields("heartrate", { bpm: String(bpm) });
          setPulse("heartrate", bpm);
        });
        device.addEventListener("gattserverdisconnected", () => {
          setStatus("heartrate", "waiting", "Disconnected");
        });
      } catch (err: any) {
        setStatus("heartrate", err?.name === "NotFoundError" ? "denied" : "error", err?.message ?? "Error");
      }
    },
    false
  );
}

export function initLight() {
  const AmbientLightSensor = (window as any).AmbientLightSensor;
  if (!AmbientLightSensor) {
    setStatus("light", "unavailable", "No API in this browser");
    return;
  }
  const canvas = getCanvas("light");
  const spark = canvas ? new Sparkline(canvas) : null;
  try {
    const sensor = new AmbientLightSensor();
    sensor.addEventListener("reading", () => {
      setStatus("light", "live");
      setFields("light", { lux: String(Math.round(sensor.illuminance)) });
      setGauge("light", Math.min(100, (sensor.illuminance / 1000) * 100), `${Math.round(sensor.illuminance)} lux`);
      spark?.push(sensor.illuminance);
    });
    sensor.addEventListener("error", (e: any) => {
      setStatus("light", e.error?.name === "NotAllowedError" ? "permission" : "unavailable", e.error?.name);
    });
    sensor.start();
  } catch {
    setStatus("light", "unavailable", "Not supported");
  }
}

export function initAudio() {
  if (!navigator.mediaDevices?.getUserMedia) {
    setStatus("sound", "unavailable", "No API");
    return;
  }
  setStatus("sound", "permission");
  addAction("sound", "Enable Microphone", async () => {
    setStatus("sound", "waiting");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const AudioCtxCls = window.AudioContext || (window as any).webkitAudioContext;
      const ctx = new AudioCtxCls();
      const source = ctx.createMediaStreamSource(stream);
      const analyser = ctx.createAnalyser();
      analyser.fftSize = 2048;
      source.connect(analyser);
      const data = new Uint8Array(analyser.fftSize);
      const canvas = getCanvas("sound");
      const spark = canvas ? new Sparkline(canvas) : null;
      setStatus("sound", "live");

      function tick() {
        analyser.getByteTimeDomainData(data);
        let sumSquares = 0;
        for (let i = 0; i < data.length; i++) {
          const v = (data[i] - 128) / 128;
          sumSquares += v * v;
        }
        const rms = Math.sqrt(sumSquares / data.length);
        const db = rms > 0 ? 20 * Math.log10(rms) : -60;
        const clamped = Math.max(-60, db);
        setFields("sound", { db: clamped.toFixed(1) });
        setMeter("sound", ((clamped + 60) / 60) * 100);
        spark?.push(clamped);
        requestAnimationFrame(tick);
      }
      tick();
    } catch (err: any) {
      setStatus("sound", err?.name === "NotAllowedError" ? "denied" : "error", err?.name ?? "Error");
    }
  });
}
