import { BoundedCache } from './boundedCache.ts'
import { aborted, ThumbnailScheduler } from './thumbnailScheduler.ts'
import type { PreparedAssets, VirtualHandler } from 'block-model-renderer'
import rendererAssetsUrl from '../../node_modules/block-model-renderer/assets.zip?url'

interface Session { revision: string; version: string; controller: AbortController; assets?: Promise<PreparedAssets> }
let epoch = 0
let session: Session | undefined
const thumbnails = new BoundedCache<string, string>(4 * 1024 * 1024, 256)
const scheduler = new ThumbnailScheduler<string>()
const listeners = new Set<() => void>()

export function subscribeRenderer(listener: () => void): () => void { listeners.add(listener); return () => { listeners.delete(listener) } }
export function rendererEpoch(): number { return epoch }

export function setRendererAssets(revision: string, version: string): number {
  if (session?.revision === revision && session.version === version) return epoch
  const previous = session
  previous?.controller.abort(); scheduler.clear(); thumbnails.clear()
  // Disposal is queued behind any active render, since the library shares textures within a session.
  if (previous?.assets) void scheduler.idle().then(async () => {
    const assets = await previous.assets!.catch(() => undefined)
    if (assets) (await import('block-model-renderer')).disposeCache(assets)
  }).catch(() => undefined)
  session = { revision, version, controller: new AbortController() }
  epoch++; listeners.forEach(listener => listener())
  return epoch
}

function assets(current: Session): Promise<PreparedAssets> {
  return current.assets ??= (async () => {
    const renderer = await import('block-model-renderer')
    const THREE = await import('three')
    renderer.configure({ THREE, assetsUrl: rendererAssetsUrl })
    const resource = async (endpoint: 'file' | 'list', path: string) => {
      const query = new URLSearchParams({ path, revision: current.revision })
      const response = await fetch(`/api/assets/${endpoint}?${query}`, { signal: current.controller.signal, cache: 'force-cache' })
      if (response.status === 404 && endpoint === 'file') return null
      if (!response.ok) throw new Error('Could not read the game resources.')
      return response
    }
    const resources: VirtualHandler = {
      async read(path) {
        if (!path.startsWith('assets/')) return null
        const response = await resource('file', path)
        return response ? new Uint8Array(await response.arrayBuffer()) : null
      },
      async list(path) { return (await resource('list', path))!.json() },
    }
    return renderer.prepareAssets(resources, { cache: true, version: current.version })
  })()
}

export function itemThumbnail(id: string, kind: 'item' | 'block', signal: AbortSignal): Promise<string> {
  if (signal.aborted) return Promise.reject(aborted())
  const current = session, key = `${epoch}:${kind}:${id}`
  if (!current) return Promise.reject(new Error('Game assets are not connected.'))
  const cached = thumbnails.get(key)
  if (cached) return Promise.resolve(cached)
  return scheduler.request(key, async () => {
    if (current.controller.signal.aborted) throw aborted()
    const renderer = await import('block-model-renderer')
    const canvas = document.createElement('canvas')
    const options = { id, assets: await assets(current), canvas, width: 64, height: 64 }
    if (kind === 'block') await renderer.renderBlock(options)
    else await renderer.renderItem(options)
    if (current.controller.signal.aborted) throw aborted()
    const thumbnail = canvas.toDataURL('image/png')
    if (session === current) thumbnails.set(key, thumbnail, thumbnail.length * 2 + key.length * 2 + 128)
    return thumbnail
  }, signal)
}
