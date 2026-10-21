export const colors = {
  blue: '#2563eb',
  green: '#168364',
  amber: '#c78b25',
  slate: '#7990ac',
  cyan: '#0891b2',
};

export const esc = (value) =>
  String(value ?? '').replace(
    /[&<>"']/g,
    (c) =>
      ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;',
      })[c],
  );

export const num = (value, digits = 2) =>
  value == null
    ? 'Không có số đo'
    : new Intl.NumberFormat('vi-VN', {
        maximumFractionDigits: digits,
      }).format(value);

/** SVG charts use the real report, zero baseline, native tooltips, and a data alternative. */
export function chart(
  labels,
  series,
  {
    kind = 'bar',
    unit = '',
    height = 220,
    emptyMessage = 'Chưa có dữ liệu để vẽ biểu đồ.',
    expanded = false,
    xLabel = '',
    yLabel = '',
    explanation = '',
    evidenceRows = [],
    formula = '',
  } = {},
) {
  if (!labels.length || !series.length) {
    return `<div class="bar-empty">${esc(emptyMessage)}</div>`;
  }

  xLabel =
    xLabel ||
    (labels.every((l) => /^DB|^DB0/.test(l))
      ? 'Batch dữ liệu'
      : labels.every((l) => /^TC-/.test(l))
        ? 'Test case'
        : labels.every((l) => /^Lượt /.test(l))
          ? 'Lượt chạy'
          : labels.every((l) => / · [0-9]+$/.test(l))
            ? 'Nhóm test case'
            : labels.some((l) => /Original|Tight/.test(l))
              ? 'Chính sách re-scan'
              : series.some((s) => /AUUB/.test(s.name))
                ? 'Item'
                : series.some((s) => /AU\(P\)/.test(s.name))
                  ? 'Mẫu P'
                  : 'Hạng mục');

  yLabel =
    yLabel ||
    {
      utility: 'Tiện ích',
      ms: 'Thời gian',
      MiB: 'JVM heap',
      case: 'Số test case',
      mẫu: 'Số mẫu',
      lần: 'Số lần',
      'giao dịch': 'Số giao dịch',
      'lượt / nút': 'Số lượng',
    }[unit] ||
    'Giá trị';

  const axes = `
    <p class="chart-axes">
      <span><strong>X:</strong> ${esc(xLabel)}</span>
      <span>
        <strong>Y:</strong> ${esc(yLabel)}${unit ? ' (' + esc(unit) + ')' : ''}
      </span>
    </p>
  `;

  // Mỗi nhãn có ô riêng; không ép nhãn cuối sát nhãn trước.
  // Dùng khoảng rộng bảo thủ cho chữ 11px, giữ tên đầy đủ trong tooltip và bảng.
  const longestLabel = Math.max(...labels.map((label) => String(label).length));
  const labelChars = Math.min(longestLabel, expanded ? 32 : 20);
  const slotWidth = Math.max(70, labelChars * 8 + 24);
  const width = Math.max(expanded ? 900 : 600, labels.length * slotWidth + 83);
  const left = 65;
  const right = 18;
  const top = 22;
  const bottom = 42;
  const plotW = width - left - right;
  const plotH = height - top - bottom;

  const values = series.flatMap((s) => s.values).filter(Number.isFinite);
  const max = Math.max(...values, 0) * 1.15 || 1;
  const min = Math.min(...values, 0) * 1.15;
  const y = (v) => top + plotH - ((v - min) / (max - min)) * plotH;
  const groupW = plotW / labels.length;

  let svg = `
    <svg
      class="chart"
      style="min-width: ${width}px"
      viewBox="0 0 ${width} ${height}"
      role="img"
      aria-label="${esc(series.map((s) => s.name).join(', '))} theo ${esc(
        labels.join(', '),
      )}. Đơn vị ${esc(unit)}. Có bảng số liệu bên dưới."
    >
      <title>
        ${esc(series.map((s) => s.name).join(' / '))} (${esc(unit)})
      </title>
  `;

  for (let i = 0; i <= 4; i++) {
    const v = min + ((max - min) * i) / 4;

    svg += `
      <line
        class="axis"
        x1="${left}"
        x2="${width - right}"
        y1="${y(v)}"
        y2="${y(v)}"
      />
      <text
        x="${left - 9}"
        y="${y(v) + 4}"
        text-anchor="end"
      >${esc(compact(v))}</text>
    `;
  }

  labels.forEach((label, i) => {
    label = String(label);

    svg += `
      <text
        x="${left + groupW * (i + 0.5)}"
        y="${height - 13}"
        text-anchor="middle"
      >
        ${esc(label.length > labelChars ? label.slice(0, labelChars - 1) + '…' : label)}
        <title>${esc(label)}</title>
      </text>
    `;
  });

  series.forEach((s, j) => {
    const color = s.color || Object.values(colors)[j % 5];

    if (kind === 'line') {
      let points = s.values.map((v, i) => `${left + groupW * (i + 0.5)},${y(v)}`).join(' ');

      svg += `
        <polyline
          points="${points}"
          fill="none"
          stroke="${color}"
          stroke-width="2.5"
        />
      `;

      s.values.forEach((v, i) => {
        svg += `
          <circle
            cx="${left + groupW * (i + 0.5)}"
            cy="${y(v)}"
            r="4"
            fill="white"
            stroke="${color}"
            stroke-width="2"
          >
            <title>
              ${esc(labels[i])} · ${esc(s.name)}: ${num(v)} ${esc(unit)}
            </title>
          </circle>
        `;
      });
    } else {
      s.values.forEach((v, i) => {
        const barW = Math.min(46, (groupW * 0.65) / series.length);
        const x = left + groupW * (i + 0.5) + (j - series.length / 2) * barW;

        svg += `
          <rect
            x="${x + 2}"
            y="${Math.min(y(v), y(0))}"
            width="${Math.max(1, barW - 4)}"
            height="${Math.abs(y(0) - y(v))}"
            rx="3"
            fill="${color}"
          >
            <title>
              ${esc(labels[i])} · ${esc(s.name)}: ${num(v)} ${esc(unit)}
            </title>
          </rect>
        `;
      });
    }
  });

  svg += '</svg>';

  svg += `
    <div class="legend">
      ${series
        .map(
          (s, i) => `
            <span>
              <i style="background:${s.color || Object.values(colors)[i % 5]}"></i>
              ${esc(s.name)}${unit ? ' · ' + esc(unit) : ''}
            </span>
          `,
        )
        .join('')}
    </div>
  `;

  svg += `
    <details class="source-note chart-data" open>
      <summary>Bảng số liệu</summary>
      <div class="table-wrap">
        <table class="data-table">
          <caption>
            Bảng số liệu · ${esc(series.map((s) => s.name).join(' / '))}
          </caption>
          <thead>
            <tr>
              <th scope="col" class="column-text">${esc(xLabel)}</th>
              ${series
                .map(
                  (s) => `
                    <th scope="col" class="column-number">
                      ${esc(s.name)} (${esc(unit)})
                    </th>
                  `,
                )
                .join('')}
            </tr>
          </thead>
          <tbody>
            ${labels
              .map(
                (l, i) => `
                  <tr>
                    <td>${esc(l)}</td>
                    ${series
                      .map(
                        (s) => `
                          <td class="numeric column-number">
                            ${num(s.values[i], 6)}
                          </td>
                        `,
                      )
                      .join('')}
                  </tr>
                `,
              )
              .join('')}
          </tbody>
        </table>
      </div>
    </details>
  `;

  const names = series.map((s) => s.name).join(' / ');

  explanation =
    explanation ||
    (names.includes('MU(ID)')
      ? 'Eq. (12): MU(ID) − Sᵤ×TU(ID) ≥ (Sᵤ−Sₗ)×TU(OD) ⇒ re-scan.'
      : names.includes('AUUB')
        ? 'AUUB(P) < Sᵤ×TU(DB) ⇒ loại trừ HAUP; AUUB là cận trên.'
        : names.includes('AU(P)') || yLabel === 'AU(P)'
          ? 'AU(P) là tiện ích trung bình của mẫu P: lấy tổng tiện ích U(P) chia cho số item |P|. Mẫu là HAUP (LARGE) khi AU(P) đạt ngưỡng Sᵤ × TU(DB).'
          : unit === 'ms'
            ? 'Thời gian thực đo (ms). CPU và wall time là hai số đo riêng.'
            : unit === 'MiB'
              ? 'JVM heap quan sát; 1 MiB=1.048.576 byte. Không phải RSS.'
              : names.includes('TU(DB)')
                ? 'Ngưỡng LARGE=Sᵤ×TU(DB); ngưỡng PRE-LARGE=Sₗ×TU(DB).'
                : unit === 'mẫu'
                  ? 'LARGE=HAUP; PRE-LARGE là mẫu đang quản lý, chưa đạt LARGE.'
                  : unit === 'lần'
                    ? 'Số re-scan không gồm initial scan DB0.'
                    : unit === 'case'
                      ? 'ĐẠT: thực tế khớp kỳ vọng. CHƯA CHẠY chưa có kết quả.'
                      : unit === 'giao dịch'
                        ? 'Mới: số giao dịch trong batch. Tích lũy: tổng đến batch đó.'
                        : unit === 'utility'
                          ? 'TU(DB)=Σ TU(T); MU(DB)=Σ MU(T).'
                          : 'Bộ đếm công việc miner; không phải thời gian.');

  formula =
    formula ||
    (names.includes('MU(ID)')
      ? 'MU(ID) − Sᵤ × TU(ID) ≥ (Sᵤ − Sₗ) × TU(OD) ⇒ re-scan.'
      : names.includes('AUUB')
        ? 'AUUB(P) = Σ MU(T), với T chứa P; MU(T) = max u(i,T).'
        : names.includes('AU(P)')
          ? 'AU(P) = U(P) / |P|; HAUP khi AU(P) ≥ Sᵤ × TU(DB).'
          : names.includes('Sᵤ') || names.includes('Sₗ')
            ? 'Ngưỡng trên = Sᵤ × TU(DB); ngưỡng dưới = Sₗ × TU(DB).'
            : unit === 's'
              ? 'Runtime (s) = thời gian kết thúc − thời gian bắt đầu, tính bằng ns / 10⁹. Khi đo lặp: dùng median các lượt, không gồm warm-up.'
              : unit === 'MB'
                ? 'Heap (MB) = số byte heap đã dùng / 1.000.000; đỉnh quan sát = max các mẫu đã lấy.'
                : unit === 'MiB'
                  ? 'Heap (MiB) = số byte heap đã dùng / 1.048.576; đỉnh quan sát = max các mẫu đã lấy.'
                  : unit === 'ms'
                    ? 'Thời gian (ms) = thời gian đo bằng ns / 10⁶. Wall time đo khoảng thời gian; CPU time đo CPU đã dùng.'
                    : unit === 'mẫu'
                      ? 'LARGE: AU(P) ≥ Sᵤ × TU(DB). PRE-LARGE: Sₗ × TU(DB) ≤ AU(P) < Sᵤ × TU(DB). Số mẫu = số phần tử từng tập.'
                      : unit === 'lần'
                        ? 'Số re-scan = tổng các batch chèn có quyết định re-scan; không tính DB0.'
                        : unit === 'giao dịch'
                          ? 'N tích lũy tại batch k = Σ N(batch j), với j từ 0 đến k.'
                          : names.includes('TU(batch)')
                            ? 'TU(batch) = Σ TU(T); TU(T) = Σ u(i,T). MU(batch) = Σ max u(i,T).'
                            : 'Số liệu ghi nhận trực tiếp từ bộ đếm hoặc phép đo tương ứng.');
  const i = labels.length - 1;

  const evidence = `
    <div class="chart-explanation">
      <strong>Công thức áp dụng</strong>
      <p class="chart-formula">${esc(formula)}</p>
      <p>${esc(explanation)}</p>
      ${
        evidenceRows.length
          ? `
            <details class="chart-workings" open><summary>Thay số & kết quả</summary><ol class="evidence-steps">
              ${evidenceRows
                .map(
                  (row) => `
                    <li>
                      <strong>${esc(row.label)}</strong>
                      <span>${esc(row.text)}</span>
                    </li>
                  `,
                )
                .join('')}
            </ol></details>
          `
          : `
            <p class="chart-latest">
              <strong>Mốc ${esc(labels[i])}:</strong>
              ${series
                .map((s) => `${esc(s.name)} = ${num(s.values[i], 6)} ${esc(unit)}`)
                .join(' · ')}
            </p>
          `
      }
    </div>
  `;

  const payload = esc(
    JSON.stringify({
      labels,
      series,
      options: {
        kind,
        unit,
        height: 360,
        emptyMessage,
        xLabel,
        yLabel,
        explanation,
        evidenceRows,
        formula,
      },
    }),
  );

  return `
    <div class="chart-widget">
      ${
        expanded
          ? ''
          : `
            <div class="chart-actions">
              <button
                type="button"
                class="button chart-expand"
                data-chart="${payload}"
                aria-label="Phóng to biểu đồ ${esc(series.map((s) => s.name).join(' / '))}"
              >⤢ Phóng to</button>
            </div>
          `
      }
      ${axes}
      <div class="chart-viewport" tabindex="0" role="region" aria-label="Biểu đồ; cuộn ngang để xem các mốc">
        ${svg.slice(0, svg.indexOf('</svg>') + 6)}
      </div>
      ${svg.slice(svg.indexOf('</svg>') + 6)}
      ${evidence}
      ${
        !expanded && width > 600
          ? '<p class="source-note">Cuộn ngang hoặc phóng to để xem toàn bộ biểu đồ.</p>'
          : ''
      }
    </div>
  `;
}

function compact(value) {
  if (Math.abs(value) >= 1e9) return num(value / 1e9, 1) + 'B';
  if (Math.abs(value) >= 1e6) return num(value / 1e6, 1) + 'M';
  if (Math.abs(value) >= 1e3) return num(value / 1e3, 1) + 'k';

  return num(value, 1);
}

// One delegated handler supports charts added by every page and subsequent runs.
document.addEventListener('click', (event) => {
  const button = event.target.closest('.chart-expand');
  if (!button) return;

  const data = JSON.parse(button.dataset.chart);
  let dialog = document.getElementById('chart-dialog');

  if (!dialog) {
    dialog = document.createElement('dialog');
    dialog.id = 'chart-dialog';
    dialog.className = 'chart-dialog';

    document.body.append(dialog);

    dialog.addEventListener('click', (e) => {
      if (e.target === dialog) dialog.close();
    });

    dialog.addEventListener('close', () => {
      document.body.classList.remove('chart-modal-open');
    });
  }

  const title = button.closest('.card')?.querySelector('h2')?.textContent || 'Biểu đồ chi tiết';

  dialog.innerHTML = `
    <div class="chart-dialog-head">
      <div>
        <h2 id="chart-dialog-title">${esc(title)}</h2>
        <p>
          Đơn vị: ${esc(data.options.unit)} · Cuộn ngang nếu có nhiều mốc.
          Xem bảng để đọc số chính xác.
        </p>
      </div>
      <button
        class="button chart-close"
        type="button"
        aria-label="Đóng biểu đồ"
      >Đóng ✕</button>
    </div>
    <div class="chart-dialog-content">
      ${chart(data.labels, data.series, {
        ...data.options,
        expanded: true,
      })}
    </div>
  `;

  dialog.setAttribute('aria-labelledby', 'chart-dialog-title');

  dialog.querySelector('.chart-close').onclick = () => dialog.close();

  document.body.classList.add('chart-modal-open');
  dialog.showModal();
});
