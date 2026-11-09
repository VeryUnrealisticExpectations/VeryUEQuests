import type { ChatComponent, ComponentObject, ComponentPreviewFragment, ComponentTextContext } from '../types.ts'

export const COMPONENT_STYLES = ['color', 'font', 'bold', 'italic', 'underlined', 'strikethrough', 'obfuscated', 'hoverEvent', 'clickEvent', 'insertion'] as const
export const COMPONENT_COLORS: Record<string, string> = {
  black: '#000000', dark_blue: '#0000aa', dark_green: '#00aa00', dark_aqua: '#00aaaa', dark_red: '#aa0000', dark_purple: '#aa00aa',
  gold: '#ffaa00', gray: '#aaaaaa', dark_gray: '#555555', blue: '#5555ff', green: '#55ff55', aqua: '#55ffff', red: '#ff5555',
  light_purple: '#ff55ff', yellow: '#ffff55', white: '#ffffff',
}
const CONTENT_KEYS = ['type', 'text', 'translate', 'fallback', 'with', 'keybind', 'score', 'selector', 'nbt', 'block', 'entity', 'storage', 'interpret', 'separator']
type Path = (string | number)[]
export interface ComponentLeaf {
  id: string
  path: Path
  kind: 'literal' | 'atomic'
  text: string
  start: number
  end: number
  own: string | ComponentObject
  style: ComponentObject
}
export interface ComponentSelection { start: number; end: number }
export interface ComponentProjection { text: string; leaves: ComponentLeaf[]; fragments: ComponentPreviewFragment[] }

export function isChatComponent(value: unknown, depth = 0): value is ChatComponent {
  if (depth > 64) return false
  if (typeof value === 'string') return true
  if (Array.isArray(value)) return value.length > 0 && value.every(entry => isChatComponent(entry, depth + 1))
  if (!value || typeof value !== 'object') return false
  const object = value as ComponentObject
  const hasContent = ['text', 'translate', 'keybind', 'score', 'selector', 'nbt'].some(key => key in object)
  return hasContent && (object.extra === undefined || Array.isArray(object.extra) && object.extra.every(entry => isChatComponent(entry, depth + 1)))
}

function styles(object: ComponentObject, inherited: ComponentObject): ComponentObject {
  const result = { ...inherited }
  for (const key of COMPONENT_STYLES) if (object[key] !== undefined && object[key] !== null) (result as Record<string, unknown>)[key] = object[key]
  return result
}

function rootStyle(value: ChatComponent, inherited: ComponentObject): ComponentObject {
  return typeof value === 'string' ? inherited : Array.isArray(value) ? rootStyle(value[0] ?? '', inherited) : styles(value, inherited)
}

function ownContent(value: ComponentObject): ComponentObject {
  const own = { ...value }
  delete own.extra
  return own
}

function pathId(path: Path): string {
  return `component${path.map(part => `/${String(part).replaceAll('~', '~0').replaceAll('/', '~1')}`).join('')}`
}

export function projectComponent(value: ChatComponent): ComponentProjection {
  const leaves: ComponentLeaf[] = []
  let text = ''
  const append = (own: string | ComponentObject, path: Path, style: ComponentObject) => {
    const literal = typeof own === 'string' ? own : own.text
    const kind = typeof literal === 'string' ? 'literal' : 'atomic'
    const content = kind === 'literal' ? literal as string : '\ufffc'
    const start = text.length
    text += content
    leaves.push({ id: pathId(path), path, kind, text: content, start, end: text.length, own, style })
  }
  const walk = (component: ChatComponent, path: Path, inherited: ComponentObject) => {
    if (typeof component === 'string') { append(component, path, inherited); return }
    if (Array.isArray(component)) {
      const arrayStyle = rootStyle(component[0] ?? '', inherited)
      component.forEach((entry, index) => walk(entry, [...path, index], index ? arrayStyle : inherited))
      return
    }
    const effective = styles(component, inherited)
    append(ownContent(component), path, effective)
    component.extra?.forEach((entry, index) => walk(entry, [...path, 'extra', index], effective))
  }
  walk(value, [], {})
  return { text, leaves, fragments: leaves.map(leaf => ({ id: leaf.id,
    component: { ...leaf.style, ...(typeof leaf.own === 'string' ? { text: leaf.own } : leaf.own) },
    ...(leaf.kind === 'literal' ? { literal: leaf.text } : {}) })) }
}

function translate(value: ComponentObject, context?: ComponentTextContext, depth = 0): string {
  const key = value.translate ?? ''
  const format = context?.translations.find(entry => entry.key === key)?.value ?? value.fallback ?? key
  let sequential = 0
  return format.replace(/%(?:(\d+)\$)?([s%])/g, (whole, index: string | undefined, code: string) => {
    if (code === '%') return '%'
    const argument = value.with?.[index ? Number(index) - 1 : sequential++]
    return argument === undefined ? whole : isChatComponent(argument) ? plainComponentText(argument, context, depth + 1) : String(argument)
  })
}

export function plainComponentText(value: ChatComponent, context?: ComponentTextContext, depth = 0): string {
  if (depth > 64) return ''
  if (typeof value === 'string') return value
  if (Array.isArray(value)) return value.map(entry => plainComponentText(entry, context, depth + 1)).join('')
  let own = value.text ?? ''
  if (value.translate) own = translate(value, context, depth)
  else if (value.keybind) own = context?.keybinds.find(entry => entry.key === value.keybind)?.resolved ?? value.keybind
  else if (value.selector) own = String(value.selector)
  else if (value.nbt) own = String(value.nbt)
  else if (value.score && typeof value.score === 'object') own = String((value.score as Record<string, unknown>).value ?? (value.score as Record<string, unknown>).name ?? '')
  return own + (value.extra ?? []).map(entry => plainComponentText(entry, context, depth + 1)).join('')
}

export function hasComponentContent(value: ChatComponent): boolean {
  return projectComponent(value).leaves.some(leaf => {
    if (leaf.kind === 'literal') return leaf.text.trim().length > 0
    const own = leaf.own as ComponentObject
    if (typeof own.translate === 'string') return !!own.translate.trim() || !!own.fallback?.trim()
    if (typeof own.keybind === 'string') return !!own.keybind.trim()
    return true
  })
}

export function hasObfuscatedComponent(value: ChatComponent): boolean {
  return projectComponent(value).leaves.some(leaf => leaf.style.obfuscated === true || typeof leaf.own !== 'string'
    && (leaf.own.with ?? []).some(argument => isChatComponent(argument) && hasObfuscatedComponent(argument)))
}

export function componentLiteralLength(value: ChatComponent): number {
  return projectComponent(value).leaves.reduce((length, leaf) => length + (leaf.kind === 'literal' ? Array.from(leaf.text).length : 0), 0)
}

export function leafAtOffset(projection: ComponentProjection, offset: number): ComponentLeaf | undefined {
  return projection.leaves.find(leaf => leaf.start < offset && leaf.end >= offset)
    ?? projection.leaves.find(leaf => leaf.start === offset && leaf.end > leaf.start)
    ?? projection.leaves.at(-1)
}

function same(a: unknown, b: unknown): boolean { return JSON.stringify(a) === JSON.stringify(b) }

function wrapper(value: ComponentObject, parts: ChatComponent[], extras: ChatComponent[]): ComponentObject {
  const result = { ...value }
  for (const key of CONTENT_KEYS) delete result[key]
  delete result.extra
  const children = [...parts, ...extras]
  return { ...result, text: '', ...(children.length ? { extra: children } : {}) }
}

function inheritRootStyle(value: ChatComponent, inherited: ComponentObject): ChatComponent {
  if (typeof value === 'string') return { ...inherited, text: value }
  if (Array.isArray(value)) return [inheritRootStyle(value[0], inherited), ...value.slice(1)]
  return { ...value, ...styles(value, inherited) }
}

function compactStyleWrapper(value: ComponentObject): ChatComponent {
  const child = value.extra?.[0]
  if (value.text !== '' || value.extra?.length !== 1 || child === undefined || Array.isArray(child)
    || Object.keys(value).some(key => key !== 'text' && key !== 'extra' && !COMPONENT_STYLES.some(style => style === key))) return value
  return inheritRootStyle(child, styles(value, {}))
}

function transformLeaves(value: ChatComponent, changes: Map<string, ChatComponent[]>,
  { styleEdit, pruneDeleted = false }: { styleEdit?: { removed: string[] }; pruneDeleted?: boolean } = {}): ChatComponent {
  const walk = (component: ChatComponent, path: Path, inherited: ComponentObject, arrayAnchor = false): { component?: ChatComponent; changed: boolean } => {
    if (Array.isArray(component)) {
      const arrayStyle = rootStyle(component[0], inherited)
      const children = component.map((entry, index) => walk(entry, [...path, index], index ? arrayStyle : inherited,
        index === 0 && (arrayAnchor || component.length > 1)))
      if (!children.some(child => child.changed)) return { component, changed: false }
      const parts = children.flatMap(child => child.component === undefined ? [] : [child.component])
      if (!parts.length) return { changed: true }
      // Array siblings inherit the original first component's style, including after its deletion.
      if (children[0].component === undefined) parts[0] = inheritRootStyle(parts[0], arrayStyle)
      if (pruneDeleted && !projectComponent(parts).text.length) return { changed: true }
      return { component: parts, changed: true }
    }
    const parts = changes.get(pathId(path))
    if (typeof component === 'string') {
      if (!parts) return { component, changed: false }
      const result = parts.length === 1 && !(styleEdit && arrayAnchor) ? parts[0] : parts.length ? { text: '', extra: parts } : ''
      return { component: pruneDeleted && !projectComponent(result).text.length ? undefined : result, changed: true }
    }
    const effective = styles(component, inherited)
    const children = component.extra?.map((entry, index) => walk(entry, [...path, 'extra', index], effective)) ?? []
    if (!parts && !children.some(child => child.changed)) return { component, changed: false }
    const extras = children.flatMap(child => child.component === undefined ? [] : [child.component])
    let result: ComponentObject
    if (!parts) {
      result = { ...component }
      if (extras.length) result.extra = extras
      else delete result.extra
    } else {
      const own = ownContent(component)
      if (parts.length === 1 && typeof parts[0] !== 'string' && !Array.isArray(parts[0])) {
        const part = parts[0]
        const oldWithoutText = { ...own }, newWithoutText = { ...part }
        delete oldWithoutText.text; delete newWithoutText.text
        if ((styleEdit && !arrayAnchor && !extras.length) || same(oldWithoutText, newWithoutText)) {
          result = { ...part, ...(extras.length ? { extra: extras } : {}) }
        } else result = wrapper(component, parts, extras)
      } else result = wrapper(component, parts, extras)
      for (const key of styleEdit?.removed ?? []) delete result[key]
    }
    if (styleEdit && !arrayAnchor) return { component: compactStyleWrapper(result), changed: true }
    return { component: pruneDeleted && !projectComponent(result).text.length ? undefined : result, changed: true }
  }
  return walk(value, [], {}).component ?? (typeof value === 'string' ? '' : { text: '' })
}

function literalPart(leaf: ComponentLeaf, text: string, change: ComponentObject = {}): ChatComponent {
  if (typeof leaf.own === 'string' && !Object.keys(change).length) return text
  const part: ComponentObject = { ...(typeof leaf.own === 'string' ? {} : leaf.own), text }
  for (const [key, value] of Object.entries(change)) {
    if (value === null || value === undefined) delete part[key]
    else part[key] = value
  }
  return part
}

export function replaceComponentRange(value: ChatComponent, start: number, end: number, replacement: string | ChatComponent[], typingStyle: ComponentObject = {}): ChatComponent {
  const projection = projectComponent(value)
  start = Math.max(0, Math.min(projection.text.length, start)); end = Math.max(start, Math.min(projection.text.length, end))
  if (start === end && replacement === '') return value
  const pruneDeleted = replacement === ''
  const anchor = leafAtOffset(projection, start)
  const changes = new Map<string, ChatComponent[]>()
  for (const leaf of projection.leaves) {
    const selected = leaf.start < end && leaf.end > start
    const insert = leaf === anchor
    if (!selected && !insert) continue
    const from = Math.max(0, Math.min(leaf.text.length, start - leaf.start))
    const to = Math.max(from, Math.min(leaf.text.length, end - leaf.start))
    const parts: ChatComponent[] = []
    if (leaf.kind === 'literal') {
      if (from) parts.push(literalPart(leaf, leaf.text.slice(0, from)))
      if (insert) {
        if (typeof replacement === 'string' && replacement) parts.push(literalPart(leaf, replacement, typingStyle))
        else if (Array.isArray(replacement)) parts.push(...replacement)
      }
      if (to < leaf.text.length) parts.push(literalPart(leaf, leaf.text.slice(to)))
      if (!parts.length && !pruneDeleted) parts.push(literalPart(leaf, ''))
      if (!parts.length) changes.set(leaf.id, [])
      else if (parts.every(part => typeof part === 'string')) changes.set(leaf.id, [(parts as string[]).join('')])
      else {
        const first = parts[0]
        if (typeof first !== 'string' && !Array.isArray(first) && parts.every(part => {
          if (typeof part === 'string' || Array.isArray(part)) return false
          const a = { ...first }, b = { ...part }; delete a.text; delete b.text
          return typeof part.text === 'string' && same(a, b)
        })) changes.set(leaf.id, [{ ...first, text: parts.map(part => (part as ComponentObject).text).join('') }])
        else changes.set(leaf.id, parts)
      }
    } else {
      const inserted = typeof replacement === 'string' ? replacement ? [{ text: replacement, ...typingStyle }] : [] : replacement
      if (insert && from === 0) parts.push(...inserted)
      if (!selected) parts.push(leaf.own)
      if (insert && from > 0) parts.push(...inserted)
      changes.set(leaf.id, parts)
    }
  }
  return transformLeaves(value, changes, { pruneDeleted })
}

function materializeAttributes(value: ChatComponent, keys: string[]): ChatComponent {
  const walk = (component: ChatComponent, inherited: ComponentObject): ChatComponent => {
    if (typeof component === 'string') {
      const own: ComponentObject = { text: component }
      for (const key of keys) if (inherited[key] !== undefined) own[key] = inherited[key]
      return own
    }
    if (Array.isArray(component)) {
      const arrayStyle = rootStyle(component[0] ?? '', inherited)
      return component.map((entry, index) => walk(entry, index ? arrayStyle : inherited))
    }
    const effective = styles(component, inherited)
    const own = ownContent(component)
    for (const key of keys) {
      if (effective[key] === undefined) delete own[key]
      else own[key] = effective[key]
    }
    const result = wrapper(component, [own], (component.extra ?? []).map(entry => walk(entry, effective)))
    for (const key of keys) delete result[key]
    return result
  }
  return walk(value, {})
}

export function styleComponentRange(value: ChatComponent, start: number, end: number, change: ComponentObject): ChatComponent {
  if (start === end) return value
  const removed = Object.keys(change).filter(key => change[key] === null || change[key] === undefined)
  if (removed.length) value = materializeAttributes(value, removed)
  const projection = projectComponent(value)
  const changes = new Map<string, ChatComponent[]>()
  for (const leaf of projection.leaves) {
    if (!leaf.text.length || leaf.start >= end || leaf.end <= start) continue
    if (leaf.kind === 'atomic') {
      const own = { ...(leaf.own as ComponentObject) }
      for (const [key, next] of Object.entries(change)) { if (next === null || next === undefined) delete own[key]; else own[key] = next }
      changes.set(leaf.id, [own])
    } else {
      const from = Math.max(0, start - leaf.start), to = Math.min(leaf.text.length, end - leaf.start)
      const parts: ChatComponent[] = []
      if (from) parts.push(literalPart(leaf, leaf.text.slice(0, from)))
      parts.push(literalPart(leaf, leaf.text.slice(from, to), change))
      if (to < leaf.text.length) parts.push(literalPart(leaf, leaf.text.slice(to)))
      changes.set(leaf.id, parts)
    }
  }
  return transformLeaves(value, changes, { styleEdit: { removed } })
}

export function selectedComponent(value: ChatComponent, start: number, end: number): ChatComponent {
  const selected = projectComponent(value).leaves.filter(leaf => leaf.end > start && leaf.start < end).map(leaf => ({
    ...leaf.style, ...(leaf.kind === 'literal' ? { text: leaf.text.slice(Math.max(0, start - leaf.start), Math.min(leaf.text.length, end - leaf.start)) }
      : typeof leaf.own === 'string' ? { text: leaf.own } : leaf.own),
  }))
  return selected.length ? { text: '', extra: selected } : ''
}

export function componentInputChange(before: string, after: string, oldSelection: ComponentSelection, newSelection: ComponentSelection): { start: number; end: number; text: string } {
  let start = 0
  const maxPrefix = Math.min(oldSelection.start, newSelection.start)
  while (start < maxPrefix && before[start] === after[start]) start++
  let suffix = 0
  const maxSuffix = Math.min(before.length - oldSelection.end, after.length - newSelection.end, before.length - start, after.length - start)
  while (suffix < maxSuffix && before[before.length - suffix - 1] === after[after.length - suffix - 1]) suffix++
  return { start, end: before.length - suffix, text: after.slice(start, after.length - suffix) }
}

export interface ComponentHistoryEntry { value: ChatComponent; selection: ComponentSelection }
export class ComponentHistory {
  private undoEntries: ComponentHistoryEntry[] = []
  private redoEntries: ComponentHistoryEntry[] = []
  private lastTypedAt = 0
  record(entry: ComponentHistoryEntry, typing = false, now = Date.now()): void {
    if (!typing || now - this.lastTypedAt > 500 || !this.undoEntries.length) {
      this.undoEntries.push(structuredClone(entry))
      if (this.undoEntries.length > 100) this.undoEntries.shift()
    }
    this.lastTypedAt = typing ? now : 0
    this.redoEntries = []
  }
  undo(current: ComponentHistoryEntry): ComponentHistoryEntry | undefined {
    const previous = this.undoEntries.pop()
    if (previous) this.redoEntries.push(structuredClone(current))
    this.lastTypedAt = 0
    return previous
  }
  redo(current: ComponentHistoryEntry): ComponentHistoryEntry | undefined {
    const next = this.redoEntries.pop()
    if (next) this.undoEntries.push(structuredClone(current))
    this.lastTypedAt = 0
    return next
  }
}
