# 前端测试后端全接口傻瓜教程（新手版）

> 目标：不使用 Postman，只用“前端页面 + 浏览器开发者工具”，把后端接口都测一遍。  
> 适用目录：`D:\Github\baicizhan`

## 1. 你将覆盖到的接口

本教程会覆盖以下全部业务接口：

- `POST /v1/auth/register`
- `POST /v1/auth/login`
- `POST /v1/auth/refresh`
- `GET /v1/dictionary/lookup`
- `GET /v1/dictionary/match`
- `GET /v1/dictionary/random`
- `POST /v1/lists`
- `GET /v1/lists`
- `POST /v1/lists/{listId}/items`
- `GET /v1/lists/{listId}/items`
- `PUT /v1/lists/{listId}/items/{itemId}`
- `DELETE /v1/lists/{listId}/items/{itemId}`
- `GET /v1/terms/{termId}`
- `GET /v1/review/next`
- `POST /v1/review/{termId}/result`
- `POST /v1/ai/enrich`
- `GET /v1/ai/jobs/{jobId}`
- `POST /v1/webhooks/openai`

## 2. 启动环境（一步步照抄）

### 2.1 打开 3 个 PowerShell 窗口

### 窗口 A：启动依赖（MySQL + Redis）
```powershell
cd D:\Github\baicizhan
docker compose up -d
```

### 窗口 B：启动后端
```powershell
cd D:\Github\baicizhan\demo
.\mvnw.cmd -s settings.xml spring-boot:run
```

看到类似 `Started BackendApplication` 说明后端启动成功（端口默认 `8080`）。

### 窗口 C：启动前端（强制 5173，避免 CORS）
```powershell
cd D:\Github\baicizhan\frontend
npm install
$env:PORT=5173
npm start
```

浏览器打开：`http://localhost:5173`

## 3. 先用页面测一遍（不用写代码）

按下面顺序点击页面：

1. 打开 `Search` 页面，输入 `apple`（会调用 `dictionary/match`）。
2. 点击候选词进入词义页面（会调用 `dictionary/lookup`）。
3. 在词义页把单词加入词本（会触发 `lists` + `items` 相关接口）。
4. 打开 `Journal` 页面查看词本内容（会调用 `GET /v1/lists`、`GET /v1/lists/{id}/items`）。
5. 在 `Journal` 里编辑、删除一个词（会测 `PUT` 和 `DELETE /items`）。
6. 打开 `Review` -> `Flashcards` 或 `Quiz` 做一轮复习（会测 `review/next`、`terms/{id}`、`review/{termId}/result`）。

到这里你已经覆盖了大部分接口。  
下面用浏览器 Console 补齐“页面不方便直接测”的接口（例如 refresh、AI、webhook）。

## 4. 用浏览器 Console 补齐全部接口

在前端页面按 `F12` 打开开发者工具，切到 `Console`，粘贴下面工具代码：

```js
const API = 'http://localhost:8080';
let ACCESS = '';
let REFRESH = '';

async function api(method, path, body, withAuth = true) {
  const headers = { 'Content-Type': 'application/json' };
  if (withAuth && ACCESS) headers.Authorization = `Bearer ${ACCESS}`;

  const resp = await fetch(`${API}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined
  });

  const json = await resp.json().catch(() => ({}));
  console.log(`[${method}] ${path}`, json);
  return json;
}
```

### 4.1 测 auth 三个接口

```js
const seed = Date.now();
const account = {
  email: `starter-${seed}@local.test`,
  password: `DevPass#${seed}`.slice(0, 20),
  displayName: 'Starter User'
};

const reg = await api('POST', '/v1/auth/register', account, false);
ACCESS = reg?.data?.accessToken || '';
REFRESH = reg?.data?.refreshToken || '';
```

```js
const login = await api('POST', '/v1/auth/login', {
  email: account.email,
  password: account.password
}, false);
ACCESS = login?.data?.accessToken || ACCESS;
REFRESH = login?.data?.refreshToken || REFRESH;
```

```js
const refresh = await api('POST', '/v1/auth/refresh', {
  refreshToken: REFRESH
}, false);
ACCESS = refresh?.data?.accessToken || ACCESS;
REFRESH = refresh?.data?.refreshToken || REFRESH;
```

### 4.2 测词本和词条接口

```js
const createdList = await api('POST', '/v1/lists', {
  name: 'Beginner List',
  sourceLanguage: 'en',
  targetLanguage: 'zh-Hans',
  isPublic: false
});
const listId = createdList?.data?.id;
```

```js
await api('GET', '/v1/lists');
```

```js
const createdItem = await api('POST', `/v1/lists/${listId}/items`, {
  text: 'apple',
  partOfSpeech: 'noun',
  definition: 'a round fruit',
  translation: '苹果',
  example: 'I eat an apple every day.',
  ipa: '/ˈæp.əl/',
  audioUrl: ''
});
const termId = createdItem?.data?.termId;
const itemId = createdItem?.data?.itemId;
```

```js
await api('GET', `/v1/lists/${listId}/items`);
```

```js
await api('PUT', `/v1/lists/${listId}/items/${itemId}`, {
  text: 'apple',
  partOfSpeech: 'noun',
  definition: 'an edible fruit from an apple tree',
  translation: '苹果（可食用水果）',
  example: 'The apple is sweet.',
  ipa: '/ˈæp.əl/',
  audioUrl: ''
});
```

```js
await api('GET', `/v1/terms/${termId}`);
```

### 4.3 测 dictionary 三个接口（公开接口）

```js
await api('GET', '/v1/dictionary/lookup?word=apple', null, false);
await api('GET', '/v1/dictionary/match?prefix=ap&limit=10', null, false);
await api('GET', '/v1/dictionary/random', null, false);
```

### 4.4 测 review 两个接口

```js
const next = await api('GET', '/v1/review/next?limit=20');
const reviewTermId = next?.data?.[0]?.termId || termId;
```

```js
await api('POST', `/v1/review/${reviewTermId}/result`, {
  rating: 4,
  elapsedMs: 3200
});
```

### 4.5 测 AI 两个接口

```js
const aiQueued = await api('POST', '/v1/ai/enrich', {
  termId: reviewTermId,
  targetLang: 'zh-Hans'
});
const jobId = aiQueued?.data?.jobId;
```

```js
await api('GET', `/v1/ai/jobs/${jobId}`);
```

说明：如果你本地没启动 LLM 服务，`/v1/ai/enrich` 也可能返回排队成功，但 job 状态会变成 `FAILED`，这仍然算接口测试通过。

### 4.6 测 webhook 接口（公开接口）

```js
await api('POST', '/v1/webhooks/openai', {
  type: 'response.completed',
  data: { id: 'manual-test-response-id' }
}, false);
```

### 4.7 回收测试数据（测 delete）

```js
await api('DELETE', `/v1/lists/${listId}/items/${itemId}`);
await api('GET', `/v1/lists/${listId}/items`);
```

## 5. 如何判断“真的测通了”

每次响应都看这 3 点：

1. HTTP 状态码是 2xx（例如 200/202）。
2. 返回 JSON 里 `code` 为 `0`。
3. 返回里有 `traceId`（便于排查）。

标准成功结构：

```json
{
  "code": 0,
  "message": "OK",
  "data": {},
  "traceId": "..."
}
```

## 6. 常见报错与处理

### 401 UNAUTHORIZED
- 原因：没带 token，或 token 过期。
- 处理：重新执行 `login` 或 `refresh`，把 `ACCESS` 更新后再测。

### 400 参数校验错误
- 原因：请求体字段缺失/格式不对（比如邮箱不合法、密码过短）。
- 处理：按本教程示例字段名和格式重发。

### 404 TERM_NOT_FOUND / VOCAB_LIST_NOT_FOUND
- 原因：ID 不存在或已删除。
- 处理：重新走创建流程，使用最新 `termId/listId/itemId`。

### 前端跨域报错（CORS）
- 原因：前端不是 `5173` 端口。
- 处理：前端用 `$env:PORT=5173` 启动，或在后端 CORS 配置里加入你的端口。

## 7. 一次性回归清单（打勾用）

- [ ] auth: register/login/refresh
- [ ] dictionary: lookup/match/random
- [ ] lists: create/list
- [ ] items: add/list/update/delete
- [ ] terms: detail
- [ ] review: next/result
- [ ] ai: enrich/job detail
- [ ] webhook: openai

全部勾上就说明“前端视角下后端全接口功能”已经测完。

