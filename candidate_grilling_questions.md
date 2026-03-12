# Baicizhan 项目面试拷打题（20 问，含参考回答）

> 目标：围绕当前仓库真实实现提问，重点考察候选人是否“真的做过”这个项目。

## 1. 学习链路总流程

**问题：** 你在 `LearningService.nextCards` 里为什么要走 `Recall -> Feature -> Rank -> Re-rank -> toReviewCards` 这条链路？每一步输入输出是什么？

**参考回答：** 这是标准推荐流水线分层，便于独立优化。`Recall` 产出候选词 ID，`Feature` 把候选词转为固定长度特征向量，`Rank` 产出打分排序，`Re-rank` 做业务约束（配额/多样性），最后再查词条+进度组装给前端。每层可单测和替换实现。

## 2. 候选召回配额与去重

**问题：** `CandidateRecallService` 里 `review/hard/new` 三路召回如何配额？同一个词被多路召回时怎么决策？

**参考回答：** 配额来自 `LearningProperties.recall`，先按 `review -> hard -> new` 分配。合并时按 source 优先级（REVIEW > HARD > NEW > EMBEDDING）和 recallScore 选更优版本，用 `LinkedHashMap` 保证去重后顺序可控。

## 3. ANN 召回降级策略

**问题：** ANN 服务挂掉会不会拖垮主流程？你做了什么保护？

**参考回答：** `AnnRecallClient` 用短超时（默认 300ms），异常捕获 `RestClientException` 后直接返回空列表并记录 warn，主流程继续走规则召回，不会把 `/v1/review/next` 卡死。在当前学习流程中，核心召回主要依赖规则召回（review/hard/new），因为学习任务需要严格控制复习节奏。ANN 召回更多是作为补充召回来源，例如通过词向量相似度扩展候选词，或者根据用户学习历史生成语义相关词推荐，并且在系统中做了降级保护以避免影响主流程。

## 4. 用户向量构建细节

**问题：** 你怎么构造用户 embedding？为什么用最近词并加权平均？

**参考回答：** 从 `review_logs` 取最近去重词（按最近复习时间排序），对词做 hash embedding（300 维）后按 `0.9^i` 衰减加权平均，最近行为权重更高。这样实现简单、可解释，且不依赖外部模型文件。

## 5. 特征工程设计

**问题：** 你的 LTR 特征 `f1...f10` 各是什么？默认值为什么这么设？

**参考回答：** `f1` 新词标记，`f2` 逾期天数，`f3` 距上次复习天数，`f4` 30 天错题数，`f5` 最近评分，`f6` ease factor，`f7` repetition，`f8` 词频排名，`f9` 难度分，`f10` 词长。默认值用于冷启动和缺失容错，比如频次默认大值（100000）代表“不高频”。

## 6. 在线特征缓存一致性

**问题：** 你在 `ReviewService.submit` 里写了 Redis 在线特征缓存，这样做会不会和 DB 统计不一致？

**参考回答：** 会有短期偏差，但可接受：写入时实时更新 `lastRating` 和 `wrong30d`，保证下一次推荐能快速感知用户反馈；Redis 挂了也不影响主真相（DB）。后续可用定时回填或按 term 细粒度 TTL 改善窗口漂移问题。

## 7. LTR 契约防错位

**问题：** 你怎么防止“训练特征顺序”和“线上推理顺序”错位？

**参考回答：** 启动时加载 `ltr_feature_schema.json` 和 `ltr_xgb.json`，校验 `featureNames` 必须一致；运行时再次校验 `features.length`。一旦不一致直接 fail-fast，避免静默劣化。

## 8. 重排策略与多样性

**问题：** `ReRankingService` 为什么先按配额取 NEW/REVIEW/HARD，再做连续来源限制？

**参考回答：** 两步控制体验：先保证题型结构（比如新词、复习词比例），再用 `maxConsecutiveSameSource` 限制连续同来源，减少“全是同类题”带来的疲劳。EMBEDDING 被归入 REVIEW 桶，语义上更接近复习补充。

## 9. `/v1/review/next` 缓存策略

**问题：** `nextCards` 为什么加缓存？TTL 还要加随机抖动？

**参考回答：** 这个接口高频且计算链路较长，缓存能降后端压力。TTL 抖动（基础 TTL + 0~5 秒）防止大量 key 同时过期造成瞬时击穿。

## 10. 提交复习结果的一致性

**问题：** `submit` 里先写 `review_logs` 再更新 `user_progress`，如果中间失败会怎样？

**参考回答：** 方法是事务性的，DB 层会一起回滚。Redis 更新和缓存删除是“尽力而为”，失败不影响主流程 correctness，只影响短期命中率和实时性。

## 11. SM-2 细节追问

**问题：** 你实现的 SM-2 在评分 <3 和 >=3 时分别怎么更新 `repetition/interval/ease`？

**参考回答：** `<3`：重置 repetition=0，interval=1，ease 减 0.2。`>=3`：按 repetition 走 1 天/6 天/`interval*ease`，再按 `(5-rating)` 罚分公式更新 ease，并下限钳制到 1.30，最后 round 到 2 位小数。

## 12. JWT 刷新机制的安全边界

**问题：** 你现在的 refresh token 方案有什么安全短板？怎么补？

**参考回答：** 当前是无状态 JWT，只校验签名与 `typ=refresh`，没有服务端撤销列表和设备绑定，泄露后在有效期内可用。可补：refresh token 持久化+轮换（rotation）+黑名单+设备指纹。

## 13. 限流实现在多实例下的问题

**问题：** `RateLimitFilter` 用进程内 `ConcurrentHashMap` 计数，扩容到多实例会怎样？

**参考回答：** 限流会“按实例分摊”，不再是全局一致；重启会清空计数；`X-Forwarded-For` 还存在伪造风险。生产应迁移到 Redis/Lua 或网关层限流，并做可信代理链校验。

## 14. 词详情缓存防穿透/击穿

**问题：** `TermService` 里 `NULL_TOKEN + lock:term:{id}` 的设计想解决什么问题？还缺什么？

**参考回答：** `NULL_TOKEN` 防缓存穿透，分布式锁+短等待防缓存击穿。缺点是锁失败后仍可能并发回源，且锁过期固定 5 秒。可加互斥续期、singleflight、本地热点缓存进一步优化。

## 15. 索引与查询路径

**问题：** 你为什么给 `user_progress(user_id, next_review_at)` 建索引？`review_logs` 相关索引是否够用？

**参考回答：** due 查询是 `where user_id=? and next_review_at<=? order by next_review_at`，复合索引能显著减少扫描。`review_logs` 目前有 `(user_id, created_at)` 和 `(term_id, created_at)`，对 hard/recent 统计有帮助，但高并发下可考虑补 `(user_id, term_id, created_at)`。

## 16. 删除生词与学习进度的关系

**问题：** `VocabService.deleteItem` 为什么只在“用户所有列表都不含该 term”时才删 `user_progress`？

**参考回答：** 避免误删仍在其他词表使用的学习进度。只有当词对用户完全失效时才清理进度，保证复习历史与业务语义一致。

## 17. 前端 401 并发刷新

**问题：** `backendAPI.js` 里为什么要 `pendingQueue`？没有它会怎样？

**参考回答：** 多请求同时 401 时只发一次 refresh，其他请求排队复用新 token。没有队列会导致 refresh 风暴、竞态覆盖 token，甚至反复失败。

## 18. 前端取消请求与竞态

**问题：** 你在 Review/Quiz/Flashcards 页面反复 `AbortController.abort()` 的价值是什么？

**参考回答：** 防止组件切换后旧请求回写 state，避免“脏响应覆盖新状态”和内存泄漏告警。并且通过 `isRequestCanceled` 把取消和真实错误分开处理。

## 19. 结果回传的部分失败处理

**问题：** `useReviewResult` 为什么用 `Promise.allSettled`，而不是 `Promise.all`？

**参考回答：** 允许部分成功，先落库成功项，再记录失败 termId 并提供重试按钮，减少整批失败对体验的影响。同步后还会刷新 journal，确保前端状态最终一致。

## 20. AI 任务“伪异步”设计

**问题：** `AiService.createEnrichJob` 返回 202 queued，但立即 `runLocalJob` 同步执行，这种实现的利弊是什么？

**参考回答：** 优点是实现简单、链路短；缺点是接口时延受 LLM 影响，且和“真正异步队列”语义不完全一致。后续应拆为消息队列/任务执行器，webhook 做幂等和签名校验，完善重试与死信机制。
