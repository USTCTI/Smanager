# 部署与配置指南

## 构建

需要 JDK 21 与 Maven。

```bash
mvn -DskipTests package
```

构建产物位于：`target/SManager-1.0.0.jar`。

## 安装

1. 将 `SManager-1.0.0.jar` 放入 Paper 服务器的 `plugins` 目录。
2. 启动服务器，插件会在 `plugins/SManager/config.yml` 写入默认配置。

## 配置

`plugins/SManager/config.yml`：

```yaml
monitor:
  intervalMillis: 1000
web:
  port: 25566
auth:
  token: ""    # 可设置任意字符串作为访问令牌
```

- `monitor.intervalMillis`：采样间隔，毫秒。
- `web.port`：Web 服务端口。
- `auth.token`：访问令牌，留空则不校验。

修改配置后执行命令重载：

```
/smanager reload
```

需要权限 `smanager.admin`，默认仅 OP 拥有。

## 访问

- 仪表板页面：http://<服务器IP>:<端口>/
- REST：`GET /api/metrics`
- WS：`ws://<服务器IP>:<端口>/ws`

如设置令牌，在页面 URL 上添加 `?token=YOUR_TOKEN`，或为 API 请求添加 `Authorization: Bearer YOUR_TOKEN`。

## 常见问题

- 端口占用：修改 `web.port` 为未占用端口并重载。
- 无法获取速率：磁盘与网络速率基于差分计算，首次启动可能显示为 0，随后会正常。 
