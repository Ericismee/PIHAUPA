import { esc } from './charts.js';
const icons = {
  overview: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  batches: 'M8 6h13 M8 12h13 M8 18h13 M3 6h1 M3 12h1 M3 18h1',
  rescan: 'M12 3v6 M5 15v-3h14v3 M5 15v6 M19 15v6 M8 3h8 M2 21h6 M16 21h6',
  patterns: 'M3 5h18v14H3z M3 10h18 M9 10v9 M15 10v9',
  measurements: 'M4 3v18h17 M8 16v-4 M13 16V7 M18 16v-7',
  datasets: 'M20 6c0 2-16 2-16 0s16-2 16 0 M4 6v12c0 3 16 3 16 0V6 M4 12c0 3 16 3 16 0',
  verification: 'M12 3l8 4v5c0 5-8 9-8 9s-8-4-8-9V7z M8 12l3 3 5-6',
  glossary: 'M3 4h7l2 2 2-2h7v15h-7l-2 2-2-2H3z M12 6v15',
  play: 'M8 4l12 8-12 8z',
  download: 'M12 3v12 M7 10l5 5 5-5 M4 16v5h16v-5',
  clock: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18 M12 7v5l3 2',
  info: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18 M12 11v6 M12 7h.01',
  arrow: 'M4 12h16 M14 6l6 6-6 6',
};
export const icon = (name) =>
  `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="${icons[name] || icons.overview}"/></svg>`;
export const badge = (text, kind = 'neutral') => `<span class="tag ${kind}">${esc(text)}</span>`;
export const card = (title, subtitle, body, extra = '') =>
  `<section class="card"><div class="card-head"><div><h2>${title}</h2><p>${subtitle}</p></div>${extra}</div><div class="card-body">${body}</div></section>`;
export const kpi = (title, value, note, ico = 'overview', unit = '') =>
  `<div class="card kpi"><div class="kpi-top">${title}${icon(ico)}</div><div class="kpi-value">${value}<small>${unit}</small></div><div class="kpi-note">${note}</div></div>`;
export const notice = (text) => `<div class="notice">${icon('info')}<div>${text}</div></div>`;
export const last = (report) => report.batches.at(-1);
export function action(batch) {
  return batch.index === 0
    ? badge('Initial scan', 'blue')
    : batch.rescanTriggered
      ? badge('Re-scan', 'amber')
      : badge('Cập nhật PT', 'green');
}
export function table(headers, rows, caption = '') {
  // Một quy tắc căn lề cho cả cột: tiêu đề và mọi ô dữ liệu phải khớp nhau.
  const cells = rows.map((row) => [...row.matchAll(/<td\b([^>]*)>([\s\S]*?)<\/td>/g)]);
  const numericColumns = headers.map((_, i) =>
    cells.some((row) => /class="[^"]*\bnumeric\b/.test(row[i]?.[1] || '')),
  );
  const body = rows
    .map((row) => {
      let index = 0;
      return row.replace(/<td\b([^>]*)>/g, (_, attributes) => {
        const alignment = numericColumns[index++] ? 'column-number' : 'column-text';
        if (/class="/.test(attributes))
          attributes = attributes.replace(/class="/, `class="${alignment} `);
        else attributes += ` class="${alignment}"`;
        return `<td${attributes}>`;
      });
    })
    .join('');
  return `<div class="table-wrap"><table class="data-table"><caption>${caption || 'Bảng · ' + headers.slice(0, 3).join(' / ')}</caption><thead><tr>${headers.map((h, i) => `<th scope="col" class="${numericColumns[i] ? 'column-number' : 'column-text'}">${h}</th>`).join('')}</tr></thead><tbody>${body}</tbody></table></div>`;
}
export const tr = (values) =>
  `<tr>${values.map((v) => `<td class="${typeof v === 'number' || /^-?[0-9][0-9., ]*$/.test(String(v)) ? 'numeric' : ''}">${v}</td>`).join('')}</tr>`;
