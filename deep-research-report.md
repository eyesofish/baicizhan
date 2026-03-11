# Baicizhan 深度调研报告（按当前代码校准）

> 仓库路径：`D:\Github\baicizhan`  
> 校准时间：2026-03-11  
> 说明：本报告基于当前工作区代码状态（含未提交改动）重新核对，目标是反映“现在能跑、现在已接入、现在仍缺失”的真实情况。

## 1. 结论摘要

项目已具备本地可运行的完整闭环，核心链路是：

1. 前端（React + Redux + Router）调用 Spring Boot API。
2. 后端完成鉴权、词典、词单、复习、术语详情、AI 作业接口。
3. MySQL/H2 + Flyway 管理数据，Redis用于缓存和限流辅助场景。
4. 根目录脚本 `start-dev.ps1` / `stop-dev.ps1` 可一键拉起与停止。

本次已实际验证：

1. `demo`：`./mvnw.cmd -q -DskipTests package` 成功。
2. `frontend`：`npm run build` 成功（CRA 构建通过）。

## 2. 当前技术栈与工程形态

### 2.1 后端（`demo`）

1. Java 21，Spring Boot 3.5.7。
2. 依赖：Web、Validation、Security、JPA、Redis、Actuator、Flyway、springdoc、jjwt。
3. 默认 profile 为 H2（`application.yml`），`mysql` profile 覆盖数据源（`application-mysql.yml`）。

### 2.2 前端（`frontend`）

1. React 18 + react-scripts（CRA）+ Redux Toolkit + React Router v6。
2. `npm scripts` 仅配置 CRA（`start/build/test`），非 Vite 工程结构。
3. API 基址：`REACT_APP_API_BASE_URL`，默认 `http://localhost:8080`。

### 2.3 基础设施（仓库根目录）

1. `docker-compose.yml` 提供 MySQL 8.4 与 Redis 7（均含健康检查）。
2. 启动脚本默认端口：
   - 前端：`http://localhost:5173`
   - 后端：`http://localhost:8080`
   - Swagger：`http://localhost:8080/swagger-ui.html`

## 3. 后端能力实况（按代码核对）

### 3.1 鉴权与安全

已实现：

1. JWT 双 Token：`access` + `refresh`。
2. 接口：
   - `POST /v1/auth/register`
   - `POST /v1/auth/login`
   - `POST /v1/auth/refresh`
3. 密码加密：`BCryptPasswordEncoder(12)`。
4. 鉴权过滤：`JwtAuthenticationFilter` 仅接受 `typ=access` 的 Bearer Token。
5. 统一响应包：`ApiResponse{code,message,data,traceId}`。

当前策略：

1. 放行：`/v1/auth/**`、`/v1/dictionary/**`、`/v1/webhooks/openai`、健康检查与 Swagger。
2. 其他接口默认需要登录。
3. 刷新令牌目前无黑名单/吊销机制。

### 3.2 词典 API（已接前端）

接口：

1. `GET /v1/dictionary/lookup?word=...`
2. `GET /v1/dictionary/match?prefix=...&limit=...`
3. `GET /v1/dictionary/random`

现状：

1. 查询来源是本地数据库 `terms/senses/example_sentences`，不是外部词典 API。
2. 返回结构已对齐前端：`word + pronunciation + results`。
3. `match` 返回格式为 `results.data[]`。

### 3.3 词单与词条 API（Journal 已后端化）

接口：

1. `POST /v1/lists`
2. `GET /v1/lists`
3. `POST /v1/lists/{listId}/items`
4. `GET /v1/lists/{listId}/items`
5. `PUT /v1/lists/{listId}/items/{itemId}`
6. `DELETE /v1/lists/{listId}/items/{itemId}`

现状：

1. Journal 的增删改查已全部走后端 API。
2. 条目返回包含 `points/lastReviewed/createdAt/updatedAt`。
3. 前端图片字段目前不落库，后端以文本类字段为主。

### 3.4 复习与 SRS

接口：

1. `GET /v1/review/next?limit=...`
2. `POST /v1/review/{termId}/result`
3. `GET /v1/terms/{termId}`（复习时补全术语详情）

现状：

1. 前端 `Flashcards/Quiz` 使用 `/v1/review/next` 获取卡片池。
2. 结果页会把评分写回 `/v1/review/{termId}/result`。
3. SRS 状态持久化在 `user_progress`，行为日志写入 `review_logs`。
4. `elapsedMs` 在 DTO 中为可选；当前前端提交通常只传 `rating`。

### 3.5 AI 作业

接口：

1. `POST /v1/ai/enrich`
2. `GET /v1/ai/jobs/{jobId}`
3. `POST /v1/webhooks/openai`

关键事实：

1. `AiService` 按 OpenAI 兼容格式请求 `.../chat/completions`。
2. `OPENAI_BASE_URL` 可配置（默认 `http://127.0.0.1:18000/v1`）。
3. 作业状态会写入数据库（`QUEUED/RUNNING/SUCCEEDED/FAILED`）。
4. 但当前实现是“请求内同步执行+落库状态”，不是真正的异步队列 worker 模式。

## 4. 缓存、限流与可观测性

### 4.1 Redis 缓存

`TermService` 已落地三类缓存保护：

1. 穿透保护：不存在数据写 `__NULL__`（短 TTL）。
2. 击穿保护：互斥锁 `lock:term:{id}` + 双查。
3. 雪崩缓解：TTL 抖动（jitter）。

`ReviewService` 对下一批复习卡片做短 TTL 缓存（含抖动）。

降级行为：

1. Redis 访问异常时，服务会回退 DB，不中断主流程。

### 4.2 限流与链路跟踪

1. `RateLimitFilter`：进程内内存计数，按分钟/IP 限流（默认 240 rpm），非分布式。
2. `TraceIdFilter`：注入/透传 `X-Trace-Id`，并放入响应头与 `ApiResponse.traceId`。

## 5. 数据库与导入实况

### 5.1 Flyway 与表结构

MySQL/H2 都有 V1/V2 迁移脚本，核心表包含：

1. `users`
2. `languages`
3. `terms`
4. `senses`
5. `translations`
6. `example_sentences`
7. `vocab_lists`
8. `vocab_items`
9. `user_progress`
10. `review_logs`
11. `ai_jobs`

### 5.2 高频词导入链路

数据源：

1. `datasets/high-frequency-vocabulary/30k-explained.txt`

实现：

1. `HighFrequencyVocabImportRunner`（受 `app.import.enabled=true` 控制）。
2. 脚本：
   - `demo/scripts/import-high-frequency.ps1`
   - `demo/scripts/run-h2-dev.ps1`
   - `demo/scripts/smoke-h2.ps1`
3. 支持参数：`profile`、`limit`、`dry-run`、`exit-on-finish` 等。

## 6. 前端接入实况（与代码一致）

1. 词典查询已改为后端 `/v1/dictionary/*`，不再依赖 RapidAPI。
2. Journal 操作全部通过 `backendAPI.js` 调 `/v1/lists/*`。
3. 复习池通过 `/v1/review/next` + `/v1/terms/{id}` 组装展示数据。
4. 评分提交 `/v1/review/{termId}/result`，并回刷 Journal。
5. 当前仍保留“开发便捷登录”逻辑：前端自动生成账号并自动 register/login。
6. 图片搜索仍走 `pexels` 客户端（前端直连第三方，不经后端）。
7. AI 能力目前只在后端有接口，前端暂无完整业务闭环接入。

## 7. 本地运行链路（脚本行为）

`start-dev.ps1` 当前行为：

1. 启动 MySQL/Redis（可选 `-ResetData`）。
2. 自动检查 `terms` 是否为空，为空时自动导入词库（可 `-ForceImport`）。
3. 启动后端（`mysql` profile）。
4. 启动前端：
   - 优先 `react-scripts start`（PORT 强制 5173）
   - 否则回退到 Vite CLI（仅当依赖存在）
5. 记录 PID 与日志到 `.runtime`。

`stop-dev.ps1` 当前行为：

1. 根据 `.runtime/dev-processes.json` 停止前后端进程。
2. 默认只停 MySQL/Redis 容器；`-RemoveData` 会删卷。

## 8. 与“项目实际”仍有差距的点

1. 登录态是“自动注册/自动登录 dev 账号”，不是正式账号体系体验。
2. Refresh Token 无撤销机制，安全策略仍偏基础。
3. 限流为单机内存实现，横向扩展后不一致。
4. AI 作业不是真正异步执行模型，吞吐和隔离性有限。
5. 前端图片检索直连第三方，存在密钥与配额管理风险。
6. 自动化测试覆盖较薄（后端仅基础测试类，缺少关键链路集成回归）。

## 9. 建议的下一步（按优先级）

1. 去除前端自动账号逻辑，补齐正式登录/会话管理与退出流程。
2. 将 AI 作业迁移为消息队列 + worker 异步执行。
3. 将限流升级为 Redis 分布式方案。
4. 增加后端集成测试：Auth、List、Review、Import、AI。
5. 梳理前端图片策略（后端代理或签名方案），避免前端直接暴露第三方调用风险。
