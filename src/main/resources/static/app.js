import { chart, colors, esc, num } from './charts.js';
import { request } from './api.js';
import { icon, badge, card, kpi, notice, last, action, table, tr } from './ui-components.js';
import { auEvidence, finalConclusion, verificationBadges, manifestPanel } from './report-ui.js';
import { notifyStatus } from './notifications.js';

const $ = (id) => document.getElementById(id);

function setSidebarHidden(hidden) {
  document.body.classList.toggle('sidebar-hidden', hidden);
  $('sidebar-hide').setAttribute('aria-expanded', String(!hidden));
  $('sidebar-hide').setAttribute(
    'aria-label',
    hidden ? 'Hiện thanh điều hướng' : 'Ẩn thanh điều hướng',
  );
  $('sidebar-hide').textContent = hidden ? '›' : '‹';
  try {
    localStorage.setItem('pihaupa.sidebarHidden', String(hidden));
  } catch {}
}
let savedSidebarHidden = false;
try {
  savedSidebarHidden = localStorage.getItem('pihaupa.sidebarHidden') === 'true';
} catch {}
setSidebarHidden(savedSidebarHidden);
$('sidebar-hide').onclick = () =>
  setSidebarHidden(!document.body.classList.contains('sidebar-hidden'));

document.querySelectorAll('[data-icon]').forEach((el) => (el.innerHTML = icon(el.dataset.icon)));
document.querySelectorAll('#sidebar nav a').forEach((link) => {
  const label = link.textContent.trim();
  link.title = label;
  link.setAttribute('aria-label', label);
  for (const node of [...link.childNodes]) {
    if (node.nodeType === Node.TEXT_NODE && node.textContent.trim()) {
      const span = document.createElement('span');
      span.className = 'nav-text';
      span.textContent = node.textContent;
      node.replaceWith(span);
    }
  }
});
const pages = {
  overview: [
    'Phân tích PIHAUPA',
    'Một báo cáo liền mạch: tổng quan → từng batch và re-scan → kết quả khai phá.',
  ],
  batches: ['Phân tích từng bước', 'Theo dõi utility, ngưỡng phân loại và tập mẫu qua mỗi batch.'],
  rescan: [
    'Quyết định re-scan',
    'Giải thích điều kiện Tight bằng số liệu tích lũy từ lần full scan gần nhất.',
  ],
  patterns: ['Kết quả khai phá', 'Tra cứu các mẫu đang quản lý và tiện ích trung bình thực tế.'],
  measurements: [
    'Đo lường & biểu đồ',
    'Tách chi phí đọc dữ liệu, thuật toán, CPU luồng và JVM heap.',
  ],
  datasets: [
    'Bộ dữ liệu thực nghiệm',
    'Đọc trực tiếp transactions.txt; mỗi bộ dữ liệu được phân tích độc lập.',
  ],
  testcases: [
    'Test case & kiểm thử',
    'Chạy test thật, xem đầu vào, kết quả kỳ vọng và lỗi của từng case.',
  ],
  verification: [
    'Kiểm chứng bài báo gốc',
    'Đối chiếu dữ liệu Tables 2–3, tập HAUP và hành vi re-scan.',
  ],
  runs: [
    'Hồ sơ thực nghiệm',
    'Các lượt hoàn tất đã lưu trên máy; mở lại cấu hình, kết quả và số đo.',
  ],
  glossary: [
    'Công thức & thuật ngữ',
    'Các định nghĩa theo Kim et al., Journal of Big Data (2026), 13:13.',
  ],
};
const state = {
  page: 'overview',
  current: null,
  history: [],
  datasets: [],
  busy: false,
  verification: null,
  testResults: null,
  testsBusy: false,
  patternPage: 0,
  patternSearch: '',
  patternType: 'ALL',
  batchIndex: 0,
};
let currentJob = null;
let runPhase = 'IDLE',
  lastJobPhase = '',
  toastTimer;
const runMilestones = new Set();
const runLabels = {
  IDLE: 'Chạy phân tích',
  SUBMITTING: 'Đang gửi dữ liệu…',
  READING: 'Đang đọc dữ liệu…',
  MINING: 'Đang khai phá…',
  COMPARING: 'Đang đối chiếu…',
  REPORTING: 'Đang dựng báo cáo…',
  RECONNECTING: 'Đang kết nối lại…',
  COMPLETED: '✓ Hoàn tất · Chạy lại',
  FAILED: 'Thử lại phân tích',
  CANCELLING: 'Đang hủy…',
  CANCELLED: 'Đã hủy · Chạy lại',
};

function setRunPhase(phase) {
  runPhase = phase;
  $('run').dataset.phase = phase;
  $('run').setAttribute('aria-busy', String(state.busy));
  $('run').innerHTML =
    (state.busy ? '<span class="run-spinner" aria-hidden="true"></span>' : icon('play')) +
    (runLabels[phase] || runLabels.IDLE);
  $('workspace-state').textContent = state.busy
    ? runLabels[phase] || 'Đang xử lý…'
    : phase === 'FAILED'
      ? 'Có lỗi xử lý'
      : phase === 'COMPLETED'
        ? 'Phân tích hoàn tất'
        : 'Local workspace';
}
const phases = {
  READING: 'Đang đọc dữ liệu',
  MINING: 'Đọc hoàn tất · đang khai phá',
  COMPARING: 'Khai phá hoàn tất · đang đối chiếu',
  REPORTING: 'Đang dựng báo cáo',
  COMPLETED: 'Xử lý hoàn tất',
  FAILED: 'Xử lý thất bại',
  CANCELLING: 'Đang hủy an toàn',
  CANCELLED: 'Đã hủy',
};

function showJobStatus(status) {
  const phase = status.phase === 'COMPLETED' ? 'REPORTING' : status.phase;
  setRunPhase(phase);
  if (status.phase !== lastJobPhase) {
    lastJobPhase = status.phase;
    if (!['FAILED', 'COMPLETED'].includes(status.phase))
      notifyStatus(phases[status.phase] || status.phase, status.source || 'Lõi Java đang xử lý.');
  }
  if (status.importMs != null && !runMilestones.has('read')) {
    runMilestones.add('read');
    notifyStatus('Đọc dữ liệu hoàn tất', num(status.importMs) + ' ms.', 'success');
  }
  if (status.algorithmMs != null && status.phase !== 'MINING' && !runMilestones.has('mined')) {
    runMilestones.add('mined');
    notifyStatus(
      'Khai phá hoàn tất',
      num(status.algorithmMs) + ' ms · đang nhận kết quả.',
      'success',
    );
  }
  $('cancel-run').disabled = !status.canCancel;
  $('cancel-run').hidden = status.state !== 'RUNNING';
  $('live-phase').textContent = phases[status.phase] || status.state;
  $('live-total').textContent = 'Tổng: ' + num(status.elapsedMs / 1000) + ' s';
  $('live-read').textContent =
    'Đọc: ' + (status.importMs == null ? 'đang xử lý…' : num(status.importMs) + ' ms');
  $('live-algorithm').textContent =
    'Thuật toán: ' + (status.algorithmMs == null ? '—' : num(status.algorithmMs) + ' ms');
  $('live-heap').textContent = 'Heap: ' + num(status.heapUsedMb, 1) + ' MiB';
  $('live-heap-detail').textContent =
    'Đỉnh heap lấy mẫu: ' + num(status.heapPeakObservedMb, 1) + ' MiB · không phải RSS';
  $('live-metrics').dataset.phase = status.phase;
  $('phase-trail').hidden = false;
  $('phase-trail').innerHTML = [
    ['Đọc dữ liệu', status.importMs != null],
    ['Khai phá', status.algorithmMs != null && status.phase !== 'MINING'],
    ['Báo cáo', status.state === 'COMPLETED'],
  ]
    .map(([label, done]) => badge((done ? '✓ ' : '') + label, done ? 'green' : 'neutral'))
    .join('');
}

function setError(error) {
  if (!error) {
    $('error').hidden = true;
    $('error').replaceChildren();
    return;
  }
  const message = typeof error === 'string' ? error : error.message || error.error;
  const code = error.code || 'VALIDATION',
    action = error.action || 'Kiểm tra thông tin nhập và chạy lại.';
  $('error').innerHTML =
    `<strong>Lỗi ${esc(code)}</strong><p>${esc(message)}</p><p class="error-action">Cách xử lý: ${esc(action)}</p>`;
  if (!state.busy) setRunPhase('FAILED');
  $('error').hidden = false;
  notifyStatus('Lỗi ' + code, message + ' · ' + action, 'error');
}

function setBusy(busy) {
  state.busy = busy;
  $('repeats').disabled = busy;
  $('threshold-mode').disabled = busy;
  $('size-percent').disabled = busy;
  $('run-size-suite').disabled = busy || sizeSuiteRunning;
  $('cancel-run').hidden = !busy;
  $('cancel-run').disabled = !currentJob;
  updatePending();
  $('run').disabled = busy;
  $('source').disabled = busy;
  $('su').disabled = busy;
  $('sl').disabled = busy;
  setRunPhase(runPhase);
  $('compare').disabled = busy;
  $('file').disabled = busy;
  $('format').disabled = busy;
}

function updatePending() {
  const selected = $('source').value,
    name =
      selected === 'paper'
        ? 'Bài báo · Tables 2–3'
        : state.datasets.find((d) => d.id === selected)?.name || $('file').files[0]?.name;
  const config = state.current?.configuration;
  const changed =
    state.current &&
    (config
      ? selected !== config.source ||
        Number($('su').value) !== config.su ||
        Number($('sl').value) !== config.sl ||
        Number($('size-percent').value || 100) !== (config.sizePercent || 100) ||
        (selected === 'upload' &&
          (($('file').files[0]?.lastModified ?? 0) !== config.fileModified ||
            $('format').value !== config.format))
      : state.current.source !== name ||
        Number($('su').value) !== state.current.report.upperThreshold ||
        Number($('sl').value) !== state.current.report.lowerThreshold);
  $('pending-run').hidden = !state.current || (!changed && !state.busy);
  if (state.busy && state.current)
    $('pending-run').textContent = 'Đang chạy lượt mới · báo cáo bên dưới là lượt trước.';
  else if (changed)
    $('pending-run').textContent = 'Cấu hình đã đổi · bấm Chạy để cập nhật báo cáo.';
}

function thresholdLabels() {
  $('su-percent').textContent = num(Number($('su').value) * 100, 5) + '%';
  $('sl-percent').textContent = num(Number($('sl').value) * 100, 5) + '%';
  updatePending();
}

// Các mức ngưỡng là cấu hình khảo sát, không giới hạn thời gian hay số mẫu trả về.
const thresholdLevels = {
  overview: [0.7, 0.5],
  extended: [0.5, 0.3],
  deep: [0.25, 0.1],
};
function applyThresholdMode() {
  const mode = $('threshold-mode').value;
  const dataset = state.datasets.find((d) => d.id === $('source').value);
  const profile = dataset?.profile;
  const paper = $('source').value === 'paper';
  if (mode !== 'custom') {
    if (mode === 'reference') {
      $('su').value = paper ? 0.23 : profile.su;
      $('sl').value = paper ? 0.1 : profile.sl;
    } else {
      // Mẫu bài báo: max AU(singleton) = 84, TU(DB) = 240.
      const ratio = paper ? 84 / 240 : profile.singletonAU / profile.totalTU;
      const [upper, lower] = thresholdLevels[mode];
      $('su').value = upper * ratio;
      $('sl').value = lower * ratio;
    }
  }
  $('threshold-note').textContent =
    mode === 'custom'
      ? 'Nhập tỷ lệ: 0,01 = 1%. Giữ nguyên ngưỡng bạn chọn; không tự tăng để chạy nhanh.'
      : mode === 'reference'
        ? paper
          ? 'Ngưỡng ví dụ bài báo: 23% / 10%.'
          : 'Ngưỡng khởi đầu cũ, sát mẫu mạnh nhất; có thể ít HAUP.'
        : 'Khuyến nghị bắt đầu ở 02; thử 03 → 04 để mở rộng khảo sát. r = max AU(item đơn) / TU(DB). Đây là mức khảo sát của ứng dụng, không phải benchmark bài báo. Hạ ngưỡng có thể tăng số mẫu, thời gian và bộ nhớ.';
  thresholdLabels();
}
function markCustomThreshold() {
  $('threshold-mode').value = 'custom';
  applyThresholdMode();
}
$('threshold-mode').addEventListener('change', applyThresholdMode);

function sourceChanged() {
  const value = $('source').value,
    dataset = state.datasets.find((d) => d.id === value),
    profile = dataset?.profile;
  $('upload-fields').hidden = value !== 'upload';
  if (value === 'paper') {
    $('su').value = 0.23;
    $('sl').value = 0.1;
  } else if (profile) {
    $('su').value = profile.su;
    $('sl').value = profile.sl;
  }
  $('source-kind').textContent =
    value === 'paper'
      ? 'Dữ liệu mẫu bài báo'
      : value === 'upload'
        ? 'Dữ liệu TXT tùy chọn'
        : 'Thực nghiệm dữ liệu lớn';
  $('run-subtitle').textContent =
    value === 'paper'
      ? 'Phân tích dữ liệu mẫu từ bài báo gốc.'
      : 'Chọn ngưỡng thực nghiệm; kết quả và số đo lấy trực tiếp từ Java.';
  $('source-hint').textContent =
    value === 'paper'
      ? '9 giao dịch · 6 item · 3 batch đúng theo bài báo'
      : value === 'upload'
        ? 'Giữ nguyên thứ tự giao dịch; kiểm tra số item, utility và TU'
        : 'Đọc tệp trên máy chủ Java · chia DB0/DB1/DB2: 80% / 10% / 10%';
  $('preset-hint').textContent = profile
    ? profile.description + ' Item đối chứng: ' + profile.strongestItem + '. Có thể điều chỉnh.'
    : value === 'paper'
      ? 'Ngưỡng ví dụ bài báo · nhập tỷ lệ: 0,23 = 23%.'
      : 'Ngưỡng tùy chỉnh: nhập tỷ lệ, ví dụ 0,01 = 1%. Không dùng ngưỡng 23% cho mọi dữ liệu.';
  const known = value === 'paper' || !!profile;
  for (const option of $('threshold-mode').options) {
    option.disabled = !known && option.value !== 'custom';
  }
  $('threshold-mode').value = value === 'paper' ? 'reference' : profile ? 'overview' : 'custom';
  applyThresholdMode();
  if (!state.busy) setRunPhase('IDLE');
  thresholdLabels();
  if (!state.current && state.page === 'overview') renderEmpty();
}
$('su').addEventListener('input', markCustomThreshold);
$('sl').addEventListener('input', markCustomThreshold);
$('source').addEventListener('change', sourceChanged);
$('file').addEventListener('change', updatePending);
$('format').addEventListener('change', updatePending);
async function pollJob(id) {
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      return await request('/api/jobs/' + id, { timeoutMs: 10000 });
    } catch (error) {
      if (!['NETWORK_UNAVAILABLE', 'REQUEST_TIMEOUT'].includes(error.code) || attempt === 2)
        throw error;
      $('live-phase').textContent = 'Đang kết nối lại…';
      setRunPhase('RECONNECTING');
      if (attempt === 0)
        notifyStatus('Đang kết nối lại', 'Chờ phản hồi từ server Java.', 'warning');
      await new Promise((resolve) => setTimeout(resolve, 500));
    }
  }
}
let sizeSuiteRunning = false;
async function runAnalysis(event) {
  event.preventDefault();
  if (state.busy) return;
  const su = Number($('su').value),
    sl = Number($('sl').value);
  if (!(sl > 0 && sl < su && su <= 1)) {
    setError({
      code: 'INVALID_THRESHOLDS',
      message: 'Ngưỡng cần thỏa mãn 0 < Sₗ < Sᵤ ≤ 1.',
      action: 'Nhập tỷ lệ; ví dụ Su=0.03 và Sl=0.025.',
    });
    return;
  }
  const selected = $('source').value,
    file = $('file').files[0];
  if (selected === 'upload' && !file) {
    setError('Hãy chọn tệp TXT cần phân tích.');
    return;
  }
  const query = new URLSearchParams({
    su,
    sl,
    compare: $('compare').checked,
    repeats: $('repeats').value,
    sizePercent: $('size-percent').value || '100',
    kind: selected === 'paper' ? 'paper' : selected === 'upload' ? 'upload' : 'catalog',
  });
  if (selected === 'upload') {
    query.set('name', file.name);
    query.set('format', $('format').value);
  } else if (selected !== 'paper') query.set('dataset', selected);
  runMilestones.clear();
  lastJobPhase = '';
  runPhase = 'SUBMITTING';
  setError('');
  setBusy(true);
  notifyStatus(
    'Bắt đầu phân tích',
    selected === 'upload' ? file.name : $('source').selectedOptions[0].textContent,
  );
  $('status').hidden = false;
  $('status').textContent = 'Đang nạp dữ liệu và chuẩn bị phân tích…';
  $('progress-panel').hidden = false;
  $('progress-log').textContent = '';
  $('export-zip').disabled = true;
  $('export').disabled = true;
  $('export-txt').disabled = true;
  // UI DESIGN PATTERN — SKELETON SCREEN: giữ chỗ 4 card khi chờ kết quả đầu tiên.
  // Khi đã có báo cáo thì giữ báo cáo cũ; placeholder không phải số liệu hoặc % tiến độ.
  if (
    !state.current &&
    ['overview', 'batches', 'rescan', 'patterns', 'measurements'].includes(state.page)
  )
    $('content').innerHTML =
      '<div class="kpi-grid">' +
      Array(4).fill('<div class="loading-block"></div>').join('') +
      '</div>';
  try {
    const job = await request('/api/jobs?' + query, {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain; charset=utf-8' },
      timeoutMs: selected === 'upload' ? 180000 : 15000,
      body: selected === 'upload' ? file : undefined,
    });
    currentJob = job.id;
    let status = job;
    showJobStatus(status);
    while (status.state === 'RUNNING') {
      showJobStatus(status);
      $('status').textContent =
        status.lines.at(-1) || 'Java đang xử lý; số liệu sẽ xuất hiện khi hoàn tất.';
      $('progress-log').textContent = status.lines.join('\n');
      await new Promise((resolve) => setTimeout(resolve, 650));
      status = await pollJob(job.id);
    }
    showJobStatus(status);
    $('progress-log').textContent = status.lines.join('\n');
    if (status.state === 'CANCELLED') {
      runPhase = 'CANCELLED';
      notifyStatus('Đã hủy phân tích', 'Không sử dụng kết quả dở dang.');
      render();
      return;
    }
    if (status.state === 'FAILED')
      throw Object.assign(new Error(status.error), { code: status.code, action: status.action });
    const result = await request('/api/jobs/' + job.id + '/result');
    result.configuration = {
      source: selected,
      su,
      sl,
      format: $('format').value,
      fileModified: file?.lastModified ?? 0,
      repeats: Number($('repeats').value),
      sizePercent: Number($('size-percent').value || 100),
    };
    runPhase = 'COMPLETED';
    notifyStatus(
      'Phân tích hoàn tất',
      result.source +
        ' · ' +
        num(result.report.transactionCount, 0) +
        ' giao dịch · ' +
        last(result.report).largePatterns.length +
        ' HAUP.',
      'success',
    );
    state.current = result;
    state.patternSearch = '';
    state.patternType = 'ALL';
    state.patternPage = 0;
    state.batchIndex = result.report.batches.length - 1;
    state.history.unshift(result);
    state.history = state.history.slice(0, 8);
    $('status').textContent =
      'Đã hoàn tất · ' +
      result.source +
      ' · ' +
      num(result.report.transactionCount, 0) +
      ' giao dịch. Số liệu đã được cập nhật.';
    if (['datasets', 'glossary', 'verification', 'testcases', 'runs'].includes(state.page))
      location.hash = 'overview';
    else render();
    document
      .getElementById('analysis-final')
      ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  } catch (error) {
    runPhase = 'FAILED';
    setError(error);
    $('live-phase').textContent = 'Chưa hoàn tất · có lỗi';
    $('live-metrics').dataset.phase = 'FAILED';
    $('status').textContent = 'Lượt mới chưa hoàn tất: ' + error.message;
    render();
  } finally {
    currentJob = null;
    setBusy(false);
    $('export-zip').disabled = !state.current;
    $('export').disabled = !state.current || !!state.current.archived;
    $('export-txt').disabled = !state.current || !!state.current.archived;
  }
}
$('run-form').addEventListener('submit', runAnalysis);
$('run-size-suite').onclick = async () => {
  if (state.busy || sizeSuiteRunning) return;
  sizeSuiteRunning = true;
  try {
    for (const percent of [25, 50, 75, 100]) {
      $('size-percent').value = String(percent);
      notifyStatus('Thực nghiệm kích thước', percent + '% · giữ nguyên ngưỡng');
      const previous = state.current?.id;
      await runAnalysis({ preventDefault() {} });
      if (runPhase !== 'COMPLETED' || state.current?.id === previous) break;
    }
    if (runPhase === 'COMPLETED') location.hash = 'measurements';
  } finally {
    sizeSuiteRunning = false;
    setBusy(false);
  }
};
$('size-percent').addEventListener('change', updatePending);
$('export-zip').addEventListener('click', () => download('zip'));
$('export').addEventListener('click', () => download('csv'));
$('export-txt').addEventListener('click', () => download('txt'));
$('cancel-run').onclick = async () => {
  if (!currentJob) return;
  $('cancel-run').disabled = true;
  try {
    await request(`/api/jobs/${currentJob}/cancel`, { method: 'POST' });
    setRunPhase('CANCELLING');
  } catch (error) {
    setError(error);
  }
};
async function download(format) {
  try {
    const c = state.current,
      name =
        'pihaupa-' +
        c.source.replace(/[^a-zA-Z0-9_-]/g, '-') +
        '-' +
        c.createdAt.replace(/[:.]/g, '-') +
        '.' +
        format;
    await saveDownload(
      c.archived ? `/api/runs/${c.id}/export` : `/api/jobs/${c.id}/export?format=${format}`,
      name,
    );
  } catch (error) {
    setError(error);
  }
}
async function saveDownload(path, name) {
  notifyStatus('Đang chuẩn bị tải', name);
  const blob = await request(path, { responseType: 'blob' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  notifyStatus(
    'Đã chuẩn bị tệp tải xuống',
    name + ' · ' + num(blob.size / 1024, 1) + ' KiB.',
    'success',
  );
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function navigate() {
  const page = location.hash.slice(1),
    sections = {
      batches: 'analysis-batches',
      rescan: 'analysis-batches',
      patterns: 'analysis-patterns',
    };
  if (page === 'main') return;
  if (page.startsWith('analysis-')) {
    if (!document.getElementById(page)) {
      state.page = 'overview';
      render();
    }
    if (document.getElementById(page)?.tagName === 'DETAILS')
      document.getElementById(page).open = true;
    document.getElementById(page)?.scrollIntoView({ block: 'start' });
    return;
  }
  state.page = sections[page] ? 'overview' : pages[page] ? page : 'overview';
  render();
  if (sections[page]) document.getElementById(sections[page])?.scrollIntoView({ block: 'start' });
}
window.addEventListener('hashchange', navigate);

function render() {
  const analysis = state.page === 'overview';
  $('run-panel').hidden = !analysis;
  $('status').hidden = !analysis || !$('status').textContent;
  $('progress-panel').hidden = !analysis || !$('progress-log').textContent;
  const [title, description] = pages[state.page];
  $('page-title').textContent = title;
  $('page-description').textContent = description;
  $('breadcrumb').textContent = title;
  document.querySelectorAll('[data-page]').forEach((a) => {
    const active = a.dataset.page === state.page;
    a.classList.toggle('active', active);
    if (active) a.setAttribute('aria-current', 'page');
    else a.removeAttribute('aria-current');
  });
  if (state.page === 'datasets') renderDatasets();
  else if (state.page === 'glossary') renderGlossary();
  else if (state.page === 'verification') renderVerification();
  else if (state.page === 'testcases') renderTestCases();
  else if (state.page === 'runs') renderRuns();
  else if (!state.current) renderEmpty();
  else {
    const views = { overview: renderAnalysis, measurements: renderMeasurements };
    views[state.page]();
  }
}

function renderEmpty() {
  const selected = $('source').value,
    ready =
      selected === 'paper'
        ? 'Dữ liệu mẫu của bài báo đã sẵn sàng.'
        : selected === 'upload'
          ? 'Chọn tệp TXT và kiểm tra ngưỡng trước khi chạy.'
          : 'Bộ dữ liệu lớn đã được chọn, ngưỡng khởi đầu đã sẵn sàng.';
  $('content').innerHTML =
    `<section class="card empty"><span class="empty-icon">${icon('measurements')}</span><h2>Sẵn sàng cho lần phân tích đầu tiên</h2><p>${ready} Bấm <strong>Chạy phân tích</strong> để xem kết quả thực, công thức theo từng batch và các biểu đồ đo lường.</p><a class="button" href="#verification">${icon('verification')}Xem kiểm chứng bài báo</a><div class="empty-flow"><span>Chọn dữ liệu</span><span>→</span><span>Chạy lõi Java</span><span>→</span><span>Khám phá báo cáo</span></div></section>`;
}

function reportNote() {
  const c = state.current;
  return `<div class="result-note"><span>${badge('Đã hoàn tất', 'green')} <strong>${esc(c.source)}</strong> · ${esc(new Date(c.createdAt).toLocaleString('vi-VN'))}</span><span>Sᵤ = ${num(c.report.upperThreshold, 17)} · Sₗ = ${num(c.report.lowerThreshold, 17)} · ${num(c.report.batchCount, 0)} batch</span></div>`;
}

function renderOverview(target) {
  const c = state.current,
    r = c.report,
    b = last(r),
    labels = r.batches.map((b) => b.name);
  const counts = [
    {
      name: 'LARGE / HAUP',
      values: r.batches.map((b) => b.largePatterns.length),
      color: colors.blue,
    },
    {
      name: 'PRE-LARGE đang quản lý',
      values: r.batches.map((b) => b.preLargePatterns.length),
      color: colors.amber,
    },
  ];
  const top = [...b.largePatterns].sort((a, b) => b.averageUtility - a.averageUtility).slice(0, 6);
  const noHaup =
    b.largePatterns.length === 0
      ? notice(
          r.upperBounds[0]?.auub < b.minUtilUpper
            ? `<strong>Không có HAUP ở ngưỡng hiện tại.</strong> max AUUB(item) = ${num(r.upperBounds[0].auub)} &lt; Sᵤ × TU(DB) = ${num(b.minUtilUpper)}: cận trên loại trừ mọi HAUP. Có thể chọn ngưỡng khác để thực nghiệm.`
            : '<strong>Không có HAUP ở ngưỡng hiện tại.</strong> Các mẫu được khai phá đều không đạt ngưỡng LARGE. Xem bảng ngưỡng và AUUB ở Phân tích từng bước.',
        )
      : '';
  target.innerHTML =
    noHaup +
    `<div class="kpi-grid">${kpi('Giao dịch đã xử lý', num(r.transactionCount, 0), num(r.batchCount, 0) + ' batch · ' + num(c.report.upperBounds.length, 0) + ' item', 'datasets')}${kpi('Mẫu tiện ích cao', num(b.largePatterns.length, 0), badge('LARGE / HAUP', 'green') + ' AU ≥ ' + num(b.minUtilUpper), 'patterns')}${kpi('Thời gian thuật toán', num(r.elapsedMs), 'Không bao gồm đọc tệp và UI', 'clock', 'ms')}${kpi('Số lần re-scan', num(r.stats.rescanCount, 0), 'Không tính initial scan ở DB0', 'rescan')}</div>
  <div class="two-col">${card('Số HAUP và PRE-LARGE theo batch', 'Số mẫu sau mỗi batch dữ liệu', chart(labels, counts, { unit: 'mẫu' }), badge('Theo batch'))}${card(
    'AU của HAUP cuối',
    'Tối đa 6 mẫu LARGE ở batch cuối',
    chart(
      top.map((p) => p.name),
      [{ name: 'AU(P)', values: top.map((p) => p.averageUtility), color: colors.green }],
      {
        unit: 'utility',
        evidenceRows: auEvidence(top, b, r),
        emptyMessage:
          'Đã xử lý dữ liệu nhưng không có HAUP đạt ngưỡng LARGE ở lượt này. Xem giải thích cận trên và các mẫu PRE-LARGE bên dưới.',
      },
    ),
  )}</div>
  <div class="two-col equal-col history">${card(
    'TU(DB) và ngưỡng LARGE / PRE-LARGE',
    'Giá trị tuyệt đối trên dữ liệu tích lũy',
    chart(
      labels,
      [
        {
          name: 'TU(DB)',
          values: r.batches.map((b) => b.totalTransactionUtility),
          color: colors.slate,
        },
        { name: 'Sᵤ × TU(DB)', values: r.batches.map((b) => b.minUtilUpper), color: colors.blue },
        { name: 'Sₗ × TU(DB)', values: r.batches.map((b) => b.minUtilLower), color: colors.amber },
      ],
      { kind: 'line', unit: 'utility' },
    ),
  )}${card('Căn cứ phân loại', 'Batch cuối · ' + esc(b.name), `<div class="metric-row"><span>Tổng utility TU(DB)</span><strong>${num(b.totalTransactionUtility)}</strong></div><div class="formula">LARGE: AU(P) ≥ ${num(r.upperThreshold, 17)} × ${num(b.totalTransactionUtility)} = ${num(b.minUtilUpper)}<br>PRE-LARGE: ${num(b.minUtilLower)} ≤ AU(P) &lt; ${num(b.minUtilUpper)}</div><p class="subtle">PRE-LARGE là các mẫu được quản lý trong PT. Khi không re-scan, đây không phải phép khai phá lại toàn bộ mẫu PRE-LARGE.</p><p class="source-note"><a href="#glossary">Tra cứu định nghĩa AU, AUUB, MAU →</a></p>`)}</div>`;
}

function renderBatches(target) {
  const r = state.current.report;
  const bounds = r.upperBounds.slice(0, 12);
  target.innerHTML =
    notice(
      '<strong>Các bước xử lý:</strong> 01 Đọc & kiểm tra → 02 TU/MU & ngưỡng → 03 Eq. (12), re-scan → 04 Khai phá/cập nhật PIHAUP-List/PT → 05 AU & phân loại → 06 Đo lường & xuất. Mỗi batch dùng ngưỡng trên database tích lũy.',
    ) +
    `<div class="batch-grid">${r.batches.map((b) => `<section class="card batch-card"><div class="batch-title"><h2>${esc(b.name)}</h2>${action(b)}</div><div class="metric-row"><span>Giao dịch mới / tích lũy</span><strong>${num(b.transactionCount, 0)} / ${num(b.cumulativeTransactionCount, 0)}</strong></div><div class="metric-row"><span>TU(batch)</span><strong>${num(b.batchTransactionUtility)}</strong></div><div class="metric-row"><span>MU(batch) = Σ MU(T)</span><strong>${num(b.batchMaximumUtility)}</strong></div><div class="metric-row"><span>TU(DB) tích lũy</span><strong>${num(b.totalTransactionUtility)}</strong></div><div class="formula">Sᵤ × TU(DB) = ${num(r.upperThreshold, 17)} × ${num(b.totalTransactionUtility)} = ${num(b.minUtilUpper)}<br>Sₗ × TU(DB) = ${num(r.lowerThreshold, 17)} × ${num(b.totalTransactionUtility)} = ${num(b.minUtilLower)}</div><div class="metric-row"><span>HAUP / PRE-LARGE quản lý</span><strong>${b.largePatterns.length} / ${b.preLargePatterns.length}</strong></div>${batchDecision(b, r)}</section>`).join('')}</div>` +
    card(
      'Điều kiện re-scan qua các batch',
      'Eq. (12) · re-scan khi vế trái ≥ vế phải; không tính initial scan',
      chart(
        r.batches.slice(1).map((b) => b.name),
        [
          {
            name: 'MU(ID) − Sᵤ × TU(ID)',
            values: r.batches
              .slice(1)
              .map(
                (b) =>
                  b.accumulatedMaximumUtility - r.upperThreshold * b.accumulatedTransactionUtility,
              ),
            color: colors.blue,
          },
          {
            name: '(Sᵤ − Sₗ) × TU(OD)',
            values: r.batches
              .slice(1)
              .map(
                (b) =>
                  (r.upperThreshold - r.lowerThreshold) *
                  (b.totalTransactionUtility - b.accumulatedTransactionUtility),
              ),
            color: colors.amber,
          },
        ],
        { unit: 'utility' },
      ),
    ) +
    `<details class="card evidence"><summary>Xem dữ liệu đầu vào, utility và cận trên AUUB</summary><div class="card-body"><div class="two-col equal-col">${card(
      'Utility dữ liệu mới',
      'TU và tổng maximum utility của mỗi batch',
      chart(
        r.batches.map((b) => b.name),
        [
          {
            name: 'TU(batch)',
            values: r.batches.map((b) => b.batchTransactionUtility),
            color: colors.blue,
          },
          {
            name: 'MU(batch)',
            values: r.batches.map((b) => b.batchMaximumUtility),
            color: colors.cyan,
          },
        ],
        { unit: 'utility' },
      ),
    )}${card(
      'Số giao dịch',
      'Phân biệt dữ liệu mới và database tích lũy',
      chart(
        r.batches.map((b) => b.name),
        [
          { name: 'Mới', values: r.batches.map((b) => b.transactionCount), color: colors.slate },
          {
            name: 'Tích lũy',
            values: r.batches.map((b) => b.cumulativeTransactionCount),
            color: colors.blue,
          },
        ],
        { unit: 'giao dịch' },
      ),
    )}</div>` +
    card(
      'AUUB của item',
      'Tối đa 12 item có cận trên lớn nhất trên database cuối',
      chart(
        bounds.map((x) => x.item),
        [
          { name: 'AUUB(item)', values: bounds.map((x) => x.auub), color: colors.blue },
          {
            name: 'Ngưỡng LARGE',
            values: bounds.map(() => last(r).minUtilUpper),
            color: colors.amber,
          },
        ],
        { unit: 'utility' },
      ),
    ) +
    `<div class="history">${card(
      'Dữ liệu đầu vào · xem trước',
      'Tối đa 30 giao dịch đầu; TU = Σ u(i,T), MU = max u(i,T)',
      `<p class="subtle">EU (tối đa 30 item): ${esc(
        Object.entries(state.current.externalUtilities || {})
          .map(([i, u]) => i + '=' + num(u))
          .join(' · '),
      )}</p>` +
        table(
          ['Batch', 'TID', 'Giá trị IU (EU = 1 với transactions.txt)', 'TU(T)', 'MU(T)'],
          state.current.preview.map((t) =>
            tr([
              esc(t.batch),
              esc(t.tid),
              `<span class="mono">${esc(
                Object.entries(t.items)
                  .map(([i, u]) => i + ':' + num(u))
                  .join(' · '),
              )}</span>`,
              num(t.tu),
              num(t.mu),
            ]),
          ),
        ),
    )}</div></div></details>`;
}
function batchDecision(b, r) {
  if (b.index === 0)
    return '<div class="batch-decision initial"><strong>Khởi tạo cây mẫu</strong><p>Full scan lần đầu; lưu mốc OD. Không tính vào số re-scan.</p></div>';
  const od = b.totalTransactionUtility - b.accumulatedTransactionUtility,
    lhs = b.accumulatedMaximumUtility - r.upperThreshold * b.accumulatedTransactionUtility,
    rhs = (r.upperThreshold - r.lowerThreshold) * od;
  return `<div class="batch-decision ${b.rescanTriggered ? 'rescan' : 'update'}"><strong>${b.rescanTriggered ? 'Quét lại toàn bộ và dựng cây' : 'Chỉ cập nhật cây mẫu hiện có'}</strong><p>ID tích lũy: TU = ${num(b.accumulatedTransactionUtility)} · MU = ${num(b.accumulatedMaximumUtility)}<br>TU(OD) = ${num(od)}</p><div class="formula">${num(b.accumulatedMaximumUtility)} − ${num(r.upperThreshold, 17)} × ${num(b.accumulatedTransactionUtility)} = <strong>${num(lhs)}</strong><br>(${num(r.upperThreshold, 17)} − ${num(r.lowerThreshold, 17)}) × ${num(od)} = <strong>${num(rhs)}</strong><br>⇒ ${num(lhs)} ${b.rescanTriggered ? '≥' : '&lt;'} ${num(rhs)} · ${b.rescanTriggered ? 'Re-scan' : 'Không re-scan'}</div><p>${b.rescanTriggered ? 'Reset ID và cập nhật mốc OD.' : 'Giữ mốc OD; ID tiếp tục cộng dồn.'}</p><details><summary>Chi tiết Eq. (6)</summary><p>Mẫu số = ${num(b.tightRescanDenominator, 6)}. ${b.tightRescanDenominator > 0 ? 'Cận MU(ID) = ' + num(b.tightRescanLimit, 6) : 'Dùng Eq. (12) trực tiếp vì mẫu số không dương.'}</p></details></div>`;
}
function renderAnalysis() {
  $('content').innerHTML =
    reportNote() +
    finalConclusion(state.current) +
    `<nav class="analysis-guide" aria-label="Mục lục báo cáo"><strong class="subtle">Trong báo cáo:</strong><a href="#analysis-final">Kết luận cuối</a><span class="guide-arrow">·</span><a href="#analysis-summary">Tổng quan kết quả</a><span class="guide-arrow" aria-hidden="true">·</span><a href="#analysis-batches">Từng batch & re-scan</a><span class="guide-arrow" aria-hidden="true">·</span><a href="#analysis-patterns">Mẫu khai phá</a></nav><section id="analysis-summary" class="analysis-section" aria-labelledby="summary-title"><div class="analysis-heading"><div><h2 id="summary-title">Tổng quan kết quả</h2><p>Đọc nhanh dữ liệu, số mẫu và chi phí xử lý.</p></div></div><div id="summary-content"></div></section><details id="analysis-batches" class="analysis-section report-detail"><summary>Batch & điều kiện re-scan</summary><section aria-labelledby="batches-title"><div class="analysis-heading"><div><h2 id="batches-title">Từng batch & quyết định re-scan</h2><p>Utility, ngưỡng và lý do quét lại nằm ngay trong mỗi batch.</p></div></div><div id="batches-content"></div></section></details><section id="analysis-patterns" class="analysis-section" aria-labelledby="patterns-title"><div class="analysis-heading"><div><h2 id="patterns-title">Kết quả khai phá</h2><p>Mặc định xem batch cuối; lọc hoặc tìm mẫu ngay tại đây.</p></div></div><div id="patterns-content"></div></section>`;
  document.querySelector('.final-all').onclick = () => {
    state.batchIndex = state.current.report.batches.length - 1;
    state.patternType = 'LARGE';
    state.patternSearch = '';
    state.patternPage = 0;
    renderPatterns($('patterns-content'));
  };
  renderOverview($('summary-content'));
  renderBatches($('batches-content'));
  renderPatterns($('patterns-content'));
}
function renderPatterns(target) {
  const r = state.current.report,
    b = r.batches[Math.min(state.batchIndex, r.batches.length - 1)],
    patterns = [...b.largePatterns, ...b.preLargePatterns]
      .filter(
        (p) =>
          (state.patternType === 'ALL' || p.type === state.patternType) &&
          p.name.toLowerCase().includes(state.patternSearch.toLowerCase()),
      )
      .sort((a, b) => b.averageUtility - a.averageUtility);
  const size = 25,
    pages = Math.max(1, Math.ceil(patterns.length / size));
  state.patternPage = Math.min(state.patternPage, pages - 1);
  const totalPatterns = b.largePatterns.length + b.preLargePatterns.length;
  const emptyMessage =
    totalPatterns > 0
      ? 'Không có mẫu khớp bộ lọc. Xóa từ khóa hoặc chọn Tất cả loại mẫu để xem lại.'
      : `Đã xử lý ${num(b.cumulativeTransactionCount, 0)} giao dịch đến ${b.name}. Không có mẫu đang quản lý đạt ngưỡng ở lượt này (Sᵤ=${num(r.upperThreshold, 8)}, Sₗ=${num(r.lowerThreshold, 8)}). Đây là kết quả rỗng, không phải thiếu dữ liệu đầu vào.`;
  const shown = patterns.slice(state.patternPage * size, (state.patternPage + 1) * size),
    top = patterns.slice(0, 12);
  target.innerHTML =
    `<div class="toolbar"><label class="subtle" for="batch-select">Batch</label><select id="batch-select">${r.batches.map((b, i) => `<option value="${i}" ${i === state.batchIndex ? 'selected' : ''}>${esc(b.name)}</option>`).join('')}</select><input id="pattern-search" aria-label="Tìm mẫu theo item" placeholder="Tìm mẫu theo item…" value="${esc(state.patternSearch)}"><select id="pattern-type" aria-label="Loại mẫu"><option value="ALL">Tất cả loại mẫu</option><option value="LARGE" ${state.patternType === 'LARGE' ? 'selected' : ''}>LARGE / HAUP</option><option value="PRE_LARGE" ${state.patternType === 'PRE_LARGE' ? 'selected' : ''}>PRE-LARGE</option></select></div>` +
    card(
      'AU(P) của mẫu đang xem',
      'Tối đa 12 mẫu có AU cao nhất · ' + esc(b.name),
      chart(
        top.map((p) => p.name),
        [{ name: 'AU(P)', values: top.map((p) => p.averageUtility), color: colors.blue }],
        { unit: 'utility', emptyMessage, evidenceRows: auEvidence(top, b, r) },
      ),
    ) +
    `<section class="card history">${!patterns.length ? notice(esc(emptyMessage)) : ''}${table(
      ['Mẫu P', 'Phân loại', '|P|', 'U(P)', 'AU(P) = U(P) / |P|', 'Ngưỡng LARGE'],
      shown.map((p) =>
        tr([
          `<strong class="mono">${esc(p.name)}</strong>`,
          badge(
            p.type === 'LARGE' ? 'LARGE / HAUP' : 'PRE-LARGE',
            p.type === 'LARGE' ? 'green' : 'amber',
          ),
          p.length,
          num(p.sumUtility),
          num(p.averageUtility, 6),
          num(b.minUtilUpper),
        ]),
      ),
      'Bảng · Mẫu trong PT — LARGE / PRE-LARGE',
    )}<div class="table-footer"><span>${patterns.length ? num(patterns.length, 0) + ' mẫu · Trang ' + (state.patternPage + 1) + '/' + pages : '0 mẫu · đã xử lý dữ liệu'}</span><div><button class="button" id="previous" ${patterns.length === 0 ? 'hidden' : ''} ${state.patternPage === 0 ? 'disabled' : ''}>Trước</button> <button class="button" id="next" ${patterns.length === 0 ? 'hidden' : ''} ${state.patternPage >= pages - 1 ? 'disabled' : ''}>Sau</button></div></div></section>`;
  $('batch-select').onchange = (e) => {
    state.batchIndex = Number(e.target.value);
    state.patternPage = 0;
    renderPatterns(target);
  };
  $('pattern-type').onchange = (e) => {
    state.patternType = e.target.value;
    state.patternPage = 0;
    renderPatterns(target);
  };
  $('pattern-search').oninput = (e) => {
    const start = e.target.selectionStart;
    state.patternSearch = e.target.value;
    state.patternPage = 0;
    renderPatterns(target);
    $('pattern-search').focus();
    $('pattern-search').setSelectionRange(start, start);
  };
  $('previous').onclick = () => {
    state.patternPage--;
    renderPatterns(target);
  };
  $('next').onclick = () => {
    state.patternPage++;
    renderPatterns(target);
  };
}
function renderResourceMeasurements(c) {
  const resources = c.measurement.resources;
  if (!resources)
    return notice('Lượt cũ chưa có số đo RAM tiến trình và RAM toàn máy; chạy lại để lấy số đo.');
  const samples = resources.samples || [];
  const available = samples.filter((s) => s.processResidentMiB != null);
  const metric = (value) => (value == null ? 'Không hỗ trợ' : num(value) + ' MiB');
  return card(
    'RAM & bộ nhớ JVM',
    resources.scope,
    `<p>Đỉnh heap: <strong>${metric(resources.heapUsedMiBPeak)}</strong> · Đỉnh RAM tiến trình: <strong>${metric(resources.processResidentMiBPeak)}</strong> · RAM toàn máy dùng nhiều nhất: <strong>${metric(resources.systemUsedMiBPeak)}</strong></p>` +
      chart(
        samples.map((s) => num(s.elapsedMs / 1000)),
        [
          { name: 'JVM heap', values: samples.map((s) => s.heapUsedMiB), color: colors.blue },
          ...(available.length === samples.length
            ? [
                {
                  name: 'RAM tiến trình Java',
                  values: samples.map((s) => s.processResidentMiB),
                  color: colors.green,
                },
              ]
            : []),
        ],
        { unit: 'MiB', xLabel: 'Thời gian từ lúc bắt đầu lấy mẫu (s)', yLabel: 'Bộ nhớ' },
      ) +
      chart(
        samples.map((s) => num(s.elapsedMs / 1000)),
        [
          {
            name: 'RAM toàn máy đang dùng',
            values: samples.map((s) => s.systemUsedMiB),
            color: colors.slate,
          },
        ],
        { unit: 'MiB', xLabel: 'Thời gian (s)', yLabel: 'RAM toàn máy' },
      ) +
      '<p class="subtle">Lấy mẫu khoảng 1 giây; đỉnh quan sát có thể bỏ lỡ đỉnh ngắn. RAM toàn máy gồm mọi ứng dụng. JVM dùng chung giữa các lượt, không phải bộ nhớ tăng thêm riêng của thuật toán. Không bao gồm RAM JVM con khi đối chiếu.</p>',
  );
}
function renderSizeMeasurements() {
  const current = state.current;
  const runs = state.history
    .filter(
      (c) =>
        c.source === current.source &&
        c.report.upperThreshold === current.report.upperThreshold &&
        c.report.lowerThreshold === current.report.lowerThreshold,
    )
    .slice()
    .sort((a, b) => a.report.transactionCount - b.report.transactionCount);
  return card(
    'Thực nghiệm thay đổi kích thước dữ liệu',
    'Cùng tệp và Sᵤ/Sₗ; kiểm tra số lượt đo trước khi so sánh.',
    chart(
      runs.map((c) => num(c.report.transactionCount, 0)),
      [
        {
          name: 'Runtime thuật toán · median',
          values: runs.map((c) => c.report.elapsedMs),
          color: colors.blue,
        },
      ],
      { unit: 'ms', xLabel: 'Số giao dịch', yLabel: 'Runtime' },
    ) +
      chart(
        runs.map((c) => num(c.report.transactionCount, 0)),
        [
          {
            name: 'Đỉnh heap thuật toán',
            values: runs.map((c) => c.measurement.heapPeakObservedMb),
            color: colors.green,
          },
        ],
        { unit: 'MiB', xLabel: 'Số giao dịch', yLabel: 'Heap' },
      ) +
      table(
        [
          'Kích thước',
          'Giao dịch',
          'Sᵤ / Sₗ',
          'Lượt đo',
          'Runtime median (ms)',
          'Đỉnh heap (MiB)',
          'RAM tiến trình (MiB)',
          'HAUP',
        ],
        runs.map((c) =>
          tr([
            num(c.manifest?.sizePercent || 100, 0) + '%',
            num(c.report.transactionCount, 0),
            num(c.report.upperThreshold, 10) + ' / ' + num(c.report.lowerThreshold, 10),
            c.measurement.repetitions,
            num(c.report.elapsedMs),
            num(c.measurement.heapPeakObservedMb),
            c.measurement.resources?.processResidentMiBPeak == null
              ? 'Chưa đo'
              : num(c.measurement.resources.processResidentMiBPeak),
            last(c.report).largePatterns.length,
          ]),
        ),
        'Bảng · Các trường hợp kích thước',
      ),
    '<button class="button" id="export-sizes">Xuất so sánh kích thước CSV</button>',
  );
}

function renderMeasurements() {
  const c = state.current,
    r = c.report,
    m = c.measurement,
    labels = r.batches.map((b) => b.name);
  $('content').innerHTML =
    reportNote() +
    notice(
      '<strong>Phạm vi phép đo:</strong> elapsedMs đo thời gian xử lý trong AnalysisReport.run; đọc tệp được đo riêng. Heap là đỉnh quan sát trước/sau batch, không phải RSS hay đỉnh bộ nhớ tuyệt đối. CPU là thời gian CPU của luồng thực hiện, không phải % CPU toàn máy.',
    ) +
    `<div class="kpi-grid">${kpi('Đọc + SHA-256', num(m.importMs), 'Không bao gồm thuật toán', 'datasets', 'ms')}${kpi('Thuật toán · median', num(r.elapsedMs), (m.repetitions || 1) + ' lượt · ' + num(m.minMs ?? r.elapsedMs) + '–' + num(m.maxMs ?? r.elapsedMs) + ' ms', 'clock', 'ms')}${kpi('CPU luồng khai phá', num(m.threadCpuMs), 'Tổng CPU các lượt + warm-up; không gồm đối chiếu', 'measurements', m.threadCpuMs == null ? '' : 'ms')}${kpi('JVM heap quan sát', num(m.heapPeakObservedMb), 'Lấy mẫu trước / sau mỗi batch', 'measurements', 'MiB')}</div>` +
    `<div class="two-col equal-col">${card('Thời gian theo batch', 'Thời gian tường của processBatch', chart(labels, [{ name: 'Thuật toán', values: r.batches.map((b) => b.elapsedMs), color: colors.blue }], { unit: 'ms' }))}${card(
      'JVM heap theo batch',
      'MiB = bytes / 1.048.576 · không phải RSS',
      chart(
        labels,
        [
          {
            name: 'Trước batch',
            values: r.batches.map((b) => b.memoryBeforeMb),
            color: colors.slate,
          },
          { name: 'Sau batch', values: r.batches.map((b) => b.memoryAfterMb), color: colors.cyan },
          {
            name: 'Đỉnh quan sát tích lũy',
            values: r.batches.map((b) => b.peakMemoryMb),
            color: colors.blue,
          },
        ],
        { kind: 'line', unit: 'MiB' },
      ),
    )}</div>` +
    `<div class="two-col equal-col">${card('Các giai đoạn xử lý', 'Đọc / thuật toán: thời gian tường. CPU: thời gian CPU luồng khai phá.', chart(m.threadCpuMs == null ? ['Đọc dữ liệu', 'Thuật toán'] : ['Đọc dữ liệu', 'Thuật toán', 'CPU luồng'], [{ name: 'Thời gian', values: m.threadCpuMs == null ? [m.importMs, r.elapsedMs] : [m.importMs, r.elapsedMs, m.threadCpuMs], color: colors.blue }], { unit: 'ms' }))}${card('Khối lượng công việc của miner', 'Bộ đếm thuật toán; không phải thời gian xử lý', chart(['Mẫu đã duyệt', 'Nút kết hợp'], [{ name: 'Số lượng', values: [r.stats.patternsVisited, r.stats.combinedNodes], color: colors.slate }], { unit: 'lượt / nút' }))}</div>` +
    renderResourceMeasurements(c) +
    renderSizeMeasurements() +
    manifestPanel(c) +
    (c.comparison
      ? renderComparison(c.comparison)
      : notice(
          'Để so sánh Original Eq. (5) và Tight Eq. (6), bật <strong>Đối chiếu Original / Tight</strong> rồi chạy lại. Hai chính sách được đo bằng các lượt thực thi riêng.',
        )) +
    `<section class="card history"><div class="card-head"><div><h2>Lịch sử phép đo trong phiên</h2><p>Tối đa 8 lượt · điều kiện ngưỡng được lưu cùng số liệu</p></div></div><div class="card-body">${chart(
      state.history.map((c, i) => 'Lượt ' + (state.history.length - i)),
      [
        {
          name: 'Thuật toán',
          values: state.history.map((c) => c.report.elapsedMs),
          color: colors.blue,
        },
      ],
      { unit: 'ms' },
    )}</div><div class="two-col equal-col"><div class="card-body">${chart(
      state.history.map((c, i) => 'Lượt ' + (state.history.length - i)),
      [
        {
          name: 'HAUP cuối',
          values: state.history.map((c) => last(c.report).largePatterns.length),
          color: colors.green,
        },
        {
          name: 'PRE-LARGE',
          values: state.history.map((c) => last(c.report).preLargePatterns.length),
          color: colors.amber,
        },
      ],
      { unit: 'mẫu', xLabel: 'Lượt chạy', yLabel: 'Số mẫu' },
    )}</div><div class="card-body">${chart(
      state.history.map((c, i) => 'Lượt ' + (state.history.length - i)),
      [
        {
          name: 'Đỉnh heap lấy mẫu',
          values: state.history.map((c) => c.measurement.heapPeakObservedMb),
          color: colors.slate,
        },
      ],
      { unit: 'MiB', xLabel: 'Lượt chạy', yLabel: 'JVM heap (MiB)' },
    )}</div></div>${table(
      [
        'Lượt',
        'Dữ liệu',
        'Sᵤ / Sₗ',
        'Giao dịch',
        'HAUP',
        'Re-scan',
        'Thuật toán (ms)',
        'Heap (MiB)',
      ],
      state.history.map((c, i) =>
        tr([
          `<button class="button history-open" data-index="${i}">Mở ${state.history.length - i}</button>`,
          esc(c.source),
          num(c.report.upperThreshold, 17) + ' / ' + num(c.report.lowerThreshold, 17),
          num(c.report.transactionCount, 0),
          last(c.report).largePatterns.length,
          c.report.stats.rescanCount,
          num(c.report.elapsedMs),
          num(c.measurement.heapPeakObservedMb),
        ]),
      ),
    )}</section>`;
  $('export-sizes').onclick = () => {
    const runs = state.history.filter(
      (run) =>
        run.source === c.source &&
        run.report.upperThreshold === r.upperThreshold &&
        run.report.lowerThreshold === r.lowerThreshold,
    );
    const quote = (value) => '"' + String(value ?? '').replaceAll('"', '""') + '"';
    const lines = [
      [
        'id',
        'source',
        'sizePercent',
        'rows',
        'su',
        'sl',
        'repetitions',
        'runtimeMedianMs',
        'runtimeMinMs',
        'runtimeMaxMs',
        'heapPeakMiB',
        'processResidentPeakMiB',
        'systemUsedPeakMiB',
        'haups',
      ],
      ...runs.map((run) => [
        run.id,
        run.source,
        run.manifest?.sizePercent || 100,
        run.report.transactionCount,
        run.report.upperThreshold,
        run.report.lowerThreshold,
        run.measurement.repetitions,
        run.report.elapsedMs,
        run.measurement.minMs,
        run.measurement.maxMs,
        run.measurement.heapPeakObservedMb,
        run.measurement.resources?.processResidentMiBPeak,
        run.measurement.resources?.systemUsedMiBPeak,
        last(run.report).largePatterns.length,
      ]),
    ];
    const url = URL.createObjectURL(
      new Blob(['\uFEFF' + lines.map((row) => row.map(quote).join(',')).join('\n')], {
        type: 'text/csv;charset=utf-8',
      }),
    );
    const link = document.createElement('a');
    link.href = url;
    link.download = 'size-experiments.csv';
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  };
  document.querySelectorAll('.history-open').forEach(
    (button) =>
      (button.onclick = () => {
        state.current = state.history[Number(button.dataset.index)];
        render();
      }),
  );
}
function renderComparison(pair) {
  const reports = [pair.original, pair.tight],
    labels = ['Original', 'Tight'];
  return (
    notice(
      pair.sameHaups
        ? '<strong>Đối chiếu đạt:</strong> tập HAUP cuối và AU giống nhau giữa hai chính sách.'
        : '<strong>Đối chiếu KHÔNG đạt:</strong> tập HAUP hoặc AU khác nhau; cần kiểm tra kết quả.',
    ) +
    table(
      ['Chính sách', 'Lượt đo', 'Median (ms)', 'Min (ms)', 'Max (ms)', 'Môi trường'],
      reports.map((r, i) => {
        const t = pair.timing?.[i === 0 ? 'original' : 'tight'];
        return tr([
          labels[i],
          pair.repetitions || 1,
          num(r.elapsedMs),
          num(t?.minMs ?? r.elapsedMs),
          num(t?.maxMs ?? r.elapsedMs),
          pair.isolated ? 'JVM riêng' : 'JVM chung',
        ]);
      }),
      'Bảng · Thời gian Original / Tight',
    ) +
    `<div class="two-col equal-col">${card('Original / Tight · re-scan', 'Không tính initial scan', chart(labels, [{ name: 'Re-scan', values: reports.map((r) => r.stats.rescanCount), color: colors.amber }], { unit: 'lần' }))}${card('Original / Tight · thời gian', 'Mỗi chính sách: JVM riêng · median các lượt đo', chart(labels, [{ name: 'Thuật toán', values: reports.map((r) => r.elapsedMs), color: colors.blue }], { unit: 'ms' }))}</div><div class="two-col equal-col">${card('Original / Tight · heap', 'JVM riêng cho từng chính sách; sampled heap, không phải RSS', chart(labels, [{ name: 'Heap', values: reports.map((r) => r.peakMemoryMb), color: colors.cyan }], { unit: 'MiB' }))}${card('Original / Tight · số mẫu duyệt', 'Đo công việc thực tế trong miner', chart(labels, [{ name: 'Mẫu duyệt', values: reports.map((r) => r.stats.patternsVisited), color: colors.slate }], { unit: 'lượt' }))}</div>`
  );
}
function renderDatasets() {
  $('content').innerHTML =
    notice(
      '<strong>Định dạng gốc:</strong> items:TU:itemUtilities. Mỗi utility đã là u(i,T); parser dùng EU = 1 để giữ nguyên giá trị. Kiểm tra số item, utility dương và TU bằng tổng utility. Giữ thứ tự dòng; chia 80% / 10% / 10% là kịch bản mô phỏng chèn batch, không phải batch được tác giả cung cấp.',
    ) +
    `<div class="dataset-list">${state.datasets.map((d) => `<section class="card dataset-card"><span class="dataset-icon">${icon('datasets')}</span><h2>${esc(d.name.replace(/-metadata/i, ''))}</h2><p>transactions.txt · ${num(d.bytes / 1048576)} MiB<br>Dữ liệu gốc trên máy chủ Java${d.profile ? '<br>Ngưỡng khởi đầu: Sᵤ ' + num(d.profile.su * 100, 5) + '% · Sₗ ' + num(d.profile.sl * 100, 5) + '%' : ''}</p><button class="button dataset-select" data-id="${esc(d.id)}">Chọn bộ dữ liệu ${icon('arrow')}</button></section>`).join('')}</div>` +
    (state.datasets.length
      ? ''
      : notice(
          'Chưa cấu hình thư mục dữ liệu trên máy chủ. Chạy <code>run-web.ps1 -DataRoot "đường dẫn đến thư mục Data"</code>, hoặc chọn <strong>Tải tệp TXT lên</strong> ở phần thiết lập.',
        )) +
    `<div class="history">${card('Một tệp, một lần phân tích', 'Không ghép các bộ thực nghiệm thành một database', `<div class="formula">1 2 3:17:4 5 8<br>└ items &nbsp; └ TU &nbsp; └ u(i,T) tương ứng</div><p class="subtle">investment_table.txt là metadata, không nhân lại vào itemUtilities. Ngưỡng 0,23 / 0,10 chỉ là ngưỡng mặc định của ví dụ bài báo; chọn ngưỡng phù hợp khi thực nghiệm.</p>`)}</div>`;
  document.querySelectorAll('.dataset-select').forEach((button) => {
    button.disabled = state.busy;
    button.onclick = () => {
      $('source').value = button.dataset.id;
      sourceChanged();
      location.hash = 'overview';
      $('run-panel').hidden = false;
      $('run-title').scrollIntoView({ behavior: 'smooth', block: 'center' });
      $('run').focus();
    };
  });
}
// UI DESIGN PATTERN — SKELETON SCREEN: khung chờ kiểm chứng, thay bằng kết quả thật hoặc lỗi.
async function renderVerification() {
  if (!state.verification) {
    $('content').innerHTML = card(
      'Đang đối chiếu mẫu bài báo',
      'Chạy trực tiếp lõi Java với Sᵤ = 0,23 và Sₗ = 0,10',
      '<div class="loading-block"></div>',
    );
    try {
      state.verification = await request('/api/verify');
      if (state.page === 'verification') renderVerification();
    } catch (e) {
      setError(e);
      $('content').innerHTML = notice(
        'Chưa thực hiện được kiểm chứng. Xem mã lỗi và cách xử lý phía trên.',
      );
    }
    return;
  }
  const v = state.verification;
  $('content').innerHTML =
    notice(
      `<strong>${v.passed ? 'ĐẠT' : 'KHÔNG ĐẠT'} · ${v.checks.filter((c) => c.passed).length}/${v.checks.length} đối chiếu.</strong> Giá trị kỳ vọng được cố định từ ví dụ bài báo, không lấy từ dữ liệu đang hiển thị. Test tự động còn so với vét cạn trên các luồng nhỏ ngẫu nhiên.`,
    ) +
    `<div class="kpi-grid">${kpi('Giao dịch mẫu', '9', 'Tables 2–3 · 6 item', 'datasets')}${kpi('TU(DB0 → DB2)', '240', '141 → 173 → 240 utility', 'measurements')}${kpi('Tight / Original', '1 / 2', 'Số re-scan, không gồm DB0', 'rescan')}${kpi('HAUP cuối', '3', '{E}, {B}, {B,E}', 'verification')}</div>` +
    `<div class="two-col equal-col">${card('Utility kỳ vọng từ bài báo', 'Dữ liệu gốc Tables 2–3', chart(['DB0', 'DB0+DB1', 'DB0+DB1+DB2'], [{ name: 'TU tích lũy', values: [141, 173, 240], color: colors.blue }], { unit: 'utility' }))}${card('AU của HAUP cuối', 'Giá trị kiểm chứng ở Sᵤ = 0,23', chart(['{E}', '{B}', '{B,E}'], [{ name: 'AU(P)', values: [84, 75, 63.5], color: colors.green }], { unit: 'utility' }))}</div>` +
    `<section class="card">${table(
      ['Phép kiểm chứng', 'Kỳ vọng từ bài báo', 'Kết quả Java', 'Trạng thái'],
      v.checks.map((c) =>
        tr([
          esc(c.label),
          esc(typeof c.expected === 'number' ? num(c.expected, 6) : c.expected),
          esc(typeof c.actual === 'number' ? num(c.actual, 6) : c.actual),
          `<span class="${c.passed ? 'success-text' : 'fail-text'}">${c.passed ? 'ĐẠT' : 'KHÔNG ĐẠT'}</span>`,
        ]),
      ),
    )}</section><p class="source-note">Nguồn: <a href="https://doi.org/10.1186/s40537-025-01352-x" target="_blank" rel="noreferrer">Intelligent average utility pattern analysis using pre-large concept in dynamic stream data</a> · Tables 2–3, ví dụ khai phá và Eq. (5), (6), (12). Kiểm chứng mẫu không đồng nghĩa chứng minh mọi đầu vào.</p>`;
}
function renderGlossary() {
  const terms = [
    [
      'IU, EU và u(i,T)',
      'u(i,T) = IU(i,T) × EU(i)',
      'IU là internal utility, EU là external utility. Trong transactions.txt, itemUtilities đã chứa u(i,T); EU = 1 chỉ là biểu diễn bảo toàn utility.',
    ],
    [
      'TU và MU',
      'TU(T) = Σᵢ∈T u(i,T)\nMU(T) = maxᵢ∈T u(i,T)\nTU(DB) = Σₜ∈DB TU(T)\nMU(DB) = Σₜ∈DB MU(T)',
      'MU(DB) là tổng MU từng giao dịch, không phải max trên toàn database.',
    ],
    [
      'U(P) và AU(P)',
      'U(P,T) = Σᵢ∈P u(i,T), với P ⊆ T\nU(P) = Σₜ⊇P U(P,T)\nAU(P) = U(P) / |P|',
      'Tiện ích trung bình chia cho số item của mẫu, không chia cho số giao dịch.',
    ],
    [
      'AUUB — Average Utility Upper Bound',
      'AUUB(P) = Σₜ∈DB, P⊆T MU(T)',
      'Cận trên dùng maximum utility của các giao dịch chứa mẫu. Các item được sắp theo AUUB tăng dần khi mở rộng.',
    ],
    [
      'MRU, RN và MRN',
      'MRU(P,T) = max utility của item còn lại sau P\nRN(P,T) = số item còn lại sau P\nMRN(P) = maxₜ⊇P RN(P,T)',
      '“Còn lại” dựa trên thứ tự item dùng trong quá trình khai phá. Không phải đếm tất cả item không thuộc mẫu.',
    ],
    [
      'MAU — Maximum Average Upper bound',
      'Nếu MRU(P,T) > AU(P,T):\nMAU(P,T) = [U(P,T) + MRN(P) × MRU(P,T)] / [|P| + MRN(P)]\nNếu 0 < MRU(P,T) ≤ AU(P,T):\nMAU(P,T) = [U(P,T) + MRU(P,T)] / [|P| + 1]\nNếu MRU(P,T) = 0: MAU(P,T) = 0\nMAU(P) = Σₜ⊇P MAU(P,T)',
      'Cận trên cho mở rộng mẫu theo ba trường hợp trong phần Preliminaries của bài báo.',
    ],
    [
      'LARGE, PRE-LARGE và SMALL',
      'LARGE: AU(P) ≥ Sᵤ × TU(DB)\nPRE-LARGE: Sₗ × TU(DB) ≤ AU(P) < Sᵤ × TU(DB)\nSMALL: AU(P) < Sₗ × TU(DB)',
      'HAUP là mẫu LARGE. PT lưu các mẫu LARGE và PRE-LARGE tại lần dựng cây; giữa các lần re-scan chỉ cập nhật mẫu đang quản lý.',
    ],
    [
      'OD và ID',
      'OD: database tại lần full scan gần nhất\nID: tất cả dữ liệu thêm sau lần đó',
      'Khi re-scan: OD nhận database hiện tại, ID được reset. Khi chỉ cập nhật PT: ID tiếp tục cộng dồn.',
    ],
    [
      'Tight re-scan — Eq. (6) / (12)',
      'Eq. (12):\nMU(ID) − Sᵤ × TU(ID) ≥ (Sᵤ − Sₗ) × TU(OD)\nEq. (6), khi mẫu số dương:\nMU(ID) ≥ [(Sᵤ − Sₗ) / (1 − Sᵤ × TU(ID)/MU(ID))] × TU(OD)',
      'Lõi dùng dạng Eq. (12) để tránh chia cho 0 hoặc đổi chiều bất đẳng thức khi mẫu số không dương.',
    ],
    [
      'Original re-scan — Eq. (5) / (13)',
      'TU(ID) × (1 − Sᵤ) ≥ (Sᵤ − Sₗ) × TU(OD)',
      'So sánh với Tight để thấy số lần expansion khác nhau. Ít re-scan hơn không tự động chứng minh thời gian luôn nhanh hơn.',
    ],
  ];
  $('content').innerHTML =
    notice(
      'Định nghĩa và ký hiệu theo bài báo <strong>Kim et al. (2026), 13:13</strong>. Mã DOI có “025”, nhưng năm xuất bản in trong PDF là 2026.',
    ) +
    `<div class="glossary-grid">${terms.map(([title, formula, description]) => `<section class="card glossary-card"><h2>${esc(title)}</h2><div class="formula">${esc(formula).replace(/\n/g, '<br>')}</div><p>${esc(description)}</p></section>`).join('')}</div><p class="source-note"><a href="https://doi.org/10.1186/s40537-025-01352-x" target="_blank" rel="noreferrer">Mở bài báo gốc ↗</a> · Phần Preliminaries, tight re-scan condition và các Lemma.</p>`;
}
// UI DESIGN PATTERN — SKELETON SCREEN: giữ chỗ danh mục test khi request chưa trả về.
// Giúp nhận biết đang tải; không làm backend chạy nhanh hơn và không gán sẵn trạng thái ĐẠT.
async function renderTestCases() {
  if (!state.testResults) {
    $('content').innerHTML = card(
      'Danh sách test case',
      'Đang tải danh mục…',
      '<div class="loading-block"></div>',
    );
    try {
      state.testResults = await request('/api/testcases');
      if (state.page === 'testcases') renderTestCases();
    } catch (error) {
      setError(error);
      $('content').innerHTML = notice(
        'Chưa tải được danh sách test case. Sửa lỗi phía trên rồi mở lại mục này.',
      );
    }
    return;
  }
  const report = state.testResults,
    ran = report.state !== 'NOT_RUN',
    cases = report.cases;
  const groups = ['TC-P', 'TC-E', 'TC-A', 'TC-D'],
    labels = ['Bài báo · 12', 'Dữ liệu lỗi · 5', 'Thuật toán · 2', 'Parser · 1'];
  const groupCounts = (status) =>
    groups.map((g) => cases.filter((c) => c.id.startsWith(g) && c.state === status).length);
  const numeric = cases.filter((c) => /^TC-P/.test(c.id) && Number.isFinite(Number(c.expected)));
  const finalCase = cases.find((c) => c.id === 'TC-P09'),
    boundCase = cases.find((c) => c.id === 'TC-P12');
  const parseAu = (value) =>
    Object.fromEntries(
      [...String(value ?? '').matchAll(/(?:\{|, )([^=]+)=([0-9.]+)/g)].map((m) => [
        m[1],
        Number(m[2]),
      ]),
    );
  const expectedAu = parseAu(finalCase.expected),
    actualAu = parseAu(finalCase.actual);
  const flow = `<ol class="test-flow"><li><strong>01 · Chuẩn bị</strong><span>Tables 2–3, Sᵤ=.23 / Sₗ=.10; oracle cố định.</span></li><li><strong>02 · Thực thi</strong><span>Chạy miner Java Original & Tight.</span></li><li><strong>03 · Đối chiếu</strong><span>TU, MU, tập HAUP/AU và Eq. (6)/(12).</span></li><li><strong>04 · Kết luận</strong><span>So kỳ vọng/thực tế, ghi lỗi từng case.</span></li><li><strong>05 · Xuất</strong><span>ZIP: HTML biểu đồ + CSV + JSON.</span></li></ol>`;
  $('content').innerHTML =
    `<div class="section-row"><div><h2>Test case chạy trực tiếp</h2><p class="subtle">20 case: mẫu bài báo, dữ liệu sai, ranh giới re-scan và precision ngưỡng.</p></div><button id="run-testcases" class="button primary" ${state.testsBusy ? 'disabled' : ''}>${state.testsBusy ? 'Đang kiểm thử…' : 'Chạy 20 test case'}</button></div>` +
    notice(
      ran
        ? `<strong>${report.passed}/${report.total} ĐẠT</strong> · Lần chạy: ${esc(new Date(report.runAt).toLocaleString('vi-VN'))} · ${num(report.elapsedMs)} ms. Case không đạt sẽ ghi rõ kỳ vọng, thực tế và lỗi.`
        : 'Chưa chạy kiểm thử trong phiên server này. Bấm Chạy 20 test case để nhận kết quả thực; danh sách bên dưới là đặc tả.',
    ) +
    notice(
      '<strong>Phạm vi theo bài báo:</strong> TC-P01–12 đối chiếu ví dụ Tables 2–3, Table 5 và Eq. (5), (6), (12). TC-E/A/D là kiểm thử bổ sung của ứng dụng. 20/20 không có nghĩa tái lập toàn bộ benchmark của bài báo. TC-P dùng chung một lượt chạy mẫu; thời gian từng assertion không dùng để so tốc độ thuật toán.',
    ) +
    flow +
    (ran && cases.every((c) => c.memoryBefore && c.memoryAfter)
      ? card(
          'Heap theo testcase',
          'Hai thời điểm trước/sau case trong JVM chung; không phải peak hoặc lượng cấp phát riêng.',
          chart(
            cases.map((c) => c.id),
            [
              {
                name: 'Trước case',
                values: cases.map((c) => c.memoryBefore?.heapUsedMiB ?? 0),
                color: colors.slate,
              },
              {
                name: 'Sau case',
                values: cases.map((c) => c.memoryAfter?.heapUsedMiB ?? 0),
                color: colors.blue,
              },
            ],
            { unit: 'MiB', xLabel: 'Testcase', yLabel: 'JVM heap' },
          ),
        )
      : '') +
    card(
      'Thực nghiệm kích thước dữ liệu',
      'Chạy riêng 25%, 50%, 75%, 100% trên cùng tệp và cùng ngưỡng.',
      '<p>Đây là các trường hợp khảo sát runtime và RAM; không gán ĐẠT nếu chưa có đối chứng đúng/sai. Chọn dữ liệu ở trang Phân tích, mở Tùy chọn & giải thích rồi bấm Thực nghiệm kích thước. Kết quả, biểu đồ và CSV so sánh nằm ở Đo lường & biểu đồ.</p><a class="button" href="#overview">Chọn dữ liệu và chạy thực nghiệm</a>',
    ) +
    (ran && cases.every((c) => c.memoryBefore && c.memoryAfter)
      ? table(
          [
            'Testcase',
            'Runtime (ms)',
            'Heap trước (MiB)',
            'Heap sau (MiB)',
            'RAM toàn máy sau (MiB)',
          ],
          cases.map((c) =>
            tr([
              c.id,
              num(c.elapsedMs),
              num(c.memoryBefore.heapUsedMiB),
              num(c.memoryAfter.heapUsedMiB),
              num(c.memoryAfter.systemUsedMiB),
            ]),
          ),
          'Bảng · Tài nguyên từng testcase — JVM chung',
        )
      : '') +
    `<div class="section-row"><p class="subtle">Xuất trạng thái hiện tại, không tự chạy lại kiểm thử.</p><button id="export-tests" class="button">Xuất kiểm thử ZIP</button></div>` +
    `<div class="two-col equal-col">${card(
      'Kết quả theo nhóm',
      'Số ca; chưa chạy được thể hiện riêng',
      chart(
        labels,
        [
          { name: 'ĐẠT', values: groupCounts('PASSED'), color: colors.green },
          { name: 'KHÔNG ĐẠT', values: groupCounts('FAILED'), color: colors.amber },
          { name: 'CHƯA CHẠY', values: groupCounts('NOT_RUN'), color: colors.slate },
        ],
        { unit: 'case' },
      ),
    )}${card(
      'Kỳ vọng / thực tế bài báo',
      'TU và MU · utility; re-scan được vẽ riêng bên dưới',
      chart(
        numeric.slice(0, 6).map((c) => c.id),
        [
          {
            name: 'Kỳ vọng',
            values: numeric.slice(0, 6).map((c) => Number(c.expected)),
            color: colors.slate,
          },
          ...(ran
            ? [
                {
                  name: 'Java',
                  values: numeric.slice(0, 6).map((c) => Number(c.actual)),
                  color: colors.blue,
                },
              ]
            : []),
        ],
        { unit: 'utility' },
      ),
    )}</div>` +
    `<div class="two-col equal-col">${card('Số lần re-scan', 'Không gồm DB0; Original Eq. (5) và Tight Eq. (6)', chart(['Tight', 'Original'], [{ name: 'Kỳ vọng', values: [1, 2], color: colors.slate }, ...(ran ? [{ name: 'Java', values: cases.filter((c) => ['TC-P10', 'TC-P11'].includes(c.id)).map((c) => Number(c.actual)), color: colors.blue }] : [])], { unit: 'lần' }))}${card(
      'Thời gian kiểm thử từng case',
      'TC-P01 gồm lượt chạy mẫu dùng chung; các TC-P sau là assertion, không phải benchmark',
      chart(
        cases.map((c) => c.id),
        ran
          ? [{ name: 'Thời gian', values: cases.map((c) => c.elapsedMs), color: colors.blue }]
          : [],
        { unit: 'ms', emptyMessage: 'Chưa chạy kiểm thử; chưa có số đo.' },
      ),
    )}</div>` +
    `<div class="two-col equal-col">${card('AU của HAUP cuối', 'TC-P09 · AU(P) = U(P)/|P|', chart(Object.keys(expectedAu), [{ name: 'Kỳ vọng', values: Object.values(expectedAu), color: colors.slate }, ...(ran ? [{ name: 'Java', values: Object.keys(expectedAu).map((k) => actualAu[k]), color: colors.green }] : [])], { unit: 'utility', xLabel: 'Mẫu P', yLabel: 'AU(P)' }))}${card('Cận Tight DB1', 'TC-P12 · Eq. (6), bài báo làm tròn 26,438', chart(['DB1'], [{ name: 'Kỳ vọng', values: [Number(boundCase.expected)], color: colors.slate }, ...(ran ? [{ name: 'Java', values: [Number(boundCase.actual)], color: colors.blue }] : [])], { unit: 'utility' }))}</div>` +
    `<section class="card">${table(
      ['Test case', 'Đầu vào', 'Kỳ vọng', 'Thực tế', 'Trạng thái'],
      cases.map((c) =>
        tr([
          `<strong class="mono">${esc(c.id)}</strong><br>${esc(c.title)}`,
          `<span class="mono">${esc(c.input)}</span>`,
          `<span class="mono">${esc(c.expected)}</span>`,
          `<span class="mono">${esc(c.actual ?? 'Chưa chạy')}</span>${c.detail ? '<p class="fail-text">' + esc(c.detail) + '</p>' : ''}`,
          badge(
            c.state === 'PASSED' ? 'ĐẠT' : c.state === 'FAILED' ? 'KHÔNG ĐẠT' : 'CHƯA CHẠY',
            c.state === 'PASSED' ? 'green' : c.state === 'FAILED' ? 'amber' : 'neutral',
          ),
        ]),
      ),
      'Test case thực thi bởi lõi Java; không phải kết quả minh họa. Nhóm TC-P: bài báo; TC-E: lỗi; TC-A: thuật toán; TC-D: parser.',
    )} </section>` +
    `<div class="history">${card('Bộ test tự động trong mã nguồn', 'Phân biệt với 20 case chạy trực tiếp ở trên', `<ul class="check-list"><li><strong>PiHaupaSelfTest</strong> · oracle vét cạn, luồng nhỏ ngẫu nhiên, re-scan tích lũy và ranh giới.</li><li><strong>ExportBundleTest / ArchiveDatasetReaderTest / LargeTextDatasetTest</strong> · xuất báo cáo, parser, TU và định dạng transactions.txt.</li><li><strong>LabApplicationTest</strong> · job bất đồng bộ, precision JSON, error codes, catalog và test runner.</li><li><strong>DatasetProfileVerification</strong> · 6 bộ dữ liệu gốc, singleton đối chứng và U(P) cộng từ raw transactions.</li><li><strong>verify-web.py</strong> · API, upload, lỗi, trạng thái và 6 profile ngưỡng.</li></ul><div class="formula">./run-tests.ps1<br>python scripts/verify-web.py --url http://localhost:8015 --presets --real-data</div><p class="subtle">Các bộ test terminal cần chạy bằng lệnh trên; trang này không tự gán trạng thái ĐẠT cho chúng.</p>`)}</div>`;
  $('export-tests').onclick = async () => {
    try {
      await saveDownload('/api/testcases/export', 'pihaupa-testcases.zip');
    } catch (error) {
      setError(error);
    }
  };
  $('run-testcases').onclick = async () => {
    state.testsBusy = true;
    notifyStatus('Đang chạy 20 test case', 'Đối chiếu bài báo, dữ liệu lỗi, thuật toán và parser.');
    setError('');
    renderTestCases();
    try {
      state.testResults = await request('/api/testcases/run', { method: 'POST' });
      notifyStatus(
        'Kiểm thử hoàn tất',
        state.testResults.passed + '/' + state.testResults.total + ' ca đạt.',
        state.testResults.state === 'PASSED' ? 'success' : 'error',
      );
    } catch (error) {
      setError(error);
    } finally {
      state.testsBusy = false;
      if (state.page === 'testcases') renderTestCases();
    }
  };
}
async function renderRuns() {
  $('content').innerHTML = card('Hồ sơ trên máy', 'Đang tải…', '<div class="loading-block"></div>');
  try {
    const runs = await request('/api/runs');
    if (state.page !== 'runs') return;
    $('content').innerHTML = card(
      'Lượt chạy đã lưu',
      '20 hồ sơ gần nhất · không mất khi tải lại trang',
      runs.length
        ? table(
            ['Thời điểm', 'Dữ liệu', 'Sᵤ / Sₗ', 'Giao dịch', 'HAUP', 'Median (ms)', ''],
            runs.map((r) =>
              tr([
                esc(new Date(r.createdAt).toLocaleString('vi-VN')),
                esc(r.source),
                num(r.su, 17) + ' / ' + num(r.sl, 17),
                num(r.rows, 0),
                r.haups,
                num(r.medianMs),
                `<button class="button open-run" data-id="${esc(r.id)}">Mở kết quả</button>`,
              ]),
            ),
            'Bảng · Hồ sơ thực nghiệm',
          )
        : '<p class="subtle">Chưa có hồ sơ. Lượt hoàn tất sẽ được lưu trong results/runs.</p>',
    );
    document.querySelectorAll('.open-run').forEach((button) => {
      button.disabled = state.busy;
      button.onclick = async () => {
        try {
          const c = await request('/api/runs/' + button.dataset.id);
          c.archived = true;
          state.current = c;
          const selected =
            c.source === 'Bài báo · Tables 2–3'
              ? 'paper'
              : state.datasets.find((d) => d.name === c.source)?.id || 'upload';
          $('source').value = selected;
          sourceChanged();
          $('su').value = c.report.upperThreshold;
          $('sl').value = c.report.lowerThreshold;
          $('threshold-mode').value = 'custom';
          applyThresholdMode();
          $('repeats').value = c.manifest?.measuredRuns || 1;
          $('size-percent').value = c.manifest?.sizePercent || 100;
          $('format').value = c.manifest?.format || 'transactions';
          $('compare').checked = !!c.manifest?.comparison;
          c.configuration = {
            source: selected,
            su: c.report.upperThreshold,
            sl: c.report.lowerThreshold,
            format: $('format').value,
            fileModified: 0,
            sizePercent: c.manifest?.sizePercent || 100,
          };
          thresholdLabels();
          state.batchIndex = c.report.batches.length - 1;
          state.patternType = 'ALL';
          state.patternSearch = '';
          state.patternPage = 0;
          $('export-zip').disabled = false;
          $('export').disabled = true;
          $('export-txt').disabled = true;
          location.hash = 'overview';
        } catch (error) {
          setError(error);
        }
      };
    });
  } catch (error) {
    setError(error);
  }
}
async function initialize() {
  navigate();
  try {
    state.datasets = await request('/api/datasets');
    for (const d of state.datasets) {
      const option = document.createElement('option');
      option.value = d.id;
      option.textContent =
        d.name.replace(/-metadata/i, '') + ' · ' + num(d.bytes / 1048576) + ' MiB';
      $('source').append(option);
    }
    if (state.page === 'datasets') renderDatasets();
  } catch (error) {
    setError(error);
  }
}
sourceChanged();
initialize();
