import { setStatus, setFields, onToggle, setToggleState } from "./ui";

const ON_LABEL = "Screen will stay on — tap to allow sleep";
const OFF_LABEL = "Keep screen awake";

export function initWakeLock() {
  const nav: any = navigator;
  if (!nav.wakeLock) {
    setStatus("wakelock", "unavailable", "No Wake Lock API");
    setFields("wakelock", { state: "Unavailable" });
    return;
  }

  setStatus("wakelock", "idle");
  setFields("wakelock", { state: "Off" });
  let sentinel: any = null;
  let userWantsAwake = false;

  async function acquire() {
    try {
      sentinel = await nav.wakeLock.request("screen");
      setStatus("wakelock", "live");
      setFields("wakelock", { state: "Awake — screen won't sleep" });
      sentinel.addEventListener("release", () => {
        sentinel = null;
        // The OS releases the lock whenever the tab is hidden — if the user still
        // wants it on, visibilitychange below re-acquires; only reflect "off" in
        // the UI when they actually asked for it (or re-acquire failed).
        if (!userWantsAwake) {
          setStatus("wakelock", "idle");
          setFields("wakelock", { state: "Released" });
        }
      });
    } catch (err: any) {
      setStatus("wakelock", "error", err?.message ?? "Request failed");
    }
  }

  async function release() {
    await sentinel?.release?.();
    sentinel = null;
  }

  onToggle("wakelock", (on) => {
    userWantsAwake = on;
    setToggleState("wakelock", on, ON_LABEL, OFF_LABEL);
    if (on) acquire();
    else release();
  });

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible" && sentinel === null && userWantsAwake) acquire();
  });
}
