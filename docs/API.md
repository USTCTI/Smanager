# SManager API

## 概述
SManager 提供 REST API 与 WebSocket 实时推送，默认监听端口 `25566`（可在 `config.yml` 覆盖）。
如配置了 `auth.token`，需要通过 `Authorization: Bearer <token>` 或查询参数 `?token=<token>` 授权。

## REST

### GET /api/metrics
- 描述：返回最新系统监控快照。
- 授权：若配置了 `auth.token` 则必须携带。
- 响应示例：
```json
{
  "memoryTotalBytes": 34359738368,
  "memoryUsedBytes": 1234567890,
  "memoryFreeBytes": 33125170478,
  "cpuUsage": 0.27,
  "systemLoadAverage": [0.31, 0.42, 0.47],
  "diskTotalBytes": 512110190592,
  "diskFreeBytes": 310110190592,
  "diskReadBytesPerSec": 2048000.0,
  "diskWriteBytesPerSec": 1024000.0,
  "netUpBytesPerSec": 512000.0,
  "netDownBytesPerSec": 2048000.0,
  "timestamp": 1730000000000
}
```

### GET /api/health
- 描述：健康检查，返回 `ok`。
- 授权：若配置了 `auth.token` 则必须携带。

## WebSocket

### ws://<host>:<port>/ws
- 描述：每秒推送一次最新监控数据，数据格式与 `/api/metrics` 相同。
- 授权：若配置了 `auth.token`，通过 `?token=<token>` 查询参数携带。

### 客户端示例
```js
const ws = new WebSocket('ws://localhost:25566/ws?token=YOUR_TOKEN');
ws.onmessage = (e) => {
  const data = JSON.parse(e.data);
  console.log(data.cpuUsage);
};
```

