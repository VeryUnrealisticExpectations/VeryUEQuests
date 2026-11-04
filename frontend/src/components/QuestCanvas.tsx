import { useCallback, useEffect, useRef, useState } from 'react'
import type { CSSProperties, MouseEvent, PointerEvent } from 'react'
import type { ComponentTextContext, Position, Quest, Section } from '../types.ts'
import { snapQuestPosition } from '../lib/quests.ts'
import { ComponentText } from './ComponentText.tsx'
import { DependencyLines } from './DependencyLines.tsx'
import { QuestNode } from './QuestNode.tsx'
import { StoneBackdrop } from './StoneBackdrop.tsx'
import { ButtonSymbol } from './ButtonSymbol.tsx'

const ZOOM_LEVELS = [0.25, 0.5, 0.75, 1, 1.5, 2]
const DEFAULT_ZOOM_LEVEL = ZOOM_LEVELS.indexOf(1)
const CSS_PIXELS_PER_GUI_UNIT = 2
type Gesture = { type: 'pan' | 'quest'; id?: string; pointer: number; capture: HTMLElement; start: Position; origin: Position; moved: boolean }
type Connection = { active: boolean; source?: string; disabled: boolean; section?: string }

export function QuestCanvas({ section, selected, epoch, assetRevision, textContext, disabled, onSelect, onEdit, onAdd, onMove, onConnect }:
  { section?: Section; selected?: string; epoch: number; assetRevision: string; textContext: ComponentTextContext; disabled: boolean;
    onSelect: (id?: string) => void; onEdit: (id: string) => void; onAdd: (position: Position) => void;
    onMove: (id: string, position: Position) => void; onConnect: (prerequisite: string, dependent: string) => void }) {
  const viewport = useRef<HTMLDivElement>(null)
  const gesture = useRef<Gesture | null>(null)
  const didDrag = useRef(false)
  const connectionClick = useRef<string>(undefined)
  const [size, setSize] = useState({ x: 0, y: 0 })
  const [pan, setPan] = useState<Position>({ x: 0, y: 0 })
  const [zoomLevel, setZoomLevel] = useState(DEFAULT_ZOOM_LEVEL)
  const [mouse, setMouse] = useState<Position>({ x: 0, y: 0 })
  const [connection, setConnection] = useState<Connection>({ active: false, disabled, section: section?.section_id })
  if (connection.disabled !== disabled || connection.section !== section?.section_id) {
    setConnection({ active: false, disabled, section: section?.section_id })
  }
  const connecting = connection.active && !disabled
  const source = connecting ? connection.source : undefined
  const zoom = ZOOM_LEVELS[zoomLevel]
  const scale = zoom * CSS_PIXELS_PER_GUI_UNIT
  const cancelConnection = () => setConnection({ active: false, disabled, section: section?.section_id })
  useEffect(() => {
    const element = viewport.current!
    const observer = new ResizeObserver(([entry]) => setSize({ x: entry.contentRect.width, y: entry.contentRect.height }))
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  const toWorld = useCallback((point: Position): Position => ({
    x: (point.x - size.x / 2 - pan.x) / scale,
    y: (point.y - size.y / 2 - pan.y) / scale,
  }), [size, pan, scale])
  const local = useCallback((clientX: number, clientY: number): Position => {
    const bounds = viewport.current!.getBoundingClientRect()
    return { x: clientX - bounds.left, y: clientY - bounds.top }
  }, [])
  const zoomAt = useCallback((direction: number, point: Position = { x: size.x / 2, y: size.y / 2 }) => {
    const next = Math.max(0, Math.min(ZOOM_LEVELS.length - 1, zoomLevel + direction))
    if (next === zoomLevel) return
    const world = toWorld(point)
    const scaleChange = scale - ZOOM_LEVELS[next] * CSS_PIXELS_PER_GUI_UNIT
    setPan({ x: pan.x + world.x * scaleChange, y: pan.y + world.y * scaleChange })
    setZoomLevel(next)
  }, [size, pan, scale, toWorld, zoomLevel])
  useEffect(() => {
    const element = viewport.current!
    const wheel = (event: WheelEvent) => { event.preventDefault(); if (event.deltaY) zoomAt(event.deltaY < 0 ? 1 : -1, local(event.clientX, event.clientY)) }
    element.addEventListener('wheel', wheel, { passive: false })
    return () => element.removeEventListener('wheel', wheel)
  }, [zoomAt, local])
  const reset = () => { setPan({ x: 0, y: 0 }); setZoomLevel(DEFAULT_ZOOM_LEVEL) }
  const selectQuest = (quest: Quest, event?: MouseEvent<HTMLButtonElement>) => {
    if (event && event.detail > 1) return
    if (didDrag.current) { didDrag.current = false; return }
    if (connecting) {
      if (!source) setConnection({ ...connection, source: quest.quest_id })
      else {
        connectionClick.current = quest.quest_id
        onConnect(source, quest.quest_id)
        cancelConnection()
      }
    } else {
      connectionClick.current = undefined
      onSelect(quest.quest_id)
    }
  }
  const startQuestDrag = (event: PointerEvent<HTMLButtonElement>, quest: Quest) => {
    event.stopPropagation()
    didDrag.current = false
    if (disabled || connecting || event.button !== 0) return
    gesture.current = { type: 'quest', id: quest.quest_id, pointer: event.pointerId, capture: event.currentTarget,
      start: { x: event.clientX, y: event.clientY }, origin: quest.position, moved: false }
    event.currentTarget.setPointerCapture(event.pointerId)
  }
  const startPan = (event: PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0 && event.button !== 1 || (event.target as Element).closest('button')) return
    event.preventDefault()
    gesture.current = { type: 'pan', pointer: event.pointerId, capture: event.currentTarget,
      start: { x: event.clientX, y: event.clientY }, origin: pan, moved: false }
    didDrag.current = false
    event.currentTarget.setPointerCapture(event.pointerId)
  }
  const finishGesture = (event: PointerEvent<HTMLDivElement>, cancelled = false) => {
    const current = gesture.current
    if (!current || current.pointer !== event.pointerId) return
    gesture.current = null
    didDrag.current = !cancelled && current.moved
    if (current.capture.hasPointerCapture(event.pointerId)) current.capture.releasePointerCapture(event.pointerId)
    if (current.type === 'pan' && !current.moved && event.button === 0 && !cancelled) {
      cancelConnection()
      onSelect(undefined)
    }
  }
  const position = toWorld(mouse)
  const format = (value: number) => (Math.abs(value) < 0.05 ? 0 : value).toFixed(1)
  const addAtCenter = () => {
    cancelConnection()
    const center = toWorld({ x: size.x / 2, y: size.y / 2 })
    const point = snapQuestPosition(center)
    const clearance = 56 / CSS_PIXELS_PER_GUI_UNIT
    while (section?.quests.some(quest => Math.abs(quest.position.x - point.x) < clearance
      && Math.abs(quest.position.y - point.y) < clearance)) point.x += 80 / CSS_PIXELS_PER_GUI_UNIT
    onAdd(point)
  }
  return <main className="quest-canvas" aria-label="Quest canvas" onKeyDown={event => {
    if (event.key === 'Escape') { event.preventDefault(); cancelConnection(); onSelect(undefined) }
  }}>
    <header className="canvas-heading"><h2>{section ? <ComponentText value={section.name} textContext={textContext} useInterfaceFont /> : 'Quest canvas'}</h2><span className="spacer" />
      <button type="button" className="mc-button mc-button--green mc-button--small" disabled={!section || disabled} onClick={addAtCenter}>+ Add quest</button>
      <button type="button" className="mc-button mc-button--gray mc-button--small" disabled={!section || disabled || section.quests.length < 2} aria-pressed={connecting}
        onClick={() => setConnection({ active: !connecting, disabled, section: section?.section_id })}>Connect</button>
      <button type="button" className="mc-button mc-button--gray mc-button--small" disabled={!selected || disabled} onClick={() => { cancelConnection(); if (selected) onEdit(selected) }}>Edit quest</button>
    </header>
    <div ref={viewport} className={'canvas-viewport ' + (connecting ? 'is-connecting' : '')} tabIndex={0} aria-label="Quest map"
      onPointerDown={startPan} onPointerMove={event => {
        setMouse(local(event.clientX, event.clientY))
        const current = gesture.current
        if (!current || current.pointer !== event.pointerId) return
        const dx = event.clientX - current.start.x, dy = event.clientY - current.start.y
        if (Math.abs(dx) + Math.abs(dy) > 3) current.moved = true
        if (current.type === 'pan') setPan({ x: current.origin.x + dx, y: current.origin.y + dy })
        else if (!disabled && current.moved) onMove(current.id!, snapQuestPosition({ x: current.origin.x + dx / scale, y: current.origin.y + dy / scale }, event))
      }} onPointerUp={event => finishGesture(event)} onPointerCancel={event => finishGesture(event, true)}
      onLostPointerCapture={event => {
        const current = gesture.current
        if (current?.pointer === event.pointerId) { didDrag.current = current.moved; gesture.current = null }
      }}
      onDoubleClick={event => {
        if (!disabled && !connecting && section && !(event.target as Element).closest('button')) {
          onAdd(snapQuestPosition(toWorld(local(event.clientX, event.clientY)), event))
        }
      }} onKeyDown={event => {
        if ((event.target as Element).closest('.quest-node')) return
        if (event.key === 'Home') { event.preventDefault(); reset() }
        else if (event.key === '+' || event.key === '=') { event.preventDefault(); zoomAt(1) }
        else if (event.key === '-') { event.preventDefault(); zoomAt(-1) }
        else if (event.key.startsWith('Arrow')) {
          event.preventDefault(); setPan({ x: pan.x + (event.key === 'ArrowLeft' ? 24 : event.key === 'ArrowRight' ? -24 : 0), y: pan.y + (event.key === 'ArrowUp' ? 24 : event.key === 'ArrowDown' ? -24 : 0) })
        }
      }}>
      <StoneBackdrop assetRevision={assetRevision} size={size} pan={pan} zoom={zoom} />
      <div className="canvas-world" style={{
        '--css-pixels-per-gui-unit': CSS_PIXELS_PER_GUI_UNIT,
        transform: `translate(${size.x / 2 + pan.x}px, ${size.y / 2 + pan.y}px) scale(${scale})`,
      } as CSSProperties}>
        <DependencyLines quests={section?.quests ?? []} />
        {section?.quests.map(quest => <QuestNode key={quest.quest_id} quest={quest} selected={selected === quest.quest_id}
          prerequisite={source === quest.quest_id} connecting={connecting} disabled={disabled} epoch={epoch} textContext={textContext}
          onPointerDown={event => startQuestDrag(event, quest)} onSelect={event => selectQuest(quest, event)}
          onContextMenu={event => {
            event.preventDefault(); event.stopPropagation()
            if (disabled) return
            const current = gesture.current
            gesture.current = null
            if (current?.capture.hasPointerCapture(current.pointer)) current.capture.releasePointerCapture(current.pointer)
            didDrag.current = false
            connectionClick.current = undefined
            setConnection({ active: true, source: quest.quest_id, disabled, section: section?.section_id })
            event.currentTarget.focus()
          }} onEdit={event => {
            if (!disabled && !connecting && (!event || connectionClick.current !== quest.quest_id)) onEdit(quest.quest_id)
          }} />)}
      </div>
      {!section && <div className="canvas-empty"><h3>Create your first section</h3><p>Add a section on the left to begin your quest book.</p></div>}
      {section && !section.quests.length && <div className="canvas-empty"><p>Add a quest, or double-click anywhere on the canvas.</p></div>}
      {connecting && <div className="connection-hint" role="status">{source ? 'Choose the quest that requires this quest.' : 'Choose the quest that must be completed first.'}
        <button type="button" className="mc-button mc-button--gray mc-button--small" onClick={cancelConnection}>Cancel</button></div>}
    </div>
    <footer className="canvas-footer"><span className="coordinates" data-testid="canvas-coordinates">X: {format(position.x)}, Y: {format(position.y)}</span><span className="spacer" />
      <span className="canvas-tip">Drag to pan</span>
      <button className="mc-button mc-button--gray mc-button--small mc-button--icon" type="button" aria-label="Zoom out" disabled={zoomLevel === 0} onClick={() => zoomAt(-1)}><ButtonSymbol symbol="minus" /></button>
      <span className="zoom-label">{Math.round(zoom * 100)}%</span>
      <button className="mc-button mc-button--gray mc-button--small mc-button--icon" type="button" aria-label="Zoom in" disabled={zoomLevel === ZOOM_LEVELS.length - 1} onClick={() => zoomAt(1)}><ButtonSymbol symbol="plus" /></button>
      <button type="button" className="mc-button mc-button--gray mc-button--small" onClick={reset}>Center</button></footer>
  </main>
}
