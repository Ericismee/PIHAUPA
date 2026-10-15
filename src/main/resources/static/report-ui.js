import { esc, num } from './charts.js';
import { last, badge, table, tr } from './ui-components.js';
export function auEvidence(patterns, batch, report) {
  return [
    {
      label: 'Ngưỡng phân loại',
      text: `${batch.name}: TU(DB) = ${num(batch.totalTransactionUtility)}; Sᵤ = ${num(report.upperThreshold, 17)}. Ngưỡng HAUP = ${num(report.upperThreshold, 17)} × ${num(batch.totalTransactionUtility)} = ${num(batch.minUtilUpper)} đơn vị tiện ích.`,
    },
    ...patterns
      .slice(0, 3)
      .map((p) => ({
        label: `Mẫu ${p.name}`,
        text: `Tổng tiện ích U(P) = ${num(p.sumUtility, 6)}; số item |P| = ${p.length}. AU(P) = ${num(p.sumUtility, 6)} / ${p.length} = ${num(p.averageUtility, 6)}. ${p.averageUtility >= batch.minUtilUpper ? `${num(p.averageUtility, 6)} ≥ ${num(batch.minUtilUpper)} ⇒ HAUP (LARGE).` : p.averageUtility >= batch.minUtilLower ? `${num(batch.minUtilLower)} ≤ ${num(p.averageUtility, 6)} < ${num(batch.minUtilUpper)} ⇒ PRE-LARGE, chưa đạt HAUP.` : `${num(p.averageUtility, 6)} < ${num(batch.minUtilLower)} ⇒ SMALL.`}`,
      })),
    {
      label: 'Phạm vi',
      text: `Minh họa ${Math.min(patterns.length, 3)}/${patterns.length} mẫu trên biểu đồ. AU chia cho số item của mẫu, không chia cho số giao dịch.`,
    },
  ];
}
export function paperConclusionEvidence(c) {
  const r = c.report,
    b = last(r),
    isPaper = c.manifest?.sourceKind
      ? c.manifest.sourceKind === 'paper'
      : c.configuration?.source === 'paper' || c.source === 'Bài báo · Tables 2–3';
  const standard =
    isPaper &&
    r.upperThreshold === 0.23 &&
    r.lowerThreshold === 0.1 &&
    r.transactionCount === 9 &&
    r.batches.length === 3;
  const reference =
    '<a href="https://doi.org/10.1186/s40537-025-01352-x" target="_blank" rel="noreferrer">Bài báo gốc · Example 4, Table 5, Tables 2–3 ↗</a>';
  if (!standard)
    return `<div class="conclusion-evidence"><h3>Phạm vi kiểm chứng</h3><p>${isPaper ? 'Dữ liệu mẫu · ngưỡng tùy chỉnh.' : 'Dataset thực nghiệm · cấu hình của lượt chạy.'} Đối chiếu theo tập HAUP và AU, không chỉ số lượng.</p><p>${c.comparison ? badge(c.comparison.sameHaups ? 'Original/Tight: KHỚP HAUP và AU' : 'Original/Tight: KHÔNG KHỚP', c.comparison.sameHaups ? 'green' : 'amber') : badge('Original/Tight: chưa chạy')}</p><p class="subtle">Chưa có oracle độc lập cho cấu hình này. ${reference}</p></div>`;
  // Với tên chữ ghép BE, chuẩn hóa theo item để không phụ thuộc thứ tự hiển thị.
  const canonical = (p) =>
    p.name
      .replace(/[^A-Z]/g, '')
      .split('')
      .sort()
      .join(',');
  const near = (a, e) => Number.isFinite(a) && Math.abs(a - e) < 1e-8;
  const expectedFinal = { E: 84, B: 75, 'B,E': 63.5 },
    expectedDb1 = { E: 72, 'B,E': 45 };
  const matches = (batch, expected) =>
    batch.largePatterns.length === Object.keys(expected).length &&
    batch.largePatterns.every((p) => near(p.averageUtility, expected[canonical(p)]));
  const db1 = r.batches[1],
    direct = matches(db1, expectedDb1),
    derived = matches(b, expectedFinal) && near(b.totalTransactionUtility, 240);
  const compareRows = Object.entries(expectedFinal).map(([name, au]) => {
    const p = b.largePatterns.find((p) => canonical(p) === name);
    return tr([
      `<strong class="mono">${esc('{' + name + '}')}</strong>`,
      name === 'B,E' ? '127 / 2' : name === 'E' ? '84 / 1' : '75 / 1',
      num(au, 6),
      p ? num(p.averageUtility, 6) : 'Thiếu mẫu',
      badge(
        near(p?.averageUtility, au) ? 'KHỚP' : 'KHÔNG KHỚP',
        near(p?.averageUtility, au) ? 'green' : 'amber',
      ),
    ]);
  });
  return `<div class="conclusion-evidence"><h3>Đối chiếu HAUP với dữ liệu bài báo</h3><div class="evidence-grid"><section><h4>DB1 · Table 5 / Example 4</h4><p>HAUP: <strong>{E}:72 · {B,E}:45</strong></p><p>TU=173 · Sᵤ×TU=39,79</p><p>Tight: không re-scan · Original: re-scan</p>${badge(direct ? 'KHỚP HAUP / AU' : 'KHÔNG KHỚP', direct ? 'green' : 'amber')}</section><section><h4>DB2 · Tính từ Tables 2–3</h4><p>HAUP: <strong>{E}, {B}, {B,E}</strong></p><p>TU=240 · LARGE ≥55,2 · PRE-LARGE ≥24 và &lt;55,2</p><p class="subtle">Giá trị tính từ dữ liệu mẫu; không phải bảng DB2 in trực tiếp.</p>${badge(derived ? 'KHỚP 3 HAUP' : 'KHÔNG KHỚP', derived ? 'green' : 'amber')}</section></div>${table(['Mẫu P', 'U(P) / |P|', 'AU tham chiếu', 'AU thực tính', 'Đối chiếu'], compareRows, 'Bảng · AU của HAUP sau DB2 (utility)')}<p class="conclusion-line"><strong>Kết luận:</strong> ${derived ? '3 HAUP đạt AU ≥55,2.' : 'Tập HAUP/AU chưa khớp tham chiếu.'} PRE-LARGE không tính vào HAUP.</p><p>${c.comparison ? badge(c.comparison.sameHaups ? 'Original/Tight: KHỚP HAUP / AU' : 'Original/Tight: KHÔNG KHỚP', c.comparison.sameHaups ? 'green' : 'amber') : badge('Original/Tight: chưa chạy trong lượt này')}</p><details class="source-note"><summary>Nguồn & phạm vi</summary><p>BE và EB cùng tập item {B,E}. Tham chiếu từ dữ liệu mẫu: Tight 1, Original 2 re-scan, không gồm DB0. Thời gian là số đo trên máy hiện tại; ít re-scan không đồng nghĩa nhanh gấp đôi. Kiểm chứng mẫu không xác nhận mọi dataset.</p><p>${reference}</p></details></div>`;
}
export function finalConclusion(c) {
  const r = c.report,
    b = last(r),
    patterns = [...b.largePatterns].sort((a, b) => b.averageUtility - a.averageUtility),
    maxBound = r.upperBounds.reduce((max, x) => Math.max(max, x.auub), 0);
  const empty = patterns.length
    ? ''
    : `<p class="final-empty"><strong>Kết quả cuối: không có HAUP ở ngưỡng này.</strong> ${maxBound < b.minUtilUpper ? `max AUUB = ${num(maxBound)} &lt; ${num(b.minUtilUpper)}: cận trên loại trừ HAUP.` : 'Không có mẫu LARGE trong kết quả miner trả về.'} Đây là kết quả đã xử lý, không phải thiếu dữ liệu.</p>`;
  return `<section class="card final-conclusion" id="analysis-final"><div class="card-head"><div><h2>Bước cuối · Kết luận khai phá</h2><p>${esc(c.source)} · toàn bộ ${num(r.transactionCount, 0)} giao dịch đến ${esc(b.name)} · độc lập với bộ lọc bên trên</p></div>${badge('Đã hoàn tất', 'green')}</div><div class="card-body"><dl class="final-numbers" aria-label="Số liệu kết quả cuối"><div><dt>HAUP cuối</dt><dd>${num(patterns.length, 0)}<small>mẫu LARGE</small></dd></div><div><dt>Ngưỡng HAUP</dt><dd>${num(b.minUtilUpper, 6)}<small>Sᵤ × TU(DB) · utility</small></dd></div><div><dt>Tổng tiện ích TU(DB)</dt><dd>${num(b.totalTransactionUtility, 6)}<small>utility · ${esc(b.name)}</small></dd></div><div><dt>Re-scan</dt><dd>${num(r.stats.rescanCount, 0)}<small>lần · không gồm DB0</small></dd></div></dl><p><strong>${num(patterns.length, 0)} HAUP (LARGE)</strong> sau ${esc(b.name)}. ${num(b.preLargePatterns.length, 0)} PRE-LARGE đang quản lý là tập dự phòng, không tính vào HAUP.</p><details class="final-formula"><summary>Ngưỡng phân loại · AU ≥ ${num(b.minUtilUpper)}</summary><div class="formula">TU(DB) = ${num(b.totalTransactionUtility)}<br>Ngưỡng HAUP = Sᵤ × TU(DB) = ${num(r.upperThreshold, 17)} × ${num(b.totalTransactionUtility)} = ${num(b.minUtilUpper)}<br>Mẫu đạt khi AU(P) = U(P) / |P| ≥ ${num(b.minUtilUpper)}</div></details>${verificationBadges(c)}${empty}<details class="report-proof"><summary>Kiểm chứng & phép tính</summary>${paperConclusionEvidence(c)}</details><p>Đã xử lý ${r.batchCount} batch · ${r.stats.rescanCount} lần re-scan (không gồm DB0) · thuật toán ${num(r.elapsedMs)} ms.</p><p class="subtle">PRE-LARGE và số đo là thông tin hỗ trợ; tập HAUP/AU là kết quả khai phá.</p></div>${
    patterns.length
      ? table(
          ['HAUP cuối P', 'U(P)', '|P|', 'AU(P)', 'Sᵤ × TU(DB)', 'Phân loại'],
          patterns
            .slice(0, 20)
            .map((p) =>
              tr([
                esc(p.name),
                num(p.sumUtility, 6),
                p.length,
                num(p.averageUtility, 6),
                num(b.minUtilUpper, 6),
                'LARGE / HAUP',
              ]),
            ),
          'Bảng · HAUP cuối — tối đa 20 mẫu theo AU giảm dần',
        )
      : ''
  }<div class="card-body">${manifestPanel(c)}<a href="#analysis-patterns" class="button final-all">Xem toàn bộ HAUP cuối (${patterns.length})</a><p class="subtle">Xuất Gói báo cáo ZIP để lưu công thức, kết quả và số đo của chính lượt này.</p></div></section>`;
}

export function verificationBadges(c) {
  const r = c.report,
    b = last(r),
    paper =
      (c.manifest?.sourceKind
        ? c.manifest.sourceKind === 'paper'
        : c.source === 'Bài báo · Tables 2–3') &&
      r.upperThreshold === 0.23 &&
      r.lowerThreshold === 0.1 &&
      r.transactionCount === 9;
  const canonical = (p) =>
    p.name
      .replace(/[^A-Z]/g, '')
      .split('')
      .sort()
      .join(',');
  const expected = { E: 84, B: 75, 'B,E': 63.5 };
  const matched =
    paper &&
    b.largePatterns.length === 3 &&
    b.largePatterns.every(
      (p) => Math.abs(p.averageUtility - (expected[canonical(p)] ?? NaN)) < 1e-8,
    );
  return `<div class="verification-strip" aria-label="Mức kiểm chứng">${paper ? badge(matched ? 'Ví dụ bài báo: KHỚP' : 'Ví dụ bài báo: KHÔNG KHỚP', matched ? 'green' : 'amber') : badge('Ví dụ bài báo: không áp dụng')}${badge(c.comparison ? (c.comparison.sameHaups ? 'Original/Tight: KHỚP' : 'Original/Tight: LỆCH') : 'Original/Tight: chưa chạy', c.comparison?.sameHaups ? 'green' : 'neutral')}${badge(c.oracle?.state === 'PASSED' ? 'Oracle độc lập: KHỚP' : c.oracle?.state === 'FAILED' ? 'Oracle độc lập: LỆCH' : 'Oracle độc lập: chưa đối chứng', c.oracle?.state === 'PASSED' ? 'green' : c.oracle?.state === 'FAILED' ? 'amber' : 'neutral')}</div>`;
}
export function manifestPanel(c) {
  const m = c.manifest;
  if (!m) return '';
  return `<details class="manifest-panel"><summary>Hồ sơ thực nghiệm · ${m.measuredRuns} lượt đo</summary><dl><div><dt>SHA-256 · ${esc(m.hashKind)}</dt><dd class="mono">${esc(m.sha256)}</dd></div><div><dt>Phiên bản source</dt><dd class="mono">${esc(m.revision || 'unavailable')} · dirty: ${esc(m.workingTreeDirty || 'unknown')}</dd></div><div><dt>Môi trường</dt><dd>Java ${esc(m.javaVersion)} · ${esc(m.os)} · heap tối đa ${num(m.maxHeapMiB)} MiB</dd></div><div><dt>Batch</dt><dd>${m.batches.map((b) => esc(b.name) + ': ' + num(b.rows, 0) + ' giao dịch').join(' · ')}</dd></div><div><dt>Đo lường</dt><dd>${m.measuredRuns} lượt + ${m.warmupRuns} warm-up · median ${num(c.measurement.medianMs ?? c.report.elapsedMs)} ms · khoảng ${num(c.measurement.minMs ?? c.report.elapsedMs)}–${num(c.measurement.maxMs ?? c.report.elapsedMs)} ms</dd></div><div><dt>Oracle</dt><dd>${esc(c.oracle?.scope || c.oracle?.reason || 'Chưa đối chứng')}</dd></div></dl><p class="subtle">manifest.json, verification.json và timing-samples.csv nằm trong ZIP. Hash raw file không gồm ngưỡng; hash mẫu nhúng dùng biểu diễn chuẩn hóa.</p></details>`;
}
