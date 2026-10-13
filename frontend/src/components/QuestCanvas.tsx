import { useCallback, useEffect, useRef, useState } from 'react'
import type { PointerEvent } from 'react'
import type { Position, Quest, Section } from '../types.ts'
import { DependencyLines } from './DependencyLines.tsx'
import { QuestNode } from './QuestNode.tsx'

const ZOOM_LEVELS = [0.5, 0.75, 1, 1.5, 2]
type Gesture = { type: 'pan' | 'quest'; id?: string; pointer: number; capture: HTMLElement; start: Position; origin: Position; moved: boolean }

export function QuestCanvas({ section, selected, epoch, disabled, onSelect, onEdit, onAdd, onMove, onConnect }:
  { section?: Section; selected?: string; epoch: number; disabled: boolean; onSelect: (id?: string) => void; onEdit: (id: string) => void;
    onAdd: (position: Position) => void; onMove: (id: string, position: Position) => void; onConnect: (from: string, to: string) => void }) {
  const viewport = useRef<HTMLDivElement>(null)
  const gesture = useRef<Gesture | null>(null)
  const didDrag = useRef(false)
  const [size, setSize] = useState({ x: 0, y: 0 })
  const [pan, setPan] = useState<Position>({ x: 0, y: 0 })
  const [zoomLevel, setZoomLevel] = useState(2)
  const [mouse, setMouse] = useState<Position>({ x: 0, y: 0 })
  const [connecting, setConnecting] = useState(false)
  const [source, setSource] = useState<string>()
  const zoom = ZOOM_LEVELS[zoomLevel]
  useEffect(() => {
    const element = viewport.current!
    const observer = new ResizeObserver(([entry]) => setSize({ x: entry.contentRect.width, y: entry.contentRect.height }))
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  const toWorld = (point: Position): Position => ({ x: (point.x - size.x / 2 - pan.x) / zoom, y: (point.y - size.y / 2 - pan.y) / zoom })
  const local = useCallback((clientX: number, clientY: number): Position => {
    const bounds = viewport.current!.getBoundingClientRect()
    return { x: clientX - bounds.left, y: clientY - bounds.top }
  }, [])
  const zoomAt = useCallback((direction: number, point: Position = { x: size.x / 2, y: size.y / 2 }) => {
    const next = Math.max(0, Math.min(ZOOM_LEVELS.length - 1, zoomLevel + direction))
    const world = { x: (point.x - size.x / 2 - pan.x) / zoom, y: (point.y - size.y / 2 - pan.y) / zoom }
    setPan({ x: pan.x + world.x * (zoom - ZOOM_LEVELS[next]), y: pan.y + world.y * (zoom - ZOOM_LEVELS[next]) })
    setZoomLevel(next)
  }, [size, pan, zoom, zoomLevel])
  useEffect(() => {
    const element = viewport.current!
    const wheel = (event: WheelEvent) => { event.preventDefault(); if (event.deltaY) zoomAt(event.deltaY < 0 ? 1 : -1, local(event.clientX, event.clientY)) }
    element.addEventListener('wheel', wheel, { passive: false })
    return () => element.removeEventListener('wheel', wheel)
  }, [zoomAt, local])
  const reset = () => { setPan({ x: 0, y: 0 }); setZoomLevel(2) }
  const selectQuest = (quest: Quest) => {
    if (didDrag.current) { didDrag.current = false; return }
    if (connecting) {
      if (!source) setSource(quest.quest_id)
      else { onConnect(source, quest.quest_id); setSource(undefined); setConnecting(false) }
    } else onSelect(quest.quest_id)
  }
  const startQuestDrag = (event: PointerEvent<HTMLButtonElement>, quest: Quest) => {
    event.stopPropagation()
    if (disabled || connecting || event.button !== 0) return
    gesture.current = { type: 'quest', id: quest.quest_id, pointer: event.pointerId, capture: event.currentTarget, start: { x: event.clientX, y: event.clientY }, origin: quest.position, moved: false }
    didDrag.current = false
    event.currentTarget.setPointerCapture(event.pointerId)
  }
  const startPan = (event: PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0 && event.button !== 1 || (event.target as Element).closest('button')) return
    event.preventDefault()
    gesture.current = { type: 'pan', pointer: event.pointerId, capture: event.currentTarget, start: { x: event.clientX, y: event.clientY }, origin: pan, moved: false }
    didDrag.current = false
    event.currentTarget.setPointerCapture(event.pointerId)
  }
  const position = toWorld(mouse)
  const format = (value: number) => (Math.abs(value) < 0.05 ? 0 : value).toFixed(1)
  const addAtCenter = () => {
    const center = toWorld({ x: size.x / 2, y: size.y / 2 })
    const point = { x: Math.round(center.x), y: Math.round(center.y) }
    while (section?.quests.some(quest => Math.abs(quest.position.x - point.x) < 56 && Math.abs(quest.position.y - point.y) < 56)) point.x += 80
    onAdd(point)
  }
  return <main className="quest-canvas" aria-label="Quest canvas">
    <header className="canvas-heading"><h2>{section?.name ?? 'Quest canvas'}</h2><span className="spacer" />
      <button type="button" disabled={!section || disabled} onClick={addAtCenter}>+ Add quest</button>
      <button type="button" disabled={!section || disabled || section.quests.length < 2} aria-pressed={connecting}
        onClick={() => { setConnecting(!connecting); setSource(undefined) }}>Connect</button>
      <button type="button" disabled={!selected || disabled} onClick={() => selected && onEdit(selected)}>Edit quest</button>
    </header>
    <div ref={viewport} className={`canvas-viewport ${connecting ? 'is-connecting' : ''}`} tabIndex={0} aria-label="Quest map"
      onPointerDown={startPan} onPointerMove={event => {
        setMouse(local(event.clientX, event.clientY))
        const current = gesture.current
        if (!current) return
        const dx = event.clientX - current.start.x, dy = event.clientY - current.start.y
        if (Math.abs(dx) + Math.abs(dy) > 3) current.moved = true
        if (current.type === 'pan') setPan({ x: current.origin.x + dx, y: current.origin.y + dy })
        else if (!disabled && current.moved) onMove(current.id!, { x: Math.round(current.origin.x + dx / zoom), y: Math.round(current.origin.y + dy / zoom) })
      }} onPointerUp={event => {
        const current = gesture.current
        if (current) { didDrag.current = current.moved; gesture.current = null; if (current.capture.hasPointerCapture(event.pointerId)) current.capture.releasePointerCapture(event.pointerId) }
      }} onPointerCancel={() => { gesture.current = null }}
      onDoubleClick={event => {
        if (!disabled && section && !(event.target as Element).closest('.quest-node')) {
          const point = toWorld(local(event.clientX, event.clientY)); onAdd({ x: Math.round(point.x), y: Math.round(point.y) })
        }
      }} onKeyDown={event => {
        if ((event.target as Element).closest('.quest-node')) return
        if (event.key === 'Home') { event.preventDefault(); reset() }
        else if (event.key === '+' || event.key === '=') { event.preventDefault(); zoomAt(1) }
        else if (event.key === '-') { event.preventDefault(); zoomAt(-1) }
        else if (event.key === 'Escape') { setConnecting(false); setSource(undefined); onSelect(undefined) }
        else if (event.key.startsWith('Arrow')) {
          event.preventDefault(); setPan({ x: pan.x + (event.key === 'ArrowLeft' ? 24 : event.key === 'ArrowRight' ? -24 : 0), y: pan.y + (event.key === 'ArrowUp' ? 24 : event.key === 'ArrowDown' ? -24 : 0) })
        }
      }}>
      <div className="canvas-grid" style={{ backgroundSize: `${32 * zoom}px ${32 * zoom}px`, backgroundPosition: `${size.x / 2 + pan.x}px ${size.y / 2 + pan.y}px` }} />
      <div className="canvas-world" style={{ transform: `translate(${size.x / 2 + pan.x}px, ${size.y / 2 + pan.y}px) scale(${zoom})` }}>
        <DependencyLines quests={section?.quests ?? []} />
        {section?.quests.map(quest => <QuestNode key={quest.quest_id} quest={quest} selected={selected === quest.quest_id} prerequisite={source === quest.quest_id} epoch={epoch}
          onPointerDown={event => startQuestDrag(event, quest)} onSelect={() => selectQuest(quest)} onEdit={() => !disabled && !connecting && onEdit(quest.quest_id)} />)}
      </div>
      {!section && <div className="canvas-empty"><h3>Create your first section</h3><p>Add a section on the left to begin your quest book.</p></div>}
      {section && !section.quests.length && <div className="canvas-empty"><h3>A new adventure starts here</h3><p>Add a quest, or double-click anywhere on the canvas.</p><button className="primary" type="button" disabled={disabled} onClick={() => onAdd({ x: 0, y: 0 })}>+ Add quest</button></div>}
      {connecting && <div className="connection-hint" role="status">{source ? 'Choose the quest that depends on this prerequisite.' : 'Choose the quest that must be completed first.'}<button type="button" onClick={() => { setConnecting(false); setSource(undefined) }}>Cancel</button></div>}
    </div>
    <footer className="canvas-footer"><span className="coordinates" data-testid="canvas-coordinates">X: {format(position.x)}, Y: {format(position.y)}</span><span className="spacer" />
      <span className="canvas-tip">Drag background to pan · Scroll to zoom</span><button className="icon-button" type="button" aria-label="Zoom out" disabled={zoomLevel === 0} onClick={() => zoomAt(-1)}>−</button>
      <span className="zoom-label">{Math.round(zoom * 100)}%</span><button className="icon-button" type="button" aria-label="Zoom in" disabled={zoomLevel === ZOOM_LEVELS.length - 1} onClick={() => zoomAt(1)}>+</button>
      <button type="button" onClick={reset}>Center</button></footer>
  </main>
}
