import { render, screen } from '@testing-library/react'
import { Badge } from '@/components/ui/Badge'

describe('Badge', () => {
  // Verifica que el componente Badge renderiza el texto pasado como hijo.
  it('renders children text', () => {
    render(<Badge>Active</Badge>)
    expect(screen.getByText('Active')).toBeInTheDocument()
  })

  // Verifica que la variante green aplica las clases de fondo y texto verdes al badge.
  it('applies green variant class', () => {
    render(<Badge variant="green">In Stock</Badge>)
    const el = screen.getByText('In Stock')
    expect(el).toHaveClass('bg-green-100', 'text-green-700')
  })

  // Verifica que la variante red aplica las clases de fondo y texto rojos al badge.
  it('applies red variant class', () => {
    render(<Badge variant="red">Out of Stock</Badge>)
    const el = screen.getByText('Out of Stock')
    expect(el).toHaveClass('bg-red-100', 'text-red-700')
  })

  // Verifica que sin prop variant el badge usa las clases grises por defecto.
  it('defaults to gray variant when no variant prop is given', () => {
    render(<Badge>Unknown</Badge>)
    const el = screen.getByText('Unknown')
    expect(el).toHaveClass('bg-gray-100', 'text-gray-600')
  })
})
