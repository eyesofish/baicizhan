# Baicizhan v2-lite: 从背单词 App 到可讲清楚的推荐系统工程

> 这不是一个「单词列表 + 刷题页」项目。  
> 这是一个可运行的教育推荐系统缩小版：`Retrieval -> Ranking -> Policy`。

## 项目亮点（面试官先看这里）

1. 把背单词核心链路做成了推荐系统标准分层，而不是 CRUD：
   `Recall(rule + embedding) -> Feature(MySQL + Redis) -> LTR -> Re-ranking -> SRS(SM-2)`。
2. 后端 `/v1/review/*` API 形状保持不变，内部升级为可扩展的 Learning Pipeline，前端可无缝对接。
3. LTR 采用稳定特征契约：
   `f1...f10` + `ltr_feature_schema.json` + `ltr_xgb.json`，并在运行时做严格顺序校验，避免训练/线上错位。
4. Embedding Recall 设计为工业可扩展接口：
   Java 侧 `AnnRecallClient` 通过 HTTP 调用 ANN 服务，异常自动降级为规则召回（服务可用性优先）。
5. 前端交互按真实产品质量打磨：
   可取消请求、可重试、Review Due 驱动入口、结果 `Promise.allSettled` 提交与失败项重试。
6. 工程稳定性考虑到位：
   JWT 鉴权、限流过滤器、Redis 缓存穿透/击穿防护、Flyway 迁移、可一键拉起本地环境。

## 架构总览

```text
React App
   |
Spring Boot API Gateway (/v1/*)
   |
LearningService
   |
CandidateRecallService
  |- rule recall: review/hard/new
  |- embedding recall: ANN client (HTTP)
   |
FeatureService (MySQL + Redis online feature)
   |
XgboostLtrRankingService (LTR contract)
   |
ReRankingService (配额 + 多样性)
   |
SpacedRepetitionScheduler (SM-2 policy)
   |
MySQL + Redis
```

## 推荐链路（真实代码对应）

1. `Recall`
   `CandidateRecallService` 组合 `review/hard/new` 三路规则召回，并融合 embedding recall 候选。
2. `Feature`
   `FeatureService` 批量组装特征，慢变特征来自 MySQL，快变特征（如 wrong30d/lastRating）优先 Redis。
3. `LTR Ranking`
   `XgboostLtrRankingService` 读取 `models/ltr_feature_schema.json` 与 `models/ltr_xgb.json`，按固定 schema 打分排序。
4. `Re-ranking`
   `ReRankingService` 按 new/review/hard 配额与连续来源约束做重排，避免体验崩坏。
5. `Policy/Scheduling`
   `SpacedRepetitionScheduler` 按 SM-2 更新 `easeFactor/interval/repetition/nextReviewAt`。

## 关键工程细节

1. 新增 `term_stats` 表（Flyway V3），为排序提供 `frequency_rank`、`difficulty_score`。
2. `ReviewService.nextCards()` 已切换到 `LearningService` 编排，提交接口保持原有路径与返回结构。
3. 提交复习结果时会更新 Redis 在线特征缓存，下一轮推荐可立即感知用户反馈。
4. ANN 服务不可用时不会拖垮主流程，系统回退到规则召回继续工作。

## 技术栈

1. Backend: Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Redis, Flyway
2. Frontend: React 18, Redux Toolkit, Axios
3. Storage: MySQL 8, Redis 7
4. ML/Ranking artifacts: XGBoost-style LTR contract (`f1..f10` schema + model artifact)
5. ANN（可插拔）: Faiss via external Python/FastAPI service

## 本地快速运行（Git Bash，Windows）

### 1. 前置依赖

1. JDK 21
2. Node.js 18+
3. Docker Desktop（Running）
4. PowerShell

### 2. 安装前端依赖

```bash
cd /d/Github/baicizhan/frontend
npm install
```

### 3. 一键启动（后端 + 前端 + MySQL + Redis）

```bash
cd /d/Github/baicizhan
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./start-dev.ps1
```

启动后访问：

1. Frontend: `http://localhost:5173`
2. Backend: `http://localhost:8080`
3. Swagger: `http://localhost:8080/swagger-ui.html`

### 4. 停止服务

```bash
cd /d/Github/baicizhan
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./stop-dev.ps1
```

## 可选：ANN/Faiss 环境（用于 embedding recall）

> 当前仓库已具备 Java 侧 ANN 调用与降级逻辑。  
> Python ANN 服务可独立部署，通过 HTTP 接入。

```bash
conda create -n baicizhan python=3.11 -y
conda activate baicizhan
pip install faiss-cpu fastapi uvicorn numpy
```

如果你已有 `ann_service.py`：

```bash
conda activate baicizhan
uvicorn ann_service:app --host 0.0.0.0 --port 18080
```

## API 示例（核心闭环）

1. `POST /v1/auth/register` / `POST /v1/auth/login`
2. `GET /v1/review/next?limit=20` 获取下一批卡片
3. `POST /v1/review/{termId}/result` 提交评分（支持 `elapsedMs`）
4. 重复 2/3，可观察推荐结果随用户反馈动态变化

## 数据与迁移

1. 数据集位于 `datasets/high-frequency-vocabulary/`
2. 启动脚本会在 `terms` 为空时自动导入
3. Flyway 管理 schema，`V3__term_stats.sql` 已落地

## 测试与构建

```bash
cd /d/Github/baicizhan/demo
./mvnw.cmd -q test
./mvnw.cmd -q -DskipTests package

cd /d/Github/baicizhan/frontend
npm run build
```

## 当前边界与下一步升级

1. 当前 `XgboostLtrRankingService` 使用轻量线性推断契约（接口与模型工件已稳定）。
2. 下一步可无缝替换为原生 XGBoost 在线推断（保持 `RankingService` 接口不变）。
3. ANN 服务目前是外部可插拔组件；后续可补齐仓库内置 FastAPI + Faiss 服务实现。

## 仓库结构

```text
baicizhan/
├─ demo/                # Spring Boot backend
├─ frontend/            # React frontend
├─ datasets/            # 高频词数据
├─ start-dev.ps1        # 一键启动
├─ stop-dev.ps1         # 一键停止
├─ deep-research-report.md
└─ HOW_TO_RUN.md
```

## 联系方式

1. Name: `yang yu`
2. LinkedIn: `https://www.linkedin.com/in/yy030305/?locale=zh`
3. GitHub: `https://github.com/eyesofish`
4. Email: `devilsrocbuddhasgildedimage@gmail.com`
