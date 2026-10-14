export const colors = {blue:'#2563eb',green:'#168364',amber:'#c78b25',slate:'#7990ac',cyan:'#0891b2'};
export const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export const num = (value, digits = 2) => value == null ? 'Không có số đo' : new Intl.NumberFormat('vi-VN',{maximumFractionDigits:digits}).format(value);

/** SVG charts use the real report, zero baseline, native tooltips, and a data alternative. */
export function chart(labels, series, {kind='bar',unit='',height=220,emptyMessage='Chưa có dữ liệu để vẽ biểu đồ.',expanded=false,xLabel='',yLabel=''}={}) {
  if (!labels.length || !series.length) return `<div class="bar-empty">${esc(emptyMessage)}</div>`;
  xLabel=xLabel||(labels.every(l=>/^DB|^DB0/.test(l))?'Batch dữ liệu':labels.every(l=>/^TC-/.test(l))?'Test case':labels.every(l=>/^Lượt /.test(l))?'Lượt chạy':labels.every(l=>/ · [0-9]+$/.test(l))?'Nhóm test case':labels.some(l=>/Original|Tight/.test(l))?'Chính sách re-scan':series.some(s=>/AUUB/.test(s.name))?'Item':series.some(s=>/AU\(P\)/.test(s.name))?'Mẫu P':'Hạng mục');
  yLabel=yLabel||({utility:'Tiện ích',ms:'Thời gian',MiB:'JVM heap',case:'Số test case',mẫu:'Số mẫu',lần:'Số lần', 'giao dịch':'Số giao dịch','lượt / nút':'Số lượng'}[unit]||'Giá trị');
  const axes=`<p class="chart-axes"><span><strong>X:</strong> ${esc(xLabel)}</span><span><strong>Y:</strong> ${esc(yLabel)}${unit?' ('+esc(unit)+')':''}</span></p>`;
  const width=expanded?Math.max(900,labels.length*85):600,left=65,right=18,top=22,bottom=42,plotW=width-left-right,plotH=height-top-bottom;
  const values=series.flatMap(s=>s.values).filter(Number.isFinite);
  const max=Math.max(...values,0)*1.15 || 1, min=Math.min(...values,0)*1.15;
  const y=v=>top+plotH-((v-min)/(max-min))*plotH;
  const groupW=plotW/labels.length;
  let svg=`<svg class="chart" viewBox="0 0 ${width} ${height}" role="img" aria-label="${esc(series.map(s=>s.name).join(', '))} theo ${esc(labels.join(', '))}. Đơn vị ${esc(unit)}. Có bảng số liệu bên dưới."><title>${esc(series.map(s=>s.name).join(' / '))} (${esc(unit)})</title>`;
  for(let i=0;i<=4;i++) {const v=min+(max-min)*i/4;svg+=`<line class="axis" x1="${left}" x2="${width-right}" y1="${y(v)}" y2="${y(v)}"/><text x="${left-9}" y="${y(v)+4}" text-anchor="end">${esc(compact(v))}</text>`;}
  const labelStep=expanded?1:Math.max(1,Math.ceil(labels.length/7));
  labels.forEach((label,i)=>{if(i%labelStep!==0 && i!==labels.length-1)return;svg+=`<text x="${left+groupW*(i+.5)}" y="${height-13}" text-anchor="middle">${esc(label.length>17?label.slice(0,15)+'…':label)}<title>${esc(label)}</title></text>`;});
  series.forEach((s,j)=>{
    const color=s.color || Object.values(colors)[j%5];
    if(kind==='line') {
      let points=s.values.map((v,i)=>`${left+groupW*(i+.5)},${y(v)}`).join(' ');
      svg+=`<polyline points="${points}" fill="none" stroke="${color}" stroke-width="2.5"/>`;
      s.values.forEach((v,i)=>{svg+=`<circle cx="${left+groupW*(i+.5)}" cy="${y(v)}" r="4" fill="white" stroke="${color}" stroke-width="2"><title>${esc(labels[i])} · ${esc(s.name)}: ${num(v)} ${esc(unit)}</title></circle>`;});
    } else s.values.forEach((v,i)=>{const barW=Math.min(46,groupW*.65/series.length),x=left+groupW*(i+.5)+(j-series.length/2)*barW;svg+=`<rect x="${x+2}" y="${Math.min(y(v),y(0))}" width="${Math.max(1,barW-4)}" height="${Math.abs(y(0)-y(v))}" rx="3" fill="${color}"><title>${esc(labels[i])} · ${esc(s.name)}: ${num(v)} ${esc(unit)}</title></rect>`;});
  });
  svg+='</svg>';
  svg+=`<div class="legend">${series.map((s,i)=>`<span><i style="background:${s.color||Object.values(colors)[i%5]}"></i>${esc(s.name)}${unit?' · '+esc(unit):''}</span>`).join('')}</div>`;
  svg+=`<details class="source-note"><summary>Xem số liệu biểu đồ</summary><div class="table-wrap"><table><thead><tr><th>Mốc</th>${series.map(s=>`<th>${esc(s.name)} (${esc(unit)})</th>`).join('')}</tr></thead><tbody>${labels.map((l,i)=>`<tr><td>${esc(l)}</td>${series.map(s=>`<td>${num(s.values[i],6)}</td>`).join('')}</tr>`).join('')}</tbody></table></div></details>`;
  const payload=esc(JSON.stringify({labels,series,options:{kind,unit,height:360,emptyMessage,xLabel,yLabel}}));
  return `<div class="chart-widget">${expanded?'':`<div class="chart-actions"><button type="button" class="button chart-expand" data-chart="${payload}" aria-label="Phóng to biểu đồ ${esc(series.map(s=>s.name).join(' / '))}">⤢ Phóng to</button></div>`}${axes}${svg}${!expanded&&labelStep>1?'<p class="source-note">Đã giãn nhãn để dễ đọc · Phóng to để xem đầy đủ các mốc.</p>':''}</div>`;
}
function compact(value){if(Math.abs(value)>=1e9)return num(value/1e9,1)+'B';if(Math.abs(value)>=1e6)return num(value/1e6,1)+'M';if(Math.abs(value)>=1e3)return num(value/1e3,1)+'k';return num(value,1);}

// One delegated handler supports charts added by every page and subsequent runs.
document.addEventListener('click',event=>{
  const button=event.target.closest('.chart-expand');if(!button)return;
  const data=JSON.parse(button.dataset.chart);
  let dialog=document.getElementById('chart-dialog');
  if(!dialog){dialog=document.createElement('dialog');dialog.id='chart-dialog';dialog.className='chart-dialog';document.body.append(dialog);
    dialog.addEventListener('click',e=>{if(e.target===dialog)dialog.close();});
    dialog.addEventListener('close',()=>{document.body.classList.remove('chart-modal-open');});
  }
  const title=button.closest('.card')?.querySelector('h2')?.textContent||'Biểu đồ chi tiết';
  dialog.innerHTML=`<div class="chart-dialog-head"><div><h2 id="chart-dialog-title">${esc(title)}</h2><p>Đơn vị: ${esc(data.options.unit)} · Cuộn ngang nếu có nhiều mốc. Xem bảng để đọc số chính xác.</p></div><button class="button chart-close" type="button" aria-label="Đóng biểu đồ">Đóng ✕</button></div><div class="chart-dialog-content">${chart(data.labels,data.series,{...data.options,expanded:true})}</div>`;
  dialog.setAttribute('aria-labelledby','chart-dialog-title');
  const svg=dialog.querySelector('svg');svg.style.minWidth=Math.max(850,data.labels.length*85)+'px';
  dialog.querySelector('.chart-close').onclick=()=>dialog.close();
  document.body.classList.add('chart-modal-open');dialog.showModal();
});
