import { AiFillSound, AiOutlinePlus, AiOutlineEdit } from 'react-icons/ai'
import { MdOutlineDeleteOutline } from 'react-icons/md'
import { FiMoreHorizontal } from 'react-icons/fi'
import {
  setShowForm as setJournalShowForm,
  setListId as setJournalListId,
  setFormWord as setJournalFormWord,
  setWords as setJournalWords,
  setShowDetailsById as setJournalShowDetailsById
} from '../../../reducers/journalReducer'
import {
  setShowForm as setSearchShowForm,
  setFormWord as setSearchFormWord
} from '../../../reducers/searchReducer'
import { setShowDetailsById as setMeaningsShowDetailsById } from '../../../reducers/wordMeaningsReducer'
import { useDispatch } from 'react-redux'
import React from 'react'
import PropTypes from 'prop-types'
import {
  deleteJournalWord,
  getApiErrorMessage
} from '../../../services/backendAPI'

const WordHeader = ({ wordData, page, speak, currentShowDetails }) => {
  const {
    word,
    pronunciation,
    partOfSpeech,
    synonyms,
    antonyms,
    examples,
    images,
    points
  } = wordData

  const dispatch = useDispatch()
  const updateShowDetails =
    page === 'journal' ? setJournalShowDetailsById : setMeaningsShowDetailsById
  const updateFormWord =
    page === 'journal' ? setJournalFormWord : setSearchFormWord

  const handleDelete = async () => {
    try {
      const { listId, words } = await deleteJournalWord(
        wordData.itemId || wordData.id,
        wordData.listId
      )
      dispatch(setJournalListId(listId))
      dispatch(setJournalWords(words))
      window.dispatchEvent(
        new CustomEvent('journal:changed', {
          detail: {
            type: 'deleted',
            word: wordData.word || ''
          }
        })
      )
    } catch (error) {
      window.alert(getApiErrorMessage(error, 'Failed to delete this word.'))
    }
  }

  const toggleShowForm = (show) => {
    if (page === 'search') {
      dispatch(setSearchShowForm(show))
    } else if (page === 'journal') {
      dispatch(setJournalShowForm(show))
    }
  }

  const handleClick = (e) => {
    toggleShowForm(true)
    dispatch(updateFormWord(wordData))
  }

  const handleShowDetails = () => {
    console.log('wordData', wordData)
    console.log('wordData?.id', wordData?.id)
    dispatch(
      updateShowDetails({
        wordId: wordData?.id,
        showDetails: !currentShowDetails
      })
    )
  }

  return (
    <div className='word--header flex justify-between gap-2 flex-wrap'>
      <div className='flex gap-2 flex-row flex-wrap items-center'>
        <div className='flex flex-row gap-1 items-center flex-wrap'>
          <h2 className='text-lg md:text-xl font-bold text-indigo-800'>
            {word}
          </h2>
          {pronunciation && (
            <div className='flex gap-2'>
              <h3 className='text-md md:text-lg'>{`[${pronunciation}]`}</h3>
              <button
                className='word--audio'
                onClick={(e) => speak(e, word, 'samantha', 0.8)}
                aria-label='speak'>
                <AiFillSound size={20} />
              </button>
            </div>
          )}
        </div>
        {partOfSpeech && (
          <h4 className='font-semibold text-sm md:text-md md:ml-5'>
            {partOfSpeech[0].toUpperCase() + partOfSpeech.slice(1)}
          </h4>
        )}
        {points !== null && (
          <div className='text-blue-500 md:ml-5'>
            {points + (points === 1 ? ' point' : ' points')}
          </div>
        )}
      </div>

      <div className='flex gap-3 items-start'>
        {(synonyms?.length > 0 ||
          antonyms?.length > 0 ||
          examples?.length > 0 ||
          images?.length > 0) && (
          <button onClick={handleShowDetails}>
            <FiMoreHorizontal size={20} />
          </button>
        )}

        {page === 'search' && (
          <button
            onClick={handleClick}
            aria-label='Add to journal'>
            <AiOutlinePlus size={20} />
          </button>
        )}

        {page === 'journal' && typeof wordData?.id !== 'undefined' && (
          <button
            type='button'
            onClick={handleClick}
            aria-label='Edit'>
            <AiOutlineEdit size={20} />
          </button>
        )}

        {page === 'journal' && typeof wordData?.id !== 'undefined' && (
          <button
            type='button'
            onClick={handleDelete}
            aria-label='delete'>
            <MdOutlineDeleteOutline size={20} />
          </button>
        )}
      </div>
    </div>
  )
}

export default WordHeader

WordHeader.propTypes = {
  wordData: PropTypes.object.isRequired,
  page: PropTypes.string.isRequired,
  speak: PropTypes.func.isRequired,
  currentShowDetails: PropTypes.bool.isRequired
}
