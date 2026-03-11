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
import React, { useEffect, useState } from 'react'
import {
  getApiErrorMessage,
  getReviewWordPool
} from '../../services/backendAPI'

const QuizMode = () => {
  const { mode, number, error, isLoading } = useSelector((state) => state.quiz)
  const dispatch = useDispatch()
  const navigate = useNavigate()
  const [words, setWords] = useState([])
  const [poolError, setPoolError] = useState('')
  const [poolLoading, setPoolLoading] = useState(false)

  useEffect(() => {
    const fetchReviewPool = async () => {
      setPoolLoading(true)
      try {
        const reviewWords = await getReviewWordPool(100)
        setWords(reviewWords)
        setPoolError('')
      } catch (error) {
        setPoolError(
          getApiErrorMessage(
            error,
            'Failed to load review queue from backend.'
          )
        )
      } finally {
        setPoolLoading(false)
      }
    }
    fetchReviewPool().catch((error) => {
      console.error('load quiz review pool failed', error)
    })
  }, [])

  const minNum = Math.min(5, words.length)

  const modesArray = [
    { name: 'MC', text: 'Multiple Choice' },
    { name: 'blank', text: 'Fill in the Blanks' },
    { name: 'mixed', text: 'Mixed' }
  ]

  const handleModeClick = (e) => {
    dispatch(setQuizMode(modesArray.find((b) => b.name === e.target.name)))
  }

  const handleNumberClick = (e) => {
    e.stopPropagation()
    dispatch(setQuizNumber(Number(e.target.name)))
  }

  const handleStart = async (e) => {
    e.stopPropagation()
    if (mode === '') {
      e.preventDefault()
      window.alert('Please select the quiz mode.')
    } else if (number === 0) {
      e.preventDefault()
      window.alert('Please select the number of words you want to review.')
    } else {
      try {
        const initWordArray = getQuizInitWordArray(words, number)
        dispatch(setQuizWordArray(initWordArray))
        dispatch(setQuizIsLoading(true))
        const initQuestionArray = await getQuizInitQuestionArray(
          initWordArray,
          mode
        )
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
  }

  return (
    <div className='quiz--mode flex flex-col gap-3 w-full sm:w-3/4 items-center text-center'>
      {error && <div>Error: {error.message}</div>}
      {isLoading && <div>Initializing quiz...</div>}
      {poolError && <div className='text-rose-600'>{poolError}</div>}
      {poolLoading && <div>Loading review queue...</div>}
      {!error && !isLoading && !poolError && !poolLoading && words.length === 0 && (
        <div>No due review words right now. Please add words or try later.</div>
      )}
      {!error && !isLoading && !poolError && !poolLoading && words.length > 0 && (
        <>
          <ModeChoice
            mode={mode}
            modesArray={modesArray}
            handleModeClick={handleModeClick}
          />
          <NumberChoice
            choiceArray={[minNum, 10, 15, 20]}
            wordsLength={words?.length}
            number={number}
            prompt='Please select the number of words you want to review:'
            handleNumberClick={handleNumberClick}
          />
          <Button
            onClick={handleStart}
            bgColor='indigo'
            size='lg'
            className='mt-4 font-semibold'>
            Start
          </Button>
        </>
      )}
    </div>
  )
}

export default QuizMode
