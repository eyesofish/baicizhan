import React from 'react'
import PropTypes from 'prop-types'

const Button = ({
  bgColor,
  size,
  children,
  onClick,
  className = '',
  disabled = false,
  type = 'button'
}) => {
  const bgColorClassName =
    bgColor === 'indigo' ? 'bg-indigo-500' : 'bg-gray-200'
  const textColorClassName =
    bgColor === 'indigo' ? 'text-white' : 'text-gray-800'
  const hoverColorClassName =
    bgColor === 'indigo' ? 'hover:bg-indigo-600' : 'hover:bg-gray-300'

  let fontSizeClassName = ''
  let paddingClassName = ''

  switch (size) {
    case 'sm':
      fontSizeClassName = 'text-sm'
      paddingClassName = 'px-2 py-1'
      break
    case 'lg':
      fontSizeClassName = 'text-lg'
      paddingClassName = 'px-6 py-2'
      break
    default:
      fontSizeClassName = 'text-base'
      paddingClassName = 'px-4 py-2'
  }

  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className={`${className} ${bgColorClassName} ${textColorClassName} ${fontSizeClassName} ${paddingClassName} rounded-lg ${hoverColorClassName} disabled:opacity-50 disabled:cursor-not-allowed`}>
      {children}
    </button>
  )
}

export default Button

Button.propTypes = {
  bgColor: PropTypes.string,
  size: PropTypes.string,
  children: PropTypes.node.isRequired,
  onClick: PropTypes.func,
  className: PropTypes.string,
  disabled: PropTypes.bool,
  type: PropTypes.string
}
