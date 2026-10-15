export const colors = {
    blue: '#2563eb',
    green: '#168364',
    amber: '#c78b25',
    slate: '#7990ac',
    cyan: '#0891b2',
};

export const esc = value =>
    String(value ?? '').replace(
        /[&<>"']/g,
        c =>
        ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#39;',
        })[c],
    );

export const num = (value, digits = 2) =>
    value == null ?
    'Không có số đo' :
    new Intl.NumberFormat('vi-VN', {
        maximumFractionDigits: digits,
    }).format(value);

/** SVG charts use the real report, zero baseline, native tooltips, and a data alternative. */
export function chart(
    labels,
    series, {
        kind = 'bar',
        unit = '',
        height = 220,
        emptyMessage = 'Chưa có dữ liệu để vẽ biểu đồ.',
        expanded = false,
        xLabel = '',
        yLabel = '',
        explanation = '',
        evidenceRows = [],
    } = {},
) {
    if (!labels.length || !series.length) {
        return `<div class="bar-empty">${esc(emptyMessage)}</div>`;
    }

    xLabel =
        xLabel ||
        (labels.every(l => /^DB|^DB0/.test(l)) ?
            'Batch dữ liệu' :
            labels.every(l => /^TC-/.test(l)) ?
            'Test case' :
            labels.every(l => /^Lượt /.test(l)) ?
            'Lượt chạy' :
            labels.every(l => / · [0-9]+$/.test(l)) ?
            'Nhóm test case' :
            labels.some(l => /Original|Tight/.test(l)) ?
            'Chính sách re-scan' :
            series.some(s => /AUUB/.test(s.name)) ?
            'Item' :
            series.some(s => /AU\(P\)/.test(s.name)) ?
            'Mẫu P' :
            'Hạng mục');

    yLabel =
        yLabel || {
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

    const width = expanded ? Math.max(900, labels.length * 85) : 600;
    const left = 65;
    const right = 18;
    const top = 22;
    const bottom = 42;
    const plotW = width - left - right;
    const plotH = height - top - bottom;

    const values = series.flatMap(s => s.values).filter(Number.isFinite);
    const max = Math.max(...values, 0) * 1.15 || 1;
    const min = Math.min(...values, 0) * 1.15;
    const y = v => top + plotH - ((v - min) / (max - min)) * plotH;
    const groupW = plotW / labels.length;

    let svg = `
    <svg
      class="chart"
      viewBox="0 0 ${width} ${height}"
      role="img"
      aria-label="${esc(series.map(s => s.name).join(', '))} theo ${esc(
        labels.join(', '),
      )}. Đơn vị ${esc(unit)}. Có bảng số liệu bên dưới."
    >
      <title>
        ${esc(series.map(s => s.name).join(' / '))} (${esc(unit)})
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

    const labelStep = expanded ?
        1 :
        Math.max(1, Math.ceil(labels.length / 7));

    labels.forEach((label, i) => {
        if (i % labelStep !== 0 && i !== labels.length - 1) return;

        svg += `
      <text
        x="${left + groupW * (i + 0.5)}"
        y="${height - 13}"
        text-anchor="middle"
      >
        ${esc(label.length > 17 ? label.slice(0, 15) + '…' : label)}
        <title>${esc(label)}</title>
      </text>
    `;
    });

    series.forEach((s, j) => {
        const color = s.color || Object.values(colors)[j % 5];

        if (kind === 'line') {
            let points = s.values
                .map((v, i) => `${left + groupW * (i + 0.5)},${y(v)}`)
                .join(' ');

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
                const x =
                    left +
                    groupW * (i + 0.5) +
                    (j - series.length / 2) * barW;

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
    <details class="source-note">
      <summary>Xem số liệu biểu đồ</summary>
      <div class="table-wrap">
        <table class="data-table">
          <caption>
            Bảng số liệu · ${esc(series.map(s => s.name).join(' / '))}
          </caption>
          <thead>
            <tr>
              <th scope="col" class="column-text">${esc(xLabel)}</th>
              ${series
                .map(
                  s => `
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
                        s => `
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

  const names = series.map(s => s.name).join(' / ');

  explanation =
    explanation ||
    (names.includes('MU(ID)')
      ? 'Eq. (12): MU(ID) − Sᵤ×TU(ID) ≥ (Sᵤ−Sₗ)×TU(OD) ⇒ re-scan.'
      : names.includes('AUUB')
        ? 'AUUB(P) < Sᵤ×TU(DB) ⇒ loại trừ HAUP; AUUB là cận trên.'
        : (names.includes('AU(P)') || yLabel === 'AU(P)')
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

  const i = labels.length - 1;

  const evidence = `
    <div class="chart-explanation">
      <strong>Căn cứ</strong>
      <p>${esc(explanation)}</p>
      ${
        evidenceRows.length
          ? `
            <details class="chart-workings" ${expanded ? 'open' : ''}><summary>Phép tính & số liệu</summary><ol class="evidence-steps">
              ${evidenceRows
                .map(
                  row => `
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
                .map(
                  s =>
                    `${esc(s.name)} = ${num(s.values[i], 6)} ${esc(unit)}`,
                )
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
                aria-label="Phóng to biểu đồ ${esc(
                  series.map(s => s.name).join(' / '),
                )}"
              >⤢ Phóng to</button>
            </div>
          `
      }
      ${axes}
      ${svg}
      ${evidence}
      ${
        !expanded && labelStep > 1
          ? '<p class="source-note">Đã giãn nhãn để dễ đọc · Phóng to để xem đầy đủ các mốc.</p>'
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
document.addEventListener('click', event => {
  const button = event.target.closest('.chart-expand');
  if (!button) return;

  const data = JSON.parse(button.dataset.chart);
  let dialog = document.getElementById('chart-dialog');

  if (!dialog) {
    dialog = document.createElement('dialog');
    dialog.id = 'chart-dialog';
    dialog.className = 'chart-dialog';

    document.body.append(dialog);

    dialog.addEventListener('click', e => {
      if (e.target === dialog) dialog.close();
    });

    dialog.addEventListener('close', () => {
      document.body.classList.remove('chart-modal-open');
    });
  }

  const title =
    button.closest('.card')?.querySelector('h2')?.textContent ||
    'Biểu đồ chi tiết';

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

  const svg = dialog.querySelector('svg');
  svg.style.minWidth = Math.max(850, data.labels.length * 85) + 'px';

  dialog.querySelector('.chart-close').onclick = () => dialog.close();

  document.body.classList.add('chart-modal-open');
  dialog.showModal();
});