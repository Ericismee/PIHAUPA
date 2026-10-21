const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ctx = { document: { addEventListener() {} } };
vm.createContext(ctx);
vm.runInContext(
  fs.readFileSync('src/main/resources/static/charts.js', 'utf8').replace(/export /g, ''),
  ctx,
);
for (const count of [1, 3, 12, 20, 100]) {
  for (const expanded of [false, true]) {
    for (const kind of ['bar', 'line']) {
      const labels = Array.from(
        { length: count },
        (_, i) => `{${i}, 19, 55, 75, 91, 106, 125, 200, 300}`,
      );
      ctx.labels = labels;
      ctx.options = { expanded, kind, unit: 'utility' };
      const html = vm.runInContext(
        'chart(labels, [{name:"AU(P)", values:labels.map((_,i)=>i+1)}], options)',
        ctx,
      );
      const svg = html.match(/<svg[\s\S]*?<\/svg>/)[0];
      const [, width, height] = svg.match(/viewBox="0 0 ([\d.]+) ([\d.]+)"/).map(Number);
      const ticks = [
        ...svg.matchAll(
          /<text\s+x="([\d.]+)"\s+y="([\d.]+)"\s+text-anchor="middle"\s*>\s*([^<]+)<title>/g,
        ),
      ];
      assert.equal(ticks.length, count, 'Every category must have its own label');
      let rightEdge = 0;
      for (const tick of ticks) {
        const x = Number(tick[1]);
        const halfWidth = (tick[3].trim().length * 8) / 2;
        assert.ok(x - halfWidth >= rightEdge, 'Labels must not overlap, including last two');
        assert.ok(x + halfWidth <= width, 'Label must stay inside the SVG');
        assert.ok(Number(tick[2]) < height);
        rightEdge = x + halfWidth;
      }
      assert.ok(
        html.includes('class="chart-viewport" tabindex="0"'),
        'Keyboard-scrollable chart region',
      );
      assert.ok(
        html.includes(`min-width: ${width}px`),
        'Prevent mobile shrinking from reintroducing overlap',
      );
      for (const label of labels)
        assert.ok(html.includes(`<td>${label}</td>`), 'Full pattern remains in data table');
    }
  }
}
console.log(
  'PASS chart layout: 1/3/12/20/100 categories, bar/line, inline/expanded, full labels in tables',
);
