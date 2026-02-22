const qs=new URLSearchParams(location.search);const token=qs.get("token")||"";const statusEl=document.getElementById("status")
function fmtBytes(b){if(b<1024)return b+" B";const u=["KB","MB","GB","TB","PB"];let i=-1;do{b=b/1024;i++}while(b>=1024&&i<u.length-1);return b.toFixed(1)+" "+u[i]}
function fmtRate(bps){if(bps<1024)return bps.toFixed(0)+" B/s";const u=["KB/s","MB/s","GB/s","TB/s"];let i=-1;do{bps=bps/1024;i++}while(bps>=1024&&i<u.length-1);return bps.toFixed(1)+" "+u[i]}
function setText(id,txt){const el=document.getElementById(id);if(el)el.textContent=txt}
function setBar(id,p){const el=document.getElementById(id);if(el)el.style.width=Math.max(0,Math.min(100,p))+"%"}
function update(d){setText("cpuUsage",(d.cpuUsage*100).toFixed(1)+"%");setText("loadAvg",(d.systemLoadAverage||[]).map(v=>v.toFixed(2)).join(" / "));setBar("cpuBar",d.cpuUsage*100);setText("memUsed",fmtBytes(d.memoryUsedBytes));setText("memFree",fmtBytes(d.memoryFreeBytes));setText("memTotal",fmtBytes(d.memoryTotalBytes));setBar("memBar",d.memoryUsedBytes/d.memoryTotalBytes*100);const diskUsed=d.diskTotalBytes-d.diskFreeBytes;setText("diskUsed",fmtBytes(diskUsed));setText("diskFree",fmtBytes(d.diskFreeBytes));setText("diskR",fmtRate(d.diskReadBytesPerSec));setText("diskW",fmtRate(d.diskWriteBytesPerSec));setText("netUp",fmtRate(d.netUpBytesPerSec));setText("netDown",fmtRate(d.netDownBytesPerSec));statusEl.textContent="已连接"}
function poll(){fetch("/api/metrics",{headers:token?{"Authorization":"Bearer "+token}:{}}).then(r=>r.json()).then(update).catch(()=>{statusEl.textContent="离线"})}
function connectWs(){let proto=location.protocol==="https:"?"wss":"ws";let ws=new WebSocket(`${proto}://${location.host}/ws${token?`?token=${encodeURIComponent(token)}`:""}`);ws.onopen=()=>statusEl.textContent="已连接";ws.onmessage=(e)=>{try{update(JSON.parse(e.data))}catch{}};ws.onclose=()=>{statusEl.textContent="重试中";setTimeout(connectWs,1000)};ws.onerror=()=>ws.close()}
try{connectWs()}catch{setInterval(poll,1000)}
setInterval(()=>{document.body.style.backgroundPositionX=(Date.now()/4000)%100+"%"},1000)

