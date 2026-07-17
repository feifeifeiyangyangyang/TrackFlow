# TrackFlow 项目协作说明

## 项目结构
- `server/`：Spring Boot 主系统，负责 Webhook、幂等、状态重建、异常、对账、运营 API。
- `mock-carrier/`：Mock 物流商服务，提供差异化 A/B 格式、查询接口和故障场景推送。
- `web/`：Vue 3 + TypeScript 运营后台，端口 8001。
- `docs/`：架构、数据库、API、测试和演示文档。
- `scripts/`：本地启动、停止、验证脚本。

## 构建命令
- 后端：`mvn test`
- 主系统：`mvn -pl server spring-boot:run`
- Mock 服务：`mvn -pl mock-carrier spring-boot:run`
- 前端：`cd web && npm ci && npm run type-check && npm run test && npm run build`
- Docker 配置检查：`docker compose config`

## 端口约定
- 前端：8001
- 主系统：8002
- Mock 物流商：8003
- MySQL：3307（宿主机映射）/ 3306（容器内部）
- RabbitMQ：5672 / 15672

## 编码约定
- Controller 只做参数接收和响应组织，核心业务放入 service。
- 禁止返回数据库 Entity，API 使用 DTO/Map 响应。
- 时间统一使用 UTC `Instant` 存储和 ISO 8601 输出。
- Webhook 密钥不得返回给前端或写入日志。
- 关键操作写入 `operation_log`。

## 禁止引入
Spring Cloud、Nacos、Seata、Elasticsearch、Kubernetes、Redis、Kafka、RAG、Agent、多租户和复杂 RBAC。

## 核心业务约束
- 不用状态数字大小推断当前状态，必须按 `eventTime -> receivedTime -> id` 重放状态机。
- UNKNOWN 事件必须保存但不参与状态计算。
- 数据库唯一约束是幂等最终保障。
- 终态 DELIVERED / RETURNED / CANCELLED 后不允许继续转移。
