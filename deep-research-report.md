# Baicizhan 推荐系统 v2-lite（工程可执行版）傻瓜教程

> 仓库路径：`D:\Github\baicizhan`  
> 校准日期：`2026-03-11`  
> 目标：把当前项目升级成可面试讲的 v2-lite 链路：`Recall(rule+embedding) -> Feature -> LTR ranking -> Re-ranking -> Policy(SRS)`。  
> 原则：尽量复用现有代码，不大拆前端，不改现有主接口形状。  
> 固定技术栈：`ANN=Faiss`，`Ranking=XGBoost`，`Feature=MySQL+Redis`。  
> 工程约束：不训练重型 Two-Tower，使用 `小词向量(默认 GloVe 300d) + 用户历史平均向量` 实现 embedding recall。

---

## 0. 你最终要做成什么

```text
Mobile App (React)
    │
API Gateway (现有 Controller 层)
    │
Learning Service (新增，后端编排层)
    │
Recall Service
  (rule recall + embedding recall)
    │
ANN Service (FastAPI + Faiss)
    │
Feature Service
  (MySQL + Redis)
    │
LTR Ranking Service
  (XGBoost)
    │
Re-ranking Service
    │
Policy / Scheduling Layer
  (SM-2)
    │
Storage (MySQL + Redis)
```

说明：前端仍调用 `GET /v1/review/next` 和 `POST /v1/review/{termId}/result`。  
你只把“后端内部实现”升级成推荐系统分层，前端几乎不用大改。  
召回从“纯规则”升级为“规则召回 + embedding 召回融合”。  
SRS 在架构中作为 `Policy/Scheduling` 层，不只是算法函数。

---

## 1. 当前项目和目标的对应关系（按真实代码）

| 目标层 | 当前状态 | 你要做的事 |
|---|---|---|
| API Gateway | 已有（`ReviewController`） | 保留不动 |
| Learning Service | 没有独立层 | 新增 `learning` 包做总编排 |
| Recall | 只有 `due + bootstrap` | 扩成 `rule + embedding` 混合召回 |
| Embedding Strategy | 没有 | 默认 `glove.6B.300d.txt` + 用户近期词向量平均（后续可切 fastText） |
| ANN Search | 没有 | 固定使用 `Faiss` 做 TopK 向量检索 |
| Online Inference | 没有 | 固定 `Java -> HTTP -> FastAPI(Faiss)` |
| Feature Service | 没有 | 固定使用 `MySQL + Redis` 组装特征 |
| Ranking | 没有 | 固定使用 `XGBoost` 做 LTR 精排（强约束特征顺序） |
| Re-ranking | 逻辑在前端 `reviewHelper.js` | 挪到后端，按配额和多样性重排 |
| Policy/Scheduling | SRS 混在 `ReviewService.updateSrs` | 拆成独立调度层（SM-2）+ 单测 |
| Storage | 已有 MySQL + Redis + Flyway | 新增 `term_stats` 表支持排序特征 |

---

## 2. 开工前先跑通基线（必须先过）

### 2.1 环境检查

```powershell
java -version
node -v
npm -v
docker --version
docker compose version
conda --version
```

### 2.2 创建 Python 环境（Conda）

```powershell
conda create -n baicizhan python=3.11 -y
conda activate baicizhan
python -V
pip install --upgrade pip
pip install fastapi uvicorn faiss-cpu numpy pandas scikit-learn xgboost fasttext-wheel
```

说明：

1. 这里用你指定的环境名：`baicizhan`。
2. 后续跑 `FastAPI/Faiss` 和 `train-ltr.py` 都在这个环境里执行。
3. `Faiss` 一律使用 `pip install faiss-cpu`，不要自己编译源码。

### 2.3 安装前端依赖（首次）

```powershell
cd D:\Github\baicizhan\frontend
npm install
```

### 2.4 一键启动

```powershell
cd D:\Github\baicizhan
powershell -NoProfile -ExecutionPolicy Bypass -File .\start-dev.ps1
```

### 2.5 验收

1. 前端：`http://localhost:5173`
2. 后端：`http://localhost:8080/actuator/health`
3. Swagger：`http://localhost:8080/swagger-ui.html`

### 2.6 构建验收（我已在当前仓库跑过，能通过）

```powershell
cd D:\Github\baicizhan\demo
.\mvnw.cmd -q -DskipTests package

cd D:\Github\baicizhan\frontend
npm run build
```

### 2.7 启动 ANN 服务（测试 embedding recall 前必须执行）

```powershell
conda activate baicizhan
cd D:\Github\baicizhan
uvicorn ann_service:app --host 0.0.0.0 --port 18080
```

说明：

1. `start-dev.ps1` 不会自动启动 Python ANN 服务。
2. 如果 ANN 没启动，Java 侧调用向量召回会直接报错。

---

## 3. 施工总顺序（别跳步）

1. 新建分支，保证每步可回滚。
2. 新增数据库特征表 `term_stats`。
3. 新增 Learning 分层代码骨架。
4. 实现 Recall（三路召回）。
5. 实现 Feature（特征组装）。
6. 实现 embedding recall（`GloVe(默认)/fastText + Faiss`，含在线推断）。
7. 实现 Ranking（XGBoost LTR，含特征 schema 锁定）。
8. 实现 Re-ranking（配额 + 多样性）。
9. 接入 `ReviewService.nextCards`，保持旧 API 不变。
10. 把 SM-2 从 `ReviewService` 拆出来作为 Policy 层并写单测。
11. 做端到端验收与指标检查。

---

## 4. Step 1：新建分支

```powershell
cd D:\Github\baicizhan
git checkout -b feat/reco-pipeline
```

---

## 5. Step 2：补数据库特征底座（`term_stats`）

### 5.1 新增 Flyway 脚本

新增文件：

1. `demo/src/main/resources/db/migration/mysql/V3__term_stats.sql`
2. `demo/src/main/resources/db/migration/h2/V3__term_stats.sql`

MySQL 版本可直接用：

```sql
CREATE TABLE term_stats (
  term_id            BIGINT PRIMARY KEY,
  frequency_rank     INT NOT NULL,
  difficulty_score   DECIMAL(5,2) NOT NULL DEFAULT 50.00,
  source_type        VARCHAR(32) NOT NULL DEFAULT 'import',
  updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_term_stats_term FOREIGN KEY (term_id) REFERENCES terms(id)
) ENGINE=InnoDB;

CREATE INDEX idx_term_stats_freq ON term_stats(frequency_rank);
CREATE INDEX idx_term_stats_diff ON term_stats(difficulty_score);
```

H2 版本把时间列改成 `TIMESTAMP` 即可。

### 5.2 新增实体和仓库

新增文件：

1. `demo/src/main/java/com/example/demo/domain/entity/TermStat.java`
2. `demo/src/main/java/com/example/demo/domain/repository/TermStatRepository.java`

字段最少保留：`termId`, `frequencyRank`, `difficultyScore`, `sourceType`, `updatedAt`。

### 5.3 导入时回填 `term_stats`

修改：`demo/src/main/java/com/example/demo/tools/HighFrequencyVocabImportRunner.java`

做法：

1. 按导入顺序给 `frequencyRank`（第 1 个词 rank=1）。
2. 用简化规则给 `difficultyScore`（例如词长越长分越高）。
3. 每次导入做 upsert（已存在就更新）。

### 5.4 验收

```powershell
cd D:\Github\baicizhan
powershell -NoProfile -ExecutionPolicy Bypass -File .\start-dev.ps1 -ForceImport -ImportLimit 5000

docker compose exec mysql mysql -uroot -proot -D baicizhan -e "SELECT COUNT(*) AS term_cnt FROM terms;"
docker compose exec mysql mysql -uroot -proot -D baicizhan -e "SELECT COUNT(*) AS stat_cnt FROM term_stats;"
```

通过标准：`stat_cnt` 接近或等于 `term_cnt`。

---

## 6. Step 3：搭 Learning 分层骨架

在后端新建包：`com.example.demo.learning`

建议新增类：

1. `LearningService`（总编排）
2. `CandidateRecallService`
3. `FeatureService`
4. `RankingService`
5. `ReRankingService`
6. `dto/CandidateTerm.java`
7. `dto/FeatureVector.java`
8. `dto/RankedTerm.java`

`LearningService` 目标流程：

```text
召回(200) -> 特征 -> 排序 -> 重排(20) -> 转成 ReviewCardResponse
```

---

## 7. Step 4：实现 Recall（50000 -> 200）

### 7.1 三路召回规则

1. `review_words`：`user_progress.next_review_at <= now`（现有逻辑）。
2. `hard_words`：最近 30 天评分低（`review_logs.rating <= 2`）。
3. `new_words`：词单里有但还没有 `user_progress`。

推荐配额（可配置）：

1. review: 100
2. hard: 50
3. new: 50

### 7.2 你要改的仓库层

1. `UserProgressRepository`：复用 `findDueReviews`。
2. `VocabItemRepository`：复用 `findTermIdsWithoutProgress`。
3. `ReviewLogRepository`：新增 hard 词查询（按错题次数+最近时间排序）。

### 7.3 验收

临时加一个 debug 日志（或 debug 接口）打印召回数量：

```text
recall.review=100 recall.hard=50 recall.new=50 recall.total=200
```

---

## 8. Step 5：实现 Feature Service（模型输入）

### 8.1 固定特征 schema（必须锁顺序）

必须固定特征顺序，训练和线上保持 100% 一致：

1. `f1=is_new_word`
2. `f2=overdue_days`
3. `f3=days_since_last_review`
4. `f4=wrong_count_30d`
5. `f5=last_rating`
6. `f6=ease_factor`
7. `f7=repetition`
8. `f8=frequency_rank`
9. `f9=difficulty_score`
10. `f10=word_length`

### 8.2 落地建议（固定：MySQL + Redis）

1. `FeatureService` 输入：`userId + candidateTermIds`。
2. `MySQL` 负责离线/慢变特征：词频、难度、历史统计。
3. `Redis` 负责在线/快变特征：最近答题、短期错误计数、会话上下文。
4. 一次性批量查库，避免 N+1。
5. 缺失值给默认值，不抛错（保证推荐接口稳定）。
6. 新增 `feature_schema.json`，写死 `f1...f10` 映射，训练脚本和 Java 推断共用。

### 8.3 验收

对同一用户重复请求时，打印一条样例特征：

```text
term=123 features={isNew=0, overdue=3.0, wrong30d=2, ease=2.1, freqRank=450}
```

---

## 9. Step 6：实现 embedding recall（GloVe/fastText + Faiss + Online Inference）

### 9.0 启动 ANN Python 服务（Conda 环境）

```powershell
conda activate baicizhan
cd D:\Github\baicizhan
uvicorn ann_service:app --host 0.0.0.0 --port 18080
```

说明：

1. `ann_service:app` 是 FastAPI 服务入口（按你实际文件名替换）。
2. Java 侧配置 ANN 地址时，统一指向 `http://127.0.0.1:18080`。
3. ANN 服务必须先启动；未启动时 Java 调用会直接报错。

### 9.0.1 三个必看避坑（先看再做）

1. `Faiss` 安装只用：`conda activate baicizhan` + `pip install faiss-cpu`，不要手动编译。
2. 先启动 ANN：`uvicorn ann_service:app --host 0.0.0.0 --port 18080`，否则 Java 侧 HTTP 会失败。
3. 词向量先用小模型 `glove.6B.300d.txt`；`cc.en.300.bin`（约 1GB）放到后续再切换。

### 9.1 Embedding 方案（v2-lite 固定）

不训练重型 Two-Tower，固定用下面方案：

1. 词向量：默认 `glove.6B.300d.txt`（小、快、易跑）。
2. 进阶切换：`cc.en.300.bin`（fastText 官方模型，约 1GB）。
3. 词向量存储：`term_id -> embedding`（离线预计算并落盘）。
4. 用户向量：最近 N 个复习词向量做加权平均（可按时间衰减）。

这样做的原因：

1. 当前项目日志量不够训练稳定 Two-Tower。
2. 默认用小词向量能显著降低下载和启动成本。
3. 复杂度显著下降，仍能跑通语义召回。

### 9.2 ANN 索引构建（固定：Faiss）

索引要求（Faiss）：

1. 支持按 `term_id` 回查。
2. 支持增量 upsert（新词入库、词向量重算）。
3. 默认索引（50k 向量）：`IndexFlatIP`。
4. 规模上来后（>= 1M 向量）再切 `IndexHNSWFlat`。
5. 相似度：余弦相似度（向量先归一化，再用内积）。
6. 说明：`IndexFlatIP` 是暴力检索，但在 50k 向量规模下 CPU 延迟通常可接受，工程实现最稳。

### 9.3 Online Inference（固定部署方式）

固定调用链：

```text
Spring Boot (Java)
    -> HTTP
FastAPI ANN Service (Python + Faiss)
```

在线流程：

1. Java 侧计算用户向量（平均池化）。
2. 调 FastAPI 服务做 Faiss `topK=200~500` 检索。
3. 与规则召回（`review/hard/new`）做去重融合。
4. 输出给 Feature + XGBoost LTR。

### 9.4 延迟与可用性目标（必须写进验收）

1. Faiss 查询 p95 < 30ms。
2. Java 端用户向量构建 p95 < 20ms。
3. `review/next` 整体 p95 < 150ms（不含冷启动极端场景）。
4. FastAPI/Faiss 故障时自动降级到规则召回，不影响主流程可用性。

---

## 10. Step 7：实现 Ranking（LTR，固定 XGBoost）

### 10.1 训练数据定义（必须有 query group）

真正 LTR 不是“手写加权求分”，而是基于组内排序学习。  
建议按下面方式构造训练样本：

1. `qid`：`user_id + session_date`（同一批候选属于同一个 query）。
2. `label`：可用复习结果映射，例如 `rating<=2 -> 2`, `rating=3 -> 1`, `rating>=4 -> 0`。
3. `features`：复用 Step 5 的特征向量。

建议新增训练样本表（可选）：

1. `ltr_samples(qid, user_id, term_id, label, f1...fn, created_at)`

### 10.2 训练模型（固定 XGBoost LambdaMART）

训练脚本建议放在：

1. `demo/scripts/train-ltr.py`

核心训练参数示例：

```python
params = {
    "objective": "rank:ndcg",
    "eval_metric": "ndcg@10",
    "eta": 0.05,
    "max_depth": 6,
    "min_child_weight": 20,
    "subsample": 0.9,
    "colsample_bytree": 0.8,
    "lambda": 1.0,
}
```

产物保存到：

1. `demo/models/ltr_xgb.json`
2. `demo/models/ltr_feature_schema.json`（固定 `f1...f10` 顺序）

### 10.3 在线推断接入（Java）

在后端新增真实模型推断实现：

1. `RankingService#rank(List<FeatureVector>)` 保留接口。
2. 新增 `XgboostLtrRankingService`，启动时加载 `demo/models/ltr_xgb.json`。
3. 每个候选词把特征按 `f1...f10` 顺序转成 `float[]`，调用模型推断得到分数。
4. 按模型分数降序输出给 Re-ranking。
5. 模型加载时校验 `ltr_feature_schema.json`，不一致直接启动失败。

### 10.4 必做兜底

如果模型文件不存在或加载失败：

1. 直接抛启动错误（推荐，避免“悄悄退化”）。
2. 或降级到“最近一次可用模型快照”，并打 ERROR 日志。
3. 特征维度或顺序不匹配时，禁止继续推断并打致命日志。

---

## 11. Step 8：实现 Re-ranking（体验控制层）

重排目标：不能 20 个全难词，避免用户崩溃。

规则建议：

1. 配额：`new/review/hard = 10/7/3`（`limit=20` 时）。
2. 同源词最多连续 2 个。
3. 难词间隔穿插普通词。
4. 不够配额时，按总分从其它池补位。

输出仍然是 `ReviewCardResponse`，前端无需改协议。

---

## 12. Step 9：接入现有接口（保持前端稳定）

### 12.1 修改点

文件：`demo/src/main/java/com/example/demo/review/ReviewService.java`

改造方式：

1. `nextCards()` 不再自己做查询和组装。
2. 改为调用 `LearningService.nextCards(userId, limit)`。
3. 保留 Redis 缓存键：`user:{userId}:nextReview`。
4. `submit()` 保持原接口不变。

这样前端 `frontend/src/services/backendAPI.js` 基本不用改。

---

## 13. Step 10：实现 Policy/Scheduling Layer（SM-2）

### 13.1 新增类

1. `demo/src/main/java/com/example/demo/review/SpacedRepetitionScheduler.java`

把 `ReviewService.updateSrs()` 的逻辑移动进来，并明确该层是 `Policy/Scheduling`。

### 13.2 写单测

新增测试文件：

1. `demo/src/test/java/com/example/demo/review/SpacedRepetitionSchedulerTest.java`

至少覆盖：

1. `rating < 3` 时重置 `repetition=0`，`interval=1`
2. 第一次答对 `interval=1`
3. 第二次答对 `interval=6`
4. `easeFactor` 下限是 `1.30`

---

## 14. Step 11：端到端验收（按这个清单跑）

### 14.1 准备数据

1. 前端进入 Journal，加至少 30 个词。
2. 做几轮复习，制造 `hard` 和 `review` 历史。

### 14.2 检查推荐输出

调用：

```http
GET /v1/review/next?limit=20
```

通过标准：

1. 返回 20 条
2. 能看到混合分布（不是清一色新词或难词）
3. 复习后提交结果，`user_progress` 会更新
4. 日志中可看到 `rule_recall_count`、`embedding_recall_count`、`merged_count`
5. 关闭 ANN 服务后，系统能自动降级仍返回结果

### 14.3 SQL 快速核对

```powershell
docker compose exec mysql mysql -uroot -proot -D baicizhan -e "SELECT COUNT(*) FROM user_progress;"
docker compose exec mysql mysql -uroot -proot -D baicizhan -e "SELECT COUNT(*) FROM review_logs;"
docker compose exec mysql mysql -uroot -proot -D baicizhan -e "SELECT user_id, term_id, repetition, next_review_at FROM user_progress ORDER BY updated_at DESC LIMIT 20;"
```

### 14.4 最终闭环标准（最重要）

1. 打开网页。
2. 点击复习。
3. 能看到不同难度单词混合出现。
4. 完成答题后，下一轮推荐明显发生变化。

---

## 15. 面试时你怎么讲（30 秒版）

“这个项目不是 CRUD 背单词。我把复习推荐做成了推荐系统流水线：  
先做规则召回和 embedding 召回，ANN 用 Faiss 检索语义候选；特征层由 MySQL+Redis 提供；精排层用 XGBoost 做 LTR，最后再做重排保证学习体验。  
复习结果进入 Policy/Scheduling 层（SM-2），驱动下一轮推荐，形成闭环。”

---

## 16. 进阶（可选，字节简化版）

当前文档是稳定可落地的 v2-lite。你做完后再升级：

1. 多路召回融合学习（learning-to-recall）
2. 向量量化与压缩（PQ/OPQ）降低内存成本
3. 在线特征服务（Redis + 流式更新）
4. Bandit / RL 做探索利用平衡

---

## 17. 完成定义（DoD）

满足以下 10 条，就算“整个项目完成”：

1. 本地一键启动成功（`start-dev.ps1`）。
2. 数据库有 `term_stats` 并有数据。
3. `ReviewService.nextCards` 已切到 Learning 分层。
4. Recall 三路召回可观测（日志或调试接口）。
5. 已接入 embedding 在线推断和 Faiss 向量检索。
6. Feature 由 MySQL + Redis 联合提供。
7. 排序与重排都在后端执行，且排序模型为 XGBoost。
8. `POST /v1/review/{termId}/result` 后 SRS 生效。
9. SM-2 拆分为独立类并有单测。
10. 前端可正常完成一次 20 词复习闭环。

---

如果你想继续下一版，我建议直接做 v3：`多任务学习 + 实时特征流 + 线上 A/B 平台`，把训练和服务迭代速度也工程化。
