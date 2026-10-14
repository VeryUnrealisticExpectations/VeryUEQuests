import { useState } from 'react'
import type { ReactNode } from 'react'
import { ComponentEditingContext } from '../lib/componentEditing.ts'

export function ComponentEditingScope({ children }: { children: ReactNode }) {
  const [activeEditor, activateEditor] = useState<string>()
  return <ComponentEditingContext value={{ activeEditor, activateEditor }}>
    <div className="component-editing-scope" onFocusCapture={event => {
      const target = event.target
      if (target.closest('.component-editor, [data-component-popup]')) return
      if (target instanceof HTMLTextAreaElement || target instanceof HTMLInputElement
          && !['color', 'checkbox', 'radio', 'button', 'submit'].includes(target.type)) activateEditor(undefined)
    }}>{children}</div>
  </ComponentEditingContext>
}
