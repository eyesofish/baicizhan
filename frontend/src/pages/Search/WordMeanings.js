import React, { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import Word from '../../components/Features/Word/Word'
import { getWordData } from '../../services/wordAPI'
import Filter from '../../components/Common/Filter'
import { useDispatch, useSelector } from 'react-redux'
import {
  setWordData as setMeaningsWordData,
  setIsLoading as setMeaningsIsLoading,
  setShowAllDetails as setMeaningsShowAllDetails,
  setError as setMeaningsError,
  setFilterOpen as setMeaningsFilterOpen
} from '../../reducers/wordMeaningsReducer'
import { setCurrentPage as setSearchCurrentPage } from '../../reducers/searchReducer'
import useIsMobile from '../../hooks/useIsMobile'
import { normalizeApiError } from '../../services/backendAPI'

const WordMeanings = () => {
  const { word } = useParams()
  const dispatch = useDispatch()
  const { isMobile } = useIsMobile(1070)
  const [retryToken, setRetryToken] = useState(0)
  const [isNotFound, setIsNotFound] = useState(false)
  const {
    wordData,
    isLoading,
    partOfSpeechFilter,
    showAllDetails,
    error,
    filterOpen
  } = useSelector((state) => state.wordMeanings)

  const displayedMeanings = (() => {
    if (partOfSpeechFilter !== '') {
      if (partOfSpeechFilter === 'other') {
        return wordData?.filter(
          (result) =>
            result.partOfSpeech !== 'noun' &&
            result.partOfSpeech !== 'verb' &&
            result.partOfSpeech !== 'adjective' &&
            result.partOfSpeech !== 'adverb'
        )
      }

      return wordData?.filter(
        (result) => result.partOfSpeech === partOfSpeechFilter
      )
    }

    return wordData
  })()

  const handleDetailsClick = (e) => {
    e.preventDefault()
    dispatch(setMeaningsShowAllDetails(!showAllDetails))
  }

  useEffect(() => {
    const controller = new AbortController()

    const fetchData = async () => {
      dispatch(setMeaningsIsLoading(true))
      dispatch(setMeaningsError(null))

      try {
        const returnedWordData = await getWordData(word, controller.signal)
        if (controller.signal.aborted) {
          return
        }

        dispatch(setMeaningsWordData(returnedWordData))
        setIsNotFound((returnedWordData?.results || []).length === 0)
      } catch (error) {
        if (error?.code === 'ERR_CANCELED') {
          return
        }

        setIsNotFound(false)
        dispatch(
          setMeaningsError(
            normalizeApiError(
              error,
              'Sorry, we are having trouble fetching the data. Please try again later.'
            )
          )
        )
      } finally {
        if (!controller.signal.aborted) {
          dispatch(setMeaningsIsLoading(false))
        }
      }
    }

    fetchData().catch((error) => {
      if (error?.code !== 'ERR_CANCELED') {
        console.error('fetch word meanings failed', error)
      }
    })

    return () => {
      controller.abort()
    }
  }, [word, dispatch, retryToken])

  useEffect(() => {
    dispatch(setSearchCurrentPage(`search/${word}`))
  }, [dispatch, word])

  const wordDataElement = (() => {
    if (isLoading || !wordData) {
      return <div>Loading...</div>
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

    if (isNotFound) {
      return <div>Word not found.</div>
    }

    if (displayedMeanings && displayedMeanings.length > 0) {
      return displayedMeanings.map((result, index) => (
        <Word
          key={result.word + index}
          wordData={result}
          page='search'
        />
      ))
    }

    return (
      <div>
        No results found. Try clearing the filter or switch to other filters.
      </div>
    )
  })()

  return (
    <div className='search--word-meanings flex flex-col gap-5 px-2'>
      <nav className='flex flex-row items-center flex-wrap gap-3 justify-between'>
        <div className='flex flex-row items-center gap-3'>
          <Link
            to='..'
            className='ml-2 underline text-sm hover:text-indigo-800 md:text-md'
            onClick={() => dispatch(setSearchCurrentPage('search'))}>
            Back
          </Link>
          {isMobile
            ? (
              <button
                className={`text-sm text-gray-700 font-semibold border-2 border-indigo-100 hover:text-indigo-800 hover:bg-indigo-100 py-1 px-3 rounded-lg
                ${filterOpen ? 'bg-indigo-100' : ''}
              `}
                onClick={() => dispatch(setMeaningsFilterOpen(!filterOpen))}>
                {filterOpen ? 'Hide' : 'Show'} filter
              </button>
              )
            : (
              <Filter page='search' />
              )}
        </div>
        <button
          onClick={handleDetailsClick}
          className='py-1 px-3 border-2 border-indigo-100 rounded-lg text-sm font-semibold hover:bg-indigo-100 hover:text-indigo-800'>
          {showAllDetails ? 'Hide all details' : 'Show all details'}
        </button>
      </nav>
      {filterOpen && isMobile && (
        <div>
          <Filter page='search' />
        </div>
      )}
      {wordDataElement}
    </div>
  )
}

export default WordMeanings
