import {
  setStatus,
  setFields,
  addAction,
  getCanvas,
  showGate,
  onGateClick,
  setTriaxis,
  setStateViz,
  setGauge,
} from "./ui";
import { Sparkline } from "./sparkline";

const CARDS = ["accel", "linaccel", "gravity", "gyro", "activity", "steps"];

const STEP_HIGH = 11.5;
const STEP_LOW = 10.3;
const STEP_MIN_INTERVAL_MS = 280;

function hasDeviceMotion(): boolean {
  return typeof window !== "undefined" && "DeviceMotionEvent" in window;
}

function needsIOSPermission(): boolean {
  const DME = (window as any).DeviceMotionEvent;
  return typeof DME?.requestPermission === "function";
}

export function initMotion() {
  if (!hasDeviceMotion()) {
    for (const id of CARDS) setStatus(id, "unavailable", "No API");
    return;
  }

  const accelCanvas = getCanvas("accel");
  const accelSpark = accelCanvas ? new Sparkline(accelCanvas) : null;
  const gyroCanvas = getCanvas("gyro");
  const gyroSpark = gyroCanvas ? new Sparkline(gyroCanvas) : null;

  const gravityLP = { x: 0, y: 0, z: 9.8 };
  const magHistory: number[] = [];
  let started = false;

  let stepCount = 0;
  let aboveThreshold = false;
  let lastStepAt = 0;
  const stepTimes: number[] = [];

  function onMotion(e: DeviceMotionEvent) {
    const g = e.accelerationIncludingGravity;
    const lin = e.acceleration;
    const rot = e.rotationRate;

    if (g && (g.x != null || g.y != null || g.z != null)) {
      const x = g.x ?? 0,
        y = g.y ?? 0,
        z = g.z ?? 0;
      const mag = Math.sqrt(x * x + y * y + z * z);
      setStatus("accel", "live");
      setFields("accel", { x: x.toFixed(2), y: y.toFixed(2), z: z.toFixed(2), mag: mag.toFixed(2) });
      setTriaxis("accel", x, y, z, 20);
      accelSpark?.push(mag);

      magHistory.push(mag);
      if (magHistory.length > 30) magHistory.shift();
      if (magHistory.length >= 8) {
        const mean = magHistory.reduce((a, b) => a + b, 0) / magHistory.length;
        const variance = magHistory.reduce((a, b) => a + (b - mean) ** 2, 0) / magHistory.length;
        const moving = variance > 0.35;
        setStatus("activity", "live");
        setFields("activity", {
          state: moving ? "Moving" : "Stationary",
          variance: variance.toFixed(3),
        });
        setStateViz(
          "activity",
          moving ? "\u{1F3C3}" : "\u{1F9CD}",
          moving ? "Moving" : "Stationary",
          moving ? "active" : "idle"
        );
      }

      const now = Date.now();
      if (mag > STEP_HIGH && !aboveThreshold && now - lastStepAt > STEP_MIN_INTERVAL_MS) {
        aboveThreshold = true;
        lastStepAt = now;
        stepCount++;
        stepTimes.push(now);
      } else if (mag < STEP_LOW) {
        aboveThreshold = false;
      }
      while (stepTimes.length && now - stepTimes[0] > 60000) stepTimes.shift();
      setStatus("steps", "live");
      setFields("steps", { count: String(stepCount), cadence: `${stepTimes.length}/min` });
      setGauge("steps", Math.min(100, (stepTimes.length / 150) * 100), String(stepCount));

      if (lin && (lin.x != null || lin.y != null || lin.z != null)) {
        const gx = x - (lin.x ?? 0),
          gy = y - (lin.y ?? 0),
          gz = z - (lin.z ?? 0);
        setStatus("gravity", "live");
        setFields("gravity", { x: gx.toFixed(2), y: gy.toFixed(2), z: gz.toFixed(2) });
        setTriaxis("gravity", gx, gy, gz, 10);
      } else {
        gravityLP.x = gravityLP.x * 0.9 + x * 0.1;
        gravityLP.y = gravityLP.y * 0.9 + y * 0.1;
        gravityLP.z = gravityLP.z * 0.9 + z * 0.1;
        setStatus("gravity", "live", "Estimated");
        setFields("gravity", {
          x: gravityLP.x.toFixed(2),
          y: gravityLP.y.toFixed(2),
          z: gravityLP.z.toFixed(2),
        });
        setTriaxis("gravity", gravityLP.x, gravityLP.y, gravityLP.z, 10);
      }
    } else {
      setStatus("accel", "unavailable", "Not reported");
      setStatus("gravity", "unavailable", "Not reported");
      setStatus("activity", "unavailable", "Not reported");
      setStatus("steps", "unavailable", "Not reported");
    }

    if (lin && (lin.x != null || lin.y != null || lin.z != null)) {
      const lx = lin.x ?? 0,
        ly = lin.y ?? 0,
        lz = lin.z ?? 0;
      setStatus("linaccel", "live");
      setFields("linaccel", { x: lx.toFixed(2), y: ly.toFixed(2), z: lz.toFixed(2) });
      setTriaxis("linaccel", lx, ly, lz, 15);
    } else {
      setStatus("linaccel", "unavailable", "Not reported");
    }

    if (rot && (rot.alpha != null || rot.beta != null || rot.gamma != null)) {
      const a = rot.alpha ?? 0,
        b = rot.beta ?? 0,
        c = rot.gamma ?? 0;
      setStatus("gyro", "live");
      setFields("gyro", { x: a.toFixed(1), y: b.toFixed(1), z: c.toFixed(1) });
      setTriaxis("gyro", a, b, c, 180);
      gyroSpark?.push(Math.sqrt(a * a + b * b + c * c));
    } else {
      setStatus("gyro", "unavailable", "Not reported");
    }
  }

  function start() {
    if (started) return;
    started = true;
    for (const id of CARDS) setStatus(id, "waiting");
    window.addEventListener("devicemotion", onMotion);
    window.setTimeout(() => {
      for (const id of CARDS) {
        if (document.getElementById(`card-${id}`)?.dataset.status === "waiting") {
          setStatus(id, "waiting", "No data yet");
        }
      }
    }, 2600);
    addAction(
      "steps",
      "Reset Count",
      () => {
        stepCount = 0;
        aboveThreshold = false;
        stepTimes.length = 0;
        setFields("steps", { count: "0", cadence: "0/min" });
      },
      false
    );
  }

  if (needsIOSPermission()) {
    for (const id of CARDS) setStatus(id, "permission");
    showGate("motion", true);
    onGateClick("motion", async () => {
      try {
        const res = await (window as any).DeviceMotionEvent.requestPermission();
        if (res === "granted") {
          showGate("motion", false);
          start();
        } else {
          for (const id of CARDS) setStatus(id, "denied");
        }
      } catch {
        for (const id of CARDS) setStatus(id, "error");
      }
    });
  } else {
    start();
  }
}
