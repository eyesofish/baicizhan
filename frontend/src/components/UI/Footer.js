import {
  AiOutlineLinkedin,
  AiOutlineGithub,
  AiOutlineMail,
  AiOutlineTwitter
} from 'react-icons/ai'
import React from 'react'
import PropType from 'prop-types'

const Footer = () => {
  return (
      <footer className='mt-7 w-full text-center flex items-center justify-center py-4 bg-indigo-50 text-gray-700'>
        <div className='px-2 border-r-2 border-gray-600 text-sm'>Developed by yang yu</div>
        <div className='flex px-2 gap-1'>
          <a
            href='https://www.linkedin.com/in/yy030305/?locale=zh'
            target='_blank'
            rel='noopener noreferrer'>
            <AiOutlineLinkedin size={25}/>
          </a>
          <a
            href='https://github.com/eyesofish'
            target='_blank'
            rel='noopener noreferrer'>
            <AiOutlineGithub size={25}/>
          </a>
          <a
            href='mailto:devilsrocbuddhasgildedimage@gmail.com'
            target='_blank'
            rel='noopener noreferrer'>
            <AiOutlineMail size={25}/>
          </a>
          <a
            href=''
            target='_blank'
            rel='noopener noreferrer'>
            <AiOutlineTwitter size={25}/>
          </a>
        </div>
      </footer>
  )
}

export default Footer

Footer.propTypes = {
  children: PropType.node
}
