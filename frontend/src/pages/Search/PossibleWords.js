import React, { useEffect, useMemo, useState } from 'react'
import { useNavigate, Outlet } from 'react-router-dom'
import { getMatchedWords } from '../../services/wordAPI'
import { useSelector, useDispatch } from 'react-redux'
import {
  setMatchedWords as setPossibleWordsMatchedWords,
  setIsLoading as setPossibleWordsIsLoading,
  setError as setPossibleWordsError
} from '../../reducers/possibleWordsReducer'
import { normalizeApiError } from '../../services/backendAPI'

const CACHE_EXPIRATION_MS = 5 * 60 * 1000 // 5 min

const PossibleWords = () => {
  const dispatch = useDispatch()
  const navigate = useNavigate()
  const { searchValue } = useSelector((state) => state.search)
  const { matchedWords, isLoading, error } = useSelector(
    (state) => state.possibleWords
  )

  const [isNotFound, setIsNotFound] = useState(false)
  const [retryToken, setRetryToken] = useState(0)
  const cache = useMemo(() => ({}), [])

  const wordStyleClassName =
    'cursor-pointer border-2 border-gray-100 p-3 rounded-xl text-lg font-medium text-indigo-900 hover:border-indigo-100 hover:bg-indigo-100 hover:text-indigo-900 select-none'

  useEffect(() => {
    const query = searchValue.trim().toLowerCase()

    if (query === '') {
      dispatch(setPossibleWordsMatchedWords([]))
      dispatch(setPossibleWordsError(null))
      dispatch(setPossibleWordsIsLoading(false))
      setIsNotFound(false)
      return
    }

    const controller = new AbortController()

    const isCacheExpired = (cacheItem) =>
      cacheItem && Date.now() - cacheItem.timestamp > CACHE_EXPIRATION_MS

    const cleanupExpiredCache = () => {
      Object.keys(cache).forEach((key) => {
        if (isCacheExpired(cache[key])) {
          delete cache[key]
        }
      })
    }

    const timeoutId = setTimeout(async () => {
      try {
        cleanupExpiredCache()

        dispatch(setPossibleWordsIsLoading(true))
        dispatch(setPossibleWordsError(null))

        if (cache[query] && !isCacheExpired(cache[query])) {
          const cachedWords = cache[query].data
          dispatch(setPossibleWordsMatchedWords(cachedWords))
          setIsNotFound(cachedWords.length === 0)
          return
        }

        const returnedWords = await getMatchedWords(query, controller.signal)
        const nextMatchedWords = returnedWords?.results?.data || []
        cache[query] = {
          timestamp: Date.now(),
          data: nextMatchedWords
        }

        dispatch(setPossibleWordsMatchedWords(nextMatchedWords))
        setIsNotFound(nextMatchedWords.length === 0)
      } catch (error) {
        if (error?.code === 'ERR_CANCELED') {
          return
        }

        setIsNotFound(false)
        dispatch(
          setPossibleWordsError(
            normalizeApiError(
              error,
              'Sorry, we could not fetch possible words. Please try again later.'
            )
          )
        )
      } finally {
        if (!controller.signal.aborted) {
          dispatch(setPossibleWordsIsLoading(false))
        }
      }
    }, 200)

    return () => {
      clearTimeout(timeoutId)
      controller.abort()
    }
  }, [searchValue, dispatch, cache, retryToken])

  const matchedWordsElement = (() => {
    if (isLoading && searchValue !== '') {
      return <div>Loading...</div>
    }

    if (searchValue === '') {
      return null
    }

    if (error) {
      return (
        <div className='flex flex-col items-start gap-2'>
          <div>Error: {error.message}</div>
          <button
            type='button'
            className='border-2 border-indigo-100 rounded-lg px-3 py-1 text-sm font-semibold hover:bg-indigo-100 hover:text-indigo-800'
            onClick={() => setRetryToken((value) => value + 1)}>
            Retry
          </button>
        </div>
      )
    }

    if (matchedWords.length > 0) {
      return matchedWords.map((word, index) => (
        <div
          key={word + index}
          className={wordStyleClassName}
          onClick={() => navigate(`${word}`)}>
          {word}
        </div>
      ))
    }

    if (isNotFound) {
      return <div>Word not found</div>
    }

    return null
  })()

  return (
    <div>
      <div className='search--matched-words flex flex-col gap-4'>
        {matchedWordsElement}
      </div>
      <Outlet />
    </div>
  )
}

export default PossibleWords
