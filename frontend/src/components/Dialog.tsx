import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'

export function Dialog({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialog = ref.current
    dialog?.showModal()
    return () => { dialog?.close() }
  }, [])
  return <dialog ref={ref} className="editor-dialog" aria-label={title} onCancel={onClose}>
    <header className="dialog-header"><h2>{title}</h2><button type="button" className="icon-button" aria-label="Close dialog" onClick={onClose}>×</button></header>
    {children}
  </dialog>
}
