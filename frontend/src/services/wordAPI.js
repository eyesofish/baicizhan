import axios from 'axios'

const API_BASE_URL =
  (process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080').replace(
    /\/+$/,
    ''
  )

const client = axios.create({
  baseURL: API_BASE_URL,
  timeout: 12000
})

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

const getWordData = async (word) => {
  try {
    const response = await client.get('/v1/dictionary/lookup', {
      params: { word }
    })
    return unwrap(response)
  } catch (error) {
    if (error.response && error.response.status === 404) {
      console.error('word not found')
      return {
        word,
        results: []
      }
    } else {
      console.error(error)
      throw error
    }
  }
}

const getMatchedWords = async (word) => {
  try {
    const response = await client.get('/v1/dictionary/match', {
      params: {
        prefix: word,
        limit: 10
      }
    })
    return unwrap(response)
  } catch (error) {
    if (error.response && error.response.status === 404) {
      console.error('word not found')
      return { results: { data: [] } }
    } else {
      console.error(error)
      throw error
    }
  }
}

const getRandomWord = async () => {
  try {
    const response = await client.get('/v1/dictionary/random')
    return unwrap(response)
  } catch (error) {
    console.error(error)
    throw error
  }
}

export { getWordData, getMatchedWords, getRandomWord }
