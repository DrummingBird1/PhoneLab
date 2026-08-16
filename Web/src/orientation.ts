import { setStatus, setFields, setNeedle, setBubble, showGate, onGateClick, setTriaxis, setRotateIcon } from "./ui";

const GATED_CARDS = ["compass", "attitude"];

function needsIOSPermission(): boolean {
  const DOE = (window as any).DeviceOrientationEvent;
  return typeof DOE?.requestPermission === "function";
}

function initCompassAndAttitude() {
  if (!("DeviceOrientationEvent" in window)) {
    for (const id of GATED_CARDS) setStatus(id, "unavailable", "No API");
    return;
  }

  let started = false;
  let usingAbsoluteStream = false;

  function onOrientation(e: DeviceOrientationEvent, forceAbsolute: boolean) {
    const wk = (e as any).webkitCompassHeading as number | undefined;
    if (wk != null) {
      setStatus("compass", "live");
      setFields("compass", { heading: wk.toFixed(0), ref: "True north" });
      setNeedle("compass", wk);
    } else if (e.alpha != null) {
      const abs = forceAbsolute || (e as any).absolute === true;
      const heading = abs ? (360 - e.alpha) % 360 : e.alpha;
      setStatus("compass", "live");
      setFields("compass", { heading: heading.toFixed(0), ref: abs ? "True north" : "Relative (uncalibrated)" });
      setNeedle("compass", heading);
    } else {
      setStatus("compass", "unavailable", "Not reported");
    }

    if (e.beta != null || e.gamma != null) {
      const beta = e.beta ?? 0;
      const gamma = e.gamma ?? 0;
      setStatus("attitude", "live");
      setFields("attitude", { beta: beta.toFixed(1), gamma: gamma.toFixed(1) });
      const xPct = Math.max(-35, Math.min(35, gamma / 90 * 35));
      const yPct = Math.max(-35, Math.min(35, beta / 90 * 35));
      const hot = Math.abs(beta) > 45 || Math.abs(gamma) > 45;
      setBubble("attitude", xPct, yPct, hot);
    } else {
      setStatus("attitude", "unavailable", "Not reported");
    }
  }

  function start() {
    if (started) return;
    started = true;
    for (const id of GATED_CARDS) setStatus(id, "waiting");
    // Chrome/Android fires the Earth-referenced "deviceorientationabsolute" event
    // separately from "deviceorientation" — prefer it for a true compass heading,
    // and stop trusting the relative stream once it shows up.
    window.addEventListener("deviceorientationabsolute", (e) => {
      usingAbsoluteStream = true;
      onOrientation(e as DeviceOrientationEvent, true);
    });
    window.addEventListener("deviceorientation", (e) => {
      if (usingAbsoluteStream) return;
      onOrientation(e, false);
    });
    window.setTimeout(() => {
      for (const id of GATED_CARDS) {
        if (document.getElementById(`card-${id}`)?.dataset.status === "waiting") {
          setStatus(id, "waiting", "No data yet");
        }
      }
    }, 2600);
  }

  if (needsIOSPermission()) {
    for (const id of GATED_CARDS) setStatus(id, "permission");
    showGate("orientation", true);
    onGateClick("orientation", async () => {
      try {
        const res = await (window as any).DeviceOrientationEvent.requestPermission();
        if (res === "granted") {
          showGate("orientation", false);
          start();
        } else {
          for (const id of GATED_CARDS) setStatus(id, "denied");
        }
      } catch {
        for (const id of GATED_CARDS) setStatus(id, "error");
      }
    });
  } else {
    start();
  }
}

function initMagnetometer() {
  const Magnetometer = (window as any).Magnetometer;
  if (!Magnetometer) {
    setStatus("magneto", "unavailable", "No API in this browser");
    return;
  }
  try {
    const sensor = new Magnetometer({ frequency: 10 });
    sensor.addEventListener("reading", () => {
      setStatus("magneto", "live");
      setFields("magneto", {
        x: sensor.x != null ? sensor.x.toFixed(1) : "—",
        y: sensor.y != null ? sensor.y.toFixed(1) : "—",
        z: sensor.z != null ? sensor.z.toFixed(1) : "—",
      });
      setTriaxis("magneto", sensor.x ?? 0, sensor.y ?? 0, sensor.z ?? 0, 100);
    });
    sensor.addEventListener("error", (ev: any) => {
      if (ev.error?.name === "NotAllowedError") setStatus("magneto", "permission", "Blocked by policy");
      else if (ev.error?.name === "NotReadableError") setStatus("magneto", "unavailable", "No hardware");
      else setStatus("magneto", "error", ev.error?.name ?? "Error");
    });
    sensor.start();
  } catch {
    setStatus("magneto", "unavailable", "Not supported");
  }
}

function initScreenOrientation() {
  function update() {
    const so = screen.orientation;
    if (so) {
      setStatus("screenorient", "live");
      setFields("screenorient", { angle: String(so.angle), type: so.type });
      setRotateIcon("screenorient", so.angle);
    } else {
      setStatus("screenorient", "unavailable", "No API");
    }
  }
  update();
  window.addEventListener("orientationchange", update);
  screen.orientation?.addEventListener?.("change", update);
}

export function initOrientation() {
  initCompassAndAttitude();
  initMagnetometer();
  initScreenOrientation();
}
