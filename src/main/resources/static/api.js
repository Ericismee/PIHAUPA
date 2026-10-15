export async function request(url,options={}){
  const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),options.timeoutMs||30000);
  try{
    let response;
    try{response=await fetch(url,{...options,signal:controller.signal});}
    catch(error){throw Object.assign(new Error(error.name==='AbortError'?'Máy chủ chưa trả lời trong thời gian chờ.':'Không kết nối được tới máy chủ Java.'),{code:error.name==='AbortError'?'REQUEST_TIMEOUT':'NETWORK_UNAVAILABLE',action:'Kiểm tra server Java đang chạy đúng cổng. Lượt mới chưa có báo cáo hoàn tất.'});}
    if(response.ok&&options.responseType==='blob')return await response.blob();
    let body;
    try{body=await response.json();}catch(error){throw Object.assign(new Error('Phản hồi máy chủ không phải JSON hợp lệ.'),{code:'INVALID_RESPONSE',action:'Kiểm tra địa chỉ server và nhật ký Java.'});}
    if(!response.ok)throw Object.assign(new Error(body.error||`HTTP ${response.status}`),{code:body.code||'HTTP_'+response.status,action:body.action||'Kiểm tra nhật ký và thử lại sau khi sửa lỗi.'});
    return body;
  }finally{clearTimeout(timeout);}
}
