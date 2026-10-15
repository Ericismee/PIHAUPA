// Component integration without a browser: render actual API results through every module.
const fs=require('fs'),vm=require('vm');
class Element{constructor(id=''){this.id=id;this.value='';this.hidden=false;this.dataset={};this.files=[];this.checked=false;this.innerHTML='';this.textContent='';this.disabled=false;this.children=[];this.listeners={};this.classList={toggle(){},contains(){return false},add(){},remove(){}};}setAttribute(){}removeAttribute(){}addEventListener(type,fn){this.listeners[type]=fn;}append(el){this.children.push(el);}replaceChildren(){}focus(){}scrollIntoView(){}querySelector(){return new Element();}get selectedOptions(){return [{textContent:'Bài báo gốc'}];}}
const elements=new Map();const el=id=>{if(!elements.has(id))elements.set(id,new Element(id));return elements.get(id);};
const doc={getElementById:el,querySelector:()=>new Element(),querySelectorAll:()=>[],addEventListener(){},createElement:()=>new Element(),body:new Element('body')};
for(const [id,v]of Object.entries({source:'paper',su:'.23',sl:'.1',repeats:'1',format:'transactions'}))el(id).value=v;
const ctx={document:doc,window:{addEventListener(){}},localStorage:{getItem(){return null},setItem(){}},location:{hash:'#overview'},Node:{TEXT_NODE:3},Intl,Number,Math,Date,Map,Set,URLSearchParams,setTimeout,clearTimeout,console,request:async()=>[]};vm.createContext(ctx);
function load(name){let code=fs.readFileSync('src/main/resources/static/'+name,'utf8').replace(/^import .*;\s*$/gm,'').replace(/export /g,'');vm.runInContext(code,ctx,{filename:name});}
load('charts.js');load('ui-components.js');load('report-ui.js');
// Test main application with the actual notification module but avoid duplicate local $ declaration.
const notify=fs.readFileSync('src/main/resources/static/notifications.js','utf8').replace(/^import .*;\s*$/gm,'').replace('const $=id=>document.getElementById(id);','').replace(/export /g,'').replace(/\btoastTimer\b/g,'notificationToastTimer');
let app=fs.readFileSync('src/main/resources/static/app.js','utf8').replace(/^import .*;\s*$/gm,'');app=app.replace('sourceChanged();initialize();','');vm.runInContext(app,ctx,{filename:'app.js'});vm.runInContext(notify,ctx,{filename:'notifications.js'});
for(const file of ['tmp/experiment-result.json','tmp/experiment-large.json']){
 if(!fs.existsSync(file))continue;ctx.result=JSON.parse(fs.readFileSync(file,'utf8'));vm.runInContext("state.current=result;state.history=[result];state.batchIndex=result.report.batches.length-1;renderAnalysis();",ctx);
 const content=el('content').innerHTML;if(content.indexOf('id="analysis-final"')>content.indexOf('id="analysis-summary"'))throw Error('Result must lead');
 if(!content.includes('Oracle độc lập'))throw Error('Missing verification level');
 vm.runInContext('renderMeasurements()',ctx);if(!el('content').innerHTML.includes('median'))throw Error('Missing repeated measurement');
 console.log('PASS module render:',ctx.result.source);
}
vm.runInContext("state.busy=true;currentJob='test';setRunPhase('CANCELLING');",ctx);if(!el('run').innerHTML.includes('Đang hủy'))throw Error('Cancel status');
console.log('PASS cancel UI, report module imports, alignment builder');
