import { render, screen } from '@testing-library/react'

describe('client test harness', () => {
  it('runs a basic assertion', () => {
    expect(true).toBe(true)
  })

  it('loads jest-dom matchers in jsdom', () => {
    render(<h1>Hello</h1>)

    expect(screen.getByText('Hello')).toBeInTheDocument()
  })
})
