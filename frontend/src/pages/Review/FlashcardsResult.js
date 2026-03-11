import { useSelector } from 'react-redux'
import { useNavigate } from 'react-router-dom'
import ResultTable from '../../components/Features/Review/ResultTable'
import useReviewResult from '../../hooks/useReviewResult'
import React from 'react'

const FlashcardsResult = () => {
  const { wordArray } = useSelector((state) => state.flashcards)
  const navigate = useNavigate()
  const {
    isSyncingResult,
    syncError,
    failedTermIds,
    retryFailedItems
  } = useReviewResult('flashcards', wordArray)

  return (
    <div className='flex flex-col gap-3 items-center w-full'>
      <h2 className='font-bold text-xl text-center text-indigo-800'>Result</h2>
      {isSyncingResult && (
        <div className='text-sm text-gray-600'>Submitting review results...</div>
      )}
      {syncError && (
        <div className='flex flex-col items-center gap-2'>
          <div className='text-rose-600 text-center'>{syncError}</div>
          {failedTermIds.length > 0 && (
            <button
              type='button'
              onClick={retryFailedItems}
              disabled={isSyncingResult}
              className='border-2 border-indigo-100 rounded-lg px-4 py-2 font-semibold hover:bg-indigo-100 hover:text-indigo-800 disabled:opacity-50'>
              Retry failed items
            </button>
          )}
        </div>
      )}
      <ResultTable wordArray={wordArray} />
      <button
        onClick={() => navigate('../../../journal')}
        disabled={isSyncingResult}
        className='mt-4 bg-indigo-500 hover:bg-indigo-600 disabled:opacity-50 text-lg font-semibold text-white rounded-lg px-5 py-2'>
        Return to Journal
      </button>
    </div>
  )
}

export default FlashcardsResult
