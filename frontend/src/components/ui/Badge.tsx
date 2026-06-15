type Variant = 'green' | 'red' | 'yellow' | 'blue' | 'gray'

const VARIANTS: Record<Variant, string> = {
  green: 'bg-green-100 text-green-700',
  red: 'bg-red-100 text-red-700',
  yellow: 'bg-yellow-100 text-yellow-700',
  blue: 'bg-blue-100 text-blue-700',
  gray: 'bg-gray-100 text-gray-600',
}

interface Props {
  variant?: Variant
  children: React.ReactNode
}

export function Badge({ variant = 'gray', children }: Props) {
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${VARIANTS[variant]}`}>
      {children}
    </span>
  )
}
