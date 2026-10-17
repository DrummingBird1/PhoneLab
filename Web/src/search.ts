import { SECTIONS } from "./cards";
import { t, tf } from "./i18n";

/** English card titles, so "GPS" or "battery" still finds a card while the UI is in another language. */
const ENGLISH_TITLE = new Map(SECTIONS.flatMap((s) => s.cards.map((c) => [c.id, c.title] as const)));

export function initSearch() {
  const maybeInput = document.getElementById("card-search") as HTMLInputElement | null;
  const countEl = document.getElementById("search-count");
  if (!maybeInput) return;
  const input = maybeInput;

  const cards = Array.from(document.querySelectorAll<HTMLElement>(".card"));
  const entries = cards.map((card) => ({
    card,
    haystack:
      `${card.querySelector("h3")?.textContent ?? ""} ${ENGLISH_TITLE.get(card.dataset.cardId ?? "") ?? ""}`.toLowerCase(),
  }));
  // Read by the .section.search-empty::after rule.
  const emptyLabel = t("No sensors in this section match your search.");
  document.querySelectorAll<HTMLElement>(".section").forEach((s) => (s.dataset.emptyLabel = emptyLabel));

  function apply() {
    const q = input.value.trim().toLowerCase();
    let shown = 0;
    for (const { card, haystack } of entries) {
      const match = q === "" || haystack.includes(q);
      card.classList.toggle("search-hidden", !match);
      if (match) shown++;
    }
    document.querySelectorAll<HTMLElement>(".section").forEach((section) => {
      const anyVisible = Array.from(section.querySelectorAll(".card")).some(
        (c) => !c.classList.contains("search-hidden")
      );
      section.classList.toggle("search-empty", !anyVisible && q !== "");
    });
    if (countEl) countEl.textContent = q === "" ? "" : tf("{shown} of {total} sensors", { shown, total: entries.length });
  }

  input.addEventListener("input", apply);
}
