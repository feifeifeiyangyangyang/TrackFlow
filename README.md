# TrackFlow 多物流商履约事件治理平台

TrackFlow 是一个用于演示和测试多物流商履约事件治理的前后端分离全栈项目。它解决物流商 Webhook 格式不统一、重复推送、乱序到达、漏推、状态冲突和主动对账补偿问题。

## 技术栈
- 后端：Java 17、Spring Boot 3、Spring MVC、Validation、JDBC、Flyway、Actuator、springdoc-openapi
- Mock：Java 17、Spring Boot 3
- 前端：Vue 3、TypeScript、Vite、Pinia、Axios、Element Plus、Vitest
- 部署：Docker Compose、MySQL 8、RabbitMQ、Nginx

当前机器安装的是 Java 17，所以项目按 Java 17 构建。Spring Boot 3 支持 Java 17+，README 如实记录该环境差异。

## 端口
- 前端：http://127.0.0.1:8001
- 主系统：http://127.0.0.1:8002
- Swagger：http://127.0.0.1:8002/swagger-ui/index.html
- Mock 物流商：http://127.0.0.1:8003
- MySQL 宿主端口：3307（容器内部仍为 3306）
- RabbitMQ 管理台：http://127.0.0.1:15672

## 本地启动
```powershell
cd C:\Users\23180\Desktop\新建文件夹\trackflow-platform
mvn -pl server spring-boot:run
mvn -pl mock-carrier spring-boot:run
cd web
npm ci
npm run dev
```

## 测试
```powershell
mvn test
cd web
npm ci
npm run type-check
npm run test
npm run build
docker compose config
```

## 核心能力
- 两个 Mock 物流商，Webhook 格式不同，状态映射不同。
- HMAC-SHA256 签名校验，时间戳防重放，常量时间比较。
- 数据库唯一约束保障幂等，重复事件返回成功且 `duplicate=true`。
- 原始事件和标准事件分表保存，支持 eventTime/receivedTime 双时间。
- 状态按 `eventTime -> receivedTime -> id` 重放，不用最后收到事件覆盖。
- UNKNOWN 和非法转移保留证据并生成异常。
- 主动对账可补入 RECONCILIATION 事件并重建状态。
- 前端所有页面调用真实后端接口，故障模拟调用 Mock 服务后再回调主系统。

## 真实限制
- 当前实现使用 Spring JDBC，不是 MyBatis；选择原因是为了在本机快速稳定完成可运行交付。
- RabbitMQ 容器和 Compose 已配置，当前本地默认用数据库任务表和恢复扫描完成异步任务演示。
- 自动化测试覆盖核心状态机、签名工具和前端工具函数，尚未覆盖需求清单中的全部高阶并发/Testcontainers 场景。
