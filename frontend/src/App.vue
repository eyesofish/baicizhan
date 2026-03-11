<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { clearTokens, getApiBaseUrl, isLoggedIn, setApiBaseUrl, setTokens } from "./api/client";
import {
  addListItem,
  createAiEnrich,
  createList,
  getAiJob,
  getLists,
  getReviewCards,
  getTermDetail,
  login,
  register,
  submitReview
} from "./api/service";
import type { AiJob, ReviewCard, Sense, TermDetail, VocabList } from "./types";

const authTab = ref<"login" | "register">("login");
const mainTab = ref<"lists" | "term" | "review" | "ai">("lists");
const loggedIn = ref(isLoggedIn());
const apiBaseUrl = ref(getApiBaseUrl());

const loading = reactive({
  login: false,
  register: false,
  lists: false,
  createList: false,
  addItem: false,
  term: false,
  review: false,
  reviewSubmit: false,
  aiCreate: false,
  aiFetch: false
});

const loginForm = reactive({
  email: "",
  password: ""
});

const registerForm = reactive({
  email: "",
  password: "",
  displayName: ""
});

const languages = [
  { label: "English", value: "en" },
  { label: "Chinese (Simplified)", value: "zh-Hans" },
  { label: "Japanese", value: "ja" }
];

const lists = ref<VocabList[]>([]);

const createListForm = reactive({
  name: "My Starter List",
  sourceLanguage: "en",
  targetLanguage: "zh-Hans",
  isPublic: false
});

const addItemForm = reactive({
  listId: null as number | null,
  text: "",
  partOfSpeech: "",
  definition: "",
  translation: "",
  example: "",
  ipa: "",
  audioUrl: ""
});

const termQueryId = ref<number | null>(null);
const termDetail = ref<TermDetail | null>(null);

const reviewCards = ref<ReviewCard[]>([]);
const reviewIndex = ref(0);
const reviewStartAt = ref(0);

const aiCreateForm = reactive({
  termId: null as number | null,
  targetLang: "zh-Hans"
});
const aiQueryJobId = ref<number | null>(null);
const aiJobDetail = ref<AiJob | null>(null);

const currentReviewCard = computed(() => reviewCards.value[reviewIndex.value] ?? null);
const reviewProgress = computed(() => {
  if (!reviewCards.value.length) {
    return "0 / 0";
  }
  return `${Math.min(reviewIndex.value + 1, reviewCards.value.length)} / ${reviewCards.value.length}`;
});
const listOptions = computed(() => lists.value.map((x) => ({ label: `${x.name} (#${x.id})`, value: x.id })));
const prettyAiJob = computed(() => (aiJobDetail.value ? JSON.stringify(aiJobDetail.value, null, 2) : ""));

if (loggedIn.value) {
  void refreshLists();
}

function applyApiBaseUrl() {
  setApiBaseUrl(apiBaseUrl.value);
  apiBaseUrl.value = getApiBaseUrl();
  ElMessage.success(`API 地址已设置为 ${apiBaseUrl.value}`);
}

async function doLogin() {
  loading.login = true;
  try {
    const tokens = await login({
      email: loginForm.email.trim(),
      password: loginForm.password
    });
    setTokens(tokens);
    loggedIn.value = true;
    mainTab.value = "lists";
    loginForm.password = "";
    await refreshLists();
    ElMessage.success("登录成功");
  } catch (error) {
    showError("登录失败", error);
  } finally {
    loading.login = false;
  }
}

async function doRegister() {
  loading.register = true;
  try {
    const tokens = await register({
      email: registerForm.email.trim(),
      password: registerForm.password,
      displayName: registerForm.displayName.trim()
    });
    setTokens(tokens);
    loggedIn.value = true;
    mainTab.value = "lists";
    registerForm.password = "";
    await refreshLists();
    ElMessage.success("注册并登录成功");
  } catch (error) {
    showError("注册失败", error);
  } finally {
    loading.register = false;
  }
}

function doLogout() {
  clearTokens();
  loggedIn.value = false;
  lists.value = [];
  termDetail.value = null;
  reviewCards.value = [];
  reviewIndex.value = 0;
  aiJobDetail.value = null;
  ElMessage.info("已退出登录");
}

async function refreshLists() {
  loading.lists = true;
  try {
    const data = await getLists();
    lists.value = data;
    if (data.length > 0 && !addItemForm.listId) {
      addItemForm.listId = data[0].id;
    }
  } catch (error) {
    showError("获取词表失败", error);
  } finally {
    loading.lists = false;
  }
}

async function doCreateList() {
  loading.createList = true;
  try {
    const created = await createList(createListForm);
    lists.value.unshift(created);
    addItemForm.listId = created.id;
    ElMessage.success(`词表已创建：${created.name}`);
  } catch (error) {
    showError("创建词表失败", error);
  } finally {
    loading.createList = false;
  }
}

async function doAddItem() {
  if (!addItemForm.listId) {
    ElMessage.warning("请先选择词表");
    return;
  }
  if (!addItemForm.text.trim()) {
    ElMessage.warning("请输入词条文本");
    return;
  }
  loading.addItem = true;
  try {
    const result = await addListItem(addItemForm.listId, {
      text: addItemForm.text.trim(),
      partOfSpeech: optionalText(addItemForm.partOfSpeech),
      definition: optionalText(addItemForm.definition),
      translation: optionalText(addItemForm.translation),
      example: optionalText(addItemForm.example),
      ipa: optionalText(addItemForm.ipa),
      audioUrl: optionalText(addItemForm.audioUrl)
    });
    addItemForm.text = "";
    addItemForm.partOfSpeech = "";
    addItemForm.definition = "";
    addItemForm.translation = "";
    addItemForm.example = "";
    addItemForm.ipa = "";
    addItemForm.audioUrl = "";
    await refreshLists();
    ElMessage.success(`已添加词条，termId=${result.termId}`);
  } catch (error) {
    showError("添加词条失败", error);
  } finally {
    loading.addItem = false;
  }
}

async function fetchTermDetail() {
  if (!termQueryId.value) {
    ElMessage.warning("请输入 termId");
    return;
  }
  loading.term = true;
  try {
    termDetail.value = await getTermDetail(termQueryId.value);
  } catch (error) {
    termDetail.value = null;
    showError("查询词条失败", error);
  } finally {
    loading.term = false;
  }
}

async function loadReviewQueue() {
  loading.review = true;
  try {
    reviewCards.value = await getReviewCards(20);
    reviewIndex.value = 0;
    reviewStartAt.value = Date.now();
    if (reviewCards.value.length === 0) {
      ElMessage.info("当前没有待复习词条");
    } else {
      ElMessage.success(`加载到 ${reviewCards.value.length} 个复习项`);
    }
  } catch (error) {
    showError("获取复习队列失败", error);
  } finally {
    loading.review = false;
  }
}

async function submitCurrentReview(rating: number) {
  const card = currentReviewCard.value;
  if (!card) {
    ElMessage.info("没有可提交的复习项");
    return;
  }
  loading.reviewSubmit = true;
  try {
    await submitReview(card.termId, {
      rating,
      elapsedMs: Math.max(0, Date.now() - reviewStartAt.value)
    });
    if (reviewIndex.value < reviewCards.value.length - 1) {
      reviewIndex.value += 1;
      reviewStartAt.value = Date.now();
    } else {
      reviewCards.value = [];
      reviewIndex.value = 0;
      ElMessage.success("本轮复习完成");
    }
  } catch (error) {
    showError("提交复习结果失败", error);
  } finally {
    loading.reviewSubmit = false;
  }
}

async function createAiJob() {
  if (!aiCreateForm.termId) {
    ElMessage.warning("请输入 termId");
    return;
  }
  loading.aiCreate = true;
  try {
    const result = await createAiEnrich({
      termId: aiCreateForm.termId,
      targetLang: optionalText(aiCreateForm.targetLang)
    });
    aiQueryJobId.value = result.jobId;
    ElMessage.success(`AI 任务已入队，jobId=${result.jobId}`);
  } catch (error) {
    showError("创建 AI 任务失败", error);
  } finally {
    loading.aiCreate = false;
  }
}

async function fetchAiJob() {
  if (!aiQueryJobId.value) {
    ElMessage.warning("请输入 jobId");
    return;
  }
  loading.aiFetch = true;
  try {
    aiJobDetail.value = await getAiJob(aiQueryJobId.value);
  } catch (error) {
    showError("查询 AI 任务失败", error);
  } finally {
    loading.aiFetch = false;
  }
}

function optionalText(value: string): string | undefined {
  const text = value.trim();
  return text ? text : undefined;
}

function showError(prefix: string, error: unknown) {
  const message = extractErrorMessage(error);
  ElMessage.error(`${prefix}: ${message}`);
}

function extractErrorMessage(error: unknown): string {
  if (typeof error === "string") {
    return error;
  }
  if (error && typeof error === "object") {
    const maybe = error as Record<string, any>;
    const responseMessage = maybe?.response?.data?.message;
    if (typeof responseMessage === "string" && responseMessage.trim()) {
      return responseMessage;
    }
    if (typeof maybe.message === "string" && maybe.message.trim()) {
      return maybe.message;
    }
  }
  return "未知错误";
}

function formatTime(value: string | undefined | null): string {
  if (!value) {
    return "-";
  }
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

function senseTitle(sense: Sense, idx: number): string {
  const pos = sense.partOfSpeech ? `[${sense.partOfSpeech}] ` : "";
  return `${pos}Sense ${idx + 1}`;
}
</script>

<template>
  <div class="app-shell">
    <div class="shape shape-1"></div>
    <div class="shape shape-2"></div>
    <div class="shape shape-3"></div>

    <header class="app-header panel">
      <div class="brand">
        <h1>Baicizhan MVP Console</h1>
        <p>Vue3 + Element Plus · 对接 Spring Boot API</p>
      </div>
      <div class="header-actions">
        <el-input v-model="apiBaseUrl" placeholder="http://localhost:8080" class="url-input" clearable />
        <el-button type="primary" plain @click="applyApiBaseUrl">应用 API 地址</el-button>
        <el-button v-if="loggedIn" type="danger" plain @click="doLogout">退出</el-button>
      </div>
    </header>

    <main class="app-main">
      <section v-if="!loggedIn" class="panel auth-panel">
        <el-tabs v-model="authTab" stretch>
          <el-tab-pane label="登录" name="login">
            <el-form label-position="top" @submit.prevent>
              <el-form-item label="邮箱">
                <el-input v-model="loginForm.email" autocomplete="username" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="loginForm.password" type="password" show-password autocomplete="current-password" />
              </el-form-item>
              <el-button type="primary" :loading="loading.login" @click="doLogin">登录</el-button>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="注册" name="register">
            <el-form label-position="top" @submit.prevent>
              <el-form-item label="昵称">
                <el-input v-model="registerForm.displayName" />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="registerForm.email" autocomplete="username" />
              </el-form-item>
              <el-form-item label="密码（至少8位）">
                <el-input v-model="registerForm.password" type="password" show-password autocomplete="new-password" />
              </el-form-item>
              <el-button type="success" :loading="loading.register" @click="doRegister">注册并登录</el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </section>

      <section v-else class="panel workspace">
        <el-tabs v-model="mainTab">
          <el-tab-pane label="词表管理" name="lists">
            <div class="grid two-col">
              <el-card shadow="never">
                <template #header>
                  <div class="card-head">创建词表</div>
                </template>
                <el-form label-position="top">
                  <el-form-item label="词表名称">
                    <el-input v-model="createListForm.name" />
                  </el-form-item>
                  <el-form-item label="源语言">
                    <el-select v-model="createListForm.sourceLanguage">
                      <el-option v-for="lang in languages" :key="lang.value" :label="lang.label" :value="lang.value" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="目标语言">
                    <el-select v-model="createListForm.targetLanguage">
                      <el-option v-for="lang in languages" :key="lang.value" :label="lang.label" :value="lang.value" />
                    </el-select>
                  </el-form-item>
                  <el-form-item>
                    <el-switch v-model="createListForm.isPublic" inline-prompt active-text="公开" inactive-text="私有" />
                  </el-form-item>
                  <el-button type="primary" :loading="loading.createList" @click="doCreateList">创建词表</el-button>
                </el-form>
              </el-card>

              <el-card shadow="never">
                <template #header>
                  <div class="card-head with-action">
                    <span>我的词表</span>
                    <el-button size="small" :loading="loading.lists" @click="refreshLists">刷新</el-button>
                  </div>
                </template>
                <el-table :data="lists" size="small" height="220">
                  <el-table-column prop="id" label="ID" width="70" />
                  <el-table-column prop="name" label="名称" />
                  <el-table-column label="语言" width="140">
                    <template #default="{ row }">
                      {{ row.sourceLanguage }} → {{ row.targetLanguage }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="itemCount" label="词条" width="70" />
                </el-table>

                <div class="sub-title">向词表添加词条</div>
                <el-form label-position="top">
                  <el-form-item label="目标词表">
                    <el-select v-model="addItemForm.listId" placeholder="请选择词表">
                      <el-option v-for="opt in listOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="词条文本">
                    <el-input v-model="addItemForm.text" placeholder="例如: resilient" />
                  </el-form-item>
                  <div class="grid two-col compact">
                    <el-form-item label="词性">
                      <el-input v-model="addItemForm.partOfSpeech" placeholder="adj / noun / verb..." />
                    </el-form-item>
                    <el-form-item label="音标">
                      <el-input v-model="addItemForm.ipa" placeholder="/rɪˈzɪliənt/" />
                    </el-form-item>
                  </div>
                  <el-form-item label="释义">
                    <el-input v-model="addItemForm.definition" type="textarea" :rows="2" />
                  </el-form-item>
                  <el-form-item label="翻译">
                    <el-input v-model="addItemForm.translation" placeholder="有韧性的；能恢复的" />
                  </el-form-item>
                  <el-form-item label="例句">
                    <el-input v-model="addItemForm.example" type="textarea" :rows="2" />
                  </el-form-item>
                  <el-button type="success" :loading="loading.addItem" @click="doAddItem">添加词条</el-button>
                </el-form>
              </el-card>
            </div>
          </el-tab-pane>

          <el-tab-pane label="词条详情" name="term">
            <el-card shadow="never">
              <div class="inline-actions">
                <el-input-number v-model="termQueryId" :min="1" controls-position="right" />
                <el-button type="primary" :loading="loading.term" @click="fetchTermDetail">查询</el-button>
              </div>
              <el-empty v-if="!termDetail" description="输入 termId 后查询词条详情" />
              <div v-else class="term-detail">
                <el-descriptions :column="2" border>
                  <el-descriptions-item label="termId">{{ termDetail.id }}</el-descriptions-item>
                  <el-descriptions-item label="词面">{{ termDetail.text }}</el-descriptions-item>
                  <el-descriptions-item label="标准化">{{ termDetail.normalizedText }}</el-descriptions-item>
                  <el-descriptions-item label="语言">{{ termDetail.language }}</el-descriptions-item>
                  <el-descriptions-item label="IPA">{{ termDetail.ipa || "-" }}</el-descriptions-item>
                  <el-descriptions-item label="更新时间">{{ formatTime(termDetail.updatedAt) }}</el-descriptions-item>
                </el-descriptions>

                <el-collapse class="sense-collapse">
                  <el-collapse-item
                    v-for="(sense, idx) in termDetail.senses"
                    :key="sense.id"
                    :title="senseTitle(sense, idx)"
                    :name="sense.id"
                  >
                    <div class="sense-block">
                      <p><strong>释义：</strong>{{ sense.definition || "-" }}</p>
                      <p class="sub-label">翻译</p>
                      <el-tag
                        v-for="trans in sense.translations"
                        :key="trans.id"
                        class="tag-space"
                        effect="plain"
                        type="success"
                      >
                        {{ trans.targetLanguage }}: {{ trans.translatedText }}
                      </el-tag>
                      <p class="sub-label">例句</p>
                      <el-card v-for="ex in sense.examples" :key="ex.id" shadow="hover" class="example-card">
                        <p>{{ ex.sentenceText }}</p>
                        <p class="muted">{{ ex.sentenceTrans || "-" }}</p>
                      </el-card>
                    </div>
                  </el-collapse-item>
                </el-collapse>
              </div>
            </el-card>
          </el-tab-pane>

          <el-tab-pane label="复习闭环" name="review">
            <el-card shadow="never">
              <div class="inline-actions">
                <el-button type="primary" :loading="loading.review" @click="loadReviewQueue">获取复习队列</el-button>
                <el-tag type="warning" effect="dark">进度 {{ reviewProgress }}</el-tag>
              </div>

              <el-empty v-if="!currentReviewCard" description="点击“获取复习队列”开始复习" />
              <div v-else class="review-card">
                <h3>{{ currentReviewCard.text }}</h3>
                <p class="muted">language: {{ currentReviewCard.language }}</p>
                <p class="muted">
                  ease={{ currentReviewCard.easeFactor }} · interval={{ currentReviewCard.intervalDays }}d ·
                  repetition={{ currentReviewCard.repetition }}
                </p>
                <div class="rating-group">
                  <el-button
                    v-for="rating in [0, 1, 2, 3, 4, 5]"
                    :key="rating"
                    :type="rating >= 4 ? 'success' : rating >= 3 ? 'primary' : 'danger'"
                    plain
                    :loading="loading.reviewSubmit"
                    @click="submitCurrentReview(rating)"
                  >
                    评分 {{ rating }}
                  </el-button>
                </div>
              </div>
            </el-card>
          </el-tab-pane>

          <el-tab-pane label="AI任务" name="ai">
            <div class="grid two-col">
              <el-card shadow="never">
                <template #header>
                  <div class="card-head">创建异步 AI 任务</div>
                </template>
                <el-form label-position="top">
                  <el-form-item label="termId">
                    <el-input-number v-model="aiCreateForm.termId" :min="1" controls-position="right" />
                  </el-form-item>
                  <el-form-item label="目标语言">
                    <el-input v-model="aiCreateForm.targetLang" placeholder="zh-Hans" />
                  </el-form-item>
                  <el-button type="primary" :loading="loading.aiCreate" @click="createAiJob">提交任务</el-button>
                </el-form>
              </el-card>

              <el-card shadow="never">
                <template #header>
                  <div class="card-head">查询任务状态</div>
                </template>
                <div class="inline-actions">
                  <el-input-number v-model="aiQueryJobId" :min="1" controls-position="right" />
                  <el-button :loading="loading.aiFetch" @click="fetchAiJob">刷新状态</el-button>
                </div>
                <el-empty v-if="!aiJobDetail" description="先创建任务，或输入 jobId 查询" />
                <pre v-else class="json-output">{{ prettyAiJob }}</pre>
              </el-card>
            </div>
          </el-tab-pane>
        </el-tabs>
      </section>
    </main>
  </div>
</template>
