import { SECTIONS } from "./cards";

interface Column {
  cardId: string;
  key: string;
  header: string;
}

const MAX_ROWS = 3600; // ~1 hour at 1 sample/sec, keeps memory bounded
let columns: Column[] = [];
const rows: string[][] = [];

function buildColumns(): Column[] {
  const cols: Column[] = [];
  for (const section of SECTIONS) {
    for (const card of section.cards) {
      if (!card.fields?.length) continue;
      for (const f of card.fields) {
        const header = f.unit ? `${card.title} — ${f.label} (${f.unit})` : `${card.title} — ${f.label}`;
        cols.push({ cardId: card.id, key: f.key, header });
      }
    }
  }
  return cols;
}

export function readValue(cardId: string, key: string): string {
  const card = document.getElementById(`card-${cardId}`);
  const el = card?.querySelector<HTMLElement>(`[data-field="${key}"]`);
  if (!el) return "";
  // The unit lives in a nested <span class="unit">; the raw value is the
  // leading text node — read that directly so exports don't carry the unit twice.
  for (const node of Array.from(el.childNodes)) {
    if (node.nodeType === Node.TEXT_NODE) {
      const txt = node.textContent?.trim() ?? "";
      if (txt) return txt;
    }
  }
  return el.textContent?.trim() ?? "";
}

export function csvEscape(value: string): string {
  if (/[",\n]/.test(value)) return `"${value.replace(/"/g, '""')}"`;
  return value;
}

export function initDataLog() {
  columns = buildColumns();
  window.setInterval(() => {
    const row = [new Date().toISOString(), ...columns.map((c) => readValue(c.cardId, c.key))];
    rows.push(row);
    if (rows.length > MAX_ROWS) rows.shift();
    const counter = document.getElementById("log-count");
    if (counter) counter.textContent = String(rows.length);
  }, 1000);
}

export function exportCsv() {
  const header = ["Timestamp", ...columns.map((c) => c.header)];
  const lines = [header, ...rows].map((r) => r.map(csvEscape).join(","));
  // Leading BOM so Excel/Sheets read the UTF-8 (µ, °, etc.) correctly; CRLF for Windows/Sheets compatibility.
  const csv = "﻿" + lines.join("\r\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  const ts = new Date().toISOString().replace(/[:.]/g, "-");
  a.href = url;
  a.download = `SensoLab-sensor-log-${ts}.csv`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
