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
import React, { useEffect, useState } from 'react'
import {
  getApiErrorMessage,
  getReviewWordPool
} from '../../services/backendAPI'

const FlashcardsMode = () => {
  const { mode, number } = useSelector((state) => state.flashcards)
  const dispatch = useDispatch()
  const navigate = useNavigate()
  const [words, setWords] = useState([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const fetchReviewPool = async () => {
      setIsLoading(true)
      try {
        const reviewWords = await getReviewWordPool(100)
        setWords(reviewWords)
        setError('')
      } catch (error) {
        setError(
          getApiErrorMessage(
            error,
            'Failed to load review queue from backend.'
          )
        )
      } finally {
        setIsLoading(false)
      }
    }

    fetchReviewPool().catch((error) => {
      console.error('load flashcards review pool failed', error)
    })
  }, [])

  const minNum = Math.min(5, words.length)

  const modesArray = [
    { name: 'wordToMeaning', text: 'Show Word, Guess Meaning' },
    { name: 'meaningToWord', text: 'Show Meaning, Guess Word' },
    { name: 'mixed', text: 'Mixed Mode' }
  ]

  const handleModeClick = (e) => {
    dispatch(
      setFlashcardsMode(modesArray.find((b) => b.name === e.target.name))
    )
  }

  const handleStart = (e) => {
    if (mode === '') {
      e.preventDefault()
      alert('Please select a mode')
    } else if (number === 0) {
      e.preventDefault()
      alert('Please select the number of flashcards')
    } else {
      dispatch(
        setFlashcardsWordArray(getFlashcardsInitWordArray(words, number, mode))
      )
      dispatch(setFlashcardsInSession(true))
      navigate(mode === '' || number === 0 ? '' : '0')
    }
  }

  const handleNumberClick = (e) => {
    dispatch(setFlashcardsNumber(Number(e.target.name)))
  }

  return (
    <div className='flashcards--options flex flex-col gap-5 text-center items-center'>
      {error && <div className='text-rose-600'>{error}</div>}
      {isLoading && <div>Loading review queue...</div>}
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
        wordsLength={words?.length}
        number={number}
        prompt='Please select the number of flashcards:'
        handleNumberClick={handleNumberClick}
      />
      <Button
        onClick={handleStart}
        bgColor='indigo'
        size='lg'
        className='mt-4 font-semibold'>
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
