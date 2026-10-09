const paperSample = `upper=0.23
lower=0.10

external:
A=2 B=5 C=1 D=3 E=6 F=4

batch DB0
T1: A:2 B:1 C:3
T2: C:1 D:2
T3: A:6 B:1 C:4 E:5
T4: A:4 B:4 E:3
T5: B:1 E:2 F:2

batch DB1
T6: A:3 F:3
T7: C:2 E:2

batch DB2
T8: B:5 C:1 D:3 E:2
T9: A:1 B:3 D:1`;

let inputData = paperSample;
let sourceName = "paper-example.txt";
let currentReport = null;
let analyzedInput = null;
let selectedBatch = 0;
let selectedDiagramBatch = 0;
let diagramPatterns = [];
let selectedDiagramPattern = 0;
let selectedDiagramGroup = null;
let diagramChanges = [];
let diagramChangeFilter = "changed";
let diagramChangeLimit = 12;
let patternFilter = "ALL";
let patternPage = 0;
let archiveAudit = null;
let largeFiles = [];
let largeHistory = [];
const $ = id => document.getElementById(id);
const format = (n, digits = 3) => Number(n).toLocaleString("vi-VN", { maximumFractionDigits: digits, minimumFractionDigits: digits });
let activeView = "overview";
const count = n => Number(n).toLocaleString("vi-VN");
const number = n => Number(n).toLocaleString("vi-VN", {maximumFractionDigits:3});
const html = s => String(s).replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"})[c]);
const ms = n => n == null || !Number.isFinite(Number(n)) ? "Không khả dụng" : `${format(n)} ms`;
const mb = n => n == null || !Number.isFinite(Number(n)) ? "Không khả dụng" : `${format(n)} MiB`;
function boundVerdict(report) {
  const last = report.batches.at(-1);
  const strongest = report.upperBounds[0];
  const bound = strongest?.auub ?? 0;
  const belowLower = !strongest || bound < last.minUtilLower;
  const belowUpper = !strongest || bound < last.minUtilUpper;
  const title = belowLower ? "Cận trên loại tất cả ứng viên" : belowUpper ? "Không thể có HAUP ở ngưỡng Su này" : "Đã khai phá và phân loại theo AU";
  const explanation = belowLower
    ? `AUUB lớn nhất ${number(bound)} < Lower ${number(last.minUtilLower)}. Vì AU(P) không vượt AUUB của item trong P, không mẫu nào đạt PRE-LARGE hay LARGE. “0 mẫu duyệt” nghĩa là thuật toán cắt tỉa sớm, không phải không đọc dữ liệu.`
    : belowUpper
      ? `AUUB lớn nhất ${number(bound)} < Upper ${number(last.minUtilUpper)}. Không mẫu nào đạt LARGE/HAUP; ứng viên PRE-LARGE vẫn có thể được quản lý.`
      : `AUUB lớn nhất ${number(bound)} chưa đủ để kết luận rỗng. Thuật toán đã xét ${count(report.stats.patternsVisited)} mẫu và tìm ${last.largePatterns.length} HAUP cuối.`;
  return {title, explanation, bound, item:strongest?.item || "—", belowLower, belowUpper};
}
document.querySelectorAll(".nav-tab").forEach(button => button.addEventListener("click", () => showView(button.dataset.view)));
function showView(view) {
  activeView = view;
  const introductions = {
    overview: ["01 / TỔNG QUAN", "Kết luận của lượt phân tích", "Xem kết quả chính trước, sau đó đối chiếu số liệu và ngưỡng đã sử dụng."],
    steps: ["02 / PHÉP TÍNH", "Từ giao dịch đến quyết định re-scan", "Theo dõi DB0 → DB1 → DB2; Bước 1–5 ghi rõ công thức, số đã thế và ý nghĩa."],
    diagram: ["03 / SƠ ĐỒ", "Tight Re-Scan Condition và kết quả theo batch", "Đọc quyết định re-scan và thay đổi LARGE/PRE-LARGE; mở bản đồ tra cứu khi cần xem từng mẫu."],
    patterns: ["04 / KẾT QUẢ", "Các mẫu được giữ ở batch cuối", "Đối chiếu AU của mẫu với Upper và Lower để phân biệt LARGE, PRE-LARGE và trường hợp không có mẫu."],
    resources: ["05 / TÀI NGUYÊN", "Chi phí của lượt phân tích", "Xem thời gian từng giai đoạn, bộ nhớ heap Java và khâu nên ưu tiên tối ưu."],
    datasets: ["06 / THỰC NGHIỆM", "Kiểm tra trên tệp dữ liệu lớn", "Chạy từng tệp transactions.txt độc lập với Su/Sl đã chọn; đọc kết luận trước rồi xem chi phí xử lý."],
    glossary: ["07 / THUẬT NGỮ", "Đọc đúng ký hiệu trong bài báo", "Tra cứu Table 4 và những thuật ngữ của PIHAUP-List, Tight Re-Scan Condition, PatternExpansion và thực nghiệm."]
  };
  const [kicker, title, description] = introductions[view] || introductions.overview;
  const readingOrder = {
    overview: ["Kết luận", "Chỉ số chính", "Số liệu đối chứng"],
    steps: ["Dữ liệu đã đọc", "Chọn DB0–DB2", "Theo phép tính Bước 1–5"],
    diagram: ["Chọn DB", "Đọc quyết định", "So mẫu trước → sau", "Mở sơ đồ nếu cần"],
    patterns: ["Điều kiện ngưỡng", "Ví dụ tính AU", "Danh sách mẫu"],
    resources: ["Tổng thời gian", "Phân rã Bước 1–3", "Bộ nhớ và hướng tối ưu"],
    datasets: ["Chọn tệp TXT", "Đặt Su/Sl và chạy", "Đọc kết luận, rồi xem log"],
    glossary: ["Xem bản đồ khái niệm", "Tìm ký hiệu", "Đọc công thức và ví dụ"]
  };
  $("viewKicker").textContent = kicker;
  $("viewTitle").textContent = title;
  $("viewDescription").textContent = description;
  $("readingGuide").innerHTML = readingOrder[view].map((label, index) => `<span><b>${index + 1}</b>${label}</span>`).join("");
  document.querySelectorAll(".nav-tab").forEach(b => {
    b.classList.toggle("active", b.dataset.view === view);
    b.setAttribute("aria-current", b.dataset.view === view ? "page" : "false");
  });
  $("archiveResults").hidden = true;
  const largeReportOpen = !!analyzedInput?.dataset;
  $("standardControls").hidden = view === "datasets" || view === "glossary" || largeReportOpen;
  $("reportContext").hidden = view === "datasets" || view === "glossary" || !largeReportOpen;
  if (largeReportOpen) $("reportContextText").textContent = `${analyzedInput.source} · Su ${format(analyzedInput.su,6)} · Sl ${format(analyzedInput.sl,6)}`;
  $("largeData").hidden = view !== "datasets";
  $("glossaryPanel").hidden = view !== "glossary";
  $("largePlaceholder").hidden = $("largeDetail").childElementCount > 0;
  $("dashboard").hidden = !currentReport || view === "datasets" || view === "glossary";
  $("emptyState").hidden = view === "datasets" || view === "glossary" || !!currentReport;
  $("emptyState").dataset.view = view;
  $("summaryCards").hidden = view !== "overview";
  $("overviewInsight").hidden = view !== "overview";
  $("numbersPanel").hidden = view !== "overview";
  $("resourcePanel").hidden = view !== "resources";
  $("optimizationPanel").hidden = view !== "resources";
  $("flowPanel").hidden = view !== "steps";
  $("methodPanel").hidden = view !== "steps";
  document.querySelector(".batches-panel").hidden = view !== "steps";
  $("diagramPanel").hidden = view !== "diagram";
  document.querySelector(".patterns-panel").hidden = view !== "patterns";
  if (view === "diagram" && currentReport) renderDiagram();
  if (view === "glossary") renderGlossary();
  updateEmptyState();
}

function updateEmptyState() {
  $("emptySource").textContent = sourceName;
  $("emptyThreshold").textContent = `Su ${format($("su").value, 6)} · Sl ${format($("sl").value, 6)}`;
}

$("folderButton").addEventListener("click", () => $("folderInput").click());
$("returnStandardButton").addEventListener("click", () => { invalidateReport(); showView("overview"); setStatus("idle", "Cấu hình lượt chạy thường đã sẵn sàng."); });
$("largeFileButton").addEventListener("click", () => $("largeFileInput").click());
$("folderInput").addEventListener("change", e => selectLargeFiles(e.target.files));
$("largeFileInput").addEventListener("change", e => selectLargeFiles(e.target.files));
$("runLargeButton").addEventListener("click", runLargeDataset);
$("largeExportTxtButton").addEventListener("click", () => exportReport("txt"));
$("largeExportCsvButton").addEventListener("click", () => exportReport("csv"));
$("largeSu").addEventListener("input", invalidateLargeThreshold);
$("largeSl").addEventListener("input", invalidateLargeThreshold);
function invalidateLargeThreshold() {
  if (analyzedInput?.dataset) invalidateReport();
  else {
    $("largeDetail").replaceChildren();
    $("largePlaceholder").hidden = false;
    $("largeOptimization").hidden = true;
    $("largeOptimizationAdvice").replaceChildren();
  }
  $("largeExportTxtButton").disabled = true;
  $("largeExportCsvButton").disabled = true;
  setStatus("idle", "Đã đổi ngưỡng dữ liệu lớn; hãy chạy lại để tính kết quả mới.");
}
$("largeDatasetSelect").addEventListener("change", () => {
  updateDatasetName();
  invalidateReport();
  setStatus("idle", "Đã đổi bộ dữ liệu; hãy chạy để tính với Su/Sl hiện tại.");
  showView("datasets");
});
function updateDatasetName() {
  const file = largeFiles[Number($("largeDatasetSelect").value)];
  if (!file) return;
  $("datasetName").value = file.webkitRelativePath?.split("/").at(-2)?.replace(/-metadata$/i, "") ||
    (file.name.toLowerCase() === "transactions.txt" ? "" : file.name.replace(/\.txt$/i, ""));
}
function selectLargeFiles(files) {
  invalidateReport();
  largeFiles = [...files].filter(f => f.name.toLowerCase() === "transactions.txt" ||
    (!f.webkitRelativePath && f.name.toLowerCase().endsWith(".txt") && f.name.toLowerCase() !== "investment_table.txt"));
  const select = $("largeDatasetSelect");
  select.replaceChildren();
  largeFiles.forEach((file, index) => {
    const option = document.createElement("option");
    option.value = String(index);
    option.textContent = `${file.webkitRelativePath || file.name} · ${format(file.size / 1048576, 1)} MiB`;
    select.append(option);
  });
  $("runLargeButton").disabled = !largeFiles.length;
  updateDatasetName();
  $("largeSourceNote").textContent = largeFiles.length ?
    `Đã nhận ${largeFiles.length} tệp giao dịch. Chọn bộ và đặt Su/Sl riêng cho lượt chạy; không dùng ngưỡng cố định.` :
    "Không tìm thấy transactions.txt. Hãy chọn thư mục đã giải nén hoặc tệp giao dịch TXT.";
  $("largeDetail").replaceChildren();
  largeHistory = [];
  $("largeHistory").replaceChildren();
  showView("datasets");
  setStatus("idle", largeFiles.length ? `Đã chọn ${largeFiles.length} tệp giao dịch; đặt Su/Sl rồi chạy.` : "Chưa có tệp giao dịch hợp lệ.");
}

async function runLargeDataset() {
  const file = largeFiles[Number($("largeDatasetSelect").value)];
  if (!file) return;
  const su = Number($("largeSu").value), sl = Number($("largeSl").value);
  if (!(sl > 0 && sl < su && su <= 1)) return setStatus("error", "Cần 0 < Sl < Su ≤ 1");
  const dataset = $("datasetName").value.trim() ||
    file.webkitRelativePath?.split("/").at(-2)?.replace(/-metadata$/i, "") || file.name;
  const job = crypto.randomUUID();
  invalidateReport();
  $("runLargeButton").disabled = true;
  setStatus("running", `Đang đọc ${dataset}…`);
  const poll = async () => {
    try {
      const response = await fetch(`/api/progress?job=${encodeURIComponent(job)}`);
      const progress = await response.json();
      $("largeLog").textContent = (progress.lines || []).join("\n");
      if (progress.lines?.length && $("status").dataset.kind === "running")
        setStatus("running", progress.lines.at(-1));
    } catch { /* main request reports errors */ }
  };
  const timer = setInterval(poll, 800);
  try {
    const query = new URLSearchParams({su, sl, dataset, job, source:`${dataset}/transactions.txt`, inputFormat:"transactions"});
    const response = await fetch(`/api/analyze?${query}`, {method:"POST", headers:{"Content-Type":"text/plain;charset=utf-8"}, body:file});
    if (!response.ok) throw new Error(await apiError(response));
    const report = validateReport(await response.json());
    if (Number($("largeSu").value) !== su || Number($("largeSl").value) !== sl ||
        largeFiles[Number($("largeDatasetSelect").value)] !== file)
      throw new Error("Dữ liệu hoặc ngưỡng đã thay đổi trong lúc chạy. Hãy chạy lại để nhận kết quả đúng cấu hình hiện tại.");
    currentReport = report;
    selectedBatch = currentReport.batches.length - 1;
    selectedDiagramBatch = Math.min(1, currentReport.batches.length - 1);
    analyzedInput = {data:file, source:`${dataset}/transactions.txt`, dataset, inputFormat:"transactions", su, sl, exportId:report.exportId};
    await poll();
    $("exportTxtButton").disabled = false;
    $("exportCsvButton").disabled = false;
    $("largeExportTxtButton").disabled = false;
    $("largeExportCsvButton").disabled = false;
    render();
    const last = currentReport.batches.at(-1);
    const finding = boundVerdict(currentReport), measurement = currentReport.measurement || {};
    $("largeDetail").innerHTML = `<div class="large-summary">
      <div class="eyebrow">KẾT QUẢ LƯỢT CHẠY</div>
      <h3>${html(dataset)} <small>Su ${format(su,6)} · Sl ${format(sl,6)}</small></h3>
      <p>${html(finding.explanation)}</p>
      <div class="large-verification"><strong>Đã kiểm tra dữ liệu đầu vào</strong><span>${count(currentReport.transactionCount)} giao dịch từ ${html(file.name)}; TU từng dòng khớp tổng utility item trong sai số cho phép. Nếu vượt mức sai số, chương trình dừng trước khi khai phá.</span></div>
      <div class="evidence-line"><span>AUUB lớn nhất<b>${number(finding.bound)}</b></span><span>Lower<b>${number(last.minUtilLower)}</b></span><span>Upper<b>${number(last.minUtilUpper)}</b></span></div>
      <h4>Bước 1–5 · Dữ liệu và quyết định</h4>
      <div class="large-facts">
        <article><small>Bước 1 · Đầu vào</small><strong>${count(currentReport.transactionCount)} giao dịch</strong><span>${count(currentReport.upperBounds.length)} item · tệp ${format(file.size/1048576,2)} MiB</span></article>
        <article><small>Bước 2 · TU và ngưỡng</small><strong>TU(DB) ${number(last.totalTransactionUtility)}</strong><span>Upper = Su×TU; Lower = Sl×TU</span></article>
        <article><small>Bước 3 · Re-scan</small><strong>${currentReport.stats.rescanCount} lần</strong><span>Quyết định theo Eq.(12) ở từng batch</span></article>
        <article><small>Bước 4 · Cận trên và khai phá</small><strong>${number(finding.bound)}</strong><span>Item ${html(finding.item)} · ${count(currentReport.stats.patternsVisited)} mẫu duyệt</span></article>
        <article><small>Bước 5 · Mẫu cuối</small><strong>${last.largePatterns.length} HAUP</strong><span>${last.preLargePatterns.length} PRE-LARGE đang quản lý</span></article>
      </div>
      <h4>Chi phí của lượt chạy</h4>
      <div class="large-facts">
        <article><small>Đọc TXT</small><strong>${ms(measurement.importMs)}</strong></article>
        <article><small>Thuật toán</small><strong>${ms(currentReport.elapsedMs)}</strong></article>
        <article><small>Tổng ở server</small><strong>${ms(measurement.requestMs)}</strong></article>
        <article><small>CPU luồng xử lý</small><strong>${ms(measurement.threadCpuMs)}</strong></article>
        <article><small>Heap sau đọc</small><strong>${mb(measurement.heapAfterInputMb)}</strong></article>
        <article><small>Heap peak quan sát</small><strong>${mb(measurement.heapPeakObservedMb ?? currentReport.peakMemoryMb)}</strong></article>
      </div>
      <p class="small-note">Tổng server gồm nhận request nhưng chưa gồm gửi response và dựng giao diện. CPU không phải %CPU toàn máy; heap là mẫu đo bộ nhớ JVM, không phải RAM toàn hệ thống. Xem tab “Phép tính theo batch” để kiểm tra DB0–DB2.</p>
    </div>`;
    $("largePlaceholder").hidden = true;
    $("largeOptimizationAdvice").innerHTML = $("optimizationAdvice").innerHTML;
    $("largeOptimization").hidden = false;
    largeHistory.push({dataset, su, sl, rows:currentReport.transactionCount,
      upper:last.minUtilUpper, lower:last.minUtilLower, haup:last.largePatterns.length,
      pre:last.preLargePatterns.length, rescan:currentReport.stats.rescanCount,
      visited:currentReport.stats.patternsVisited, ms:currentReport.elapsedMs,
      importMs:measurement.importMs, requestMs:measurement.requestMs,
      cpuMs:measurement.threadCpuMs, heapAfterInputMb:measurement.heapAfterInputMb,
      mb:measurement.heapPeakObservedMb ?? currentReport.peakMemoryMb});
    renderLargeHistory();
    showView("datasets");
    setStatus("success", `Hoàn tất ${dataset}`);
  } catch (error) { await poll(); setStatus("error", error.message); }
  finally { clearInterval(timer); $("runLargeButton").disabled = false; }
}

function renderLargeHistory() {
  $("largeHistory").innerHTML = `<section class="history"><h3>Các lượt chạy đã đo trong phiên này</h3><p>Mỗi thẻ dùng ngưỡng riêng; số đo có thể biến động giữa các lần chạy.</p><div class="run-list">${largeHistory.map((r,i)=>`<article class="run-card"><h4>${i+1}. ${html(r.dataset)} <small>Su ${format(r.su,6)} · Sl ${format(r.sl,6)}</small></h4><div class="run-facts"><span>Giao dịch<b>${count(r.rows)}</b></span><span>Upper / Lower<b>${number(r.upper)} / ${number(r.lower)}</b></span><span>HAUP / PRE<b>${r.haup} / ${r.pre}</b></span><span>Re-scan · mẫu duyệt<b>${r.rescan} · ${count(r.visited)}</b></span><span>Đọc TXT<b>${ms(r.importMs)}</b></span><span>Thuật toán<b>${ms(r.ms)}</b></span><span>Tổng server<b>${ms(r.requestMs)}</b></span><span>CPU luồng<b>${ms(r.cpuMs)}</b></span><span>Heap sau đọc<b>${mb(r.heapAfterInputMb)}</b></span><span>Heap peak<b>${mb(r.mb)}</b></span></div></article>`).join("")}</div></section>`;
}

$("chooseFileButton").addEventListener("click", () => $("fileInput").click());
$("emptyChooseButton").addEventListener("click", () => $("fileInput").click());
$("emptyRunButton").addEventListener("click", run);

$("sampleButton").addEventListener("click", () => {
  inputData = paperSample;
  sourceName = "paper-example.txt";
  $("fileInput").value = "";
  $("fileName").textContent = "paper-example.txt";
  $("su").value = "0.23"; $("sl").value = "0.10";
  $("runButton").querySelector("span").textContent = "Chạy PIHAUPA";
  $("archiveProgress").hidden = true;
  invalidateReport();
  setStatus("idle", "Đã nạp dữ liệu bài báo");
});

$("fileInput").addEventListener("change", event => {
  const file = event.target.files[0];
  if (!file) return;
  if (!file.name.toLowerCase().endsWith(".txt")) return setStatus("error", "Vui lòng chọn tệp .txt.");
  inputData = file;
  sourceName = file.name;
  $("fileName").textContent = file.name;
  invalidateReport();
  $("runButton").querySelector("span").textContent = "Chạy PIHAUPA";
  showView("overview");
  $("archiveProgress").hidden = true;
  setStatus("idle", `Đã chọn ${file.name} · ${format(file.size / 1048576, 1)} MiB`);
});

$("runButton").addEventListener("click", run);
$("analyzeDatasetButton").addEventListener("click", runArchiveDataset);
$("exportTxtButton").addEventListener("click", () => exportReport("txt"));
$("exportCsvButton").addEventListener("click", () => exportReport("csv"));
$("su").addEventListener("input", invalidateReport);
$("sl").addEventListener("input", invalidateReport);
document.querySelectorAll(".filter").forEach(button => button.addEventListener("click", () => {
  document.querySelectorAll(".filter").forEach(x => x.classList.remove("active"));
  button.classList.add("active"); patternFilter = button.dataset.filter; patternPage = 0; renderPatterns();
}));

async function run() {
  const su = Number($("su").value), sl = Number($("sl").value);
  if (!(sl > 0 && sl < su && su <= 1)) return setStatus("error", "Cần 0 < Sl < Su ≤ 1");
  const source = inputData;
  const name = sourceName;
  invalidateReport();
  $("runButton").disabled = true;
  $("emptyRunButton").disabled = true;
  setStatus("running", "Đang chạy PIHAUPA Tight…");
  try {
    const inputFormat = source instanceof File && source.name.toLowerCase() === "transactions.txt" ? "transactions" : "standard";
    const query = new URLSearchParams({su, sl, inputFormat, source:name});
    const response = await fetch(`/api/analyze?${query}`, {method:"POST",headers:{"Content-Type":"text/plain;charset=utf-8"},body:source});
    if (!response.ok) throw new Error(await apiError(response));
    if (!response.headers.get("content-type")?.includes("application/json"))
      throw new Error("Server trả sai định dạng. Hãy đóng server cũ và chạy lại ứng dụng web.");
    const data = await response.json();
    if (inputData !== source || Number($("su").value) !== su || Number($("sl").value) !== sl) return;
    currentReport = validateReport(data); selectedBatch = currentReport.batches.length - 1; selectedDiagramBatch = Math.min(1, currentReport.batches.length - 1);
    analyzedInput = { data: source, source: name, inputFormat, su, sl, exportId:currentReport.exportId };
    $("exportTxtButton").disabled = false;
    $("exportCsvButton").disabled = false;
    render(); showView("overview"); setStatus("success", "Phân tích hoàn tất");
  } catch (error) { setStatus("error", error.message); }
  finally { $("runButton").disabled = false; $("emptyRunButton").disabled = false; }
}

function invalidateReport() {
  currentReport = null;
  diagramPatterns = [];
  selectedDiagramPattern = 0;
  selectedDiagramGroup = null;
  diagramChanges = [];
  diagramChangeFilter = "changed";
  diagramChangeLimit = 12;
  archiveAudit = null;
  analyzedInput = null;
  $("exportTxtButton").disabled = true;
  $("exportCsvButton").disabled = true;
  $("largeExportTxtButton").disabled = true;
  $("largeExportCsvButton").disabled = true;
  $("dashboard").hidden = true;
  $("archiveResults").hidden = true;
  $("emptyState").hidden = activeView === "datasets" || activeView === "glossary";
  $("datasetDetail").replaceChildren();
  $("largeDetail").replaceChildren();
  $("largePlaceholder").hidden = false;
  $("largeOptimization").hidden = true;
  $("largeOptimizationAdvice").replaceChildren();
  updateEmptyState();
}

async function inspectArchive(su, sl) {
  const source = inputData;
  invalidateReport();
  $("runButton").disabled = true;
  $("archiveProgress").hidden = false;
  setStatus("running", "Đang đọc và kiểm tra 6 bộ dữ liệu…");
  const job = crypto.randomUUID();
  const poll = async () => {
    try {
      const response = await fetch(`/api/progress?job=${encodeURIComponent(job)}`);
      if (!response.ok) return;
      const progress = await response.json();
      const lines = progress.lines || [];
      if (lines.length) {
        $("archiveProgressText").textContent = lines.at(-1);
        $("archiveLog").textContent = lines.join("\n");
      }
    } catch { /* Analysis response still reports any actionable error. */ }
  };
  const timer = setInterval(poll, 800);
  try {
    const response = await fetch(`/api/archive/inspect?job=${encodeURIComponent(job)}`, {
      method: "POST", headers: {"Content-Type": "application/zip"}, body: source
    });
    if (!response.ok) throw new Error(await apiError(response));
    if (inputData !== source || Number($("su").value) !== su || Number($("sl").value) !== sl) return;
    archiveAudit = await response.json();
    await poll();
    renderArchiveAudit(archiveAudit.datasets, su, sl);
    showView("datasets");
    setStatus("success", `Đã kiểm tra ${archiveAudit.datasets.length} bộ dữ liệu`);
  } catch (error) {
    await poll();
    setStatus("error", error.message);
  } finally {
    clearInterval(timer);
    $("runButton").disabled = false;
  }
}

function renderArchiveAudit(datasets, su, sl) {
  const body = $("archiveTable");
  body.replaceChildren();
  let certified = 0;
  for (const dataset of datasets) {
    const valid = dataset.mismatchedTransactionUtilities === 0 && dataset.missingInvestmentItems === 0;
    const noHaup = valid && dataset.strongestUpperBound < su * dataset.totalTransactionUtility;
    if (noHaup) certified++;
    const row = document.createElement("tr");
    const values = [dataset.name, count(dataset.rows), count(dataset.items),
      dataset.mismatchedTransactionUtilities, format(dataset.totalTransactionUtility, 0),
      format(dataset.totalMaximumUtility, 0),
      `${dataset.strongestUpperBoundItem}: ${format(dataset.strongestUpperBound, 0)}`,
      format(su * dataset.totalTransactionUtility, 0),
      !valid ? "Dữ liệu chưa hợp lệ" : noHaup ? "Không có HAUP (cận trên)" : "Cần khai phá tiếp"];
    for (const value of values) {
      const cell = document.createElement("td");
      cell.textContent = value;
      row.append(cell);
    }
    body.append(row);
  }
  $("archiveConclusion").textContent = `Đã đối chiếu ${datasets.length} thư mục. Với Su=${su} và Sl=${sl}, ${certified}/${datasets.length} bộ được chứng minh không có HAUP cuối: AU(P) ≤ AUUB(item) < Su×TU(DB). Đây là kiểm tra kết quả cuối, không thay cho bảng PRE-LARGE và re-scan theo batch.`;
  $("archiveCharts").innerHTML = datasets.map(d => {
    const ratio = d.totalTransactionUtility > 0 ? d.strongestUpperBound / (su * d.totalTransactionUtility) : 0;
    return `<div class="chart-row"><strong>${html(d.name)}</strong><span class="chart-track"><i style="width:${Math.min(100, Math.max(0, ratio * 100))}%"></i></span><span>${format(ratio * 100, 2)}% cận Su×TU</span></div>`;
  }).join("");
  const select = $("datasetSelect");
  select.replaceChildren();
  for (const dataset of datasets) {
    const option = document.createElement("option");
    option.value = dataset.name;
    option.textContent = `${dataset.name} · ${dataset.rows.toLocaleString("vi-VN")} giao dịch`;
    select.append(option);
  }
}

async function runArchiveDataset() {
  if (!archiveAudit) return;
  const source = inputData;
  const name = sourceName;
  const dataset = $("datasetSelect").value;
  const su = Number($("largeSu").value), sl = Number($("largeSl").value);
  if (!(sl > 0 && sl < su && su <= 1)) return setStatus("error", "Cần 0 < Sl < Su ≤ 1");
  const job = crypto.randomUUID();
  $("runButton").disabled = true;
  $("analyzeDatasetButton").disabled = true;
  $("exportTxtButton").disabled = true;
  $("exportCsvButton").disabled = true;
  $("largeExportTxtButton").disabled = true;
  $("largeExportCsvButton").disabled = true;
  $("archiveProgress").hidden = false;
  setStatus("running", `Đang phân tích ${dataset}…`);
  const poll = async () => {
    try {
      const response = await fetch(`/api/progress?job=${encodeURIComponent(job)}`);
      const progress = await response.json();
      if (progress.lines?.length) {
        $("archiveProgressText").textContent = progress.lines.at(-1);
        $("archiveLog").textContent = progress.lines.join("\n");
      }
    } catch { /* The analysis response provides errors. */ }
  };
  const timer = setInterval(poll, 800);
  try {
    const query = new URLSearchParams({su, sl, dataset, job, source:`${name} · ${dataset}`});
    const response = await fetch(`/api/analyze?${query}`, {
      method: "POST", headers: {"Content-Type": "application/zip"}, body: source
    });
    if (!response.ok) throw new Error(await apiError(response));
    if (inputData !== source || Number($("largeSu").value) !== su || Number($("largeSl").value) !== sl) return;
    currentReport = validateReport(await response.json());
    selectedBatch = currentReport.batches.length - 1;
    selectedDiagramBatch = Math.min(1, currentReport.batches.length - 1);
    analyzedInput = {data: source, source: `${name} · ${dataset}`, dataset, su, sl, exportId:currentReport.exportId};
    await poll();
    render();
    const audit = archiveAudit.datasets.find(d => d.name === dataset);
    $("datasetDetail").innerHTML = `<h3>Đã chạy ${html(dataset)} · các bước 1–5</h3><p>Bước 1: ${count(audit.rows)} giao dịch, ${count(audit.items)} item, TU sai = ${audit.mismatchedTransactionUtilities}. Bước 2: TU(DB) = ${format(audit.totalTransactionUtility,0)}, Upper = ${format(su * audit.totalTransactionUtility,0)}, Lower = ${format(sl * audit.totalTransactionUtility,0)}. Bước 3: ${currentReport.stats.rescanCount} lần re-scan. Bước 4: AUUB lớn nhất ${format(audit.strongestUpperBound,0)} (${html(audit.strongestUpperBoundItem)}). Bước 5: ${currentReport.batches.at(-1).largePatterns.length} HAUP cuối, ${format(currentReport.elapsedMs)} ms, heap peak quan sát ${format(currentReport.peakMemoryMb)} MB.</p><p>Chi tiết phép tính theo từng DB xem tab “Chạy từng bước”.</p>`;
    showView("datasets");
    $("exportTxtButton").disabled = false;
    $("exportCsvButton").disabled = false;
    $("largeExportTxtButton").disabled = false;
    $("largeExportCsvButton").disabled = false;
    setStatus("success", `Phân tích ${dataset} hoàn tất`);
  } catch (error) {
    await poll();
    setStatus("error", error.message);
  } finally {
    clearInterval(timer);
    $("runButton").disabled = false;
    $("analyzeDatasetButton").disabled = false;
  }
}

async function exportReport(format) {
  if (!analyzedInput) return;
  const snapshot = analyzedInput;
  $("exportTxtButton").disabled = true;
  $("exportCsvButton").disabled = true;
  $("largeExportTxtButton").disabled = true;
  $("largeExportCsvButton").disabled = true;
  $("runButton").disabled = true;
  setStatus("running", `Đang tạo báo cáo ${format.toUpperCase()}…`);
  try {
    const query = new URLSearchParams({id:snapshot.exportId, format});
    const response = await fetch(`/api/export?${query}`, {method:"GET"});
    if (!response.ok) throw new Error(await apiError(response));
    const expectedType = format === "txt" ? "text/plain" : "text/csv";
    if (!response.headers.get("content-type")?.includes(expectedType))
      throw new Error("Server chưa hỗ trợ xuất TXT/CSV. Hãy đóng server cũ và chạy lại ứng dụng web.");
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement("a");
    link.href = url; link.download = `PIHAUPA-bao-cao.${format}`;
    document.body.append(link); link.click(); link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
    setStatus("success", `Đã tải báo cáo ${format.toUpperCase()}`);
  } catch (error) { setStatus("error", error.message); }
  finally {
    $("runButton").disabled = false;
    $("exportTxtButton").disabled = !analyzedInput;
    $("exportCsvButton").disabled = !analyzedInput;
    $("largeExportTxtButton").disabled = !analyzedInput?.dataset;
    $("largeExportCsvButton").disabled = !analyzedInput?.dataset;
  }
}

async function apiError(response) {
  if (response.status === 404) return "Không tìm thấy chức năng trên server đang chạy (404). Hãy đóng server cũ và mở lại bằng run-web.ps1.";
  const body = await response.text();
  try { return JSON.parse(body).error || `Lỗi HTTP ${response.status}`; }
  catch { return `Lỗi HTTP ${response.status}: ${body.slice(0, 140) || "không có chi tiết"}`; }
}

function validateReport(report) {
  if (report?.original || report?.tight) {
    throw new Error("Server PIHAUPA cũ vẫn đang chạy ở cổng này. Hãy đóng cửa sổ server cũ, chạy lại run-web.ps1 rồi tải lại trang.");
  }
  if (!report || typeof report.exportId !== "string" || !report.exportId ||
      !Array.isArray(report.batches) || !report.batches.length ||
      !report.stats || !Array.isArray(report.upperBounds) || !report.inputPreview || !report.measurement ||
      !report.batches.every(b => Array.isArray(b.largePatterns) && Array.isArray(b.preLargePatterns))) {
    throw new Error("UI và server PIHAUPA không cùng phiên bản. Hãy dừng server cũ, chạy lại run-web.ps1 và làm mới trang.");
  }
  return report;
}

function setStatus(kind, text) {
  const node = $("status");
  node.dataset.kind = kind;
  node.querySelector("span").textContent = text;
  if (activeView === "datasets") {
    const largeNode = $("largeStatus");
    largeNode.dataset.kind = kind;
    largeNode.querySelector("span").textContent = text;
  }
  $("emptyState").dataset.loading = kind === "running" ? "true" : "false";
  $("largePlaceholder").dataset.loading = kind === "running" ? "true" : "false";
  $("emptyStateLabel").textContent = kind === "running" ? "ĐANG PHÂN TÍCH" : kind === "error" ? "CẦN KIỂM TRA CẤU HÌNH" : "CHƯA CHẠY";
  $("emptyTitle").textContent = kind === "running" ? "Đang tính kết quả…" : kind === "error" ? "Chưa thể tạo báo cáo" : "Chưa có kết quả phân tích";
  $("emptyMessage").textContent = kind === "running" ? "Các khung đang chờ số liệu từ thuật toán." : kind === "error" ? text : "Chạy dữ liệu đã chọn để xem số liệu trong các khung bên dưới.";
}
function metric(label, value, note) { return `<article class="metric panel"><small>${label}</small><strong>${value}</strong><em>${note}</em></article>`; }

function render() {
  const n=currentReport, last=n.batches.at(-1);
  const verdict = boundVerdict(n);
  patternPage = 0;
  $("overviewVerdict").innerHTML = `<div class="verdict ${verdict.belowLower?'empty-verdict':''}"><div class="eyebrow">KẾT LUẬN CÓ ĐỐI CHIẾU</div><strong>${html(verdict.title)}</strong><p>${html(verdict.explanation)}</p><div class="evidence-line"><span>AUUB lớn nhất <b>${number(verdict.bound)}</b></span><span>Lower <b>${number(last.minUtilLower)}</b></span><span>Upper <b>${number(last.minUtilUpper)}</b></span></div><div class="report-provenance"><span><b>Nguồn</b>${html(analyzedInput?.source || sourceName)}</span><span><b>Quy mô</b>${count(n.transactionCount)} giao dịch · ${count(n.upperBounds.length)} item · ${n.batchCount} batch</span><span><b>Ngưỡng đã dùng</b>Su ${format(n.upperThreshold,6)} · Sl ${format(n.lowerThreshold,6)}</span></div></div>`;
  $("summaryCards").innerHTML = metric("Quy mô dữ liệu", count(n.transactionCount), `${n.batchCount} batch · ${count(n.upperBounds.length)} item`) + metric("Mẫu HAUP cuối", count(last.largePatterns.length), "AU ≥ Upper") + metric("Mẫu PRE-LARGE", count(last.preLargePatterns.length), "Lower ≤ AU < Upper; số đang quản lý");
  renderNumbers(n, verdict);
  renderResources(n);
  renderOptimization(n, verdict);
  renderBatchOverview(n);
  const flow = [
    ["Bước 1", "Đọc dữ liệu", `${count(n.transactionCount)} giao dịch · ${n.batchCount} batch`, "U(i,T) = IU(i,T) × EU(i)"],
    ["Bước 2", "Tính ngưỡng", `Upper ${format(last.minUtilUpper)} · Lower ${format(last.minUtilLower)}`, "Su × TU(DB); Sl × TU(DB)"],
    ["Bước 3", "Quyết định re-scan", `${n.stats.rescanCount} lần`, "Eq.(12) cho từng batch mới"],
    ["Bước 4", "Cận trên & khai phá", `${count(n.upperBounds.length)} AUUB · ${count(n.stats.patternsVisited)} mẫu`, "PIHAUP-List và pattern tree"],
    ["Bước 5", "Phân loại mẫu", `${last.largePatterns.length} LARGE · ${last.preLargePatterns.length} PRE-LARGE`, "AU(P) = sumU(P) / |P|"]
  ];
  $("flowGrid").innerHTML = flow.map(([letter,title,value,note]) => `<article class="flow-step"><span>${letter}</span><h3>${title}</h3><strong>${value}</strong><small>${note}</small></article>`).join("");
  renderInputPreview(n);
  $("batchTabs").innerHTML=n.batches.map((b,i)=>`<button class="batch-tab ${i===selectedBatch?'active':''}" data-index="${i}" role="tab">${html(b.name)}</button>`).join("");
  $("batchTabs").querySelectorAll("button").forEach(btn=>btn.addEventListener("click",()=>{selectedBatch=Number(btn.dataset.index);renderBatches();}));
  renderBatches(); renderPatterns(); showView(activeView);
}

function renderNumbers(report, verdict) {
  const last = report.batches.at(-1);
  const row = (label, formula, value, meaning) => `<tr><th scope="row">${label}</th><td>${formula}</td><td class="numeric-value">${value}</td><td>${meaning}</td></tr>`;
  const group = (title, rows) => `<tbody><tr class="number-group"><th colspan="4">${title}</th></tr>${rows.join("")}</tbody>`;
  const data = group("Bước 1 · Dữ liệu đã đọc", [
    row("Giao dịch", "Đếm toàn bộ T trong DB0…DB cuối", count(report.transactionCount), "Đã nạp và xử lý, không phải số mẫu khai phá"),
    row("Batch", "Số DB đầu vào", count(report.batchCount), "Các phần dữ liệu theo thời gian"),
    row("TU(DB)", "Σ TU(T) của các giao dịch", number(last.totalTransactionUtility), "Mẫu số để tính Upper và Lower")
  ]);
  const thresholds = group("Bước 2 · Ngưỡng và cận trên", [
    row("Su / Sl", "Tỷ lệ người dùng chọn", `${format(report.upperThreshold,6)} / ${format(report.lowerThreshold,6)}`, "Su phải lớn hơn Sl"),
    row("Upper", "Su × TU(DB)", number(last.minUtilUpper), "AU ≥ Upper → LARGE / HAUP"),
    row("Lower", "Sl × TU(DB)", number(last.minUtilLower), "Lower ≤ AU < Upper → PRE-LARGE"),
    row("AUUB lớn nhất", `Cận trên của item ${html(verdict.item)}`, number(verdict.bound), "Dùng để loại ứng viên trước khi tính AU chính xác")
  ]);
  const result = group("Bước 3 · Quyết định và kết quả", [
    row("So cận trên", "AUUB lớn nhất so với ngưỡng", `${number(verdict.bound)} ${verdict.belowLower ? "<" : verdict.belowUpper ? "<" : "≥"} ${number(verdict.belowLower ? last.minUtilLower : last.minUtilUpper)}`, verdict.belowLower ? "Không mẫu nào đạt Lower" : verdict.belowUpper ? "Không thể có HAUP ở Upper này" : "Cần khai phá để phân loại chính xác"),
    row("Mẫu duyệt", "Bộ đếm của thuật toán", count(report.stats.patternsVisited), "Có thể bằng 0 dù mọi giao dịch đã được đọc"),
    row("Re-scan", "Số lần quét lại theo Eq.(12)", count(report.stats.rescanCount), "Không tính quét khởi tạo DB0"),
    row("HAUP / PRE", "Trạng thái ở batch cuối", `${count(last.largePatterns.length)} / ${count(last.preLargePatterns.length)}`, "PRE là số mẫu đang được quản lý")
  ]);
  $("numbersTable").innerHTML = `<div class="numbers-table-wrap"><table class="numbers-table"><thead><tr><th>Chỉ số</th><th>Nguồn / công thức</th><th>Giá trị</th><th>Cách hiểu</th></tr></thead>${data}${thresholds}${result}</table></div>`;
}

function renderOptimization(report, verdict) {
  const m = report.measurement || {};
  const total = Number(m.requestMs) || 0;
  const importShare = total > 0 ? Number(m.importMs) / total : 0;
  const mineShare = total > 0 ? Number(m.algorithmMs) / total : 0;
  const buildShare = total > 0 ? Number(m.responseBuildMs) / total : 0;
  const advice = [];
  if (importShare >= mineShare && importShare >= buildShare) {
    advice.push(["Ưu tiên đọc dữ liệu", `Đọc và chuyển đổi chiếm ${format(importShare * 100,1)}% thời gian server (${ms(m.importMs)}). Với TXT lớn, ưu tiên parser đọc luồng và tránh tạo thêm bản sao toàn bộ tệp.`]);
  } else if (mineShare >= buildShare) {
    advice.push(["Ưu tiên khai phá", `Thuật toán chiếm ${format(mineShare * 100,1)}% thời gian server (${ms(m.algorithmMs)}). So sánh số mẫu duyệt và re-scan ở từng ngưỡng trước khi thay đổi cách cắt tỉa.`]);
  } else {
    advice.push(["Ưu tiên tạo kết quả", `Tạo JSON và bản xem trước chiếm ${format(buildShare * 100,1)}% thời gian server (${ms(m.responseBuildMs)}). Nên chỉ trả các mẫu của trang đang xem thay vì toàn bộ danh sách trong một response.`]);
  }
  if (verdict.belowLower && report.stats.patternsVisited === 0) {
    advice.push(["Cắt tỉa đã hoạt động", `AUUB lớn nhất ${number(verdict.bound)} thấp hơn Lower ${number(report.batches.at(-1).minUtilLower)}; 0 mẫu duyệt là kết quả cắt tỉa sớm. Nếu mục tiêu là tìm mẫu, hãy thử ngưỡng thấp hơn và ghi rõ đó là một lượt chạy khác.`]);
  } else {
    advice.push(["Giữ giao diện phản hồi nhanh", "Bảng mẫu trên web chỉ vẽ 50 hàng mỗi trang. Nếu số mẫu trả về rất lớn, bước tiếp theo là phân trang từ server để giảm kích thước JSON và bộ nhớ trình duyệt."]);
  }
  advice.push(["Đo đúng phạm vi", `Heap peak quan sát ${mb(m.heapPeakObservedMb ?? report.peakMemoryMb)} là số của JVM, không phải RAM trình duyệt. Muốn tối ưu thời gian tải và hiển thị web, cần đo thêm Network và Performance trong trình duyệt.`]);
  $("optimizationAdvice").innerHTML = `<p class="optimization-intro">Gợi ý dựa trên lượt chạy này; không phải cam kết tăng tốc nếu chưa benchmark lại cùng dữ liệu và ngưỡng.</p><div class="optimization-list">${advice.map(([title, detail], index) => `<article><span>${index + 1}</span><div><h3>${title}</h3><p>${detail}</p></div></article>`).join("")}</div>`;
}

function renderResources(report) {
  const m = report.measurement || {};
  const timeItems = [
    ["Đọc & chuyển đổi TXT", ms(m.importMs), "Gồm nhận nội dung request và tạo giao dịch/batch ở server"],
    ["Chạy PIHAUPA", ms(m.algorithmMs), `Xử lý các batch; thời gian lõi thuật toán ${ms(report.elapsedMs)}`],
    ["Chuẩn bị kết quả", ms(m.responseBuildMs), "Tạo dữ liệu JSON và bảng xem trước"],
    ["Tổng ở server", ms(m.requestMs), "Gồm đọc request; chưa tính gửi response và dựng giao diện"],
    ["CPU của luồng xử lý", ms(m.threadCpuMs), "Thời gian CPU của luồng request; không phải %CPU cả máy"]
  ];
  const memoryItems = [
    ["Heap trước đọc", mb(m.heapBeforeMb), "JVM heap đã dùng khi bắt đầu request"],
    ["Heap sau đọc", mb(m.heapAfterInputMb), "Gồm dữ liệu giao dịch đang giữ trong bộ nhớ"],
    ["Heap sau khai phá", mb(m.heapAfterMiningMb), "JVM heap sau khi thuật toán hoàn tất"],
    ["Heap peak quan sát", mb(m.heapPeakObservedMb ?? report.peakMemoryMb), "Lớn nhất trong các điểm lấy mẫu; không phải RAM/RSS cực đại"]
  ];
  const total = Number(m.requestMs) || 0;
  const phases = [
    ["Bước 1", "Nhận và đọc dữ liệu", "Nhận nội dung gửi lên, đọc TXT và tạo giao dịch/batch cho thuật toán.", m.importMs],
    ["Bước 2", "Chạy thuật toán PIHAUPA", "Khai phá mẫu qua các batch, xét ngưỡng và điều kiện re-scan.", m.algorithmMs],
    ["Bước 3", "Chuẩn bị kết quả", "Tạo báo cáo JSON và phần xem trước dữ liệu để gửi về web.", m.responseBuildMs]
  ];
  const validPhases = phases.filter(([, , , value]) => Number.isFinite(Number(value)) && Number(value) >= 0);
  const dominant = validPhases.reduce((best, phase) => !best || Number(phase[3]) > Number(best[3]) ? phase : best, null);
  const timeline = total > 0 ? `<section class="resource-timeline" aria-label="Phân rã thời gian trên server">
    <div class="time-intro"><div><h3>Thời gian xử lý được dùng ở đâu?</h3><p><b>Đo gì:</b> thời gian thực từ khi server nhận yêu cầu đến khi chuẩn bị xong câu trả lời. <b>Giúp gì:</b> chỉ ra bước tốn thời gian nhất để biết nên kiểm tra và tối ưu ở đâu.</p></div><div class="time-total"><span>Tổng thời gian tại server</span><strong>${ms(m.requestMs)}</strong><small>100% của lượt chạy này</small></div></div>
    <div class="time-stages">${phases.map(([step,label,description,value]) => { const share = Number.isFinite(Number(value)) ? Math.max(0, Number(value) / total * 100) : 0; return `<div class="time-stage"><div class="time-stage-top"><span class="time-step">${step}</span><div class="time-stage-copy"><strong>${label}</strong><p>${description}</p></div><div class="time-stage-value"><strong>${ms(value)}</strong><span>${format(share, 1)}% tổng thời gian</span></div></div><div class="time-track" role="meter" aria-label="${label}" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${Math.min(100, share).toFixed(1)}"><span style="width:${Math.min(100, share)}%"></span></div></div>`; }).join("")}</div>
    ${dominant ? `<p class="time-insight"><b>Nhận xét lượt chạy này:</b> ${dominant[1]} chiếm nhiều thời gian nhất (${format(Number(dominant[3]) / total * 100, 1)}%). Đây là bước nên xem xét trước nếu muốn giảm thời gian xử lý.</p>` : ""}
    <p class="time-scope"><b>Phạm vi đo:</b> ba bước trên là thời gian trôi qua, không cộng thêm “CPU của luồng xử lý” vì đó là một phép đo khác. Tổng tại server chưa tính thời gian truyền kết quả qua mạng hoặc trình duyệt hiển thị trang.</p>
  </section>` : "";
  const cards = values => values.map(([label,value,note])=>`<article><small>${label}</small><strong>${value}</strong><span>${note}</span></article>`).join("");
  $("resourceMetrics").innerHTML = `${timeline}<div class="measure-columns"><section><h3>Các mốc thời gian chi tiết</h3><div class="resource-grid">${cards(timeItems)}</div></section><section><h3>Bộ nhớ heap Java</h3><div class="resource-grid">${cards(memoryItems)}</div></section></div><p class="measurement-note"><b>Lưu ý khi đối chiếu:</b> thời gian và heap có thể thay đổi theo máy, dữ liệu, lần chạy và quá trình dọn bộ nhớ (GC). Heap là bộ nhớ JVM được lấy mẫu, không phải toàn bộ RAM/RSS hay điện năng tiêu thụ.</p>`;
}

function renderBatchOverview(report) {
  $("batchOverview").innerHTML = `<h3>Đường đi DB0 → DB1 → DB2</h3><p>Mỗi thẻ là trạng thái sau khi thêm batch. Chọn batch bên dưới để mở phép tính Bước 1–5.</p><div class="batch-route">${report.batches.map(b=>`<article><h4>${html(b.name)} <small>${b.index===0?'Khởi tạo':b.rescanTriggered?'Có re-scan':'Không re-scan'}</small></h4><dl><div><dt>Giao dịch mới / tích lũy</dt><dd>${count(b.transactionCount)} / ${count(b.cumulativeTransactionCount)}</dd></div><div><dt>TU / MU batch</dt><dd>${number(b.batchTransactionUtility)} / ${number(b.batchMaximumUtility)}</dd></div><div><dt>TU(DB)</dt><dd>${number(b.totalTransactionUtility)}</dd></div><div><dt>Upper / Lower</dt><dd>${number(b.minUtilUpper)} / ${number(b.minUtilLower)}</dd></div><div><dt>LARGE / PRE</dt><dd>${b.largePatterns.length} / ${b.preLargePatterns.length}</dd></div><div><dt>Thời gian / heap sau</dt><dd>${ms(b.elapsedMs)} / ${mb(b.memoryAfterMb)}</dd></div></dl></article>`).join("")}</div>`;
}

function renderInputPreview(report) {
  const preview = report.inputPreview;
  if (!preview) return;
  $("inputPreview").innerHTML = `<p>Hiển thị ${preview.items.length}/${count(preview.itemCount)} item và ${preview.transactions.length}/${count(report.transactionCount)} giao dịch đầu. Thuật toán đã tính trên toàn bộ dữ liệu.</p><h3>External utility</h3><div class="preview-tags">${preview.items.map(i=>`<span>${html(i.name)} · EU ${format(i.eu)}</span>`).join("")}</div><h3>Giao dịch</h3><div class="preview-table"><table><thead><tr><th>Batch</th><th>TID</th><th>Item : IU</th><th>TU(T)</th><th>MU(T)</th></tr></thead><tbody>${preview.transactions.map(t=>`<tr><td>${html(t.batch)}</td><td>${html(t.tid)}</td><td>${html(t.items)}</td><td>${format(t.tu)}</td><td>${format(t.mu)}</td></tr>`).join("")}</tbody></table></div>`;
}

function renderRescanFlow(batch, previous, initial, oldTU, left, right) {
  const currentCount = batch.largePatterns.length + batch.preLargePatterns.length;
  const previousPatterns = previous ? [...previous.largePatterns, ...previous.preLargePatterns] : [];
  const patternPreview = patterns => patterns.slice(0, 4).map(p => `<span>${html(p.name ?? p.items?.join(", ") ?? "Mẫu")} · AU ${number(p.averageUtility)}</span>`).join("");
  const examples = patternPreview(previousPatterns);
  const currentExamples = patternPreview([...batch.largePatterns, ...batch.preLargePatterns]);
  if (initial) return `<figure class="rescan-figure" aria-label="Sơ đồ khởi tạo DB0">
    <figcaption><strong>Initial scan · DB0</strong><span>Chưa có incremental database (ID), nên tight re-scan condition chưa được kiểm tra.</span></figcaption>
    <div class="rescan-initial"><div><small>ORIGINAL DATABASE · DB0</small><b>${count(batch.transactionCount)} giao dịch</b><span>TU(DB0) = ${number(batch.totalTransactionUtility)}</span></div><span class="rescan-arrow" aria-hidden="true">→</span><div><small>CONSTRUCT + RESTRUCTURE</small><b>Global PIHAUP-List (GL)</b><span>PatternExpansion(GL) → pattern tree (PT)</span></div><span class="rescan-arrow" aria-hidden="true">→</span><div><small>PATTERN TREE · PT</small><b>${count(currentCount)} LARGE/PRE-LARGE patterns</b><span>${count(batch.largePatterns.length)} LARGE · ${count(batch.preLargePatterns.length)} PRE-LARGE</span></div></div>
  </figure>`;
  const selected = batch.rescanTriggered ? "rebuild" : "update";
  const paperLimit = batch.tightRescanLimit == null ? null : Number(batch.tightRescanLimit);
  const paperComparison = Number.isFinite(paperLimit)
    ? `<p class="rescan-paper-equation"><b>Tight re-scan condition · Eq. (6):</b> MU(ID) = ${number(batch.accumulatedMaximumUtility)} ${batch.rescanTriggered ? "≥" : "<"} ${number(paperLimit)}. Dòng trên là dạng biến đổi Eq. (12) dùng để kiểm tra số học.</p>`
    : "";
  return `<figure class="rescan-figure" aria-label="Sơ đồ quyết định re-scan cho ${html(batch.name)}">
    <figcaption><strong>Tight re-scan condition · ${html(batch.name)}</strong><span>PT hiện có + incremental database (ID) → kiểm tra Eq. (6) → update PT hoặc re-scan OD ∪ ID.</span></figcaption>
    <div class="rescan-source">
      <div class="rescan-node"><small>PATTERN TREE HIỆN CÓ · PT</small><b>${count(previousPatterns.length)} LARGE/PRE-LARGE patterns</b><span>rTU = TU(OD) = ${number(oldTU)}</span>${examples ? `<div class="rescan-patterns">${examples}</div>` : ""}</div>
      <span class="rescan-plus" aria-hidden="true">+</span>
      <div class="rescan-node"><small>INCREMENTAL DATABASE · ID</small><b>${count(batch.transactionCount)} giao dịch trong ${html(batch.name)}</b><span>TU(${html(batch.name)}) = ${number(batch.batchTransactionUtility)} · MU(${html(batch.name)}) = ${number(batch.batchMaximumUtility)}</span><span>iTU = TU(ID) = ${number(batch.accumulatedTransactionUtility)} · MU(ID) = ${number(batch.accumulatedMaximumUtility)}</span></div>
    </div>
    <div class="rescan-down" aria-hidden="true">↓</div>
    <div class="rescan-rule"><div><small>TIGHT RE-SCAN CONDITION · EQ. (12)</small><strong>MU(ID) − S_u × TU(ID) ≥ (S_u − S_l) × TU(OD)</strong></div><div class="rescan-comparison"><b>${number(left)}</b><span>${batch.rescanTriggered ? "≥" : "<"}</span><b>${number(right)}</b><em class="${batch.rescanTriggered ? "is-rebuild" : "is-update"}">${batch.rescanTriggered ? "TRUE · RE-SCAN" : "FALSE · NO RE-SCAN"}</em></div>${paperComparison}</div>
    <div class="rescan-down" aria-hidden="true">↓</div>
    <div class="rescan-branches">
      <div class="rescan-branch ${selected === "update" ? "is-selected" : ""}"><small>CONDITION FALSE</small><b>Update existing PT</b><p>Scan DB_i và cập nhật utility của các node đã có trong PT; bỏ qua full PatternExpansion.</p><span>${selected === "update" ? "Nhánh thực hiện" : "Không thực hiện"}</span></div>
      <div class="rescan-branch ${selected === "rebuild" ? "is-selected is-rebuild" : ""}"><small>CONDITION TRUE</small><b>Reconstruct GL and PT</b><p>Full re-scan OD ∪ ID; Construct và Restructure GL, rồi PatternExpansion để dựng lại PT.</p><span>${selected === "rebuild" ? "Nhánh thực hiện" : "Không thực hiện"}</span></div>
    </div>
    <div class="rescan-result"><b>HAUP và patterns sau ${html(batch.name)}</b><span>${count(batch.largePatterns.length)} LARGE/HAUP · ${count(batch.preLargePatterns.length)} PRE-LARGE được báo cáo</span>${currentExamples ? `<div class="rescan-patterns">${currentExamples}</div>` : ""}</div>
    <p class="rescan-footnote">Số mẫu và AU lấy từ báo cáo; sơ đồ này không vẽ các node/cạnh nội bộ của PT. Dấu bằng (≥) vẫn kích hoạt re-scan.</p>
  </figure>`;
}

function renderDiagram() {
  if (!currentReport) return;
  const report = currentReport;
  selectedDiagramBatch = Math.max(0, Math.min(selectedDiagramBatch, report.batches.length - 1));
  const batch = report.batches[selectedDiagramBatch];
  const previous = selectedDiagramBatch > 0 ? report.batches[selectedDiagramBatch - 1] : null;
  const oldTU = batch.totalTransactionUtility - batch.accumulatedTransactionUtility;
  const left = batch.accumulatedMaximumUtility - report.upperThreshold * batch.accumulatedTransactionUtility;
  const right = (report.upperThreshold - report.lowerThreshold) * oldTU;
  $("diagramBatchTabs").innerHTML = report.batches.map((b, index) => `<button type="button" class="batch-tab ${index === selectedDiagramBatch ? "active" : ""}" data-index="${index}" role="tab" aria-selected="${index === selectedDiagramBatch}">${html(b.name)}</button>`).join("");
  $("diagramBatchTabs").querySelectorAll("button").forEach(button => button.addEventListener("click", () => {
    selectedDiagramBatch = Number(button.dataset.index);
    selectedDiagramPattern = 0;
    selectedDiagramGroup = null;
    diagramChangeFilter = "changed";
    diagramChangeLimit = 12;
    renderDiagram();
  }));
  const initial = selectedDiagramBatch === 0;
  const beforeCount = previous ? previous.largePatterns.length + previous.preLargePatterns.length : 0;
  const afterCount = batch.largePatterns.length + batch.preLargePatterns.length;
  $("diagramSummary").innerHTML = `<div class="linked-diagram-intro"><b>${html(batch.name)} · ${initial ? "Initial scan" : batch.rescanTriggered ? "Tight re-scan" : "Update existing PT"}</b><span>${count(batch.transactionCount)} giao dịch trong batch · ${count(afterCount)} mẫu LARGE/PRE-LARGE được báo cáo sau batch</span></div>`;
  const paperLimit = batch.tightRescanLimit == null ? null : Number(batch.tightRescanLimit);
  const equation = `<details class="linked-equation"><summary>Xem phép thế Eq. (12) và dạng gốc Eq. (6)</summary><div class="diagram-equation"><div><small>VẾ TRÁI EQ. (12)</small><b>MU(ID) − S_u × TU(ID)</b><span>${number(batch.accumulatedMaximumUtility)} − ${format(report.upperThreshold,6)} × ${number(batch.accumulatedTransactionUtility)} = ${format(left,6)}</span></div><div><small>VẾ PHẢI EQ. (12)</small><b>(S_u − S_l) × TU(OD)</b><span>(${format(report.upperThreshold,6)} − ${format(report.lowerThreshold,6)}) × ${number(oldTU)} = ${format(right,6)}</span></div></div><p>OD là original database ở lần re-scan gần nhất; ID là incremental database tích lũy sau đó.${Number.isFinite(paperLimit) ? ` Dạng gốc Eq. (6): MU(ID) ${number(batch.accumulatedMaximumUtility)} ${batch.rescanTriggered ? "≥" : "<"} ${number(paperLimit)}.` : ""} Dấu bằng (≥) kích hoạt re-scan.</p></details>`;
  const result = `<div class="linked-result linked-card"><small>PT SAU ${html(batch.name)} · KẾT QUẢ BÁO CÁO</small><strong>${count(batch.largePatterns.length)} LARGE / HAUP · ${count(batch.preLargePatterns.length)} PRE-LARGE</strong><p>TU(DB) = ${number(batch.totalTransactionUtility)}; ${count(afterCount)} mẫu LARGE/PRE-LARGE được báo cáo. Đây không phải số node nội bộ của PT.</p><div class="linked-result-actions"><button type="button" data-diagram-jump="changes">Xem mẫu thay đổi ↓</button><button type="button" data-diagram-jump="map">Tra cứu mẫu ↓</button></div></div>`;
  $("diagramFlow").innerHTML = initial
    ? `<div class="linked-diagram linked-initial" role="group" aria-label="Luồng initial scan cho ${html(batch.name)}"><div class="linked-card linked-single"><small>01 · ORIGINAL DATABASE (OD)</small><strong>${html(batch.name)} · ${count(batch.transactionCount)} giao dịch</strong><p>TU(OD) = ${number(batch.totalTransactionUtility)}. Chưa có incremental database (ID).</p></div><div class="linked-straight" aria-hidden="true"></div><div class="linked-card linked-process"><small>02 · INITIAL SCAN</small><strong>Construct(GL) → Restructure(GL) → PatternExpansion(GL)</strong><p>Tạo global PIHAUP-List (GL) và pattern tree (PT) ban đầu.</p></div><div class="linked-straight" aria-hidden="true"></div>${result}</div>`
    : `<div class="linked-diagram ${batch.rescanTriggered ? "linked-is-rescan" : "linked-is-update"}" role="group" aria-label="Luồng Tight Re-Scan Condition cho ${html(batch.name)}"><div class="linked-sources"><div class="linked-card"><small>01 · OD VÀ PT TRƯỚC BATCH</small><strong>rTU = TU(OD) = ${number(oldTU)}</strong><p>${count(beforeCount)} mẫu LARGE/PRE-LARGE được báo cáo sau ${html(previous.name)}; không phải số node PT.</p></div><div class="linked-card"><small>02 · INCREMENTAL DATABASE (ID)</small><strong>iTU = TU(ID) = ${number(batch.accumulatedTransactionUtility)}</strong><p>${html(batch.name)} thêm ${count(batch.transactionCount)} giao dịch; MU(ID) = ${number(batch.accumulatedMaximumUtility)}.</p></div></div><div class="linked-merge" aria-hidden="true"><span></span></div><div class="linked-card linked-condition"><small>03 · TIGHT RE-SCAN CONDITION · EQ. (12)</small><strong>${format(left,6)} ${batch.rescanTriggered ? "≥" : "<"} ${format(right,6)}</strong><p>MU(ID) − S_u × TU(ID) ≥ (S_u − S_l) × TU(OD)</p><span class="linked-verdict">${batch.rescanTriggered ? "TRUE · RE-SCAN" : "FALSE · NO RE-SCAN"}</span></div><div class="linked-split" aria-hidden="true"><span></span></div><div class="linked-branches"><div class="linked-card linked-branch ${batch.rescanTriggered ? "is-inactive" : "is-active"}"><small>FALSE · NO RE-SCAN</small><strong>Update existing PT</strong><p>Scan DBᵢ, cập nhật utility của node hiện có; không chạy full PatternExpansion.</p><span>${batch.rescanTriggered ? "Nhánh không thực hiện" : "✓ Nhánh thực hiện"}</span></div><div class="linked-card linked-branch ${batch.rescanTriggered ? "is-active" : "is-inactive"}"><small>TRUE · RE-SCAN</small><strong>Reconstruct GL and PT</strong><p>Re-scan OD ∪ ID; Construct, Restructure và PatternExpansion để dựng lại PT.</p><span>${batch.rescanTriggered ? "✓ Nhánh thực hiện" : "Nhánh không thực hiện"}</span></div></div><div class="linked-exit" aria-hidden="true"><span></span></div>${result}${equation}</div>`;
  $("diagramFlow").querySelectorAll("[data-diagram-jump]").forEach(button => button.addEventListener("click", () => {
    const map = button.dataset.diagramJump === "map";
    if (map) $("diagramTreeDisclosure").open = true;
    $(map ? "diagramTreeDisclosure" : "diagramChangeSection").scrollIntoView({behavior: "smooth", block: "start"});
  }));
  const previousPatterns = previous ? [...previous.largePatterns, ...previous.preLargePatterns] : [];
  const currentPatterns = [...batch.largePatterns, ...batch.preLargePatterns];
  const patternKey = pattern => String(pattern.items || pattern.name).split(",").map(item => item.trim()).filter(Boolean).sort((a, b) => a.localeCompare(b, "vi", {numeric: true})).join("\u001f");
  const beforeByItems = new Map(previousPatterns.map(pattern => [patternKey(pattern), pattern]));
  const afterByItems = new Map(currentPatterns.map(pattern => [patternKey(pattern), pattern]));
  diagramChanges = [...new Set([...beforeByItems.keys(), ...afterByItems.keys()])].map(items => {
    const before = beforeByItems.get(items), after = afterByItems.get(items);
    const kind = !before ? "appeared" : !after ? "removed" : before.type !== after.type ? "reclassified" : Math.abs(before.averageUtility - after.averageUtility) > 1e-9 ? "utility" : "stable";
    return {items, before, after, kind};
  });
  const priority = {reclassified: 0, appeared: 1, removed: 2, utility: 3, stable: 4};
  diagramChanges.sort((a, b) => priority[a.kind] - priority[b.kind] || (b.after?.averageUtility ?? b.before?.averageUtility ?? 0) - (a.after?.averageUtility ?? a.before?.averageUtility ?? 0));
  renderDiagramTransitions(batch, previous);
  const allPatterns = currentPatterns.sort((a, b) => b.averageUtility - a.averageUtility || a.name.localeCompare(b.name));
  diagramPatterns = allPatterns.slice(0, 60);
  selectedDiagramPattern = Math.max(0, Math.min(selectedDiagramPattern, diagramPatterns.length - 1));
  const groups = new Map();
  diagramPatterns.forEach((pattern, index) => {
    const firstItem = String(pattern.items || pattern.name).split(",")[0].trim();
    if (!groups.has(firstItem)) groups.set(firstItem, []);
    groups.get(firstItem).push({pattern, index});
  });
  const sortedGroups = [...groups.entries()].sort((a, b) => a[0].localeCompare(b[0], "vi", {numeric: true}));
  if (!groups.has(selectedDiagramGroup)) selectedDiagramGroup = sortedGroups[0]?.[0] ?? null;
  const focusedPatterns = groups.get(selectedDiagramGroup) || [];
  if (focusedPatterns.length && !focusedPatterns.some(({index}) => index === selectedDiagramPattern)) selectedDiagramPattern = focusedPatterns[0].index;
  $("diagramTree").innerHTML = diagramPatterns.length
    ? `<div class="diagram-map-caption"><span>Hiển thị ${count(diagramPatterns.length)}/${count(allPatterns.length)} mẫu${allPatterns.length > 60 ? " có AU cao nhất" : ""}</span><small>1. Chọn nhánh → 2. Chọn mẫu → 3. Xem phép tính</small></div><div class="diagram-map"><div class="diagram-map-root"><small>GỐC SƠ ĐỒ</small><strong>Mẫu sau ${html(batch.name)}</strong><span>${count(diagramPatterns.length)} mẫu đang hiển thị</span></div><div class="diagram-map-rail" aria-hidden="true"></div><div class="diagram-branch-grid" role="group" aria-label="Chọn nhánh item đầu">${sortedGroups.map(([item, patterns], groupIndex) => `<button type="button" class="diagram-branch-choice ${item === selectedDiagramGroup ? "is-active" : ""}" data-group-index="${groupIndex}" aria-pressed="${item === selectedDiagramGroup}"><span class="diagram-branch-item">${html(item)}</span><span><b>Nhánh ${html(item)}</b><small>${count(patterns.length)} mẫu · ${count(patterns.filter(({pattern}) => pattern.type === "LARGE").length)} LARGE</small></span><span class="diagram-branch-chevron" aria-hidden="true">${item === selectedDiagramGroup ? "↓" : "→"}</span></button>`).join("")}</div><section class="diagram-focused-branch" aria-label="Các mẫu thuộc nhánh ${html(selectedDiagramGroup)}"><div class="diagram-focused-heading"><div><small>NHÁNH ĐANG XEM</small><h4>${html(selectedDiagramGroup)} <span>→ ${count(focusedPatterns.length)} mẫu</span></h4></div><p>Thẻ xanh là LARGE; thẻ vàng là PRE-LARGE. Bấm một mẫu để xem vì sao được phân loại.</p></div><div class="diagram-branch-leaves">${focusedPatterns.map(({pattern, index}) => `<button type="button" class="diagram-map-pattern ${pattern.type === "LARGE" ? "is-large" : "is-pre"} ${index === selectedDiagramPattern ? "is-active" : ""}" data-pattern-index="${index}" aria-pressed="${index === selectedDiagramPattern}" aria-label="Mẫu ${html(pattern.name)}, ${pattern.type === "LARGE" ? "LARGE" : "PRE-LARGE"}, AU ${number(pattern.averageUtility)}"><b>${html(String(pattern.items || pattern.name).split(",").join(" → "))}</b><span>${pattern.type === "LARGE" ? "LARGE" : "PRE-LARGE"}<strong>AU ${number(pattern.averageUtility)}</strong></span></button>`).join("")}</div></section><p class="diagram-map-note">Các nhánh chỉ nhóm mẫu theo item đầu để dễ tìm, không phải các nút của pattern tree nội bộ.</p></div>`
    : `<div class="diagram-tree-empty"><strong>Chưa có mẫu LARGE/PRE-LARGE để tạo bản đồ.</strong><p>Giao dịch vẫn đã được xử lý. Xem S_u × TU(DB), S_l × TU(DB) và AUUB ở tab Tổng quan để biết vì sao tập mẫu rỗng.</p></div>`;
  $("diagramTree").querySelectorAll(".diagram-branch-choice").forEach(button => button.addEventListener("click", () => {
    selectedDiagramGroup = sortedGroups[Number(button.dataset.groupIndex)][0];
    selectedDiagramPattern = groups.get(selectedDiagramGroup)[0].index;
    renderDiagram();
    $("diagramTree").querySelector(`.diagram-branch-choice[data-group-index="${button.dataset.groupIndex}"]`)?.focus();
  }));
  $("diagramTree").querySelectorAll(".diagram-map-pattern").forEach(button => button.addEventListener("click", () => {
    selectedDiagramPattern = Number(button.dataset.patternIndex);
    $("diagramTree").querySelectorAll(".diagram-map-pattern").forEach(node => {
      const active = Number(node.dataset.patternIndex) === selectedDiagramPattern;
      node.classList.toggle("is-active", active);
      node.setAttribute("aria-pressed", String(active));
    });
    renderDiagramPatternDetail(batch);
    $("diagramPatternDetail").scrollIntoView({behavior: "smooth", block: "start"});
  }));
  renderDiagramPatternDetail(batch);
}

function renderDiagramTransitions(batch, previous) {
  const initial = !previous;
  const totals = {
    reclassified: diagramChanges.filter(change => change.kind === "reclassified").length,
    utility: diagramChanges.filter(change => change.kind === "utility").length,
    appeared: diagramChanges.filter(change => change.kind === "appeared").length,
    removed: diagramChanges.filter(change => change.kind === "removed").length,
    stable: diagramChanges.filter(change => change.kind === "stable").length
  };
  const changedCount = diagramChanges.length - totals.stable;
  const filters = initial
    ? [{id: "changed", label: "Mẫu ban đầu", total: changedCount}]
    : [
        {id: "changed", label: "Có thay đổi", total: changedCount},
        {id: "reclassified", label: "Đổi loại", total: totals.reclassified},
        {id: "utility", label: "Đổi AU, giữ loại", total: totals.utility},
        {id: "membership", label: "Vào / ra kết quả", total: totals.appeared + totals.removed},
        {id: "stable", label: "Không đổi", total: totals.stable}
      ];
  if (!filters.some(filter => filter.id === diagramChangeFilter)) diagramChangeFilter = "changed";
  const filtered = diagramChanges.filter(change => diagramChangeFilter === "changed" ? change.kind !== "stable"
    : diagramChangeFilter === "membership" ? change.kind === "appeared" || change.kind === "removed"
      : change.kind === diagramChangeFilter);
  const visible = filtered.slice(0, diagramChangeLimit);
  const state = (pattern, missing) => pattern
    ? `<b class="is-${pattern.type === "LARGE" ? "large" : "pre"}">${pattern.type === "LARGE" ? "LARGE" : "PRE-LARGE"}</b><strong>AU ${number(pattern.averageUtility)}</strong>`
    : `<span class="is-missing">${missing}</span>`;
  const label = {appeared: initial ? "Mẫu ban đầu" : "Vào kết quả", removed: "Không còn báo cáo", reclassified: "Đổi loại", utility: "AU đổi", stable: "Không đổi"};
  const reason = change => {
    if (change.kind === "removed") return "Không còn trong danh sách LARGE/PRE-LARGE được báo cáo. Không thể kết luận mẫu đã rời pattern tree (PT) hoặc là SMALL chỉ từ danh sách này.";
    if (change.kind === "appeared") return `${initial ? "Mẫu được báo cáo từ DB0" : "Mẫu xuất hiện trong kết quả sau batch"}: AU ${number(change.after.averageUtility)} ≥ S_l × TU(DB) = ${number(batch.minUtilLower)}.`;
    if (change.kind === "reclassified") return change.after.type === "LARGE"
      ? `AU sau batch ${number(change.after.averageUtility)} ≥ S_u × TU(DB) = ${number(batch.minUtilUpper)}, nên được phân loại LARGE (HAUP).`
      : `S_l × TU(DB) = ${number(batch.minUtilLower)} ≤ AU sau batch ${number(change.after.averageUtility)} < S_u × TU(DB) = ${number(batch.minUtilUpper)}, nên được phân loại PRE-LARGE.`;
    if (change.kind === "utility") {
      const delta = change.after.averageUtility - change.before.averageUtility;
      return `AU ${delta > 0 ? "tăng" : "giảm"} ${number(Math.abs(delta))} so với ${html(previous.name)}; phân loại vẫn là ${change.after.type === "LARGE" ? "LARGE" : "PRE-LARGE"}.`;
    }
    return "AU và phân loại không đổi so với batch trước.";
  };
  const formula = (pattern, context) => pattern
    ? `<div><small>${html(context.name)}</small><span>sumU ${number(pattern.sumUtility)} / |P| ${count(pattern.length)} = <b>AU ${number(pattern.averageUtility)}</b></span><span>S_l × TU(DB) ${number(context.minUtilLower)} · S_u × TU(DB) ${number(context.minUtilUpper)}</span></div>`
    : "";
  $("diagramTransitions").innerHTML = diagramChanges.length
    ? `<div class="diagram-evolution-summary"><strong>${initial ? `${count(changedCount)} mẫu được báo cáo ở DB0` : `${count(changedCount)} mẫu thay đổi sau ${html(batch.name)}`}</strong><span>${initial ? "Chưa có trạng thái trước để so sánh." : `${count(totals.reclassified)} đổi loại · ${count(totals.appeared)} vào kết quả · ${count(totals.removed)} không còn báo cáo · ${count(totals.stable)} không đổi.`}</span></div>
       <div class="diagram-evolution-filters" role="group" aria-label="Lọc mẫu theo thay đổi">${filters.map(filter => `<button type="button" class="diagram-evolution-filter ${diagramChangeFilter === filter.id ? "is-active" : ""}" data-filter="${filter.id}" aria-pressed="${diagramChangeFilter === filter.id}">${filter.label} <span>${count(filter.total)}</span></button>`).join("")}</div>
       ${filtered.length ? `<div class="diagram-evolution-count">Hiển thị ${count(visible.length)}/${count(filtered.length)} mẫu trong nhóm đang chọn.</div><div class="diagram-evolution-list">${visible.map(change => `<article class="diagram-evolution-row kind-${change.kind}"><div class="diagram-evolution-head"><strong>${html(change.after?.name ?? change.before?.name)}</strong><span>${label[change.kind]}</span></div><div class="diagram-evolution-path"><div><small>${initial ? "CHƯA CÓ" : `SAU ${html(previous.name)}`}</small>${state(change.before, initial ? "Chưa khởi tạo" : "Chưa có trong kết quả")}</div><span class="diagram-evolution-arrow" aria-hidden="true">→</span><div><small>SAU ${html(batch.name)}</small>${state(change.after, "Không còn trong kết quả")}</div></div><p>${reason(change)}</p><details class="diagram-evolution-math"><summary>Xem số dùng để tính AU và ngưỡng</summary><div>${change.before && previous ? formula(change.before, previous) : ""}${formula(change.after, batch)}</div></details></article>`).join("")}</div>${visible.length < filtered.length ? `<button type="button" class="diagram-evolution-more">Xem thêm ${count(Math.min(12, filtered.length - visible.length))} mẫu ↓</button>` : ""}` : `<div class="diagram-evolution-empty">Không có mẫu thuộc nhóm này. Chọn bộ lọc khác để tiếp tục đối chiếu.</div>`}`
    : `<div class="diagram-tree-empty"><strong>Chưa có mẫu nào được quản lý.</strong><p>Không có trạng thái mẫu để đối chiếu trước và sau batch này.</p></div>`;
  $("diagramTransitions").querySelectorAll(".diagram-evolution-filter").forEach(button => button.addEventListener("click", () => {
    diagramChangeFilter = button.dataset.filter;
    diagramChangeLimit = 12;
    renderDiagramTransitions(batch, previous);
    $("diagramTransitions").querySelector(`.diagram-evolution-filter[data-filter="${diagramChangeFilter}"]`)?.focus();
  }));
  $("diagramTransitions").querySelector(".diagram-evolution-more")?.addEventListener("click", () => {
    const firstNewIndex = diagramChangeLimit;
    diagramChangeLimit += 12;
    renderDiagramTransitions(batch, previous);
    const firstNewRow = $("diagramTransitions").querySelectorAll(".diagram-evolution-row")[firstNewIndex];
    if (firstNewRow) { firstNewRow.tabIndex = -1; firstNewRow.focus(); }
  });
}

function renderDiagramPatternDetail(batch) {
  const pattern = diagramPatterns[selectedDiagramPattern];
  if (!pattern) { $("diagramPatternDetail").innerHTML = ""; return; }
  const previous = selectedDiagramBatch > 0 ? currentReport.batches[selectedDiagramBatch - 1] : null;
  const change = diagramChanges.find(item => item.after?.name === pattern.name);
  const before = change?.before;
  const beforeLabel = before ? `${before.type.replace("_", "-")} · AU ${number(before.averageUtility)}` : "Chưa có trong kết quả báo cáo";
  const afterLabel = `${pattern.type.replace("_", "-")} · AU ${number(pattern.averageUtility)}`;
  $("diagramPatternDetail").innerHTML = `<div class="diagram-pattern-detail"><header><small>CHI TIẾT MẪU ĐÃ CHỌN · ${html(batch.name)}</small><h4>${html(pattern.name)}</h4></header><div class="diagram-pattern-link"><div><small>${previous ? `SAU ${html(previous.name)}` : "TRƯỚC INITIAL SCAN"}</small><strong>${beforeLabel}</strong></div><span aria-hidden="true">→</span><div><small>SAU ${html(batch.name)}</small><strong>${afterLabel}</strong></div></div><div class="diagram-pattern-facts"><div><small>BƯỚC 1 · TÍNH AVERAGE UTILITY</small><p>AU(P) = sumU(P) / |P|</p><strong>${number(pattern.sumUtility)} / ${count(pattern.length)} = ${number(pattern.averageUtility)}</strong></div><div><small>BƯỚC 2 · PHÂN LOẠI MẪU</small><strong>${pattern.type === "LARGE" ? "LARGE / HAUP" : "PRE-LARGE"}</strong><p>${pattern.type === "LARGE" ? `AU ${number(pattern.averageUtility)} ≥ S_u × TU(DB) = ${number(batch.minUtilUpper)}` : `S_l × TU(DB) = ${number(batch.minUtilLower)} ≤ AU ${number(pattern.averageUtility)} < S_u × TU(DB) = ${number(batch.minUtilUpper)}`}</p></div></div><button type="button" class="diagram-follow-change">Xem mẫu này trong bảng trước → sau ↑</button></div>`;
  $("diagramPatternDetail").querySelector(".diagram-follow-change")?.addEventListener("click", () => {
    const changed = diagramChanges.filter(item => item.kind !== "stable");
    diagramChangeFilter = change?.kind === "stable" ? "stable" : "changed";
    const list = diagramChangeFilter === "stable" ? diagramChanges.filter(item => item.kind === "stable") : changed;
    const index = Math.max(0, list.indexOf(change));
    diagramChangeLimit = Math.max(12, index + 1);
    renderDiagramTransitions(batch, previous);
    const row = $("diagramTransitions").querySelectorAll(".diagram-evolution-row")[index];
    (row || $("diagramChangeSection")).scrollIntoView({behavior: "smooth", block: "center"});
  });
}

function renderBatches(){
  const n=currentReport, b=n.batches[selectedBatch];
  $("batchTabs").querySelectorAll("button").forEach((x,i)=>x.classList.toggle("active",i===selectedBatch));
  const initial=b.index===0;
  const oldTU=b.totalTransactionUtility-b.accumulatedTransactionUtility;
  const left=b.accumulatedMaximumUtility-n.upperThreshold*b.accumulatedTransactionUtility;
  const right=(n.upperThreshold-n.lowerThreshold)*oldTU;
  const previous = selectedBatch > 0 ? n.batches[selectedBatch - 1] : null;
  const notes = {
    "Bước 1": "Biến số lượng (IU) và giá trị ngoài (EU) thành tiện ích U(i,T); TU là tổng, MU là giá trị item lớn nhất trong mỗi giao dịch.",
    "Bước 2": "Upper và Lower là ngưỡng tiện ích tuyệt đối; chúng được tính lại từ TU(DB) tích lũy sau mỗi batch.",
    "Bước 3": "OD là dữ liệu tại lần quét toàn bộ gần nhất; ID gồm các batch mới tích lũy từ lúc đó. Điều kiện đạt thì quét lại toàn bộ.",
    "Bước 4": "AUUB là cận trên để cắt tỉa ứng viên, không phải AU thực tế của mẫu. Số mẫu duyệt ở đây tính cho toàn lượt chạy.",
    "Bước 5": "So AU(P) với Upper/Lower để phân loại. Thời gian là riêng batch; heap là bộ nhớ JVM tại các điểm lấy mẫu."
  };
  const step = (number,title,body) => `<article class="step-card"><span class="step-letter">${number}</span><div><h3>${title}</h3><p class="step-explain"><b>Ý nghĩa:</b> ${notes[number]}</p>${body}${number === "Bước 3" ? renderRescanFlow(b, previous, initial, oldTU, left, right) : ""}</div></article>`;
  $("batchDetail").innerHTML = `<div class="step-list">
    ${step("Bước 1", "Đầu vào của " + html(b.name), `<p>${count(b.transactionCount)} giao dịch batch; ${count(b.cumulativeTransactionCount)} giao dịch tích lũy. EU và IU được nhân để tính U(i,T); TU(T) là tổng, MU(T) là giá trị lớn nhất trong giao dịch.</p>`)}
    ${step("Bước 2", "Tổng và ngưỡng", `<div class="formula-grid"><div>TU(batch)<strong>${format(b.batchTransactionUtility)}</strong></div><div>MU(batch)<strong>${format(b.batchMaximumUtility)}</strong></div><div>TU(DB)<strong>${format(b.totalTransactionUtility)}</strong></div><div>Upper = ${format(n.upperThreshold,6)} × TU(DB)<strong>${format(b.minUtilUpper)}</strong></div><div>Lower = ${format(n.lowerThreshold,6)} × TU(DB)<strong>${format(b.minUtilLower)}</strong></div></div>`)}
    ${step("Bước 3", "Điều kiện re-scan Eq.(12)", initial ? `<p>DB0 là lần quét khởi tạo; chưa có OD/ID để áp dụng Eq.(12).</p>` : `<div class="formula-grid"><div>TU(ID)<strong>${format(b.accumulatedTransactionUtility)}</strong></div><div>MU(ID)<strong>${format(b.accumulatedMaximumUtility)}</strong></div><div>TU(OD)<strong>${format(oldTU)}</strong></div><div>Vế trái = MU(ID) − Su×TU(ID)<strong>${format(left,6)}</strong></div><div>Vế phải = (Su−Sl)×TU(OD)<strong>${format(right,6)}</strong></div></div><p class="decision ${b.rescanTriggered?'rescan':''}">${format(left,6)} ${b.rescanTriggered?'≥':'<'} ${format(right,6)} → <b>${html(b.action)}</b></p>`)}
    ${step("Bước 4", "Cận trên & ứng viên", `<p>AUUB(i) = Σ MU(T) của các giao dịch chứa item i. PIHAUP-List và pattern tree dùng cận trên để loại nhánh không thể đạt ngưỡng. Bảng sau là 10 AUUB lớn nhất của <b>toàn lượt</b>; CSV xuất đủ ${count(n.upperBounds.length)} item.</p><div class="preview-tags">${n.upperBounds.slice(0,10).map(x=>`<span>${html(x.item)} · AUUB ${format(x.auub)}</span>`).join("")}</div><p>Toàn lượt: ${count(n.stats.patternsVisited)} mẫu duyệt, ${count(n.stats.combinedNodes)} node kết hợp. Đây là bộ đếm toàn lượt, không gán riêng cho batch này.</p>`)}
    ${step("Bước 5", "Phân loại và đo lường", `<p>LARGE: AU ≥ ${number(b.minUtilUpper)} · PRE-LARGE: ${number(b.minUtilLower)} ≤ AU &lt; ${number(b.minUtilUpper)} · SMALL: AU &lt; ${number(b.minUtilLower)}.</p><div class="formula-grid"><div>LARGE<strong>${b.largePatterns.length}</strong></div><div>PRE-LARGE quản lý<strong>${b.preLargePatterns.length}</strong></div><div>Thời gian xử lý batch<strong>${ms(b.elapsedMs)}</strong></div><div>Heap trước batch<strong>${mb(b.memoryBeforeMb)}</strong></div><div>Heap sau batch<strong>${mb(b.memoryAfterMb)}</strong></div><div>Peak quan sát đến batch này<strong>${mb(b.peakMemoryMb)}</strong></div></div><p class="small-note">Thời gian là riêng batch này; heap là các điểm lấy mẫu của JVM, không phải RAM toàn máy. PRE-LARGE đang quản lý có thể không phải tập vét cạn nếu batch không re-scan.</p>`)}
  </div>`;
}
function renderPatterns(){
  if (!currentReport) return;
  const n=currentReport, b=n.batches.at(-1), verdict=boundVerdict(n);
  let items=[...b.largePatterns,...b.preLargePatterns];
  if(patternFilter!=="ALL") items=items.filter(x=>x.type===patternFilter);
  const pageSize = 50;
  const pageCount = Math.max(1, Math.ceil(items.length / pageSize));
  patternPage = Math.min(patternPage, pageCount - 1);
  const pageItems = items.slice(patternPage * pageSize, (patternPage + 1) * pageSize);
  $("patternVerdict").innerHTML = `<div class="verdict ${verdict.belowLower?'empty-verdict':''}"><strong>${html(verdict.title)}</strong><p>${html(verdict.explanation)}</p><div class="result-facts"><span>Upper = Su×TU(DB)<b>${number(b.minUtilUpper)}</b></span><span>Lower = Sl×TU(DB)<b>${number(b.minUtilLower)}</b></span><span>AUUB lớn nhất<b>${number(verdict.bound)}</b></span><span>Thời gian khai phá<b>${ms(n.elapsedMs)}</b></span><span>Heap peak quan sát<b>${mb(n.measurement?.heapPeakObservedMb ?? n.peakMemoryMb)}</b></span></div></div><p class="result-help">AU(P) = sumU(P) / |P|. LARGE nếu AU ≥ Upper; PRE-LARGE nếu Lower ≤ AU &lt; Upper. Bảng chỉ chứa LARGE và PRE-LARGE đang quản lý; SMALL không được liệt kê.</p>`;
  const example = items[0];
  $("patternExample").innerHTML = example
    ? `<section class="worked-example"><div class="eyebrow">VÍ DỤ THẾ SỐ TỪ KẾT QUẢ</div><h3>Mẫu ${html(example.name)}</h3><p>AU(P) = sumU(P) / |P| = <b>${number(example.sumUtility)} / ${example.length} = ${number(example.averageUtility)}</b>.</p><p>${example.type === "LARGE" ? `AU ≥ Upper ${number(b.minUtilUpper)} → LARGE / HAUP.` : `Lower ${number(b.minUtilLower)} ≤ AU &lt; Upper ${number(b.minUtilUpper)} → PRE-LARGE.`}</p></section>`
    : `<section class="worked-example"><div class="eyebrow">VÌ SAO BẢNG TRỐNG?</div><p>${items.length === 0 && (b.largePatterns.length || b.preLargePatterns.length) ? "Không có mẫu thuộc bộ lọc đang chọn. Chọn “Tất cả” để xem những mẫu còn lại." : html(verdict.explanation)}</p><p>Giao dịch vẫn được đọc; số mẫu duyệt bằng 0 có thể là kết quả của cắt tỉa bằng cận trên.</p></section>`;
  $("patternBody").innerHTML=items.length?pageItems.map(p=>`<tr><td><strong>${html(p.name)}</strong></td><td><span class="type-badge ${p.type==='PRE_LARGE'?'pre':''}">${p.type.replace('_','-')}</span></td><td>${number(p.averageUtility)}</td><td>${number(p.sumUtility)}</td><td>${p.length}</td></tr>`).join(""):`<tr><td colspan="5">${verdict.belowLower?'Không có mẫu vì AUUB của mọi item đều thấp hơn Lower.':'Không có mẫu trong bộ lọc này.'}</td></tr>`;
  $("patternPager").innerHTML = `<span>Hiển thị <b>${items.length ? count(patternPage * pageSize + 1) : 0}–${count(Math.min(items.length, (patternPage + 1) * pageSize))}</b> / ${count(items.length)} mẫu</span><div><button type="button" data-page="previous" ${patternPage === 0 ? "disabled" : ""}>← Trước</button><span>Trang ${count(patternPage + 1)} / ${count(pageCount)}</span><button type="button" data-page="next" ${patternPage + 1 >= pageCount ? "disabled" : ""}>Sau →</button></div>`;
  $("patternPager").querySelectorAll("button").forEach(button => button.addEventListener("click", () => {
    patternPage += button.dataset.page === "next" ? 1 : -1;
    renderPatterns();
  }));
}

// Table 4 and the algorithm/experiment terminology in Kim et al. (2026).
// Keep paper terms distinct from measurements added by this application.
const glossaryGroups = [
  {id:"data", title:"01 · Dữ liệu và mẫu", source:"Preliminaries · tr. 7–9; Table 4", terms:[
    ["DB", "transactional database", "Tập các giao dịch đang xét; DB₀ là dữ liệu ban đầu, DBᵢ là batch chèn thêm.", "DB = {T₁, T₂, …, Tₙ}"],
    ["DB₀", "original database", "Batch gốc được quét để khởi tạo global PIHAUP-List và pattern tree.", "Tables 2–3: T₁–T₅"],
    ["DBᵢ", "incremental database / inserted batch", "Một batch mới chèn vào sau DB₀; phân biệt với ID là toàn bộ phần tích lũy sau lần re-scan gần nhất.", "DB₁ = {T₆,T₇}; DB₂ = {T₈,T₉}"],
    ["I", "set of unique items", "Tập các item khác nhau xuất hiện trong DB.", "I = {A,B,C,D,E,F} trong Table 3"],
    ["iₚ", "item", "Một item trong tập I; p là vị trí của item trong mẫu.", "iₚ ∈ I"],
    ["Tq", "transaction", "Một giao dịch gồm các item khác nhau; q là chỉ số giao dịch.", "Tq ⊆ I"],
    ["TID", "transaction identifier", "Mã định danh duy nhất của một giao dịch, dùng để nối các entry cùng giao dịch trong PIHAUP-List.", "Ví dụ: T₁, T₂"],
    ["P", "pattern", "Một tập item được đánh giá tiện ích trung bình.", "P ⊆ I"],
    ["|P|", "pattern length", "Số item trong mẫu P; mẫu số khi tính average utility.", "|{B,E}| = 2"],
    ["OD", "original database since last re-scan", "Những giao dịch đã được xử lý ở lần re-scan gần nhất; sau một re-scan, OD được cập nhật.", "DB = OD ∪ ID"],
    ["ID", "incremental database since last re-scan", "Tất cả giao dịch chèn thêm kể từ lần re-scan gần nhất; có thể gồm nhiều DBᵢ.", "DB₁ trước re-scan: ID = {T₆,T₇}"]
  ]},
  {id:"utility", title:"02 · Các loại tiện ích", source:"Preliminaries · tr. 7–8; Table 4", terms:[
    ["IU(i,T)", "internal utility", "Số lượng của item i trong giao dịch T; chưa phải tiện ích tiền/giá trị.", "IU(A,T₁) = 2"],
    ["EU(i)", "external utility", "Giá trị ngoài cố định của item i, lấy từ bảng EU.", "EU(A) = 2"],
    ["U(i,T)", "item utility", "Tiện ích của item i trong giao dịch T bằng số lượng nhân giá trị ngoài.", "U(i,T) = IU(i,T) × EU(i); U(A,T₁)=4"],
    ["U(P,T)", "pattern utility in a transaction", "Tổng U(i,T) của các item thuộc P khi T chứa toàn bộ P.", "U(P,T) = Σᵢ∈P U(i,T)"],
    ["U(P)", "total utility of a pattern", "Tổng U(P,T) trên mọi giao dịch chứa P.", "U(P) = ΣT⊇P U(P,T)"],
    ["sumU", "PIHAUP-List node utility field", "Trường lưu U(P) của node trong PIHAUP-List; không phải average utility.", "AU(P) = sumU / |P|"],
    ["TU(T)", "transaction utility", "Tổng tiện ích của tất cả item trong một giao dịch.", "TU(T₁) = 4+5+3 = 12"],
    ["TU(DB)", "database transaction utility", "Tổng TU(T) của các giao dịch trong DB; dùng tính ngưỡng tuyệt đối.", "TU(DB₀) = 141"],
    ["TU(OD) / TU(ID)", "utility of OD / ID", "Tổng transaction utility của phần đã quét gần nhất / phần mới chèn tích lũy; hai số cùng tham gia Eq. (12).", "DB₁: TU(OD)=141, TU(ID)=32"],
    ["U(DB)", "database utility", "Ký hiệu tổng tiện ích toàn DB trong phần dẫn nhập; khi phân loại, bài báo viết TU(DB) cho tổng các TU(T).", "U(DB) = ΣT∈DB TU(T)"],
    ["MU(T)", "maximum utility of a transaction", "Giá trị U(i,T) lớn nhất của một item trong giao dịch T.", "MU(T₁) = max(4,5,3) = 5"],
    ["MU(ID)", "sum of transaction maximum utilities in ID", "Tổng MU(T) của mọi giao dịch trong ID; không phải một giá trị max duy nhất của ID.", "MU(DB₁) = MU(T₆)+MU(T₇)=12+12=24"],
    ["AU(P,T)", "average utility in a transaction", "U(P,T) chia cho độ dài mẫu P, nếu T chứa P.", "AU(P,T) = U(P,T) / |P|"],
    ["AU(P)", "total average utility of a pattern", "Cộng AU(P,T) trên các giao dịch chứa P; dùng để phân loại LARGE/PRE-LARGE/SMALL.", "AU(P) = U(P) / |P|"]
  ]},
  {id:"classes", title:"03 · Ngưỡng và phân loại", source:"Definition 1 · Eq. (2)–(4), tr. 8–9", terms:[
    ["Sᵤ", "upper threshold ratio", "Tỷ lệ ngưỡng trên do người dùng đặt; bản thân Sᵤ chưa phải ngưỡng tiện ích tuyệt đối.", "0 < Sₗ < Sᵤ ≤ 1"],
    ["Sₗ", "lower threshold ratio", "Tỷ lệ ngưỡng dưới cho vùng đệm PRE-LARGE.", "Mặc định ví dụ: Sₗ = 0,10"],
    ["δ", "single minimum-utility ratio", "Tỷ lệ ngưỡng đơn được nhắc ở phần dẫn nhập trước khi bài báo chuyển sang hai tỷ lệ Sᵤ/Sₗ.", "minUtil = δ × U(DB)"],
    ["minutilᵤ", "absolute upper threshold", "Ngưỡng tiện ích trên tại DB đang xét; tính lại khi TU(DB) thay đổi.", "minutilᵤ = Sᵤ × TU(DB)"],
    ["minutilₗ", "absolute lower threshold", "Ngưỡng tiện ích dưới tại DB đang xét.", "minutilₗ = Sₗ × TU(DB)"],
    ["minUtil", "minimum utility threshold", "Tên ngưỡng đơn của phần dẫn nhập; bài báo dùng Sᵤ/Sₗ để phân loại ba mức.", "minUtil = δ × U(DB)"],
    ["LARGE", "large pattern", "Mẫu đạt ngưỡng trên; được trả về như HAUP.", "AU(P) ≥ TU(DB) × Sᵤ"],
    ["PRE-LARGE", "pre-large pattern", "Mẫu nằm giữa hai ngưỡng; được giữ để cập nhật khi dữ liệu tăng.", "TU(DB)×Sₗ ≤ AU(P) < TU(DB)×Sᵤ"],
    ["SMALL", "small pattern", "Mẫu dưới ngưỡng dưới; không có trong danh sách LARGE/PRE-LARGE báo cáo.", "AU(P) < TU(DB) × Sₗ"],
    ["HAUP", "high average utility pattern", "Mẫu tiện ích trung bình cao; tập kết quả là các mẫu LARGE theo ngưỡng trên.", "HAUP ⇔ LARGE ở DB đang xét"]
  ]},
  {id:"bounds", title:"04 · Cận trên và cắt tỉa", source:"Eq. (1), Fig. 2–5 · tr. 8, 11–14; Table 4", terms:[
    ["AUUB(P)", "average utility upper bound", "Cận trên bằng tổng MU(T) của các giao dịch chứa P; dùng sắp thứ tự item và cắt tỉa ứng viên.", "AUUB(P) = ΣT⊇P MU(T)"],
    ["MRU(P,T)", "maximal remaining item utility", "Tiện ích item lớn nhất còn lại sau P trong T theo thứ tự AUUB tăng dần.", "Dùng xây MAU(P,T)"],
    ["RN(P,T)", "remaining item number", "Số item còn có thể nối sau P trong giao dịch T theo thứ tự AUUB.", "RN(P,T) ≥ 0"],
    ["MRN(P)", "maximal remaining item number", "Giá trị RN(P,T) lớn nhất trong các giao dịch chứa P.", "MRN(P) = maxT⊇P RN(P,T)"],
    ["MAU(P,T)", "maximum average upper bound in T", "Cận trên chặt hơn để xem P còn có thể mở rộng trong một giao dịch hay không; Eq. (1) xét các trường hợp MRU.", "Dựa trên U(P,T), MRU và MRN"],
    ["MAU(P)", "maximum average upper bound", "Tổng MAU(P,T) trên các giao dịch chứa P; nếu thấp hơn ngưỡng dưới thì dừng mở rộng nhánh.", "MAU(P) < minutilₗ → prune"],
    ["pruning", "search-space pruning", "Loại nhánh không thể đạt ngưỡng nhờ AUUB/MAU; không có nghĩa là chưa đọc tệp đầu vào.", "AUUB, MAU là cận trên, không phải AU thực tế"]
  ]},
  {id:"algorithm", title:"05 · Cấu trúc và re-scan", source:"Fig. 1–7; Algorithms 1–2 · tr. 10–20", terms:[
    ["PIHAUPA", "proposed algorithm", "Thuật toán khai phá HAUP trên dữ liệu tăng dần, kết hợp pre-large và Tight Re-Scan Condition.", "Input: DB, Sₗ, Sᵤ; output: HAUP"],
    ["PIHAUP-List", "pattern utility list", "Cấu trúc list dùng tính tiện ích trực tiếp: node có sumU/MRN; entry giao dịch có TID/U/MRU.", "Fig. 2"],
    ["GL", "global PIHAUP-List", "List toàn cục cho mẫu 1 item; tạo bằng một lượt quét, sắp theo AUUB rồi dùng mở rộng mẫu.", "Construct(GL) → Restructure(GL)"],
    ["conditional PIHAUP-List", "conditional pattern list", "List tạm cho mẫu nhiều item, tạo bằng kết hợp node có chung TID khi PatternExpansion.", "Dùng trong DFS; bỏ sau khi dùng"],
    ["PT", "pattern tree", "Cây nội bộ lưu các mẫu LARGE/PRE-LARGE và utility để cập nhật khi không re-scan.", "Bản đồ tra cứu trên Web không phải PT nội bộ"],
    ["Construct", "global-list construction", "Quét dữ liệu và tạo node/transaction entry cho GL.", "Algorithm 1, bước khởi tạo hoặc re-scan"],
    ["Restructure", "global-list restructuring", "Sắp node theo AUUB tăng dần và tính MRU/MRN.", "Trước PatternExpansion"],
    ["PatternExpansion", "pattern expansion procedure", "Mở rộng mẫu bằng conditional PIHAUP-List và cắt tỉa MAU; đưa LARGE/PRE-LARGE vào PT.", "Algorithm 2"],
    ["DFS", "depth-first search", "Cách duyệt mở rộng mẫu theo chiều sâu trong các conditional list.", "Dùng khi PatternExpansion"],
    ["rTU", "utility since last re-scan baseline", "Tổng TU của OD tại lần re-scan gần nhất.", "rTU = TU(OD)"],
    ["iTU", "inserted transaction utility", "Tổng TU của giao dịch chèn thêm kể từ lần re-scan gần nhất.", "iTU = TU(ID)"],
    ["iDB", "inserted database accumulator", "Tập giao dịch chèn thêm được tích lũy sau lần re-scan; reset khi re-scan xảy ra.", "iDB ↔ ID"],
    ["Tight Re-Scan Condition", "proposed re-scan test", "Điều kiện dựa trên MU(ID), quyết định có cần quét lại OD∪ID và dựng lại PT hay không.", "Eq. (6); dạng không chia: Eq. (12)"],
    ["Eq. (12)", "algebraic tight condition", "Dạng biến đổi tương đương Eq. (6), tránh chia cho mẫu số; dấu bằng vẫn re-scan.", "MU(ID)−Sᵤ×TU(ID) ≥ (Sᵤ−Sₗ)×TU(OD)"],
    ["Original Re-Scan Condition", "earlier re-scan test", "Điều kiện cũ dựa trên TU(ID), được bài báo dùng để đối chứng; ứng dụng hiện chạy Tight, không chạy thuật toán cũ.", "Eq. (5)"],
    ["CurrentNode / TargetNode", "PT traversal nodes", "Node đang duyệt và node con khớp item khi cập nhật utility trong PT mà không re-scan.", "Algorithm 1, nhánh condition false"]
  ]},
  {id:"pseudocode", title:"06 · Ký hiệu giả mã và chứng minh", source:"Algorithms 1–2; Lemmas 1–2 · tr. 15–20", terms:[
    ["IDB", "incremental database sequence", "Đầu vào Algorithm 1 gồm DB₀, DB₁, DB₂… theo thứ tự xuất hiện.", "IDB = {DB₀, DB₁, DB₂, …}"],
    ["CL", "conditional PIHAUP-List", "List có điều kiện ứng với prefix hiện tại, là đầu vào của PatternExpansion.", "Algorithm 2"],
    ["Nₚ", "prefix-pattern list node", "Node PIHAUP-List của prefix mẫu ở mức trước, đưa vào lời gọi PatternExpansion.", "Input Algorithm 2"],
    ["PS", "pattern set", "Tập mẫu LARGE và PRE-LARGE mà PatternExpansion thu được trước khi đưa vào PT.", "Output Algorithm 2"],
    ["NCL", "next conditional list", "Conditional PIHAUP-List được dựng cho lời gọi mở rộng mẫu kế tiếp.", "NCL → PatternExpansion(NCL, P, Nₖ)"],
    ["Nₖ / Nₗ / N𝚌", "PIHAUP-List nodes", "Node đang mở rộng, node kết hợp và node mẫu kết hợp trong giả mã Algorithm 2.", "Ghép entry có cùng TID"],
    ["Eₖ / Eₗ / E𝚌", "transaction entries", "Các entry giao dịch trong node được ghép; entry mới E𝚌 mang TID, U và MRU.", "Algorithm 2, dòng 8–12"],
    ["AU(P,OD) / AU(P,ID)", "average utility by data segment", "Phần AU của mẫu tính riêng trên OD và ID trong chứng minh không bỏ sót mẫu.", "AU(P,DB)=AU(P,OD)+AU(P,ID)"],
    ["N_PM / N_EM", "pattern expansion counts", "Số lần PatternExpansion của điều kiện Tight / điều kiện original trong Lemma 2; dùng chứng minh Tight không nhiều hơn.", "N_PM ≤ N_EM"]
  ]},
  {id:"experiment", title:"07 · Thuật ngữ thực nghiệm", source:"Experimental results · tr. 21–31", terms:[
    ["OR", "original data ratio", "Tỷ lệ dữ liệu gốc DB₀ trên toàn bộ DB trong thí nghiệm của bài báo.", "OR = |DB₀| / |DB|"],
    ["dense / sparse", "dataset density", "Dense: giao dịch chứa nhiều item tương đối so với tập item; sparse: thưa hơn.", "Chess, Mushroom / Bms-pos, Chainstore"],
    ["insertion progress", "incremental insertion test", "Đo diễn biến thuật toán khi các batch được chèn lần lượt.", "Fig. 8–9"],
    ["threshold test", "threshold sensitivity run", "Thay đổi ngưỡng để xem thời gian và bộ nhớ biến động.", "Fig. 10–11"],
    ["scalability", "scalability test", "Tăng số giao dịch để xem khả năng mở rộng theo quy mô dữ liệu.", "Fig. 12"],
    ["sensitivity test", "parameter sensitivity", "Thay đổi tham số thực nghiệm để quan sát hiệu năng.", "Fig. 13–14"],
    ["accuracy test", "result-set verification", "Đối chiếu số mẫu kết quả của phương pháp trên dữ liệu lấy mẫu; không đồng nghĩa mọi số đo runtime đều giống nhau.", "Fig. 15, Table 8"],
    ["concept drift", "change in data distribution", "Tình huống đặc điểm dữ liệu thay đổi theo thời gian trong case study của bài báo.", "Fig. 16–17"],
    ["runtime", "execution time", "Thời gian chạy được bài báo dùng đánh giá hiệu năng; phụ thuộc máy và cấu hình thực nghiệm.", "Không phải hằng số của thuật toán"],
    ["memory usage", "memory consumption", "Bộ nhớ dùng trong thực nghiệm bài báo; không đồng nhất với heap JVM đo trên Web.", "Cần nêu môi trường đo khi so sánh"]
  ]},
  {id:"app", title:"08 · Chỉ số ứng dụng bổ sung", source:"Do chương trình Java đo; không phải thuật ngữ Table 4", appOnly:true, terms:[
    ["heap peak", "observed JVM heap peak", "Mức heap cao nhất ứng dụng quan sát trong JVM; không phải tổng RAM/RSS của máy.", "Đơn vị MiB"],
    ["CPU luồng", "thread CPU time", "Thời gian CPU của luồng xử lý Java nếu hệ thống hỗ trợ; khác thời gian trôi thực tế.", "Đơn vị ms"],
    ["đọc TXT", "input parsing time", "Thời gian nhập và kiểm tra định dạng tệp trước khi khai phá.", "Không phải riêng PIHAUPA"],
    ["tổng server", "server request time", "Thời gian từ nhận yêu cầu đến chuẩn bị xong phản hồi trên server; có thể gồm đọc và tạo JSON.", "Không gồm dựng giao diện trình duyệt"]
  ]}
];
let glossaryCategory = "all";
const glossaryNormalize = value => String(value).normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[ᵤₗᵢₚₙₖ𝚌₀₁₂₃₄₅₆₇₈₉]/gu, char => ({"ᵤ":"u","ₗ":"l","ᵢ":"i","ₚ":"p","ₙ":"n","ₖ":"k","𝚌":"c","₀":"0","₁":"1","₂":"2","₃":"3","₄":"4","₅":"5","₆":"6","₇":"7","₈":"8","₉":"9"})[char]).replace(/[\s_\-–—]+/g, "").toLowerCase();
function renderGlossary() {
  const query = glossaryNormalize($("glossarySearch").value.trim());
  const total = glossaryGroups.reduce((sum, group) => sum + group.terms.length, 0);
  $("glossaryFilters").innerHTML = `<button type="button" data-group="all" class="${glossaryCategory === "all" ? "is-active" : ""}" aria-pressed="${glossaryCategory === "all"}">Tất cả <span>${total}</span></button>` + glossaryGroups.map(group => `<button type="button" data-group="${group.id}" class="${glossaryCategory === group.id ? "is-active" : ""}" aria-pressed="${glossaryCategory === group.id}">${html(group.title.slice(5))} <span>${group.terms.length}</span></button>`).join("");
  const visible = glossaryGroups.filter(group => glossaryCategory === "all" || glossaryCategory === group.id).map(group => ({...group, terms:group.terms.filter(term => !query || glossaryNormalize(term.join(" ")).includes(query))})).filter(group => group.terms.length);
  const shown = visible.reduce((sum, group) => sum + group.terms.length, 0);
  $("glossaryCount").textContent = `Hiển thị ${shown}/${total} mục. Ký hiệu của bài báo giữ nguyên; phần giải thích là tiếng Việt.`;
  $("glossaryList").innerHTML = visible.length ? visible.map(group => `<section class="glossary-group"><div class="glossary-group-heading"><div><h3>${html(group.title)}</h3><p>${html(group.source)}</p></div>${group.appOnly ? '<span>ĐO THÊM TRONG ỨNG DỤNG</span>' : '<span>THUẬT NGỮ BÀI BÁO</span>'}</div><div class="glossary-grid">${group.terms.map(([symbol,english,meaning,formula]) => `<article class="glossary-term"><header><code>${html(symbol)}</code><strong>${html(english)}</strong></header><p>${html(meaning)}</p>${formula ? `<div class="glossary-formula">${html(formula)}</div>` : ""}</article>`).join("")}</div></section>`).join("") : `<div class="glossary-no-results">Không tìm thấy thuật ngữ. Thử ký hiệu ngắn hơn hoặc chọn “Tất cả”.</div>`;
}
$("glossarySearch").addEventListener("input", renderGlossary);
$("glossaryFilters").addEventListener("click", event => {
  const button = event.target.closest("button[data-group]");
  if (!button) return;
  glossaryCategory = button.dataset.group;
  renderGlossary();
});
document.querySelectorAll("[data-open-glossary]").forEach(button => button.addEventListener("click", () => showView("glossary")));

$("sampleButton").click();
showView("overview");
