import { useLayoutEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'
import type { Position } from '../types.ts'

export function DraggablePopup({ title, anchor, children, onClose, className = '', focusOnOpen = false }:
  { title: string; anchor: HTMLElement | null; children: ReactNode; onClose: () => void; className?: string; focusOnOpen?: boolean }) {
  const popup = useRef<HTMLDivElement>(null)
  const [position, setPosition] = useState<Position>()
  const placed = !!position
  const dragging = useRef<{ pointer: number; start: Position; origin: Position } | undefined>(undefined)
  const clamp = (next: Position): Position => {
    const bounds = popup.current?.getBoundingClientRect()
    return { x: Math.max(8, Math.min(window.innerWidth - (bounds?.width ?? 480) - 8, next.x)),
      y: Math.max(8, Math.min(window.innerHeight - (bounds?.height ?? 240) - 8, next.y)) }
  }
  useLayoutEffect(() => {
    const bounds = anchor?.getBoundingClientRect()
    const size = popup.current?.getBoundingClientRect()
    const width = size?.width ?? 480
    const fitsRight = !!bounds && bounds.right + width + 16 < window.innerWidth
    const fitsLeft = !!bounds && bounds.left - width - 8 >= 8
    setPosition(clamp({ x: fitsRight ? bounds!.right + 8 : fitsLeft ? bounds!.left - width - 8 : bounds?.left ?? 24,
      y: bounds ? !fitsRight && !fitsLeft && bounds.top - (size?.height ?? 240) - 8 >= 8
        ? bounds.top - (size?.height ?? 240) - 8 : bounds.bottom + 8 : 24 }))
    const resize = () => setPosition(current => clamp(current ?? { x: 24, y: 24 }))
    const observer = new ResizeObserver(resize)
    if (popup.current) observer.observe(popup.current)
    window.addEventListener('resize', resize)
    return () => { observer.disconnect(); window.removeEventListener('resize', resize) }
  }, [anchor])
  useLayoutEffect(() => {
    if (focusOnOpen && placed) popup.current?.focus()
  }, [focusOnOpen, placed])
  const host = anchor?.closest('dialog[open]') ?? document.body
  return createPortal(<div ref={popup} className={`draggable-popup ${className}`} data-component-popup role="dialog" aria-label={title}
    tabIndex={focusOnOpen ? -1 : undefined}
    style={{ left: position?.x ?? 24, top: position?.y ?? 24, visibility: position ? 'visible' : 'hidden' }}
    onKeyDown={event => {
      if (event.key === 'Escape' && !event.defaultPrevented) { event.preventDefault(); event.stopPropagation(); onClose() }
    }}>
    <header className="draggable-popup-header">
      <button type="button" className="mc-button draggable-popup-handle" aria-label={`Move ${title}`} title="Drag to move; arrow keys to move"
        onPointerDown={event => {
          if (event.button !== 0) return
          event.preventDefault()
          const bounds = popup.current!.getBoundingClientRect()
          dragging.current = { pointer: event.pointerId, start: { x: event.clientX, y: event.clientY }, origin: { x: bounds.left, y: bounds.top } }
          event.currentTarget.setPointerCapture(event.pointerId)
        }} onPointerMove={event => {
          const gesture = dragging.current
          if (gesture?.pointer === event.pointerId) setPosition(clamp({ x: gesture.origin.x + event.clientX - gesture.start.x,
            y: gesture.origin.y + event.clientY - gesture.start.y }))
        }} onPointerUp={event => {
          dragging.current = undefined
          if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId)
        }} onPointerCancel={() => { dragging.current = undefined }} onKeyDown={event => {
          const direction = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }[event.key]
          if (!direction) return
          event.preventDefault(); event.stopPropagation()
          setPosition(current => clamp({ x: (current?.x ?? 24) + direction[0] * (event.shiftKey ? 1 : 10),
            y: (current?.y ?? 24) + direction[1] * (event.shiftKey ? 1 : 10) }))
        }}><span aria-hidden="true">⠿</span><strong>{title}</strong></button>
      <button type="button" className="mc-button mc-button--small mc-button--icon" aria-label={`Close ${title}`} onClick={onClose}>×</button>
    </header>
    <div className="draggable-popup-body">{children}</div>
  </div>, host)
}
