import { createContext } from 'react'

export const ComponentEditingContext = createContext<{
  activeEditor?: string
  activateEditor: (id?: string) => void
} | null>(null)
