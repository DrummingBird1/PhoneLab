export function initSearch() {
  const maybeInput = document.getElementById("card-search") as HTMLInputElement | null;
  const countEl = document.getElementById("search-count");
  if (!maybeInput) return;
  const input = maybeInput;

  const cards = Array.from(document.querySelectorAll<HTMLElement>(".card"));
  const entries = cards.map((card) => ({
    card,
    haystack: (card.querySelector("h3")?.textContent ?? "").toLowerCase(),
  }));

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
    if (countEl) countEl.textContent = q === "" ? "" : `${shown} of ${entries.length} sensors`;
  }

  input.addEventListener("input", apply);
}
