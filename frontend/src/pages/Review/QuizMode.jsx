import { useDispatch, useSelector } from 'react-redux'
import NumberChoice from '../../components/Features/Review/NumberChoice'
import {
  setNumber as setQuizNumber,
  setQuestionArray as setQuizQuestionArray,
  setInSession as setQuizInSession,
  setWordArray as setQuizWordArray,
  setMode as setQuizMode,
  setError as setQuizError,
  setIsLoading as setQuizIsLoading
} from '../../reducers/quizReducer'
import Button from '../../components/Common/Button'
import {
  getQuizInitQuestionArray,
  getQuizInitWordArray
} from '../../utils/reviewHelper'
import { useNavigate } from 'react-router-dom'
import ModeChoice from '../../components/Features/Review/ModeChoice'
import React, { useCallback, useEffect, useRef, useState } from 'react'
import {
  getApiErrorMessage,
  getReviewWordPool,
  isRequestCanceled
} from '../../services/backendAPI'

const QuizMode = () => {
  const { mode, number, error, isLoading } = useSelector((state) => state.quiz)
  const dispatch = useDispatch()
  const navigate = useNavigate()
  const requestControllerRef = useRef(null)
  const [words, setWords] = useState([])
  const [poolError, setPoolError] = useState('')
  const [poolLoading, setPoolLoading] = useState(false)

  const fetchReviewPool = useCallback(async () => {
    requestControllerRef.current?.abort()
    const controller = new AbortController()
    requestControllerRef.current = controller

    setPoolError('')
    setPoolLoading(true)

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
      setPoolError(getApiErrorMessage(error, 'Failed to load review queue from backend.'))
    } finally {
      if (requestControllerRef.current === controller) {
        setPoolLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    fetchReviewPool().catch((error) => {
      if (!isRequestCanceled(error)) {
        console.error('load quiz review pool failed', error)
      }
    })

    return () => {
      requestControllerRef.current?.abort()
    }
  }, [fetchReviewPool])

  const minNum = Math.min(5, words.length)

  const modesArray = [
    { name: 'MC', text: 'Multiple Choice' },
    { name: 'blank', text: 'Fill in the Blanks' },
    { name: 'mixed', text: 'Mixed' }
  ]

  const handleModeClick = (e) => {
    dispatch(setQuizMode(modesArray.find((candidate) => candidate.name === e.target.name)))
  }

  const handleNumberClick = (e) => {
    e.stopPropagation()
    dispatch(setQuizNumber(Number(e.target.name)))
  }

  const handleStart = async (e) => {
    e.stopPropagation()

    try {
      const initWordArray = getQuizInitWordArray(words, number)
      dispatch(setQuizWordArray(initWordArray))
      dispatch(setQuizIsLoading(true))
      const initQuestionArray = await getQuizInitQuestionArray(initWordArray, mode)
      dispatch(setQuizQuestionArray(initQuestionArray))
      dispatch(setQuizInSession(true))
      dispatch(setQuizError(null))
      navigate('0')
    } catch (error) {
      dispatch(
        setQuizError({
          ...error,
          message:
            'Oops, something went wrong when initializing the quiz. Please try again.'
        })
      )
    } finally {
      dispatch(setQuizIsLoading(false))
    }
  }

  const isStartDisabled =
    isLoading || poolLoading || words.length === 0 || !mode || number === 0

  return (
    <div className='quiz--mode flex flex-col gap-3 w-full sm:w-3/4 items-center text-center'>
      {error && <div className='text-rose-600'>Error: {error.message}</div>}
      {isLoading && <div>Initializing quiz...</div>}
      {poolError && (
        <div className='flex flex-col items-center gap-3'>
          <div className='text-rose-600'>{poolError}</div>
          <button
            type='button'
            className='border-2 border-indigo-100 rounded-lg px-4 py-2 font-semibold hover:bg-indigo-100 hover:text-indigo-800'
            onClick={() => {
              fetchReviewPool().catch((error) => {
                if (!isRequestCanceled(error)) {
                  console.error('retry quiz review pool failed', error)
                }
              })
            }}>
            Retry
          </button>
        </div>
      )}
      {poolLoading && <div>Loading review queue...</div>}
      {!poolError && !poolLoading && (
        <div className='text-sm text-gray-600'>Due now: {words.length}</div>
      )}
      {!error && !isLoading && !poolError && !poolLoading && words.length === 0 && (
        <div>No due review words right now. Please add words or try later.</div>
      )}
      {!poolError && !poolLoading && words.length > 0 && (
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
            prompt='Please select the number of words you want to review:'
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
        </>
      )}
    </div>
  )
}

export default QuizMode
