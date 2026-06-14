import { useAuth } from '@/contexts/AuthContext'

interface Props {
  title: string
}

export function Header({ title }: Props) {
  const { username } = useAuth()

  return (
    <header className="flex h-14 items-center justify-between border-b border-gray-200 bg-white px-6">
      <h1 className="text-base font-semibold text-gray-900">{title}</h1>
      <span className="text-sm text-gray-500">{username}</span>
    </header>
  )
}
