# 架构说明

TrackFlow 采用前后端分离和模块化单体设计。

## 模块

- `web`：Vue 3 运营后台，展示运单、原始事件、处理任务、异常和对账结果。
- `server`：Spring Boot 主系统，负责 Webhook 接入、事件标准化、状态机重放、异常治理、Outbox 发布和对账。
- `mock-carrier`：本地物流商模拟器，提供两种不同格式的 Webhook 和轨迹查询接口。
- `mysql`：生产形态数据库，Flyway 管理表结构。
- `rabbitmq`：承载事件处理任务和对账任务消息。

## 关键设计

### 原始事件与标准事件分离

`raw_carrier_event` 保存物流商原始报文、签名校验结果和幂等键。  
`normalized_event` 保存统一后的业务事件，用于状态机重放和对账补偿。

### Outbox 可靠消息

Webhook 请求不会直接处理业务状态，而是在同一个数据库事务中写入：

- `raw_carrier_event`
- `event_process_task`
- `outbox_event`

之后由 `OutboxPublisher` 扫描并发布 RabbitMQ，发布成功后标记 `SENT`。

### 任务抢占与重试

消费者不会盲目处理消息，而是先通过数据库条件更新抢占任务。  
任务支持 `PENDING`、`RUNNING`、`RETRY_WAIT`、`SUCCESS`、`FAILED` 状态，失败后按退避时间重试。

### 状态机重放

运单当前状态不依赖“最后收到的事件”，而是按 `event_time`、`received_time`、`id` 排序重放。  
这能解释乱序推送、迟到事件和漏推补偿后的状态修正。

### 主动对账

对账请求先创建 `reconciliation_batch` 和 `reconciliation_task`，再异步查询物流商完整轨迹。  
系统使用事件指纹比对缺失事件，只补入数据库中不存在的轨迹节点。
