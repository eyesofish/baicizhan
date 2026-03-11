import axios from 'axios'

const API_BASE_URL =
  (process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080').replace(
    /\/+$/,
    ''
  )
const ACCESS_TOKEN_KEY = 'baicizhan_access_token'
const REFRESH_TOKEN_KEY = 'baicizhan_refresh_token'
const JOURNAL_LIST_ID_KEY = 'baicizhan_journal_list_id'
const DEV_EMAIL_KEY = 'baicizhan_dev_email'
const DEV_PASSWORD_KEY = 'baicizhan_dev_password'
const DEV_NAME_KEY = 'baicizhan_dev_name'

let accessToken = localStorage.getItem(ACCESS_TOKEN_KEY) || ''
let refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY) || ''

const client = axios.create({
  baseURL: API_BASE_URL,
  timeout: 12000
})

const refreshClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 12000
})

let refreshing = false
let pendingQueue = []

const setTokens = (tokens) => {
  accessToken = tokens.accessToken || ''
  refreshToken = tokens.refreshToken || ''
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
}

const clearTokens = () => {
  accessToken = ''
  refreshToken = ''
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

export const isRequestCanceled = (error) =>
  axios.isCancel(error) ||
  error?.code === 'ERR_CANCELED' ||
  error?.name === 'CanceledError'

const optionalText = (value) => {
  const text = typeof value === 'string' ? value.trim() : ''
  return text === '' ? undefined : text
}

const unwrap = (response) => {
  if (!response?.data) {
    throw new Error('EMPTY_RESPONSE')
  }
  if (response.data.code !== 0) {
    const error = new Error(response.data.message || 'REQUEST_FAILED')
    error.code = response.data.code
    throw error
  }
  return response.data.data
}

client.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error?.response?.status
    const original = error?.config || {}
    const isAuthApi = original?.url?.includes('/v1/auth/')

    if (
      status !== 401 ||
      !refreshToken ||
      original._retry ||
      isAuthApi
    ) {
      return Promise.reject(error)
    }

    original._retry = true

    if (refreshing) {
      return new Promise((resolve, reject) => {
        pendingQueue.push({ resolve, reject, config: original })
      })
    }

    refreshing = true
    try {
      const refreshed = await refreshClient.post('/v1/auth/refresh', {
        refreshToken
      })
      const tokens = unwrap(refreshed)
      setTokens(tokens)

      pendingQueue.forEach(({ resolve, config }) => {
        config.headers = config.headers || {}
        config.headers.Authorization = `Bearer ${accessToken}`
        resolve(client(config))
      })
      pendingQueue = []

      original.headers = original.headers || {}
      original.headers.Authorization = `Bearer ${accessToken}`
      return client(original)
    } catch (refreshError) {
      clearTokens()
      pendingQueue.forEach(({ reject }) => reject(refreshError))
      pendingQueue = []
      return Promise.reject(refreshError)
    } finally {
      refreshing = false
    }
  }
)

const getOrCreateDevAccount = () => {
  let email = localStorage.getItem(DEV_EMAIL_KEY)
  let password = localStorage.getItem(DEV_PASSWORD_KEY)
  let displayName = localStorage.getItem(DEV_NAME_KEY)

  if (!email || !password || !displayName) {
    const seed = `${Date.now()}${Math.floor(Math.random() * 10000)}`
    email = `dev-${seed}@local.test`
    password = `DevPass#${seed}`.slice(0, 32)
    if (password.length < 8) {
      password = 'DevPass#2026'
    }
    displayName = 'Dev User'
    localStorage.setItem(DEV_EMAIL_KEY, email)
    localStorage.setItem(DEV_PASSWORD_KEY, password)
    localStorage.setItem(DEV_NAME_KEY, displayName)
  }

  return { email, password, displayName }
}

export const ensureBackendSession = async (signal) => {
  if (accessToken) {
    return
  }

  const account = getOrCreateDevAccount()
  try {
    const registerResp = await refreshClient.post('/v1/auth/register', {
      email: account.email,
      password: account.password,
      displayName: account.displayName
    }, { signal })
    setTokens(unwrap(registerResp))
    return
  } catch (error) {
    const msg = error?.response?.data?.message
    const canLoginFallback =
      msg === 'EMAIL_ALREADY_EXISTS' || error?.response?.status === 409
    if (!canLoginFallback) {
      throw error
    }
  }

  const loginResp = await refreshClient.post('/v1/auth/login', {
    email: account.email,
    password: account.password
  }, { signal })
  setTokens(unwrap(loginResp))
}

const getMyLists = async (signal) => {
  await ensureBackendSession(signal)
  const response = await client.get('/v1/lists', { signal })
  return unwrap(response) || []
}

const createJournalList = async (signal) => {
  const response = await client.post('/v1/lists', {
    name: 'My Journal',
    sourceLanguage: 'en',
    targetLanguage: 'zh-Hans',
    isPublic: false
  }, { signal })
  return unwrap(response)
}

export const ensureJournalList = async (signal) => {
  const lists = await getMyLists(signal)
  const preferredId = Number(localStorage.getItem(JOURNAL_LIST_ID_KEY))
  if (preferredId && lists.some((list) => list.id === preferredId)) {
    return preferredId
  }

  let targetId
  if (lists.length > 0) {
    targetId = lists[0].id
  } else {
    const created = await createJournalList(signal)
    targetId = created.id
  }

  localStorage.setItem(JOURNAL_LIST_ID_KEY, String(targetId))
  return targetId
}

const mapItemToWord = (item) => ({
  id: item.itemId,
  itemId: item.itemId,
  listId: item.listId,
  termId: item.termId,
  senseId: item.senseId || null,
  word: item.word || '',
  pronunciation: item.pronunciation || '',
  partOfSpeech: item.partOfSpeech || '',
  definition: item.definition || '',
  synonyms: [],
  antonyms: [],
  examples: Array.isArray(item.examples) ? item.examples : [],
  images: [],
  points: Number(item.points || 0),
  lastReviewed: item.lastReviewed || null,
  created: item.createdAt || new Date().toISOString(),
  lastUpdated: item.updatedAt || item.createdAt || new Date().toISOString()
})

const buildItemPayload = (wordData) => {
  const examples = Array.isArray(wordData.examples)
    ? wordData.examples.map((item) => (typeof item === 'string' ? item.trim() : '')).filter((item) => item !== '')
    : []

  return {
    text: optionalText(wordData.word) || '',
    partOfSpeech: optionalText(wordData.partOfSpeech),
    definition: optionalText(wordData.definition),
    translation: optionalText(wordData.translation),
    example: examples.length > 0 ? examples[0] : undefined,
    ipa: optionalText(wordData.pronunciation),
    audioUrl: optionalText(wordData.audioUrl)
  }
}

export const loadJournalWords = async (signal) => {
  const listId = await ensureJournalList(signal)
  const response = await client.get(`/v1/lists/${listId}/items`, { signal })
  const items = unwrap(response) || []
  return {
    listId,
    words: items.map(mapItemToWord)
  }
}

export const addJournalWord = async (wordData) => {
  const listId = await ensureJournalList()
  await client.post(`/v1/lists/${listId}/items`, buildItemPayload(wordData))
  return loadJournalWords()
}

export const updateJournalWord = async (wordData) => {
  const listId = wordData.listId || (await ensureJournalList())
  const itemId = wordData.itemId || wordData.id
  if (!itemId) {
    throw new Error('MISSING_ITEM_ID')
  }
  await client.put(`/v1/lists/${listId}/items/${itemId}`, buildItemPayload(wordData))
  return loadJournalWords()
}

export const deleteJournalWord = async (itemId, listId) => {
  const targetListId = listId || (await ensureJournalList())
  await client.delete(`/v1/lists/${targetListId}/items/${itemId}`)
  return loadJournalWords()
}

export const getReviewCards = async (limit = 20, signal) => {
  await ensureBackendSession(signal)
  const response = await client.get('/v1/review/next', {
    params: { limit },
    signal
  })
  return unwrap(response) || []
}

export const getTermDetail = async (termId, signal) => {
  await ensureBackendSession(signal)
  const response = await client.get(`/v1/terms/${termId}`, { signal })
  return unwrap(response)
}

const mapReviewWord = (card, detail) => {
  const selectedSense =
    detail?.senses?.find((sense) => sense?.definition) || detail?.senses?.[0]
  const examples = Array.isArray(selectedSense?.examples)
    ? selectedSense.examples.map((item) => item?.sentenceText).filter((item) => typeof item === 'string' && item.trim() !== '')
    : []
  const points = Number(card.repetition || 0)

  return {
    id: card.termId,
    termId: card.termId,
    word: card.text || '',
    pronunciation: detail?.ipa || '',
    partOfSpeech: selectedSense?.partOfSpeech || '',
    definition: selectedSense?.definition || '',
    synonyms: [],
    antonyms: [],
    examples,
    images: [],
    points,
    originalPoints: points,
    pointsEarned: null,
    newPoints: points,
    lastReviewed: null,
    created: detail?.updatedAt || new Date().toISOString(),
    lastUpdated: detail?.updatedAt || new Date().toISOString()
  }
}

export const getReviewWordPool = async (limit = 20, signal) => {
  const cards = await getReviewCards(limit, signal)
  if (cards.length === 0) {
    return []
  }

  const detailResults = await Promise.allSettled(
    cards.map((card) => getTermDetail(card.termId, signal))
  )
  const detailsByTermId = {}
  detailResults.forEach((result, index) => {
    if (result.status === 'fulfilled') {
      detailsByTermId[cards[index].termId] = result.value
    }
  })

  const canceledDetailRequest = detailResults.find(
    (result) =>
      result.status === 'rejected' && isRequestCanceled(result.reason)
  )
  if (canceledDetailRequest) {
    throw canceledDetailRequest.reason
  }

  return cards.map((card) => mapReviewWord(card, detailsByTermId[card.termId]))
}

export const submitReviewResult = async (
  termId,
  rating,
  elapsedMs,
  signal
) => {
  await ensureBackendSession(signal)
  const response = await client.post(`/v1/review/${termId}/result`, {
    rating,
    elapsedMs
  }, { signal })
  return unwrap(response)
}

export const getApiErrorMessage = (error, fallback = 'Request failed') => {
  return error?.response?.data?.message || error?.message || fallback
}

export const normalizeApiError = (error, fallback = 'Request failed') => ({
  status: error?.response?.status || 0,
  code: error?.response?.data?.code || error?.code || 'UNKNOWN',
  message: getApiErrorMessage(error, fallback),
  traceId: error?.response?.data?.traceId || ''
})
