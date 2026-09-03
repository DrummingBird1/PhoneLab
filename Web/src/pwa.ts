export function initInstallPrompt() {
  const btn = document.getElementById("install-btn") as HTMLButtonElement | null;
  if (!btn) return;
  let deferred: any = null;

  window.addEventListener("beforeinstallprompt", (e) => {
    e.preventDefault();
    deferred = e;
    btn.hidden = false;
  });

  btn.addEventListener("click", async () => {
    if (!deferred) return;
    deferred.prompt();
    await deferred.userChoice;
    deferred = null;
    btn.hidden = true;
  });

  window.addEventListener("appinstalled", () => {
    btn.hidden = true;
    deferred = null;
  });
}
