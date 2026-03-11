import { useCallback, useEffect, useRef, useState } from 'react'
import { useDispatch } from 'react-redux'
import {
  setWordArray as setQuizWordArray,
  setInSession as setQuizInSession
} from '../reducers/quizReducer'
import {
  setWordArray as setFlashcardsWordArray,
  setInSession as setFlashcardsInSession
} from '../reducers/flashcardsReducer'
import {
  setListId as setJournalListId,
  setWords as setJournalWords
} from '../reducers/journalReducer'
import {
  getApiErrorMessage,
  loadJournalWords,
  submitReviewResult
} from '../services/backendAPI'

const normalizePoints = (value, defaultValue = 0) => {
  const number = Number(value)
  return Number.isFinite(number) ? number : defaultValue
}

const getFlashcardRating = (pointsEarned) => {
  if (pointsEarned <= -1) {
    return 1
  }
  if (pointsEarned >= 2) {
    return 5
  }
  return 3
}

const getQuizRating = (pointsEarned) => {
  if (pointsEarned <= 0) {
    return 2
  }
  if (pointsEarned === 1) {
    return 3
  }
  if (pointsEarned === 2) {
    return 4
  }
  return 5
}

const resolveRating = (page, pointsEarned) =>
  page === 'flashcards'
    ? getFlashcardRating(pointsEarned)
    : getQuizRating(pointsEarned)

const normalizeElapsedMs = (value) => {
  const elapsedMs = Number(value)
  return Number.isFinite(elapsedMs) && elapsedMs >= 0
    ? elapsedMs
    : undefined
}

const useReviewResult = (page, wordArray) => {
  const dispatch = useDispatch()
  const hasSyncedRef = useRef(false)
  const activeRef = useRef(true)
  const wordArrayRef = useRef(wordArray)
  const failedTermIdsRef = useRef([])

  const [isSyncingResult, setIsSyncingResult] = useState(false)
  const [syncError, setSyncError] = useState('')
  const [failedTermIds, setFailedTermIds] = useState([])

  useEffect(() => {
    wordArrayRef.current = wordArray
  }, [wordArray])

  useEffect(() => {
    activeRef.current = true
    return () => {
      activeRef.current = false
    }
  }, [])

  const syncReviewResult = useCallback(
    async (targetTermIds = null) => {
      const updateWordArray =
        page === 'quiz' ? setQuizWordArray : setFlashcardsWordArray
      const updateInSession =
        page === 'quiz' ? setQuizInSession : setFlashcardsInSession

      const normalizedWordArray = (wordArrayRef.current || []).map((word) => ({
        ...word,
        pointsEarned: normalizePoints(word.pointsEarned, 0),
        originalPoints: normalizePoints(word.originalPoints, word.points),
        newPoints: normalizePoints(
          word.newPoints,
          normalizePoints(word.originalPoints, word.points)
        ),
        elapsedMs: normalizeElapsedMs(word.elapsedMs)
      }))

      const reviewedWords = normalizedWordArray.filter((word) => {
        const termId = word.termId || word.id
        return Boolean(termId) && word.pointsEarned !== 0
      })

      const filteredReviewedWords = Array.isArray(targetTermIds)
        ? reviewedWords.filter((word) => targetTermIds.includes(word.termId || word.id))
        : reviewedWords

      if (filteredReviewedWords.length === 0) {
        dispatch(updateWordArray(normalizedWordArray))
        dispatch(updateInSession(false))
        failedTermIdsRef.current = []
        if (activeRef.current) {
          setFailedTermIds([])
          setSyncError('')
        }
        return
      }

      if (activeRef.current) {
        setIsSyncingResult(true)
        setSyncError('')
      }

      const responseByTerm = {}
      const settledResults = await Promise.allSettled(
        filteredReviewedWords.map((word) => {
          const termId = word.termId || word.id
          const rating = resolveRating(page, word.pointsEarned)
          return submitReviewResult(termId, rating, word.elapsedMs)
        })
      )

      const nextFailedTermIds = []
      settledResults.forEach((result, index) => {
        const termId = filteredReviewedWords[index].termId || filteredReviewedWords[index].id
        if (result.status === 'fulfilled') {
          responseByTerm[termId] = result.value
        } else {
          nextFailedTermIds.push(termId)
          console.error('submit review result failed', result.reason)
        }
      })

      const newWordArray = normalizedWordArray.map((word) => {
        const termId = word.termId || word.id
        const response = responseByTerm[termId]
        const fallbackPoints = normalizePoints(word.newPoints, word.originalPoints)
        const newPoints = response
          ? normalizePoints(response.repetition, fallbackPoints)
          : fallbackPoints

        return {
          ...word,
          newPoints
        }
      })

      dispatch(updateWordArray(newWordArray))
      dispatch(updateInSession(false))

      failedTermIdsRef.current = nextFailedTermIds

      if (activeRef.current) {
        setFailedTermIds(nextFailedTermIds)
        setSyncError(
          nextFailedTermIds.length > 0
            ? 'Some review results failed to sync. You can retry failed items.'
            : ''
        )
      }

      try {
        const { listId, words } = await loadJournalWords()
        if (!activeRef.current) {
          return
        }
        dispatch(setJournalListId(listId))
        dispatch(setJournalWords(words))
      } catch (error) {
        console.error('refresh journal after review failed', error)
      } finally {
        if (activeRef.current) {
          setIsSyncingResult(false)
        }
      }
    },
    [dispatch, page]
  )

  useEffect(() => {
    if (hasSyncedRef.current) {
      return
    }
    hasSyncedRef.current = true

    syncReviewResult().catch((error) => {
      console.error('sync review result failed', error)
      failedTermIdsRef.current = (wordArrayRef.current || [])
        .map((word) => word.termId || word.id)
        .filter(Boolean)

      if (!activeRef.current) {
        return
      }

      setSyncError(getApiErrorMessage(error, 'Failed to submit review results.'))
      setFailedTermIds(failedTermIdsRef.current)
      setIsSyncingResult(false)
    })
  }, [syncReviewResult])

  const retryFailedItems = useCallback(() => {
    if (failedTermIdsRef.current.length === 0) {
      return
    }

    syncReviewResult(failedTermIdsRef.current).catch((error) => {
      console.error('retry failed review results failed', error)
      if (!activeRef.current) {
        return
      }
      setSyncError(getApiErrorMessage(error, 'Retry failed. Please try again.'))
      setIsSyncingResult(false)
    })
  }, [syncReviewResult])

  return {
    isSyncingResult,
    syncError,
    failedTermIds,
    retryFailedItems
  }
}

export default useReviewResult
