import { render, screen } from '@testing-library/react'
import { Badge } from '@/components/ui/Badge'

describe('Badge', () => {
  it('renders children text', () => {
    render(<Badge>Active</Badge>)
    expect(screen.getByText('Active')).toBeInTheDocument()
  })

  it('applies green variant class', () => {
    render(<Badge variant="green">In Stock</Badge>)
    const el = screen.getByText('In Stock')
    expect(el).toHaveClass('bg-green-100', 'text-green-700')
  })

  it('applies red variant class', () => {
    render(<Badge variant="red">Out of Stock</Badge>)
    const el = screen.getByText('Out of Stock')
    expect(el).toHaveClass('bg-red-100', 'text-red-700')
  })

  it('defaults to gray variant when no variant prop is given', () => {
    render(<Badge>Unknown</Badge>)
    const el = screen.getByText('Unknown')
    expect(el).toHaveClass('bg-gray-100', 'text-gray-600')
  })
})
