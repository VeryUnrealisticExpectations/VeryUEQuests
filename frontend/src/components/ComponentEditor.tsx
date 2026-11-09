import { useContext, useEffect, useId, useMemo, useRef, useState } from 'react'
import type { KeyboardEvent, PointerEvent } from 'react'
import type { Catalog, ChatComponent, ComponentObject, ComponentPreviewRequest, ComponentPreviewResponse, ComponentTextContext } from '../types.ts'
import { COMPONENT_COLORS, ComponentHistory, componentInputChange, componentLiteralLength, hasComponentContent, hasObfuscatedComponent, isChatComponent,
  leafAtOffset, plainComponentText, projectComponent, replaceComponentRange, selectedComponent, styleComponentRange } from '../lib/components.ts'
import type { ComponentSelection } from '../lib/components.ts'
import { approximateComponentPreview, decodeComponentPreview, nativeComponentPreview, paintComponentPreview, validateComponent } from '../lib/componentPreview.ts'
import { ComponentInspector } from './ComponentInspector.tsx'
import type { ComponentInspectorKind } from './ComponentInspector.tsx'
import { ComponentEditingContext } from '../lib/componentEditing.ts'
import { componentAnnotations } from '../lib/componentAnnotations.ts'
import { DraggablePopup } from './DraggablePopup.tsx'
import { COMPONENT_SCALE, COMPONENT_MIN_HEIGHT, COMPONENT_MAX_HEIGHT, componentPreviewHeight, componentPreviewMode, fittedComponentHeight, resizedComponentHeight } from '../lib/componentViewport.ts'

interface Selection extends ComponentSelection { direction?: 'forward' | 'backward' | 'none'; affinity?: string }
interface EditorPreview { layout: ComponentPreviewResponse; key: string; exact: boolean; message: string; wrapWidth: number; viewportHeight: number }
interface Stop { offset: number; x: number; y: number; line: number; affinity?: string }
interface Inspector { kind: ComponentInspectorKind; initial: ChatComponent; selection: Selection; whole?: boolean }
const SCALE = COMPONENT_SCALE
const STYLE_BUTTONS = [{ key: 'bold', label: 'Bold', text: 'B' }, { key: 'italic', label: 'Italic', text: 'I' },
  { key: 'underlined', label: 'Underline', text: 'U' }, { key: 'strikethrough', label: 'Strikethrough', text: 'S' }, { key: 'obfuscated', label: 'Obfuscated', text: '▧' }] as const
const CLIPBOARD_TYPE = 'application/x-vuequests-component+json'

function stopsFor(layout: ComponentPreviewResponse, projection: ReturnType<typeof projectComponent>): Stop[] {
  const stops = layout.fragments.flatMap(fragment => {
    const leaf = projection.leaves.find(entry => entry.id === fragment.id)
    if (!leaf) return []
    const mapped = fragment.carets.map(stop => ({ ...stop, offset: leaf.start + Math.min(leaf.text.length, stop.offset), affinity: `${stop.affinity ?? ''}:${stop.x}:${stop.y}` }))
    if (leaf.kind === 'atomic' && !mapped.some(stop => stop.offset === leaf.end) && mapped[0]) mapped.push({ ...mapped[0], offset: leaf.end })
    return mapped
  })
  return [...new Map(stops.map(stop => [`${stop.offset}:${stop.x}:${stop.y}`, stop])).values()]
}

function nearestStop(stops: Stop[], x: number, y: number, lineHeight: number): Stop | undefined {
  return stops.reduce<Stop | undefined>((best, stop) => {
    const distance = Math.abs(stop.y + lineHeight / 2 - y) * 1000 + Math.abs(stop.x - x)
    const previous = best ? Math.abs(best.y + lineHeight / 2 - y) * 1000 + Math.abs(best.x - x) : Infinity
    return distance < previous ? stop : best
  }, undefined)
}

export function ComponentEditor({ label, value, onChange, textContext, multiline = false, required = false, maxLiteralLength, onValidityChange,
  catalog, epoch = 0, docked = false, visible = true }:
  { label: string; value: ChatComponent; onChange: (value: ChatComponent) => void; textContext?: ComponentTextContext;
    multiline?: boolean; required?: boolean; maxLiteralLength?: number; onValidityChange?: (valid: boolean) => void; catalog?: Catalog; epoch?: number;
    docked?: boolean; visible?: boolean }) {
  const id = useId()
  const editingSession = useContext(ComponentEditingContext)
  const [documentValue, setDocumentValue] = useState(value)
  const documentRef = useRef(documentValue)
  const projection = useMemo(() => projectComponent(documentValue), [documentValue])
  const projectionRef = useRef(projection)
  const [selection, setSelection] = useState<Selection>({ start: 0, end: 0 })
  const selectionRef = useRef(selection)
  const textarea = useRef<HTMLTextAreaElement>(null)
  const surface = useRef<HTMLDivElement>(null)
  const canvas = useRef<HTMLCanvasElement>(null)
  const [popupAnchor, setPopupAnchor] = useState<HTMLElement | null>(null)
  const [width, setWidth] = useState(200)
  const [viewportHeight, setViewportHeight] = useState(multiline ? 46 : 30)
  const [editorHeight, setEditorHeight] = useState(multiline ? 96 : COMPONENT_MIN_HEIGHT)
  const [viewportTop, setViewportTop] = useState(0)
  const [preview, setPreview] = useState<EditorPreview>()
  const [decoded, setDecoded] = useState<{ preview: EditorPreview; image: HTMLImageElement }>()
  const previewRef = useRef(preview)
  const requestId = useRef(0)
  const [nativeFailure, setNativeFailure] = useState('')
  const [previewOverride, setPreviewOverride] = useState<{ revision: string; approximate: boolean }>()
  const [retryPreview, setRetryPreview] = useState(0)
  const history = useRef(new ComponentHistory())
  const clipboardRequest = useRef<AbortController | undefined>(undefined)
  const composing = useRef(false)
  const beforeInput = useRef<{ text: string; selection: Selection } | undefined>(undefined)
  const [focused, setFocused] = useState(false)
  const [localActive, setLocalActive] = useState(false)
  const active = docked ? visible : editingSession ? editingSession.activeEditor === id : localActive
  const [blink, setBlink] = useState(true)
  const [inViewport, setInViewport] = useState(true)
  const [animationTick, setAnimationTick] = useState(0)
  const [typingStyle, setTypingStyle] = useState<ComponentObject>({})
  const [inspector, setInspector] = useState<Inspector>()
  const [wasVisible, setWasVisible] = useState(visible)
  if (visible !== wasVisible) {
    setWasVisible(visible)
    if (!visible) setInspector(undefined)
  }
  const [error, setError] = useState('')
  const dragging = useRef<{ anchor: number; pointer: number } | undefined>(undefined)
  const resizing = useRef<{ height: number; y: number; pointer: number } | undefined>(undefined)
  const resizeFrame = useRef<number | undefined>(undefined)
  const resizeHeight = useRef<number | undefined>(undefined)
  const autoFit = useRef(false)
  const revealPending = useRef(false)
  const valueKey = JSON.stringify(documentValue)
  const animated = hasObfuscatedComponent(documentValue)
  const annotations = componentAnnotations(projection, selection)
  const renderMode = componentPreviewMode(textContext, previewOverride?.revision === textContext?.assetRevision && previewOverride?.approximate)
  const rowHeight = (preview?.layout.lineHeight ?? (renderMode === 'approximate' ? 11 : 9)) * SCALE
  const previewHeight = componentPreviewHeight(viewportHeight)
  const previewCurrent = decoded?.preview.key === valueKey && decoded.preview.layout.assetRevision === (textContext?.assetRevision ?? '')
    && decoded.preview.wrapWidth === width && decoded.preview.viewportHeight >= viewportHeight && decoded.preview.layout.viewportTop === viewportTop
    && decoded.preview.exact === (renderMode === 'native') && renderMode !== 'pending'
  const previewMessage = renderMode === 'pending' ? 'Loading Minecraft text resources…' : renderMode === 'approximate' ? 'Approximate preview'
    : nativeFailure || (!previewCurrent ? 'Updating Minecraft preview…' : preview?.message || 'Minecraft font preview')
  const invalid = required && !hasComponentContent(documentValue) ? `${label} is required.`
    : maxLiteralLength !== undefined && componentLiteralLength(documentValue) > maxLiteralLength ? `${label} may contain at most ${maxLiteralLength} literal characters.` : ''

  const select = (next: Selection, focus = true, resetStyle = true) => {
    const textLength = projectionRef.current.text.length
    const bounded = { ...next, start: Math.max(0, Math.min(textLength, next.start)), end: Math.max(0, Math.min(textLength, next.end)) }
    selectionRef.current = bounded; setSelection(bounded); setBlink(true)
    if (resetStyle) setTypingStyle({})
    const input = textarea.current
    if (input) {
      if (focus) input.focus({ preventScroll: true })
      input.setSelectionRange(bounded.start, bounded.end, bounded.direction ?? 'none')
    }
  }

  const commit = (next: ChatComponent, nextSelection: Selection, record = true, typing = false) => {
    if (JSON.stringify(next) === JSON.stringify(documentRef.current)) { select(nextSelection, false, false); return }
    if (record) history.current.record({ value: documentRef.current, selection: selectionRef.current }, typing)
    documentRef.current = next
    revealPending.current = true
    projectionRef.current = projectComponent(next)
    if (!projectionRef.current.text.length) setTypingStyle({})
    setDocumentValue(next); onChange(next); setError(''); setInspector(undefined)
    select(nextSelection, false, false)
    if (textarea.current && !composing.current) {
      textarea.current.value = projectionRef.current.text
      textarea.current.setSelectionRange(nextSelection.start, nextSelection.end, nextSelection.direction ?? 'none')
    }
  }

  useEffect(() => {
    previewRef.current = previewCurrent ? decoded?.preview : undefined
  }, [decoded, previewCurrent])

  useEffect(() => () => { clipboardRequest.current?.abort(); clipboardRequest.current = undefined }, [])

  useEffect(() => () => { if (resizeFrame.current !== undefined) window.cancelAnimationFrame(resizeFrame.current) }, [])

  useEffect(() => {
    if (JSON.stringify(value) !== JSON.stringify(documentRef.current)) {
      documentRef.current = value; projectionRef.current = projectComponent(value)
      setDocumentValue(value); history.current = new ComponentHistory(); setTypingStyle({}); setError('')
      const bounded = { start: Math.min(selectionRef.current.start, projectionRef.current.text.length), end: Math.min(selectionRef.current.end, projectionRef.current.text.length) }
      selectionRef.current = bounded; setSelection(bounded)
    }
  }, [value])

  useEffect(() => {
    textarea.current?.setCustomValidity(invalid)
    onValidityChange?.(!invalid)
  }, [invalid, onValidityChange])

  useEffect(() => {
    const target = surface.current
    if (!target) return
    const resize = () => {
      if (!target.clientWidth) return
      setWidth(Math.max(32, Math.min(1024, Math.floor((target.clientWidth - 56) / SCALE))))
      setViewportHeight(Math.max(1, Math.min(1024, Math.floor(target.clientHeight / SCALE))))
    }
    resize()
    const observer = new ResizeObserver(resize); observer.observe(target)
    const visibility = new IntersectionObserver(entries => setInViewport(entries[0]?.isIntersecting ?? false))
    visibility.observe(target)
    return () => { observer.disconnect(); visibility.disconnect() }
  }, [])

  useEffect(() => {
    if (!animated || !visible || !inViewport || textContext?.source !== 'local-client') return
    const timer = window.setInterval(() => setAnimationTick(previous => previous + 1), 250)
    return () => window.clearInterval(timer)
  }, [animated, visible, inViewport, textContext?.source])

  useEffect(() => {
    if (!focused) return
    const timer = window.setInterval(() => setBlink(previous => !previous), 500)
    return () => window.clearInterval(timer)
  }, [focused])

  useEffect(() => {
    if (!visible || renderMode === 'pending') return
    const controller = new AbortController()
    let cancelled = false
    const sequence = ++requestId.current
    const request: ComponentPreviewRequest = { assetRevision: textContext?.assetRevision ?? '', previewId: id, requestId: sequence,
      width, scale: SCALE, viewportTop, viewportHeight: previewHeight, fragments: projection.fragments }
    if (renderMode === 'approximate') {
      const layout = approximateComponentPreview(request, textContext)
      setPreview({ layout, key: valueKey, exact: false, wrapWidth: width, viewportHeight: previewHeight,
        message: 'Approximate preview' })
      if (autoFit.current) setEditorHeight(fittedComponentHeight(layout.totalHeight))
      return () => controller.abort()
    }
    const timer = window.setTimeout(() => {
      const timeout = window.setTimeout(() => controller.abort(), 2000)
      nativeComponentPreview(request, controller.signal).then(layout => {
        if (!cancelled && !controller.signal.aborted && requestId.current === sequence) {
          setPreview({ layout, key: valueKey, exact: true, wrapWidth: width, viewportHeight: previewHeight, message: layout.warnings.join(' ') })
          if (autoFit.current) setEditorHeight(fittedComponentHeight(layout.totalHeight))
          setNativeFailure('')
        }
      }).catch(failure => {
        if (!cancelled && requestId.current === sequence) {
          setNativeFailure(`Minecraft preview unavailable · ${failure instanceof Error ? failure.message : 'Try again.'}`)
        }
      }).finally(() => window.clearTimeout(timeout))
    }, 16)
    return () => { cancelled = true; controller.abort(); window.clearTimeout(timer) }
  }, [id, projection, valueKey, width, viewportTop, previewHeight, textContext, animationTick, renderMode, retryPreview, visible])

  useEffect(() => {
    if (!preview || preview.key !== valueKey) return
    let current = true
    decodeComponentPreview(preview.layout.png).then(image => { if (current) setDecoded({ preview, image }) }).catch(() => {
      if (current) setError('The text preview could not be displayed.')
    })
    return () => { current = false }
  }, [preview, valueKey])

  useEffect(() => {
    const target = canvas.current
    if (!target) return
    if (!projection.text.length && !previewCurrent) {
      target.getContext('2d')?.clearRect(0, 0, target.width, target.height)
      return
    }
    if (!decoded || !previewCurrent) return
    const layout = decoded.preview.layout
    paintComponentPreview(target, decoded.image, decoded.preview.wrapWidth, decoded.preview.viewportHeight, SCALE, layout.width, layout.height, graphics => {
      if (selection.end > selection.start) {
        graphics.fillStyle = 'rgba(92, 184, 88, .45)'
        for (const fragment of layout.fragments) {
          const leaf = projection.leaves.find(entry => entry.id === fragment.id)
          if (!leaf) continue
          for (const range of fragment.ranges) {
            const start = leaf.start + (leaf.kind === 'atomic' ? 0 : range.start), end = leaf.start + (leaf.kind === 'atomic' ? 1 : range.end)
            if (end > selection.start && start < selection.end) graphics.fillRect(range.x, range.y - layout.viewportTop, Math.max(1, range.width), range.height)
          }
        }
      }
    }, graphics => {
      if (focused && blink && selection.start === selection.end) {
        const stops = stopsFor(layout, projection)
        const stop = stops.find(entry => entry.offset === selection.end && (!selection.affinity || entry.affinity === selection.affinity))
          ?? stops.find(entry => entry.offset === selection.end)
        if (stop) {
          graphics.fillStyle = '#ffffff'; graphics.fillRect(stop.x, stop.y - layout.viewportTop, .75, layout.lineHeight)
        }
      }
    })
  }, [decoded, previewCurrent, projection, selection, focused, blink])

  useEffect(() => {
    if (!revealPending.current || !focused || !decoded || !previewCurrent || !surface.current) return
    const stop = stopsFor(decoded.preview.layout, projection).find(entry => entry.offset === selection.end)
    if (!stop) return
    revealPending.current = false
    const top = surface.current.scrollTop / SCALE
    if (stop.y < top) surface.current.scrollTop = stop.y * SCALE
    else if (stop.y + decoded.preview.layout.lineHeight > top + viewportHeight) surface.current.scrollTop = (stop.y + decoded.preview.layout.lineHeight - viewportHeight) * SCALE
  }, [decoded, previewCurrent, projection, focused, selection.end, viewportHeight])

  const activeLeaf = leafAtOffset(projection, selection.start)
  const activeStyle = { ...activeLeaf?.style, ...typingStyle }
  const inputSelection = (): Selection => textarea.current && document.activeElement === textarea.current ? { ...selectionRef.current, start: textarea.current.selectionStart,
    end: textarea.current.selectionEnd, direction: textarea.current.selectionDirection } : selectionRef.current
  const activate = () => {
    setPopupAnchor(surface.current)
    if (document.activeElement === textarea.current) {
      const range = inputSelection(); selectionRef.current = range; setSelection(range)
    }
    if (!active) setInspector(undefined)
    if (docked) return
    if (editingSession) editingSession.activateEditor(id)
    else setLocalActive(true)
  }
  const closeFormatting = () => {
    setInspector(undefined)
    if (editingSession) editingSession.activateEditor(undefined)
    else setLocalActive(false)
  }
  const fitText = () => {
    autoFit.current = true
    if (preview) setEditorHeight(fittedComponentHeight(preview.layout.totalHeight))
  }
  const queueResize = (height: number) => {
    resizeHeight.current = height
    if (resizeFrame.current !== undefined) return
    resizeFrame.current = window.requestAnimationFrame(() => {
      resizeFrame.current = undefined
      if (resizeHeight.current !== undefined) setEditorHeight(resizeHeight.current)
      resizeHeight.current = undefined
    })
  }
  const finishResize = () => {
    resizing.current = undefined
    if (resizeFrame.current !== undefined) window.cancelAnimationFrame(resizeFrame.current)
    resizeFrame.current = undefined
    if (resizeHeight.current !== undefined) setEditorHeight(resizeHeight.current)
    resizeHeight.current = undefined
  }
  const applyStyle = (change: ComponentObject, restoreFocus = true) => {
    const range = inputSelection()
    selectionRef.current = range; setSelection(range)
    if (range.start !== range.end) commit(styleComponentRange(documentRef.current, range.start, range.end, change), range)
    else setTypingStyle(previous => ({ ...previous, ...change }))
    if (restoreFocus) textarea.current?.focus({ preventScroll: true })
  }
  const open = (kind: ComponentInspectorKind, tokenId?: string) => {
    const leaf = tokenId ? projection.leaves.find(entry => entry.id === tokenId) : undefined
    const range = leaf ? { start: leaf.start, end: leaf.end } : inputSelection()
    const initial = kind === 'json' ? leaf?.own ?? documentRef.current
      : kind === 'hover' ? { text: '', hoverEvent: (leaf?.style ?? activeStyle).hoverEvent ?? { action: 'show_text', contents: '' } }
        : leaf?.own ?? (kind === 'translate' ? { translate: '', with: [] } : { keybind: 'key.jump' })
    setInspector({ kind, initial, selection: range, whole: kind === 'json' && !leaf })
  }
  const applyInspector = (next: ChatComponent) => {
    const editing = inspector!
    if (editing.whole) commit(next, { start: 0, end: 0 })
    else if (editing.kind === 'hover') {
      const object = next as ComponentObject
      if (editing.selection.start === editing.selection.end) setTypingStyle(previous => ({ ...previous, hoverEvent: object.hoverEvent }))
      else commit(styleComponentRange(documentRef.current, editing.selection.start, editing.selection.end, { hoverEvent: object.hoverEvent }), editing.selection)
    } else {
      const length = projectComponent(next).text.length
      commit(replaceComponentRange(documentRef.current, editing.selection.start, editing.selection.end, [next]),
        { start: editing.selection.start + length, end: editing.selection.start + length })
    }
    setInspector(undefined); textarea.current?.focus({ preventScroll: true })
  }
  const closeInspector = () => {
    setInspector(undefined)
    if (docked) textarea.current?.focus({ preventScroll: true })
  }
  const undo = (redo = false) => {
    const current = { value: documentRef.current, selection: selectionRef.current }
    const restored = redo ? history.current.redo(current) : history.current.undo(current)
    if (restored) commit(restored.value, restored.selection, false)
  }
  const updateFromInput = (input: HTMLTextAreaElement) => {
    const previous = beforeInput.current ?? { text: projectionRef.current.text, selection: selectionRef.current }
    beforeInput.current = undefined
    const nextSelection = { start: input.selectionStart, end: input.selectionEnd, direction: input.selectionDirection }
    if (input.value === projectionRef.current.text) { select(nextSelection, false, false); return }
    const change = componentInputChange(previous.text, input.value, previous.selection, nextSelection)
    const inserted = multiline ? change.text : change.text.replaceAll(/\r?\n/g, ' ')
    const next = replaceComponentRange(documentRef.current, change.start, change.end, inserted, typingStyle)
    const difference = change.text.length - inserted.length
    commit(next, { ...nextSelection, start: nextSelection.start - difference, end: nextSelection.end - difference }, !composing.current, true)
  }

  const hit = (event: PointerEvent): Stop | undefined => {
    const displayed = previewRef.current
    if (displayed?.key !== JSON.stringify(documentRef.current)) return undefined
    const layout = displayed?.layout, target = canvas.current
    if (!layout || !target) return undefined
    const bounds = target.getBoundingClientRect()
    return nearestStop(stopsFor(layout, projectionRef.current), (event.clientX - bounds.left) / SCALE,
      (event.clientY - bounds.top) / SCALE + layout.viewportTop, layout.lineHeight)
  }
  const reveal = (stop: Stop | undefined) => {
    if (!stop || !surface.current) return
    const top = surface.current.scrollTop / SCALE
    if (stop.y < top) surface.current.scrollTop = stop.y * SCALE
    else if (stop.y + (preview?.layout.lineHeight ?? 11) > top + viewportHeight) surface.current.scrollTop = (stop.y + (preview?.layout.lineHeight ?? 11) - viewportHeight) * SCALE
  }
  const keyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    const modifier = event.ctrlKey || event.metaKey
    if (modifier && ['z', 'y'].includes(event.key.toLowerCase())) {
      event.preventDefault(); event.stopPropagation(); undo(event.key.toLowerCase() === 'y' || event.shiftKey); return
    }
    if (event.key === 'Enter' && !multiline && !composing.current) { event.preventDefault(); return }
    if (['Backspace', 'Delete'].includes(event.key) && !composing.current && !projectionRef.current.text.length) {
      event.preventDefault()
      setTypingStyle({}); setInspector(undefined)
      commit(typeof documentRef.current === 'string' ? '' : { text: '' }, { start: 0, end: 0 })
      return
    }
    if (composing.current || !preview || !previewCurrent) return
    const isNavigation = ['ArrowUp', 'ArrowDown', 'Home', 'End', 'ArrowLeft', 'ArrowRight'].includes(event.key)
    if (!isNavigation || modifier && (event.key === 'ArrowLeft' || event.key === 'ArrowRight')) return
    const range = selectionRef.current
    const active = range.direction === 'backward' ? range.start : range.end
    const stops = stopsFor(preview.layout, projection)
    const current = stops.find(stop => stop.offset === active && (!range.affinity || stop.affinity === range.affinity)) ?? stops.find(stop => stop.offset === active)
    if (!current) return
    let next: Stop | undefined
    const ordered = [...stops].sort((a, b) => a.y - b.y || a.x - b.x)
    if (event.key === 'ArrowUp' || event.key === 'ArrowDown') next = nearestStop(stops, current.x, current.y + preview.layout.lineHeight * (event.key === 'ArrowUp' ? -1 : 1) + preview.layout.lineHeight / 2, preview.layout.lineHeight)
    else if (event.key === 'Home' || event.key === 'End') {
      const line = modifier ? ordered : ordered.filter(stop => stop.line === current.line)
      next = event.key === 'Home' ? line[0] : line.at(-1)
    } else {
      const index = ordered.findIndex(stop => stop.offset === current.offset && stop.x === current.x && stop.y === current.y)
      next = ordered[Math.max(0, Math.min(ordered.length - 1, index + (event.key === 'ArrowLeft' ? -1 : 1)))]
    }
    if (!next) return
    event.preventDefault()
    const anchor = event.shiftKey ? range.direction === 'backward' ? range.end : range.start : next.offset
    select({ start: Math.min(anchor, next.offset), end: Math.max(anchor, next.offset), direction: next.offset < anchor ? 'backward' : 'forward', affinity: next.affinity })
    reveal(next)
  }

  const controls = <>
    <div className="component-editor-toolbar" role="toolbar" aria-label={`${label} formatting`}>
      {STYLE_BUTTONS.map(button => <button key={button.key} type="button" className={`mc-button mc-button--small mc-button--icon component-format-button ${activeStyle[button.key] ? 'component-format-active' : ''}`}
        aria-label={button.label} aria-pressed={!!activeStyle[button.key]} title={button.label} onMouseDown={event => event.preventDefault()} onClick={() => applyStyle({ [button.key]: !activeStyle[button.key] })}>{button.text}</button>)}
      <label className="component-color-control"><span className="sr-only">Text color</span><input type="color" aria-label={`${label} text color`}
        value={COMPONENT_COLORS[activeStyle.color ?? 'white'] ?? (/^#[0-9a-f]{6}$/i.test(activeStyle.color ?? '') ? activeStyle.color! : '#ffffff')}
        onChange={event => applyStyle({ color: event.target.value }, false)} /></label>
      <select className="component-font-select" aria-label={`${label} font`} value={activeStyle.font ?? 'minecraft:default'} onChange={event => applyStyle({ font: event.target.value })}>
        {!textContext?.fonts.some(font => font.id === 'minecraft:default') && <option value="minecraft:default">Default font</option>}
        {textContext?.fonts.map(font => <option key={font.id} value={font.id}>{font.id}{font.loaded ? '' : ' (unavailable)'}</option>)}
        {activeStyle.font && activeStyle.font !== 'minecraft:default' && !textContext?.fonts.some(font => font.id === activeStyle.font) && <option value={activeStyle.font}>{activeStyle.font} (unavailable)</option>}
      </select>
      <button type="button" className="mc-button mc-button--small" onMouseDown={event => event.preventDefault()} onClick={() => applyStyle({ color: 'white', font: 'minecraft:default', bold: false, italic: false, underlined: false, strikethrough: false, obfuscated: false })}>Reset style</button>
      <button type="button" className="mc-button mc-button--small" onClick={() => open('translate')}>Translate</button>
      <button type="button" className="mc-button mc-button--small" onClick={() => open('keybind')}>Keybind</button>
      <button type="button" className="mc-button mc-button--small" onClick={() => open('hover')}>Hover</button>
      {activeStyle.hoverEvent !== undefined && <button type="button" className="mc-button mc-button--small" onMouseDown={event => event.preventDefault()} onClick={() => applyStyle({ hoverEvent: null })}>Remove hover</button>}
      <button type="button" className="mc-button mc-button--small" onMouseDown={event => event.preventDefault()} onClick={() => undo()}>Undo</button>
      <button type="button" className="mc-button mc-button--small" onMouseDown={event => event.preventDefault()} onClick={() => undo(true)}>Redo</button>
      <button type="button" className="mc-button mc-button--small" onClick={() => open('json')}>JSON</button>
    </div>
    <div className="component-color-palette" aria-label="Minecraft colors">{Object.entries(COMPONENT_COLORS).map(([name, color]) => <button key={name}
      type="button" className="mc-button mc-button--small mc-button--icon component-color-swatch" aria-label={name.replaceAll('_', ' ')} aria-pressed={activeStyle.color === name}
      title={name.replaceAll('_', ' ')} style={{ backgroundColor: color }} onMouseDown={event => event.preventDefault()} onClick={() => applyStyle({ color: name })} />)}</div>
  </>

  const formatting = <>
      <div className="component-controls">{controls}</div>
      <small className="component-preview-status">{previewMessage}</small>
      {nativeFailure && renderMode === 'native' && <div className="component-preview-actions">
        <button type="button" className="mc-button mc-button--small" onClick={() => { setNativeFailure(''); setRetryPreview(value => value + 1) }}>Retry Minecraft preview</button>
        <button type="button" className="mc-button mc-button--small" onClick={() => setPreviewOverride({ revision: textContext?.assetRevision ?? '', approximate: true })}>Use approximate preview</button>
      </div>}
      {renderMode === 'approximate' && textContext?.source === 'local-client' && <button type="button" className="mc-button mc-button--small"
        onClick={() => setPreviewOverride(undefined)}>Use Minecraft preview</button>}
      {projection.leaves.some(leaf => leaf.kind === 'atomic') && <div className="component-token-list" aria-label={`${label} embedded components`}>
        {projection.leaves.filter(leaf => leaf.kind === 'atomic').map(leaf => <button key={leaf.id} type="button" className="mc-button mc-button--small component-token-button"
          onClick={() => open(typeof leaf.own !== 'string' && !Array.isArray(leaf.own) && leaf.own.translate ? 'translate'
            : typeof leaf.own !== 'string' && !Array.isArray(leaf.own) && leaf.own.keybind ? 'keybind' : 'json', leaf.id)}>
          {typeof leaf.own !== 'string' && !Array.isArray(leaf.own) && leaf.own.translate ? `Translate: ${leaf.own.translate}`
            : typeof leaf.own !== 'string' && !Array.isArray(leaf.own) && leaf.own.keybind ? `Keybind: ${leaf.own.keybind}` : 'Native component'}</button>)}
      </div>}
  </>

  return <div className={`component-editor ${active ? 'is-active' : ''}`} role="group" aria-labelledby={`${id}-label`}
    data-preview-mode={renderMode} data-preview-current={previewCurrent ? 'true' : 'false'} hidden={!visible}
    onPointerDownCapture={event => { if (!(event.target as Element).closest('[data-component-resize]')) activate() }}
    onFocusCapture={event => { if (!(event.target as Element).closest('[data-component-resize]')) activate() }}>
    <label id={`${id}-label`} htmlFor={`${id}-input`} className={docked ? 'sr-only' : 'component-editor-label'}>{label}</label>
    {docked && visible && formatting}
    <div className="component-textarea">
    <div ref={surface} className={`component-surface ${focused ? 'focused' : ''}`} style={{ height: editorHeight }}
      onScroll={event => setViewportTop(Math.floor(event.currentTarget.scrollTop / SCALE))}>
      <div style={{ height: Math.max(viewportHeight, projection.text.length ? preview?.layout.totalHeight ?? viewportHeight : 0) * SCALE, position: 'relative' }}>
        {!!annotations.length && <div id={`${id}-annotations`} className="component-annotations" role="group" aria-label="Selected text components">
          {annotations.map(annotation => <button key={annotation.kind} type="button" className="mc-button component-annotation"
            aria-label={annotation.title} title={annotation.title} onMouseDown={event => event.preventDefault()}
            onClick={() => open(annotation.kind, annotation.leafId)}>{annotation.letter}</button>)}
        </div>}
        <div className="component-preview-viewport" style={{ height: viewportHeight * SCALE }}>
        <canvas ref={canvas} width={1} height={1} className="component-canvas" aria-hidden="true" style={{ touchAction: 'none', cursor: 'text' }}
          onPointerDown={event => {
            if (event.button !== 0) return
            const stop = hit(event)
            if (!stop) return
            event.preventDefault()
            const anchor = event.shiftKey ? selectionRef.current.direction === 'backward' ? selectionRef.current.end : selectionRef.current.start : stop.offset
            dragging.current = { anchor, pointer: event.pointerId }; event.currentTarget.setPointerCapture(event.pointerId)
            select({ start: Math.min(anchor, stop.offset), end: Math.max(anchor, stop.offset), direction: stop.offset < anchor ? 'backward' : 'forward', affinity: stop.affinity })
          }}
          onPointerMove={event => {
            if (dragging.current?.pointer !== event.pointerId) return
            const stop = hit(event)
            if (!stop) return
            const anchor = dragging.current.anchor
            select({ start: Math.min(anchor, stop.offset), end: Math.max(anchor, stop.offset), direction: stop.offset < anchor ? 'backward' : 'forward', affinity: stop.affinity })
            reveal(stop)
          }} onPointerUp={() => { dragging.current = undefined }} onPointerCancel={() => { dragging.current = undefined }}
          onDoubleClick={event => {
            const layout = previewRef.current?.layout, target = canvas.current
            if (!layout || !target || !previewCurrent) return
            const bounds = target.getBoundingClientRect()
            const stop = nearestStop(stopsFor(layout, projectionRef.current), (event.clientX - bounds.left) / SCALE, (event.clientY - bounds.top) / SCALE + layout.viewportTop, layout.lineHeight)
            if (!stop) return
            const leaf = leafAtOffset(projectionRef.current, stop.offset)
            if (leaf?.kind === 'atomic') { select({ start: leaf.start, end: leaf.end }); return }
            let locale = textContext?.language.replaceAll('_', '-') ?? 'en'
            try { locale = Intl.getCanonicalLocales(locale)[0] ?? 'en' } catch { locale = 'en' }
            const segmenter = new Intl.Segmenter(locale, { granularity: 'word' })
            const word = Array.from(segmenter.segment(projectionRef.current.text)).find(entry => entry.index <= stop.offset && entry.index + entry.segment.length > stop.offset)
            if (word) select({ start: word.index, end: word.index + word.segment.length })
          }} />
        </div>
      </div>
      <textarea ref={textarea} id={`${id}-input`} className="component-input" data-panel-focus={docked && visible ? '' : undefined}
        aria-labelledby={`${id}-label`} aria-describedby={`${id}-status`}
        aria-invalid={!!invalid || !!error} aria-required={required} rows={1} value={projection.text} spellCheck={false} autoComplete="off"
        style={{ position: 'absolute', width: 1, height: 1, left: 0, top: 0, opacity: 0, padding: 0, resize: 'none' }}
        onFocus={() => setFocused(true)} onBlur={() => setFocused(false)} onKeyDown={keyDown}
        onBeforeInput={event => { const input = event.currentTarget; beforeInput.current = { text: projectionRef.current.text,
          selection: { start: input.selectionStart, end: input.selectionEnd, direction: input.selectionDirection } } }}
        onChange={event => updateFromInput(event.currentTarget)}
        onSelect={event => {
          if (composing.current) return
          const input = event.currentTarget
          const next = { start: input.selectionStart, end: input.selectionEnd, direction: input.selectionDirection }
          if (next.start !== selectionRef.current.start || next.end !== selectionRef.current.end) {
            selectionRef.current = next; setSelection(next); setTypingStyle({}); setBlink(true)
          }
        }} onCompositionStart={() => { composing.current = true; history.current.record({ value: documentRef.current, selection: selectionRef.current }) }}
        onCompositionEnd={() => { composing.current = false; if (textarea.current) updateFromInput(textarea.current) }}
        onCopy={event => {
          const range = selectionRef.current
          if (range.start === range.end) return
          event.preventDefault()
          const selected = selectedComponent(documentRef.current, range.start, range.end)
          event.clipboardData.setData('text/plain', plainComponentText(selected, textContext)); event.clipboardData.setData(CLIPBOARD_TYPE, JSON.stringify(selected))
        }} onCut={event => {
          const range = selectionRef.current
          if (range.start === range.end) return
          event.preventDefault()
          const selected = selectedComponent(documentRef.current, range.start, range.end)
          event.clipboardData.setData('text/plain', plainComponentText(selected, textContext)); event.clipboardData.setData(CLIPBOARD_TYPE, JSON.stringify(selected))
          commit(replaceComponentRange(documentRef.current, range.start, range.end, ''), { start: range.start, end: range.start })
        }} onPaste={event => {
          event.preventDefault()
          clipboardRequest.current?.abort(); clipboardRequest.current = undefined
          const range = { ...inputSelection() }, original = documentRef.current
          const structured = event.clipboardData.getData(CLIPBOARD_TYPE)
          if (structured) {
            try {
              const component: unknown = JSON.parse(structured)
              if (!isChatComponent(component)) throw new Error('The clipboard Component is invalid.')
              const controller = new AbortController()
              clipboardRequest.current = controller
              void validateComponent(component, controller.signal).then(() => {
                if (controller.signal.aborted || clipboardRequest.current !== controller) return
                const currentSelection = inputSelection()
                if (JSON.stringify(documentRef.current) !== JSON.stringify(original) || currentSelection.start !== range.start || currentSelection.end !== range.end) {
                  setError('The text or selection changed while the clipboard was being checked. Paste again.'); return
                }
                const end = range.start + projectComponent(component).text.length
                commit(replaceComponentRange(original, range.start, range.end, [component]), { start: end, end })
              }).catch(failure => { if (!controller.signal.aborted && clipboardRequest.current === controller) setError(failure instanceof Error ? failure.message : 'Could not paste the Component.') })
                .finally(() => { if (clipboardRequest.current === controller) clipboardRequest.current = undefined })
            } catch (failure) { setError(failure instanceof Error ? failure.message : 'Could not paste the Component.') }
          } else {
            const text = event.clipboardData.getData('text/plain').replaceAll(/\r\n?/g, '\n')
            const inserted = multiline ? text : text.replaceAll('\n', ' ')
            const end = range.start + inserted.length
            commit(replaceComponentRange(original, range.start, range.end, inserted, typingStyle), { start: end, end })
          }
        }} />
    </div>
      <div className="component-resize-handle" data-component-resize role="separator" tabIndex={0} aria-orientation="horizontal" aria-label={`Resize ${label} text area`}
        aria-valuemin={COMPONENT_MIN_HEIGHT} aria-valuemax={COMPONENT_MAX_HEIGHT} aria-valuenow={editorHeight} aria-valuetext={`${editorHeight} pixels tall`}
        title="Drag to resize; double-click to fit text; use Up or Down to resize by rows" onPointerDown={event => {
          if (event.button !== 0) return
          event.preventDefault(); event.stopPropagation()
          autoFit.current = false
          resizing.current = { height: editorHeight, y: event.clientY, pointer: event.pointerId }
          event.currentTarget.setPointerCapture(event.pointerId)
        }} onPointerMove={event => {
          const resize = resizing.current
          if (resize?.pointer === event.pointerId) queueResize(resizedComponentHeight(resize.height, event.clientY - resize.y))
        }} onPointerUp={event => {
          if (resizing.current?.pointer !== event.pointerId) return
          finishResize()
          if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId)
        }} onPointerCancel={finishResize} onLostPointerCapture={finishResize} onDoubleClick={fitText} onKeyDown={event => {
          if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
            event.preventDefault(); event.stopPropagation()
            autoFit.current = false
            setEditorHeight(height => resizedComponentHeight(height, (event.key === 'ArrowUp' ? -1 : 1) * rowHeight))
          } else if (event.key === 'Home' || event.key === 'End') {
            event.preventDefault(); event.stopPropagation()
            if (event.key === 'End') fitText()
            else { autoFit.current = false; setEditorHeight(COMPONENT_MIN_HEIGHT) }
          }
        }} />
    </div>
    <div className="component-resize-actions" data-component-resize>
      <button type="button" className="mc-button mc-button--small component-fit-text" disabled={preview?.key !== valueKey}
        onClick={fitText}>Fit text</button>
    </div>
    <small id={`${id}-status`} className="component-preview-status" role="status" hidden={!invalid && !error}>{invalid || error}</small>
    {active && !docked && <DraggablePopup title={`${label} formatting`} anchor={popupAnchor} onClose={closeFormatting} className="component-format-popup">
      {formatting}
    </DraggablePopup>}
    {active && inspector && <DraggablePopup title={inspector.kind === 'translate' ? 'Translation' : inspector.kind === 'keybind' ? 'Keybind'
      : inspector.kind === 'hover' ? 'Hover event' : 'Component JSON'} anchor={popupAnchor} focusOnOpen={docked}
      onClose={closeInspector} className="component-inspector-popup">
      <ComponentInspector key={`${inspector.kind}-${JSON.stringify(inspector.selection)}`} kind={inspector.kind} initial={inspector.initial}
        textContext={textContext} catalog={catalog} epoch={epoch} onApply={applyInspector} onClose={closeInspector} />
    </DraggablePopup>}
  </div>
}
