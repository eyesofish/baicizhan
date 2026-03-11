import { useDispatch, useSelector } from 'react-redux'
import {
  setInSession as setFlashcardsInSession,
  setMode as setFlashcardsMode,
  setNumber as setFlashcardsNumber,
  setWordArray as setFlashcardsWordArray
} from '../../reducers/flashcardsReducer'
import { useNavigate } from 'react-router-dom'
import Button from '../../components/Common/Button'
import { getFlashcardsInitWordArray } from '../../utils/reviewHelper'
import NumberChoice from '../../components/Features/Review/NumberChoice'
import ModeChoice from '../../components/Features/Review/ModeChoice'
import React, { useCallback, useEffect, useRef, useState } from 'react'
import {
  getApiErrorMessage,
  getReviewWordPool,
  isRequestCanceled
} from '../../services/backendAPI'

const FlashcardsMode = () => {
  const { mode, number } = useSelector((state) => state.flashcards)
  const dispatch = useDispatch()
  const navigate = useNavigate()
  const requestControllerRef = useRef(null)
  const [words, setWords] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')

  const fetchReviewPool = useCallback(async () => {
    requestControllerRef.current?.abort()
    const controller = new AbortController()
    requestControllerRef.current = controller

    setError('')
    setIsLoading(true)

    try {
      const reviewWords = await getReviewWordPool(100, controller.signal)
      if (requestControllerRef.current !== controller) {
        return
      }
      setWords(reviewWords)
    } catch (error) {
      if (isRequestCanceled(error)) {
        return
      }
      if (requestControllerRef.current !== controller) {
        return
      }
      setWords([])
      setError(getApiErrorMessage(error, 'Failed to load review queue from backend.'))
    } finally {
      if (requestControllerRef.current === controller) {
        setIsLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    fetchReviewPool().catch((error) => {
      if (!isRequestCanceled(error)) {
        console.error('load flashcards review pool failed', error)
      }
    })

    return () => {
      requestControllerRef.current?.abort()
    }
  }, [fetchReviewPool])

  const minNum = Math.min(5, words.length)

  const modesArray = [
    { name: 'wordToMeaning', text: 'Show Word, Guess Meaning' },
    { name: 'meaningToWord', text: 'Show Meaning, Guess Word' },
    { name: 'mixed', text: 'Mixed Mode' }
  ]

  const handleModeClick = (e) => {
    dispatch(
      setFlashcardsMode(modesArray.find((candidate) => candidate.name === e.target.name))
    )
  }

  const handleStart = () => {
    dispatch(
      setFlashcardsWordArray(getFlashcardsInitWordArray(words, number, mode))
    )
    dispatch(setFlashcardsInSession(true))
    navigate('0')
  }

  const handleNumberClick = (e) => {
    dispatch(setFlashcardsNumber(Number(e.target.name)))
  }

  const isStartDisabled = isLoading || words.length === 0 || !mode || number === 0

  return (
    <div className='flashcards--options flex flex-col gap-5 text-center items-center'>
      {error && (
        <div className='flex flex-col items-center gap-3'>
          <div className='text-rose-600'>{error}</div>
          <button
            type='button'
            className='border-2 border-indigo-100 rounded-lg px-4 py-2 font-semibold hover:bg-indigo-100 hover:text-indigo-800'
            onClick={() => {
              fetchReviewPool().catch((error) => {
                if (!isRequestCanceled(error)) {
                  console.error('retry flashcards review pool failed', error)
                }
              })
            }}>
            Retry
          </button>
        </div>
      )}
      {isLoading && <div>Loading review queue...</div>}
      {!error && !isLoading && (
        <div className='text-sm text-gray-600'>Due now: {words.length}</div>
      )}
      {!error && !isLoading && words.length === 0 && (
        <div>No due review words right now. Please add words or try later.</div>
      )}
      {!error && !isLoading && words.length > 0 && (
        <>
          <ModeChoice
            mode={mode}
            modesArray={modesArray}
            handleModeClick={handleModeClick}
          />
          <NumberChoice
            choiceArray={[minNum, 10, 15, 20]}
            wordsLength={words.length}
            number={number}
            prompt='Please select the number of flashcards:'
            handleNumberClick={handleNumberClick}
          />
          <Button
            onClick={handleStart}
            bgColor='indigo'
            size='lg'
            className='mt-4 font-semibold'
            disabled={isStartDisabled}>
            Start
          </Button>
          <div className='mt-7 text-center'>
            <span className='font-bold'>Reminder: </span>If you switch to other
            pages in the middle of a review session, you will lose your current
            review progress.
          </div>
        </>
      )}
    </div>
  )
}

export default FlashcardsMode
