import { Link, Outlet, useLocation } from 'react-router-dom'
import { useDispatch } from 'react-redux'
import {
  setSearchValue as setSearchSearchValue,
  setCurrentPage as setSearchCurrentPage
} from '../../reducers/searchReducer'
import React, { useCallback, useEffect, useRef, useState } from 'react'
import {
  getApiErrorMessage,
  getReviewCards,
  isRequestCanceled
} from '../../services/backendAPI'

const Review = () => {
  const location = useLocation()
  const dispatch = useDispatch()
  const requestControllerRef = useRef(null)
  const [isLoading, setIsLoading] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [dueCount, setDueCount] = useState(0)
  const isReviewPage = location.pathname === '/review'
  const divStyleClassName =
    'w-full font-semibold hover:bg-indigo-100 hover:text-indigo-800 text-lg border-2 rounded-xl border-indigo-100 flex items-center justify-center p-6'

  const fetchDueCards = useCallback(async () => {
    requestControllerRef.current?.abort()
    const controller = new AbortController()
    requestControllerRef.current = controller

    setLoadError('')
    setIsLoading(true)

    try {
      const cards = await getReviewCards(20, controller.signal)
      if (requestControllerRef.current !== controller) {
        return
      }
      setDueCount(cards.length)
    } catch (error) {
      if (isRequestCanceled(error)) {
        return
      }
      if (requestControllerRef.current !== controller) {
        return
      }
      setDueCount(0)
      setLoadError(
        getApiErrorMessage(error, 'Failed to load due review cards from backend.')
      )
    } finally {
      if (requestControllerRef.current === controller) {
        setIsLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    if (location.pathname !== '/review') {
      return
    }

    fetchDueCards().catch((error) => {
      if (!isRequestCanceled(error)) {
        console.error('load review due cards failed', error)
      }
    })
  }, [fetchDueCards, location.pathname])

  useEffect(
    () => () => {
      requestControllerRef.current?.abort()
    },
    []
  )

  let reviewContent
  if (loadError) {
    reviewContent = (
      <div className='flex flex-col items-center gap-3'>
        <div className='text-rose-600 text-center'>{loadError}</div>
        <button
          type='button'
          className='border-2 border-indigo-100 rounded-lg px-4 py-2 font-semibold hover:bg-indigo-100 hover:text-indigo-800'
          onClick={() => {
            fetchDueCards().catch((error) => {
              if (!isRequestCanceled(error)) {
                console.error('retry review due cards failed', error)
              }
            })
          }}>
          Retry
        </button>
      </div>
    )
  } else if (isLoading) {
    reviewContent = <div className='text-center'>Loading due review cards...</div>
  } else if (dueCount === 0) {
    reviewContent = (
      <div className='text-center w-full'>
        No review words are due right now.{' '}
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
        <div className='text-center text-sm text-gray-600'>Due now: {dueCount}</div>
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
