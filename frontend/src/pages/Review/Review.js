import { Link, Outlet, useLocation } from 'react-router-dom'
import { useDispatch, useSelector } from 'react-redux'
import {
  setSearchValue as setSearchSearchValue,
  setCurrentPage as setSearchCurrentPage
} from '../../reducers/searchReducer'
import { setListId as setJournalListId, setWords as setJournalWords } from '../../reducers/journalReducer'
import React, { useEffect, useState } from 'react'
import { getApiErrorMessage, loadJournalWords } from '../../services/backendAPI'

const Review = () => {
  const location = useLocation()
  const dispatch = useDispatch()
  const { words } = useSelector((state) => state.journal)
  const [isLoading, setIsLoading] = useState(false)
  const [loadError, setLoadError] = useState('')
  const isReviewPage = location.pathname === '/review'
  const divStyleClassName =
    'w-full font-semibold hover:bg-indigo-100 hover:text-indigo-800 text-lg border-2 rounded-xl border-indigo-100 flex items-center justify-center p-6'

  useEffect(() => {
    const fetchJournal = async () => {
      setIsLoading(true)
      try {
        const { listId, words } = await loadJournalWords()
        dispatch(setJournalListId(listId))
        dispatch(setJournalWords(words))
        setLoadError('')
      } catch (error) {
        setLoadError(
          getApiErrorMessage(
            error,
            'Failed to load journal from backend before review.'
          )
        )
      } finally {
        setIsLoading(false)
      }
    }
    fetchJournal().catch((error) => {
      console.error('load review journal failed', error)
    })
  }, [dispatch])

  const isJournalEmpty = !Array.isArray(words) || words.length === 0
  let reviewContent
  if (loadError) {
    reviewContent = <div className='text-rose-600 text-center'>{loadError}</div>
  } else if (isLoading) {
    reviewContent = <div className='text-center'>Loading review data...</div>
  } else if (isJournalEmpty) {
    reviewContent = (
      <div className='text-center w-full'>
        There is no word in your journal to review.{' '}
        <Link
          className='text-indigo-800 hover:underline'
          to='../search'
          onClick={() => {
            dispatch(setSearchSearchValue(''))
            dispatch(setSearchCurrentPage('search'))
          }}>
          Explore new words here!
        </Link>
      </div>
    )
  } else {
    reviewContent = (
      <>
        <Link to='flashcards'>
          <div className={divStyleClassName}>Flashcards</div>
        </Link>
        <Link to='quiz'>
          <div className={divStyleClassName}>Quiz</div>
        </Link>
      </>
    )
  }

  return (
    <div className='w-full flex flex-col items-center'>
      {isReviewPage && (
        <div className='flex flex-col gap-2 md:gap-5 w-full max-w-screen-md'>
          <h1 className='text-xl md:text-2xl font-bold text-center'>Review</h1>
          {reviewContent}
        </div>
      )}
      <Outlet />
    </div>
  )
}

export default Review
