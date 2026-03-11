import { useEffect, useRef } from 'react'
import { useDispatch } from 'react-redux'
import {
  setWordArray as setQuizWordArray,
  setInSession as setQuizInSession
} from '../reducers/quizReducer'
import {
  setWordArray as setFlashcardsWordArray,
  setInSession as setFlashcardsInSession
} from '../reducers/flashcardsReducer'
import { setListId as setJournalListId, setWords as setJournalWords } from '../reducers/journalReducer'
import { loadJournalWords, submitReviewResult } from '../services/backendAPI'

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

const useReviewResult = (page, wordArray) => {
  const dispatch = useDispatch()
  const hasSyncedRef = useRef(false)

  useEffect(() => {
    if (hasSyncedRef.current) {
      return
    }
    hasSyncedRef.current = true

    let active = true

    const syncReviewResult = async () => {
      const normalizedWordArray = (wordArray || []).map((word) => ({
        ...word,
        pointsEarned: normalizePoints(word.pointsEarned, 0),
        originalPoints: normalizePoints(word.originalPoints, word.points)
      }))

      const reviewedWords = normalizedWordArray.filter((word) => {
        const termId = word.termId || word.id
        return Boolean(termId) && word.pointsEarned !== 0
      })

      const responseByTerm = {}
      await Promise.all(
        reviewedWords.map(async (word) => {
          const termId = word.termId || word.id
          try {
            const rating = resolveRating(page, word.pointsEarned)
            const response = await submitReviewResult(termId, rating)
            responseByTerm[termId] = response
          } catch (error) {
            console.error('submit review result failed', error)
          }
        })
      )

      const newWordArray = normalizedWordArray.map((word) => {
        const termId = word.termId || word.id
        const response = responseByTerm[termId]
        const currentPoints = normalizePoints(word.originalPoints, 0)
        const newPoints = response
          ? normalizePoints(response.repetition, currentPoints)
          : currentPoints
        return {
          ...word,
          newPoints
        }
      })

      const updateWordArray =
        page === 'quiz' ? setQuizWordArray : setFlashcardsWordArray
      const updateInSession =
        page === 'quiz' ? setQuizInSession : setFlashcardsInSession

      dispatch(updateWordArray(newWordArray))
      dispatch(updateInSession(false))

      try {
        const { listId, words } = await loadJournalWords()
        if (!active) {
          return
        }
        dispatch(setJournalListId(listId))
        dispatch(setJournalWords(words))
      } catch (error) {
        console.error('refresh journal after review failed', error)
      }
    }

    syncReviewResult().catch((error) => {
      console.error('sync review result failed', error)
    })

    return () => {
      active = false
    }
  }, [dispatch, page, wordArray])
}

export default useReviewResult
