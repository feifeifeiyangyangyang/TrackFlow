# 数据库说明

数据库结构由 Flyway 管理，主迁移文件位于：

- `server/src/main/resources/db/migration/V1__schema.sql`
- `server/src/main/resources/db/migration/V2__demo_data.sql`

## 核心表

- `carrier`：物流商配置，包括编码、名称、Webhook 密钥和查询地址。
- `carrier_status_mapping`：物流商原始状态到统一状态的映射。
- `shipment`：运单当前可信状态。
- `raw_carrier_event`：物流商原始事件，保留原始报文、幂等键、签名结果和处理状态。
- `normalized_event`：统一后的业务事件，用于状态机重放。
- `event_process_task`：Webhook 事件处理任务。
- `outbox_event`：可靠消息 Outbox 表。
- `shipment_anomaly`：异常记录。
- `reconciliation_batch`：对账批次。
- `reconciliation_task`：对账任务。
- `operation_log`：关键人工操作日志。

## 幂等约束

- `raw_carrier_event` 使用 `carrier_id + idempotency_key` 防止重复 Webhook。
- `normalized_event` 使用 `carrier_id + event_fingerprint` 防止重复标准事件。
- `outbox_event` 使用 `event_key` 防止重复入队。
- `reconciliation_task` 使用 `task_key` 防止重复任务。

## 时间字段

- `event_time`：物流事件真实发生时间。
- `received_time`：系统收到 Webhook 的时间。
- `created_at` / `updated_at`：系统记录创建和更新时间。
- `next_retry_time`：任务下次可重试时间。

## 状态重放

`shipment` 的当前状态由 `normalized_event` 重放得到。排序规则是：

1. `event_time`
2. `received_time`
3. `id`

这样可以处理乱序推送和迟到事件，不会让最后收到的旧事件回滚真实状态。
