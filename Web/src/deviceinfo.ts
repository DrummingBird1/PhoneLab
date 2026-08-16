import { setStatus, setFields, setMeter } from "./ui";

export function initDeviceInfo() {
  const cores = navigator.hardwareConcurrency;
  setStatus("cpu", cores ? "live" : "unavailable");
  setFields("cpu", {
    cores: cores ? String(cores) : "—",
    mem: (navigator as any).deviceMemory ? String((navigator as any).deviceMemory) : "—",
  });

  const nav: any = navigator;
  if (nav.getBattery) {
    nav
      .getBattery()
      .then((batt: any) => {
        function update() {
          setStatus("battery", "live");
          setFields("battery", {
            level: `${Math.round(batt.level * 100)}%`,
            charging: batt.charging ? "Yes" : "No",
            time: batt.charging
              ? isFinite(batt.chargingTime) ? `${Math.round(batt.chargingTime / 60)} min` : "—"
              : isFinite(batt.dischargingTime) ? `${Math.round(batt.dischargingTime / 60)} min` : "—",
          });
          setMeter("battery", batt.level * 100);
        }
        update();
        batt.addEventListener("levelchange", update);
        batt.addEventListener("chargingchange", update);
      })
      .catch(() => setStatus("battery", "unavailable"));
  } else {
    setStatus("battery", "unavailable", "No API");
  }

  function updateNetwork() {
    const conn = nav.connection || nav.mozConnection || nav.webkitConnection;
    setStatus("network", "live");
    setFields("network", {
      status: navigator.onLine ? "Online" : "Offline",
      type: conn?.effectiveType ?? "—",
      downlink: conn?.downlink != null ? String(conn.downlink) : "—",
      rtt: conn?.rtt != null ? String(conn.rtt) : "—",
    });
  }
  updateNetwork();
  window.addEventListener("online", updateNetwork);
  window.addEventListener("offline", updateNetwork);
  nav.connection?.addEventListener?.("change", updateNetwork);

  if (navigator.storage?.estimate) {
    navigator.storage
      .estimate()
      .then((est) => {
        setStatus("storage", "live");
        const fmt = (n?: number) => (n != null ? `${(n / (1024 * 1024)).toFixed(1)} MB` : "—");
        setFields("storage", { usage: fmt(est.usage), quota: fmt(est.quota) });
      })
      .catch(() => setStatus("storage", "unavailable"));
  } else {
    setStatus("storage", "unavailable", "No API");
  }

  function updateDisplay() {
    setStatus("display", "live");
    const w = screen.width || window.innerWidth;
    const h = screen.height || window.innerHeight;
    setFields("display", {
      res: `${w}×${h}`,
      dpr: window.devicePixelRatio.toFixed(2),
      color: String(screen.colorDepth || 24),
    });
  }
  updateDisplay();
  window.addEventListener("resize", updateDisplay);

  setStatus("platform", "live");
  setFields("platform", {
    browser: detectBrowser(),
    os: detectOS(),
    lang: navigator.language,
    tz: Intl.DateTimeFormat().resolvedOptions().timeZone ?? "—",
  });

  setStatus("input", "live");
  setFields("input", {
    touch: String(navigator.maxTouchPoints ?? 0),
    pointer: window.matchMedia("(pointer: coarse)").matches ? "Touch" : "Fine (mouse/trackpad)",
  });

  initCpuPressure();
  initBiometric();
  initPosture();
}

function initCpuPressure() {
  const PressureObserver = (window as any).PressureObserver;
  if (!PressureObserver) {
    setStatus("cpupressure", "unavailable", "No API in this browser");
    return;
  }
  try {
    const observer = new PressureObserver((records: any[]) => {
      const latest = records[records.length - 1];
      setStatus("cpupressure", "live");
      setFields("cpupressure", { state: latest.state });
    });
    observer.observe("cpu").catch((err: any) => {
      setStatus("cpupressure", "unavailable", err?.message ?? "Not supported");
    });
  } catch {
    setStatus("cpupressure", "unavailable", "Not supported");
  }
}

function initBiometric() {
  const PKC = (window as any).PublicKeyCredential;
  if (!PKC?.isUserVerifyingPlatformAuthenticatorAvailable) {
    setStatus("biometric", "unavailable", "No WebAuthn API");
    return;
  }
  PKC.isUserVerifyingPlatformAuthenticatorAvailable()
    .then((available: boolean) => {
      setStatus("biometric", "live");
      setFields("biometric", { available: available ? "Available" : "Not available" });
    })
    .catch(() => setStatus("biometric", "unavailable", "Not supported"));
}

function initPosture() {
  const posture = (navigator as any).devicePosture;
  if (!posture) {
    setStatus("posture", "unavailable", "No API — not a foldable, or unsupported browser");
    return;
  }
  function update() {
    setStatus("posture", "live");
    setFields("posture", { type: posture.type });
  }
  update();
  posture.addEventListener?.("change", update);
}

function detectBrowser(): string {
  const ua = navigator.userAgent;
  if (/Edg\//.test(ua)) return "Edge";
  if (/OPR\//.test(ua)) return "Opera";
  if (/CriOS/.test(ua)) return "Chrome (iOS)";
  if (/FxiOS/.test(ua)) return "Firefox (iOS)";
  if (/Chrome\//.test(ua) && !/Chromium/.test(ua)) return "Chrome";
  if (/Firefox\//.test(ua)) return "Firefox";
  if (/Safari\//.test(ua) && /Version\//.test(ua)) return "Safari";
  return "Unknown";
}

function detectOS(): string {
  const ua = navigator.userAgent;
  const platform = navigator.platform || "";
  if (/iPhone|iPad|iPod/.test(ua)) return "iOS";
  if (/Android/.test(ua)) return "Android";
  if (/Win/.test(platform)) return "Windows";
  if (/Mac/.test(platform) && navigator.maxTouchPoints > 1) return "iPadOS";
  if (/Mac/.test(platform)) return "macOS";
  if (/Linux/.test(platform)) return "Linux";
  return platform || "Unknown";
}
