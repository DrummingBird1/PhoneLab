import { setCardAriaLive } from "./ui";

let overlay: HTMLElement | null = null;
let placeholder: Comment | null = null;
let openCardId: string | null = null;

function closeFocus() {
  if (!overlay || !placeholder || !openCardId) return;
  const card = overlay.querySelector(".card");
  if (card && placeholder.parentNode) {
    placeholder.parentNode.replaceChild(card, placeholder);
  }
  setCardAriaLive(openCardId, false);
  overlay.remove();
  overlay = null;
  placeholder = null;
  const lastId = openCardId;
  openCardId = null;
  document.getElementById(`focus-btn-return-${lastId}`)?.focus();
}

function openFocus(card: HTMLElement) {
  const id = card.dataset.cardId;
  if (!id) return;
  placeholder = document.createComment("focus-placeholder");
  card.replaceWith(placeholder);

  overlay = document.createElement("div");
  overlay.className = "focus-overlay";
  overlay.setAttribute("role", "dialog");
  overlay.setAttribute("aria-modal", "true");

  const closeBtn = document.createElement("button");
  closeBtn.className = "focus-close";
  closeBtn.id = `focus-btn-return-${id}`;
  closeBtn.textContent = "✕ Close";
  closeBtn.setAttribute("aria-label", "Close expanded view");
  closeBtn.addEventListener("click", closeFocus);

  overlay.appendChild(closeBtn);
  overlay.appendChild(card);
  document.body.appendChild(overlay);
  openCardId = id;
  setCardAriaLive(id, true);
  closeBtn.focus();
}

export function initFocusMode() {
  document.addEventListener("click", (e) => {
    const btn = (e.target as HTMLElement).closest<HTMLElement>('[data-role="focus-btn"]');
    if (btn) {
      const card = btn.closest<HTMLElement>(".card");
      if (card) openFocus(card);
      return;
    }
    if (overlay && e.target === overlay) closeFocus();
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && overlay) closeFocus();
  });
}
