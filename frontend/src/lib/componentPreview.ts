import type { ChatComponent, ComponentPreviewRequest, ComponentPreviewResponse, ComponentTextContext } from '../types.ts'
import { COMPONENT_COLORS, hasObfuscatedComponent, isChatComponent, plainComponentText } from './components.ts'

async function api(path: string, body: unknown, signal?: AbortSignal): Promise<unknown> {
  const response = await fetch(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), signal })
  const result: unknown = await response.json()
  if (!response.ok) throw new Error(result && typeof result === 'object' && 'error' in result ? String(result.error) : `Minecraft request failed (${response.status}).`)
  return result
}

export async function validateComponent(component: ChatComponent, signal?: AbortSignal): Promise<void> {
  if (!isChatComponent(component)) throw new Error('Enter a Minecraft text component: a string, component object, or nonempty component array.')
  await api('/api/editor/components/validate', { component }, signal)
}

export async function nativeComponentPreview(request: ComponentPreviewRequest, signal?: AbortSignal): Promise<ComponentPreviewResponse> {
  const result = await api('/api/editor/components/preview', request, signal) as ComponentPreviewResponse
  if (!result || result.requestId !== request.requestId || result.assetRevision !== request.assetRevision || !Array.isArray(result.fragments)
      || typeof result.png !== 'string' || !Number.isFinite(result.width) || !Number.isFinite(result.height)) throw new Error('Minecraft returned an incomplete text preview.')
  return result
}

const decodedPreviews = new Map<string, { image: Promise<HTMLImageElement>; bytes: number }>()
function trimDecodedPreviews() {
  let bytes = [...decodedPreviews.values()].reduce((total, entry) => total + entry.bytes, 0)
  while (decodedPreviews.size > 64 || bytes > 16 * 1024 * 1024 && decodedPreviews.size > 1) {
    const oldest = decodedPreviews.keys().next().value!
    bytes -= decodedPreviews.get(oldest)!.bytes
    decodedPreviews.delete(oldest)
  }
}

export function decodeComponentPreview(png: string): Promise<HTMLImageElement> {
  const cached = decodedPreviews.get(png)
  if (cached) { decodedPreviews.delete(png); decodedPreviews.set(png, cached); return cached.image }
  const image = new Image()
  image.src = `data:image/png;base64,${png}`
  const entry = { image: image.decode().then(() => {
    if (!image.complete || !image.naturalWidth) throw new Error('The text preview image is empty.')
    entry.bytes = image.naturalWidth * image.naturalHeight * 4
    trimDecodedPreviews()
    return image
  }), bytes: 0 }
  decodedPreviews.set(png, entry); trimDecodedPreviews()
  entry.image.catch(() => { if (decodedPreviews.get(png) === entry) decodedPreviews.delete(png) })
  return entry.image
}

export function paintComponentPreview(target: HTMLCanvasElement, image: HTMLImageElement, width: number, height: number, scale: number,
  imageWidth: number, imageHeight: number, behind?: (graphics: CanvasRenderingContext2D) => void, above?: (graphics: CanvasRenderingContext2D) => void): boolean {
  if (!image.complete || !image.naturalWidth) return false
  if (target.width !== width * scale) target.width = width * scale
  if (target.height !== height * scale) target.height = height * scale
  const graphics = target.getContext('2d')
  if (!graphics) return false
  graphics.setTransform(scale, 0, 0, scale, 0, 0); graphics.imageSmoothingEnabled = false
  graphics.clearRect(0, 0, width, height)
  behind?.(graphics)
  graphics.drawImage(image, 0, 0, imageWidth, imageHeight)
  above?.(graphics)
  return true
}

export function approximateComponentPreview(request: ComponentPreviewRequest, context?: ComponentTextContext): ComponentPreviewResponse {
  const canvas = document.createElement('canvas')
  const measuring = canvas.getContext('2d')!
  const fragments: ComponentPreviewResponse['fragments'] = []
  const draws: { text: string; x: number; y: number; color: string; font: string; underline: boolean; strike: boolean; width: number }[] = []
  let x = 0, y = 0, widest = 0
  const lineHeight = 11
  for (const fragment of request.fragments) {
    const object = typeof fragment.component === 'object' && !Array.isArray(fragment.component) ? fragment.component : {}
    const text = fragment.literal ?? plainComponentText(fragment.component, context)
    const atomic = fragment.literal === undefined
    const font = `${object.italic ? 'italic ' : ''}${object.bold ? 'bold ' : ''}9px monospace`
    measuring.font = font
    const info: ComponentPreviewResponse['fragments'][number] = { id: fragment.id, plainText: text, ranges: [], carets: [] }
    let offset = 0
    info.carets.push({ offset: 0, x, y, line: Math.round(y / lineHeight) })
    for (const char of text || (atomic ? '◊' : '')) {
      if (char === '\n') {
        info.ranges.push({ start: atomic ? 0 : offset, end: atomic ? 1 : offset + 1, x, y, width: 3, height: lineHeight, line: Math.round(y / lineHeight) })
        widest = Math.max(widest, x); x = 0; y += lineHeight; offset++
        info.carets.push({ offset: atomic ? 1 : offset, x, y, line: Math.round(y / lineHeight), affinity: 'forward' })
        continue
      }
      const width = measuring.measureText(char).width
      if (x + width > request.width && x > 0) {
        widest = Math.max(widest, x); x = 0; y += lineHeight
        info.carets.push({ offset: atomic ? 0 : offset, x, y, line: Math.round(y / lineHeight), affinity: 'forward' })
      }
      info.ranges.push({ start: atomic ? 0 : offset, end: atomic ? 1 : offset + char.length, x, y, width, height: lineHeight, line: Math.round(y / lineHeight) })
      draws.push({ text: object.obfuscated ? '▧' : char, x, y, width, font, color: COMPONENT_COLORS[object.color ?? 'white'] ?? object.color ?? '#ffffff',
        underline: !!object.underlined, strike: !!object.strikethrough })
      x += width; widest = Math.max(widest, x); offset += char.length
      if (!atomic) info.carets.push({ offset, x, y, line: Math.round(y / lineHeight) })
    }
    if (atomic) info.carets.push({ offset: 1, x, y, line: Math.round(y / lineHeight) })
    fragments.push(info)
  }
  const totalHeight = y + lineHeight
  const height = Math.max(lineHeight, Math.min(request.viewportHeight, Math.max(lineHeight, totalHeight - request.viewportTop)))
  const width = Math.max(1, Math.ceil(Math.min(request.width, widest)))
  canvas.width = width * request.scale; canvas.height = height * request.scale
  const graphics = canvas.getContext('2d')!
  graphics.scale(request.scale, request.scale); graphics.textBaseline = 'top'
  for (const draw of draws) {
    if (draw.y + lineHeight < request.viewportTop || draw.y > request.viewportTop + height) continue
    graphics.font = draw.font; graphics.fillStyle = draw.color
    graphics.fillText(draw.text, draw.x, draw.y - request.viewportTop)
    if (draw.underline) graphics.fillRect(draw.x, draw.y - request.viewportTop + 9, draw.width, 1)
    if (draw.strike) graphics.fillRect(draw.x, draw.y - request.viewportTop + 4, draw.width, 1)
  }
  return { assetRevision: request.assetRevision, requestId: request.requestId, width, height, totalHeight, viewportTop: request.viewportTop, lineHeight,
    png: canvas.toDataURL('image/png').split(',')[1], fragments, warnings: [] }
}

const labelCache = new Map<string, Promise<ComponentPreviewResponse>>()
const labelQueue: (() => void)[] = []
let activeLabels = 0
function dispatchLabel() {
  while (activeLabels < 2 && labelQueue.length) { activeLabels++; labelQueue.shift()!() }
}

export function componentLabelPreview(request: ComponentPreviewRequest): Promise<ComponentPreviewResponse> {
  const animated = request.fragments.some(fragment => hasObfuscatedComponent(fragment.component))
  const key = JSON.stringify([request.assetRevision, request.width, request.scale, request.fragments.map(fragment => [fragment.component, fragment.literal])])
  const cached = animated ? undefined : labelCache.get(key)
  if (cached) { labelCache.delete(key); labelCache.set(key, cached); return cached }
  const promise = new Promise<ComponentPreviewResponse>((resolve, reject) => {
    labelQueue.push(() => {
      const controller = new AbortController()
      const timeout = window.setTimeout(() => controller.abort(), 2000)
      nativeComponentPreview(request, controller.signal).then(resolve, reject).finally(() => {
        window.clearTimeout(timeout); activeLabels--; dispatchLabel()
      })
    })
    dispatchLabel()
  })
  if (!animated) labelCache.set(key, promise)
  while (labelCache.size > 128) labelCache.delete(labelCache.keys().next().value!)
  promise.catch(() => { if (labelCache.get(key) === promise) labelCache.delete(key) })
  return promise
}
