// Component integration without a browser: render actual API results through every module.
const fs = require('fs'),
  vm = require('vm');
class Element {
  constructor(id = '') {
    this.id = id;
    this.value = '';
    this.hidden = false;
    this.dataset = {};
    this.files = [];
    this.checked = false;
    this.innerHTML = '';
    this.textContent = '';
    this.disabled = false;
    this.children = [];
    this.listeners = {};
    this.classList = {
      toggle() {},
      contains() {
        return false;
      },
      add() {},
      remove() {},
    };
  }
  setAttribute() {}
  removeAttribute() {}
  addEventListener(type, fn) {
    this.listeners[type] = fn;
  }
  append(el) {
    this.children.push(el);
  }
  replaceChildren() {}
  focus() {}
  scrollIntoView() {}
  querySelector() {
    return new Element();
  }
  get options() {
    return [];
  }
  get selectedOptions() {
    return [{ textContent: 'Bài báo gốc' }];
  }
}
const elements = new Map();
const el = (id) => {
  if (!elements.has(id)) elements.set(id, new Element(id));
  return elements.get(id);
};
const doc = {
  getElementById: el,
  querySelector: () => new Element(),
  querySelectorAll: () => [],
  addEventListener() {},
  createElement: () => new Element(),
  body: new Element('body'),
};
for (const [id, v] of Object.entries({
  source: 'paper',
  su: '.23',
  sl: '.1',
  repeats: '1',
  format: 'transactions',
}))
  el(id).value = v;
const ctx = {
  document: doc,
  window: { addEventListener() {} },
  localStorage: {
    getItem() {
      return null;
    },
    setItem() {},
  },
  location: { hash: '#overview' },
  Node: { TEXT_NODE: 3 },
  Intl,
  Number,
  Math,
  Date,
  Map,
  Set,
  URLSearchParams,
  setTimeout,
  clearTimeout,
  console,
  request: async () => [],
};
vm.createContext(ctx);
function load(name) {
  let code = fs
    .readFileSync('src/main/resources/static/' + name, 'utf8')
    .replace(/^import\s+[\s\S]*?from\s+['"][^'"]+['"];\s*/gm, '')
    .replace(/export /g, '');
  vm.runInContext(code, ctx, { filename: name });
}
load('charts.js');
load('ui-components.js');
load('report-ui.js');
// Test main application with the actual notification module but avoid duplicate local $ declaration.
const notify = fs
  .readFileSync('src/main/resources/static/notifications.js', 'utf8')
  .replace(/^import\s+[\s\S]*?from\s+['"][^'"]+['"];\s*/gm, '')
  .replace(/^const \$\s*=.*;$/gm, '')
  .replace(/export /g, '')
  .replace(/\btoastTimer\b/g, 'notificationToastTimer');
let app = fs
  .readFileSync('src/main/resources/static/app.js', 'utf8')
  .replace(/^import\s+[\s\S]*?from\s+['"][^'"]+['"];\s*/gm, '');
app = app.replace(/sourceChanged\(\);\s*initialize\(\);\s*$/, '');
vm.runInContext(app, ctx, { filename: 'app.js' });
vm.runInContext(notify, ctx, { filename: 'notifications.js' });
for (const file of [
  'tmp/experiment-result.json',
  'tmp/experiment-large.json',
  'tmp/resource-result.json',
]) {
  if (!fs.existsSync(file)) continue;
  ctx.result = JSON.parse(fs.readFileSync(file, 'utf8'));
  vm.runInContext(
    'state.current=result;state.history=[result];state.batchIndex=result.report.batches.length-1;renderAnalysis();',
    ctx,
  );
  const content = el('content').innerHTML;
  if (content.indexOf('id="analysis-final"') > content.indexOf('id="analysis-summary"'))
    throw Error('Result must lead');
  if (!content.includes('final-numbers')) throw Error('Missing final numeric summary');
  if (!content.includes('Oracle độc lập')) throw Error('Missing verification level');
  vm.runInContext('renderMeasurements()', ctx);
  if (!el('content').innerHTML.includes('median')) throw Error('Missing repeated measurement');
  if (ctx.result.measurement.resources && !el('content').innerHTML.includes('RAM tiến trình Java'))
    throw Error('Missing RAM KPI cards');
  console.log('PASS module render:', ctx.result.source);
}
vm.runInContext("state.busy=true;currentJob='test';setRunPhase('CANCELLING');", ctx);
if (!el('run').innerHTML.includes('Đang hủy')) throw Error('Cancel status');
console.log('PASS cancel UI, report module imports, alignment builder');

// Preset selection must preserve arbitrary user thresholds and the paper reference.
vm.runInContext(
  `
state.datasets = [{id:'sample', profile:{su:.09,sl:.08,singletonAU:100,totalTU:1000}}];
$('source').value='sample';
sourceChanged();
if (Math.abs(Number($('su').value)-.07)>1e-12) throw Error('Catalog default overview');
$('threshold-mode').value='extended';
applyThresholdMode();
if (Math.abs(Number($('su').value)-.05)>1e-12) throw Error('Extended threshold');
$('su').value=.0123456789;
$('sl').value=.003;
markCustomThreshold();
if (Number($('su').value)!==.0123456789 || Number($('sl').value)!==.003) throw Error('Custom threshold was overwritten');
$('source').value='paper';
$('threshold-mode').value='reference';
applyThresholdMode();
if (Number($('su').value)!==.23 || Number($('sl').value)!==.1) throw Error('Paper reference changed');
`,
  ctx,
);
console.log('PASS selectable thresholds, exact custom values and paper reference');

if (fs.existsSync('tmp/resource-result.json')) {
  ctx.result = JSON.parse(fs.readFileSync('tmp/resource-result.json', 'utf8'));
  vm.runInContext('state.current=result;state.history=[result];', ctx);
  const ram = vm.runInContext('renderResourceMeasurements(result)', ctx);
  const sizes = vm.runInContext('renderSizeMeasurements()', ctx);
  if (ram.includes('chart-widget') || sizes.includes('chart-widget'))
    throw Error('Resource/size statistics must be numeric only');
  if (!ram.includes('Runtime (ms)') || !ram.includes('RAM Java (MiB)'))
    throw Error('Missing numeric metrics');
  console.log('PASS numeric RAM/runtime tables without charts');
}

ctx.progress = { state: 'RUNNING', progressPercent: 40, completedSteps: 2, totalSteps: 5 };
vm.runInContext('updateRunProgress(progress)', ctx);
if (el('run-progress-bar').value !== 40 || !el('run-progress-count').textContent.includes('2/5'))
  throw Error('Progress ratio');
ctx.progress = { state: 'COMPLETED', progressPercent: 100, completedSteps: 5, totalSteps: 5 };
vm.runInContext('updateRunProgress(progress)', ctx);
if (!el('run-progress-label').textContent.includes('100%')) throw Error('Completed progress');
console.log('PASS determinate and completed progress states');
