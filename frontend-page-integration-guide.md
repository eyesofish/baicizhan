# 前端页面对接后端接口改造教程（交互版）

> 适用仓库：`D:\Github\baicizhan`  
> 目标：在不改 `/v1/review/*` API 形状的前提下，把前端页面改成“可感知加载、可重试、可恢复”的交互体验。  
> 结论先说：前端只对接 `Spring Boot API`，不要直接碰 ANN/Faiss 服务。

---

## 1. 先确认接口契约（当前后端）

后端统一返回：

```json
{
  "code": 0,
  "message": "OK",
  "data": {},
  "traceId": "..."
}
```

你前端要重点消费这些接口：

1. 鉴权
   `POST /v1/auth/register`
   `POST /v1/auth/login`
   `POST /v1/auth/refresh`
2. 词单
   `GET /v1/lists`
   `POST /v1/lists`
   `GET /v1/lists/{listId}/items`
   `POST /v1/lists/{listId}/items`
   `PUT /v1/lists/{listId}/items/{itemId}`
   `DELETE /v1/lists/{listId}/items/{itemId}`
3. 复习
   `GET /v1/review/next?limit=...`
   `POST /v1/review/{termId}/result`
4. 词详情
   `GET /v1/terms/{termId}`
5. 词典
   `GET /v1/dictionary/lookup`
   `GET /v1/dictionary/match`
   `GET /v1/dictionary/random`

关键字段（和你页面强相关）：

1. `VocabItemDetailResponse`：`itemId, termId, word, pronunciation, definition, examples, points, lastReviewed`
2. `ReviewCardResponse`：`termId, text, repetition, nextReviewAt`
3. `ReviewResultResponse`：`termId, rating, repetition, nextReviewAt`
4. `TermDetailResponse`：`ipa, senses[].partOfSpeech/definition/examples`

---

## 2. 要改哪些前端文件

优先改这 8 个：

1. `frontend/src/services/backendAPI.js`
2. `frontend/src/pages/Review/Review.js`
3. `frontend/src/pages/Review/FlashcardsMode.js`
4. `frontend/src/pages/Review/QuizMode.jsx`
5. `frontend/src/hooks/useReviewResult.js`
6. `frontend/src/pages/Journal.js`
7. `frontend/src/pages/Search/PossibleWords.js`
8. `frontend/src/pages/Search/WordMeanings.js`

---

## 3. Step A：先把 API 层改好（`backendAPI.js`）

### A1. 每个请求支持取消（避免页面切换后 setState 报错）

给这些函数增加 `signal` 参数：

1. `loadJournalWords(signal)`
2. `getReviewCards(limit, signal)`
3. `getTermDetail(termId, signal)`
4. `getReviewWordPool(limit, signal)`
5. `submitReviewResult(termId, rating, elapsedMs, signal)`

示例：

```js
export const getReviewCards = async (limit = 20, signal) => {
  await ensureBackendSession()
  const response = await client.get('/v1/review/next', {
    params: { limit },
    signal
  })
  return unwrap(response) || []
}
```

### A2. `getReviewWordPool` 改成可降级拼装

现在你已经是 `card + termDetail` 双请求，建议保留，但要“部分成功可展示”：

1. 单个 `termId` 详情失败时，不要整池失败。
2. 最终仍返回该单词，缺失字段用空值兜底。

### A3. 统一错误对象

新增一个规范化函数，页面不要直接读 `error.response`：

```js
export const normalizeApiError = (error) => ({
  status: error?.response?.status || 0,
  code: error?.response?.data?.code || error?.code || 'UNKNOWN',
  message: getApiErrorMessage(error, 'Request failed'),
  traceId: error?.response?.data?.traceId || ''
})
```

---

## 4. Step B：Review 首页用“到期队列”驱动，不用 Journal 长度驱动

文件：`frontend/src/pages/Review/Review.js`

当前问题：

1. 你现在用 `journal words.length` 判断是否可复习。
2. 这会出现“词单有词但当前无到期卡片”的误导。

改法：

1. 页面初始化请求 `getReviewCards(20)`。
2. 根据返回数量显示：
   `0`：暂无到期复习词
   `>0`：显示 Flashcards/Quiz 入口
3. 保留“去 Search 添加词”入口。

---

## 5. Step C：Flashcards/Quiz 入口页交互升级

文件：

1. `frontend/src/pages/Review/FlashcardsMode.js`
2. `frontend/src/pages/Review/QuizMode.jsx`

必须加的交互：

1. 加 `AbortController`，组件卸载时取消请求。
2. `Start` 按钮禁用条件：
   `isLoading || words.length === 0 || !mode || number === 0`
3. 错误态加 `Retry` 按钮（不是只显示红字）。
4. 显示“可复习数量”：`Due now: {words.length}`。

---

## 6. Step D：结果提交流程可观察、可恢复

文件：`frontend/src/hooks/useReviewResult.js`

### D1. 提交改成 `Promise.allSettled`

不要让单个失败拖垮整批提交：

1. 成功项更新本地分数。
2. 失败项记录到 `failedTermIds`，允许“重试失败项”。

### D2. 传 `elapsedMs`

后端 `POST /v1/review/{termId}/result` 支持 `elapsedMs`，前端建议传：

1. 在答题开始记录 `startedAt`。
2. 提交时 `elapsedMs = Date.now() - startedAt`。

### D3. 提交期间禁用跳转/二次提交

增加本地状态：

1. `isSyncingResult`
2. `syncError`

UI 上给出：

1. `Submitting review results...`
2. `Retry failed items`

---

## 7. Step E：Journal 和 Search 页交互细节

### E1. Journal（`frontend/src/pages/Journal.js`）

1. 首屏用 skeleton/placeholder，避免纯文本 “Loading...”
2. 加“刷新”按钮（请求失败可手动重拉）
3. 词条增删改后给轻量提示（toast 或 inline message）

### E2. Search（`PossibleWords.js` / `WordMeanings.js`）

1. 请求加 cancel，输入切换时取消旧请求
2. `Word not found` 与网络错误分开展示
3. 保留你已有 debounce（200ms），但错误要可重试

---

## 8. 一天能落地的最小改造（MVP）

如果你今天只做一版，按这个顺序：

1. `backendAPI.js` 增加 `signal + normalizeApiError`
2. `Review.js` 改成查 `/v1/review/next` 决定入口态
3. `FlashcardsMode.js` / `QuizMode.jsx` 加请求取消 + Retry + Start 按钮禁用
4. `useReviewResult.js` 改 `allSettled` + `elapsedMs`

做完这 4 步，交互会明显提升，而且对后端零破坏。

---

## 9. 联调验收清单（按页面走）

### 9.1 启动

1. 启后端与前端
2. 登录（当前项目是 dev 账号自动注册/登录）

### 9.2 Journal

1. 能加载词单
2. 添加/编辑/删除词后列表正确刷新
3. 失败态可重试

### 9.3 Review

1. `/review` 入口由“到期卡片”控制
2. Flashcards/Quiz 可拉到复习池
3. 断网或后端报错时页面不崩

### 9.4 提交结果

1. 提交后 `points/repetition` 更新
2. 部分失败时可重试失败项
3. 重新进 Review 能看到队列变化

---

## 10. 常见坑（你这个仓库最容易踩）

1. 只看 `journal.length` 不看 `review/next`，会导致入口判断不准。
2. 页面切换没取消请求，控制台会出现异步更新警告。
3. 批量提交用 `Promise.all`，一个失败全失败。
4. 不传 `elapsedMs`，后端就拿不到答题时长特征。
5. 前端误连 ANN/Faiss：不应该，前端只调 Spring Boot。

---

## 11. 你可以直接复用的页面状态模型

每个页面都统一四态：

1. `loading`
2. `error`
3. `empty`
4. `ready`

配合两个动作：

1. `retry()`
2. `cancel()`

这套模式套在 `Journal / Review / Search` 上，体验会稳定很多。

