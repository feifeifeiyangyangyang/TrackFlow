# API 摘要

默认地址：`http://127.0.0.1:8002/api`

## 运单

- `GET /dashboard`：数据概览。
- `GET /shipments?q=`：查询运单列表。
- `POST /shipments`：创建运单。
- `GET /shipments/{id}`：查看运单详情、事件、原始报文、异常和对账任务。

## Webhook

- `POST /webhooks/carrier`：物流商 Webhook 接入。

必填 Header：

- `X-Carrier-Code`
- `X-Timestamp`
- `X-Signature`

处理语义：

- 校验签名和时间戳。
- 原始事件、处理任务和 Outbox 同事务写入。
- 返回 `202 Accepted`。
- 重复事件返回成功，但 `duplicate=true`。

## 事件任务

- `GET /event-tasks?status=`：查询事件处理任务。
- `POST /event-tasks/{id}/retry`：手动重试失败任务。

## 异常

- `GET /anomalies`：查询异常列表。
- `PATCH /anomalies/{id}`：更新异常状态。

异常状态只允许：

- `OPEN`
- `PROCESSING`
- `RESOLVED`
- `IGNORED`

## 对账

- `POST /reconciliation/shipments/{id}`：发起异步对账。
- `GET /reconciliation/batches`：查询对账批次。
- `GET /reconciliation/tasks`：查询对账任务明细。

对账接口会返回 `202 Accepted`，实际远程查询和补偿由 RabbitMQ 消费者异步执行。

## 故障模拟

- `POST /simulation/run`：触发 Mock Carrier 推送不同故障场景。

支持场景：

- `NORMAL`
- `DUPLICATE_PUSH`
- `OUT_OF_ORDER`
- `MISSING_EVENT`
- `QUERY_HTTP_500`
- `QUERY_TIMEOUT`
