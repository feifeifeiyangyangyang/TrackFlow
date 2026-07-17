# 测试说明

## 后端

```powershell
.\mvnw.cmd test
```

当前后端测试覆盖：

- 状态机合法流转。
- HMAC 签名工具。
- Flyway 迁移在 H2 MySQL Mode 下可执行。
- Webhook 入库只创建 raw/task/outbox，不同步写 normalized event。
- 重复 Webhook 不重复创建任务和 Outbox。
- 乱序事件按业务时间重放，最终状态仍为 `DELIVERED`。
- 主动对账接口只创建异步 batch/task/outbox，不同步查询物流商。

## 前端

```powershell
cd web
npm run test
npm run build
```

当前前端测试覆盖状态标签和时间格式化工具，构建命令会同时执行 TypeScript 类型检查。

## Docker

```powershell
docker compose config
```

该命令用于校验 Compose 语法。完整 MySQL/RabbitMQ 实机验证需要 Docker Desktop daemon 正常运行。
