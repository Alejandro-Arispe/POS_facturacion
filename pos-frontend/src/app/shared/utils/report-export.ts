/**
 * Exportacion de reportes tabulares a PDF y Excel (HU-24), sin librerias externas.
 * El archivo lleva titulo, fecha de generacion y los filtros aplicados; se puede exportar aunque no haya filas.
 */
export interface ReportTable {
  title: string;
  /** Filtros aplicados, por ejemplo "Sucursal: Centro". */
  filters: string[];
  headers: string[];
  rows: Array<Array<string | number | null | undefined>>;
  /** Lineas finales opcionales (totales, notas). */
  footer?: string[];
}

export function exportReportPdf(table: ReportTable, fileName: string): void {
  const widths = table.headers.map((h, i) =>
    Math.min(28, Math.max(h.length, ...table.rows.map((r) => cell(r[i]).length))),
  );
  const line = (values: string[]) => values.map((v, i) => pad(v, widths[i], i > 0 && isNumeric(v))).join('  ');
  const lines = [
    table.title.toUpperCase(),
    '='.repeat(Math.min(92, table.title.length)),
    `Generado: ${new Date().toLocaleString('es-BO')}`,
    ...table.filters.map((f) => `Filtro - ${f}`),
    '',
    line(table.headers),
    '-'.repeat(Math.min(92, widths.reduce((a, w) => a + w + 2, 0))),
    ...(table.rows.length ? table.rows.map((r) => line(r.map(cell))) : ['Sin datos para los filtros seleccionados.']),
    ...(table.footer?.length ? ['', ...table.footer] : []),
  ];
  download(new Blob([buildPdf(lines).buffer as ArrayBuffer], { type: 'application/pdf' }), `${fileName}.pdf`);
}

export function exportReportExcel(table: ReportTable, fileName: string): void {
  const esc = (v: string) => {
    // Evita formulas inyectadas al abrir en Excel.
    if (/^[\s\x00-\x1f]*[=+\-@]/.test(v) && !isNumeric(v)) v = "'" + v;
    return v.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  };
  const filters = table.filters.map((f) => `<p>${esc(f)}</p>`).join('');
  const head = table.headers.map((h) => `<th>${esc(h)}</th>`).join('');
  const body = table.rows.map((r) => `<tr>${r.map((v) => `<td>${esc(cell(v))}</td>`).join('')}</tr>`).join('');
  const footer = (table.footer ?? []).map((f) => `<p><strong>${esc(f)}</strong></p>`).join('');
  const html = `<!doctype html><html><head><meta charset="utf-8"></head><body>
    <h3>${esc(table.title)}</h3><p>Generado: ${esc(new Date().toLocaleString('es-BO'))}</p>${filters}
    <table border="1"><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>${footer}</body></html>`;
  download(new Blob(['\ufeff' + html], { type: 'application/vnd.ms-excel' }), `${fileName}.xls`);
}

function cell(value: string | number | null | undefined): string {
  if (value === null || value === undefined) return '';
  return typeof value === 'number' ? (Number.isInteger(value) ? String(value) : value.toFixed(2)) : String(value);
}

function isNumeric(value: string): boolean {
  return /^-?\d+(\.\d+)?%?$/.test(value.trim());
}

function pad(value: string, width: number, right: boolean): string {
  const v = value.length > width ? value.slice(0, width - 1) + '.' : value;
  return right ? v.padStart(width) : v.padEnd(width);
}

function download(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** PDF minimo (Helvetica/Courier, A4) con paginacion; mismo enfoque que los reportes estaticos. */
function buildPdf(rawLines: string[]): Uint8Array {
  const safe = (v: string) => v.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^\x20-\x7E]/g, '');
  // En un PDF, la barra invertida y los parentesis se escapan con una barra invertida.
  const esc = (v: string) => safe(v).replace(/[\\()]/g, (c) => '\\' + c);
  const lines = rawLines.flatMap((l) => {
    const s = safe(l);
    const out: string[] = [];
    for (let i = 0; i < Math.max(1, s.length); i += 92) out.push(s.slice(i, i + 92));
    return out;
  });
  const pages: string[][] = [];
  for (let i = 0; i < lines.length; i += 50) pages.push(lines.slice(i, i + 50));
  const objects: string[] = ['<< /Type /Catalog /Pages 2 0 R >>', '', '<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>'];
  const pageRefs: number[] = [];
  for (const page of pages) {
    const stream = ['BT', '/F1 9 Tf', ...page.map((l, i) => (i === 0 ? `40 800 Td (${esc(l)}) Tj` : `0 -15 Td (${esc(l)}) Tj`)), 'ET'].join('\n');
    objects.push(`<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`);
    const content = objects.length;
    objects.push(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents ${content} 0 R >>`);
    pageRefs.push(objects.length);
  }
  objects[1] = `<< /Type /Pages /Kids [${pageRefs.map((n) => `${n} 0 R`).join(' ')}] /Count ${pageRefs.length} >>`;
  let pdf = '%PDF-1.4\n';
  const offsets: number[] = [];
  objects.forEach((o, i) => {
    offsets.push(pdf.length);
    pdf += `${i + 1} 0 obj\n${o}\nendobj\n`;
  });
  const xref = pdf.length;
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  offsets.forEach((o) => (pdf += `${String(o).padStart(10, '0')} 00000 n \n`));
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`;
  return new TextEncoder().encode(pdf);
}
