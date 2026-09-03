const STORAGE_KEY = "sensolab-card-order";

function loadOrder(): Record<string, string[]> {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "{}");
  } catch {
    return {};
  }
}

function saveOrder(order: Record<string, string[]>) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(order));
  } catch {
    /* storage unavailable — reordering just won't persist */
  }
}

function applyStoredOrder(grid: HTMLElement, sectionId: string, order: Record<string, string[]>) {
  const ids = order[sectionId];
  if (!ids) return;
  for (const id of ids) {
    const card = grid.querySelector(`#card-${id}`);
    if (card) grid.appendChild(card);
  }
}

function currentOrder(grid: HTMLElement): string[] {
  return Array.from(grid.querySelectorAll<HTMLElement>(".card")).map((c) => c.dataset.cardId ?? "");
}

export function initReorder() {
  const order = loadOrder();
  document.querySelectorAll<HTMLElement>(".grid").forEach((grid) => {
    const section = grid.closest<HTMLElement>(".section");
    const sectionId = section?.id.replace("section-", "") ?? "";
    applyStoredOrder(grid, sectionId, order);

    grid.querySelectorAll<HTMLElement>(".card").forEach((card) => {
      card.setAttribute("draggable", "true");
    });

    let dragged: HTMLElement | null = null;

    grid.addEventListener("dragstart", (e) => {
      const card = (e.target as HTMLElement).closest<HTMLElement>(".card");
      if (!card) return;
      dragged = card;
      card.classList.add("dragging");
      e.dataTransfer?.setData("text/plain", card.id);
      if (e.dataTransfer) e.dataTransfer.effectAllowed = "move";
    });

    grid.addEventListener("dragend", () => {
      dragged?.classList.remove("dragging");
      dragged = null;
      order[sectionId] = currentOrder(grid);
      saveOrder(order);
    });

    grid.addEventListener("dragover", (e) => {
      e.preventDefault();
      if (!dragged) return;
      const target = (e.target as HTMLElement).closest<HTMLElement>(".card");
      if (!target || target === dragged) return;
      const rect = target.getBoundingClientRect();
      const before = e.clientY < rect.top + rect.height / 2;
      grid.insertBefore(dragged, before ? target : target.nextSibling);
    });
  });
}
