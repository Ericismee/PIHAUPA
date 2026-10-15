import {esc} from './charts.js';
const $=id=>document.getElementById(id);
let toastTimer;
const notifications=[];
export function notifyStatus(title,detail='',kind='info'){
  notifications.unshift({title,detail,kind,time:new Date()});notifications.splice(30);
  $('notification-list').innerHTML=notifications.map(n=>`<li class="notification-item ${n.kind}"><strong>${esc(n.title)}</strong><p>${esc(n.detail)}</p><time>${esc(n.time.toLocaleTimeString('vi-VN'))}</time></li>`).join('');
  $('notification-count').hidden=false;$('notification-count').textContent=notifications.length;
  $('status-toast').innerHTML=`<div><strong>${esc(title)}</strong><p>${esc(detail)}</p></div><button type="button" aria-label="Đóng thông báo">×</button>`;
  $('status-toast').dataset.kind=kind;$('status-toast').hidden=false;
  $('status-toast').querySelector('button').onclick=()=>{$('status-toast').hidden=true;};
  clearTimeout(toastTimer);toastTimer=setTimeout(()=>{$('status-toast').hidden=true;},kind==='error'?10000:4500);
}
$('notifications-toggle').onclick=()=>{const open=$('notifications-panel').hidden;$('notifications-panel').hidden=!open;$('notifications-toggle').setAttribute('aria-expanded',String(open));};
$('notifications-clear').onclick=()=>{notifications.length=0;$('notification-count').hidden=true;$('notification-list').innerHTML='<li class="notification-empty">Chưa có thông báo.</li>';};
document.addEventListener('click',e=>{if(!e.target.closest('.notification-anchor')){$('notifications-panel').hidden=true;$('notifications-toggle').setAttribute('aria-expanded','false');}});
document.addEventListener('keydown',e=>{if(e.key==='Escape'){$('notifications-panel').hidden=true;$('notifications-toggle').setAttribute('aria-expanded','false');}});
