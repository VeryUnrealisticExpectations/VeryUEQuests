import { useEffect, useRef, useState } from 'react'
import type { CSSProperties, ReactNode } from 'react'
import { ComponentEditingScope } from './ComponentEditingScope.tsx'

export function Dialog({ title, children, onClose, size = 'default' }:
  { title: string; children: ReactNode; onClose: () => void; size?: 'default' | 'wide' }) {
  const ref = useRef<HTMLDialogElement>(null)
  const [resizeAnchor, setResizeAnchor] = useState<{ top: number; chrome: number }>()
  const anchorResize = () => {
    if (resizeAnchor || !ref.current) return
    const bounds = ref.current.getBoundingClientRect()
    const body = ref.current.querySelector('.dialog-body')?.getBoundingClientRect()
    if (body) setResizeAnchor({ top: bounds.top, chrome: bounds.height - body.height })
  }
  useEffect(() => {
    const dialog = ref.current
    dialog?.showModal()
    return () => { dialog?.close() }
  }, [])
  return <dialog ref={ref} className={`editor-dialog${size === 'wide' ? ' editor-dialog--wide' : ''}`} aria-label={title}
    data-resize-anchored={resizeAnchor ? '' : undefined}
    style={resizeAnchor ? { '--dialog-resize-top': `${resizeAnchor.top}px`, '--dialog-chrome': `${resizeAnchor.chrome}px` } as CSSProperties : undefined}
    onPointerDownCapture={event => { if ((event.target as Element).closest('.component-resize-handle')) anchorResize() }}
    onKeyDownCapture={event => {
      if (['ArrowUp', 'ArrowDown', 'Home', 'End'].includes(event.key) && (event.target as Element).closest('.component-resize-handle')) anchorResize()
    }}
    onCancel={event => { event.preventDefault(); onClose() }}>
    <header className="dialog-header"><h2>{title}</h2><button type="button" className="mc-button mc-button--icon" aria-label="Close dialog" onClick={onClose}>×</button></header>
    <ComponentEditingScope>{children}</ComponentEditingScope>
  </dialog>
}
