const qs = new URLSearchParams(location.search);
const token = qs.get("token") || "";
const statusEl = document.getElementById("status");

let currentPath = "/";
let currentFile = null;

function fmtBytes(b) {
    if (b < 1024) return b + " B";
    const u = ["KB", "MB", "GB", "TB", "PB"];
    let i = -1;
    do { b = b / 1024; i++ } while (b >= 1024 && i < u.length - 1);
    return b.toFixed(1) + " " + u[i];
}

function fmtRate(bps) {
    if (bps < 1024) return bps.toFixed(0) + " B/s";
    const u = ["KB/s", "MB/s", "GB/s", "TB/s"];
    let i = -1;
    do { bps = bps / 1024; i++ } while (bps >= 1024 && i < u.length - 1);
    return bps.toFixed(1) + " " + u[i];
}

function fmtTime(timestamp) {
    return new Date(timestamp).toLocaleString('zh-CN');
}

function setText(id, txt) {
    const el = document.getElementById(id);
    if (el) el.textContent = txt;
}

function setBar(id, p) {
    const el = document.getElementById(id);
    if (el) el.style.width = Math.max(0, Math.min(100, p)) + "%";
}

function update(d) {
    setText("cpuUsage", (d.cpuUsage * 100).toFixed(1) + "%");
    setText("loadAvg", (d.systemLoadAverage || []).map(v => v.toFixed(2)).join(" / "));
    setBar("cpuBar", d.cpuUsage * 100);
    setText("memUsed", fmtBytes(d.memoryUsedBytes));
    setText("memFree", fmtBytes(d.memoryFreeBytes));
    setText("memTotal", fmtBytes(d.memoryTotalBytes));
    setBar("memBar", d.memoryUsedBytes / d.memoryTotalBytes * 100);
    const diskUsed = d.diskTotalBytes - d.diskFreeBytes;
    setText("diskUsed", fmtBytes(diskUsed));
    setText("diskFree", fmtBytes(d.diskFreeBytes));
    setText("diskR", fmtRate(d.diskReadBytesPerSec));
    setText("diskW", fmtRate(d.diskWriteBytesPerSec));
    setText("netUp", fmtRate(d.netUpBytesPerSec));
    setText("netDown", fmtRate(d.netDownBytesPerSec));
    statusEl.textContent = "已连接";
}

function poll() {
    fetch("/api/metrics", { headers: token ? { "Authorization": "Bearer " + token } : {} })
        .then(r => r.json())
        .then(update)
        .catch(() => { statusEl.textContent = "离线" });
}

function connectWs() {
    let proto = location.protocol === "https:" ? "wss" : "ws";
    let ws = new WebSocket(`${proto}://${location.host}/ws${token ? `?token=${encodeURIComponent(token)}` : ""}`);
    ws.onopen = () => statusEl.textContent = "已连接";
    ws.onmessage = (e) => { try { update(JSON.parse(e.data)) } catch { } };
    ws.onclose = () => { statusEl.textContent = "重试中"; setTimeout(connectWs, 1000) };
    ws.onerror = () => ws.close();
}

function showTab(tabName) {
    document.querySelectorAll('.tab-content').forEach(tab => {
        tab.classList.remove('active');
    });
    document.querySelectorAll('.nav-btn').forEach(btn => {
        btn.classList.remove('active');
    });
    
    document.getElementById(tabName).classList.add('active');
    document.querySelector(`[data-tab="${tabName}"]`).classList.add('active');
    
    if (tabName === 'files') {
        loadFileList(currentPath);
    }
}

function loadFileList(path) {
    const fileListEl = document.getElementById('fileList');
    fileListEl.innerHTML = '<tr><td colspan="5" class="loading">加载中...</td></tr>';
    
    fetch(`/api/files/list?path=${encodeURIComponent(path)}${token ? `&token=${encodeURIComponent(token)}` : ''}`)
        .then(r => r.json())
        .then(data => {
            if (data.success) {
                currentPath = data.currentPath;
                setText('currentPath', currentPath);
                
                let html = '';
                if (path !== '/') {
                    html += `<tr>
                        <td><span class="file-icon">📁</span>..</td>
                        <td>-</td>
                        <td>-</td>
                        <td>-</td>
                        <td class="file-actions">
                            <button class="btn" onclick="navigateToParent()">打开</button>
                        </td>
                    </tr>`;
                }
                
                data.files.forEach(file => {
                    const icon = file.isDirectory ? '📁' : '📄';
                    const size = file.isDirectory ? '-' : fmtBytes(file.size);
                    const time = fmtTime(file.modifiedTime);
                    
                    html += `<tr>
                        <td><span class="file-icon">${icon}</span>${file.name}</td>
                        <td>${size}</td>
                        <td>${time}</td>
                        <td>${file.permissions}</td>
                        <td class="file-actions">
                            ${file.isDirectory ? 
                                `<button class="btn" onclick="openFolder('${file.name}')">打开</button>` : 
                                `<button class="btn" onclick="openFile('${file.name}')">编辑</button>`}
                            <button class="btn" onclick="renameFile('${file.name}')">重命名</button>
                            <button class="btn" onclick="deleteFile('${file.name}')">删除</button>
                        </td>
                    </tr>`;
                });
                
                fileListEl.innerHTML = html;
            } else {
                fileListEl.innerHTML = `<tr><td colspan="5" class="loading">错误: ${data.message}</td></tr>`;
            }
        })
        .catch(err => {
            fileListEl.innerHTML = `<tr><td colspan="5" class="loading">网络错误: ${err.message}</td></tr>`;
        });
}

function navigateToParent() {
    const path = currentPath.split('/').slice(0, -1).join('/') || '/';
    loadFileList(path);
}

function openFolder(folderName) {
    const newPath = currentPath === '/' ? folderName : `${currentPath}/${folderName}`;
    loadFileList(newPath);
}

function openFile(fileName) {
    const filePath = currentPath === '/' ? fileName : `${currentPath}/${fileName}`;
    
    fetch(`/api/files/read?path=${encodeURIComponent(filePath)}${token ? `&token=${encodeURIComponent(token)}` : ''}`)
        .then(r => r.json())
        .then(data => {
            if (data.success) {
                currentFile = filePath;
                setText('editorTitle', `编辑: ${data.fileName}`);
                document.getElementById('fileContent').value = data.content;
                document.getElementById('fileEditor').style.display = 'block';
            } else {
                alert('读取文件失败: ' + data.message);
            }
        })
        .catch(err => {
            alert('网络错误: ' + err.message);
        });
}

function saveFile() {
    if (!currentFile) return;
    
    const content = document.getElementById('fileContent').value;
    
    fetch(`/api/files/write?path=${encodeURIComponent(currentFile)}${token ? `&token=${encodeURIComponent(token)}` : ''}`, {
        method: 'POST',
        headers: { 'Content-Type': 'text/plain' },
        body: content
    })
    .then(r => r.json())
    .then(data => {
        if (data.success) {
            alert('文件保存成功');
        } else {
            alert('保存失败: ' + data.message);
        }
    })
    .catch(err => {
        alert('网络错误: ' + err.message);
    });
}

function closeEditor() {
    document.getElementById('fileEditor').style.display = 'none';
    currentFile = null;
}

function showModal(title, content, onConfirm) {
    setText('modalTitle', title);
    document.getElementById('modalForm').innerHTML = content;
    document.getElementById('modal').style.display = 'block';
    
    const confirmBtn = document.getElementById('modalConfirm');
    const cancelBtn = document.getElementById('modalCancel');
    const closeBtn = document.querySelector('.close');
    
    function closeModal() {
        document.getElementById('modal').style.display = 'none';
        confirmBtn.onclick = null;
        cancelBtn.onclick = null;
        closeBtn.onclick = null;
    }
    
    confirmBtn.onclick = () => {
        onConfirm();
        closeModal();
    };
    cancelBtn.onclick = closeModal;
    closeBtn.onclick = closeModal;
}

function createFile(isDirectory) {
    const type = isDirectory ? '文件夹' : '文件';
    const content = `
        <div class="form-group">
            <label for="newName">${type}名称:</label>
            <input type="text" id="newName" placeholder="输入${type}名称">
        </div>
    `;
    
    showModal(`新建${type}`, content, () => {
        const name = document.getElementById('newName').value.trim();
        if (!name) {
            alert('请输入名称');
            return;
        }
        
        const filePath = currentPath === '/' ? name : `${currentPath}/${name}`;
        
        fetch(`/api/files/create?path=${encodeURIComponent(filePath)}&isDirectory=${isDirectory}${token ? `&token=${encodeURIComponent(token)}` : ''}`)
            .then(r => r.json())
            .then(data => {
                if (data.success) {
                    loadFileList(currentPath);
                } else {
                    alert('创建失败: ' + data.message);
                }
            })
            .catch(err => {
                alert('网络错误: ' + err.message);
            });
    });
}

function renameFile(oldName) {
    const content = `
        <div class="form-group">
            <label for="newName">新名称:</label>
            <input type="text" id="newName" value="${oldName}" placeholder="输入新名称">
        </div>
    `;
    
    showModal('重命名', content, () => {
        const newName = document.getElementById('newName').value.trim();
        if (!newName) {
            alert('请输入新名称');
            return;
        }
        
        const oldPath = currentPath === '/' ? oldName : `${currentPath}/${oldName}`;
        
        fetch(`/api/files/rename?path=${encodeURIComponent(oldPath)}&newName=${encodeURIComponent(newName)}${token ? `&token=${encodeURIComponent(token)}` : ''}`)
            .then(r => r.json())
            .then(data => {
                if (data.success) {
                    loadFileList(currentPath);
                } else {
                    alert('重命名失败: ' + data.message);
                }
            })
            .catch(err => {
                alert('网络错误: ' + err.message);
            });
    });
}

function deleteFile(fileName) {
    if (!confirm(`确定要删除 "${fileName}" 吗？`)) return;
    
    const filePath = currentPath === '/' ? fileName : `${currentPath}/${fileName}`;
    
    fetch(`/api/files/delete?path=${encodeURIComponent(filePath)}${token ? `&token=${encodeURIComponent(token)}` : ''}`)
        .then(r => r.json())
        .then(data => {
            if (data.success) {
                loadFileList(currentPath);
            } else {
                alert('删除失败: ' + data.message);
            }
        })
        .catch(err => {
            alert('网络错误: ' + err.message);
        });
}

function uploadFile() {
    document.getElementById('fileUpload').click();
}

function handleFileUpload(event) {
    const file = event.target.files[0];
    if (!file) return;
    
    const filePath = currentPath === '/' ? file.name : `${currentPath}/${file.name}`;
    
    const reader = new FileReader();
    reader.onload = function(e) {
        fetch(`/api/files/write?path=${encodeURIComponent(filePath)}${token ? `&token=${encodeURIComponent(token)}` : ''}`, {
            method: 'POST',
            headers: { 'Content-Type': 'text/plain' },
            body: e.target.result
        })
        .then(r => r.json())
        .then(data => {
            if (data.success) {
                loadFileList(currentPath);
            } else {
                alert('上传失败: ' + data.message);
            }
        })
        .catch(err => {
            alert('网络错误: ' + err.message);
        });
    };
    reader.readAsText(file);
    
    event.target.value = '';
}

document.addEventListener('DOMContentLoaded', function() {
    document.querySelectorAll('.nav-btn').forEach(btn => {
        btn.addEventListener('click', function() {
            showTab(this.dataset.tab);
        });
    });
    
    document.getElementById('refreshFiles').addEventListener('click', function() {
        loadFileList(currentPath);
    });
    
    document.getElementById('createFile').addEventListener('click', function() {
        createFile(false);
    });
    
    document.getElementById('createFolder').addEventListener('click', function() {
        createFile(true);
    });
    
    document.getElementById('uploadFile').addEventListener('click', uploadFile);
    document.getElementById('fileUpload').addEventListener('change', handleFileUpload);
    
    document.getElementById('saveFile').addEventListener('click', saveFile);
    document.getElementById('closeEditor').addEventListener('click', closeEditor);
    
    try { connectWs() } catch { setInterval(poll, 1000) }
    setInterval(() => { document.body.style.backgroundPositionX = (Date.now() / 4000) % 100 + "%" }, 1000);
});