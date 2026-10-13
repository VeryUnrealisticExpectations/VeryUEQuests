import { useEffect, useRef, useState } from 'react'
import { itemThumbnail } from '../lib/itemRenderer.ts'
import { ThumbnailQueueFullError } from '../lib/thumbnailScheduler.ts'

export function ItemIcon({ id, epoch, kind = 'item' }: { id: string; epoch: number; kind?: 'item' | 'block' }) {
  const [thumbnail, setThumbnail] = useState<{ key: string; url?: string; failed?: boolean }>()
  const ref = useRef<HTMLSpanElement>(null)
  const key = `${epoch}:${kind}:${id}`
  useEffect(() => {
    const controller = new AbortController()
    let requested = false
    let retry: ReturnType<typeof setTimeout> | undefined
    const request = () => {
      if (requested) return
      requested = true
      itemThumbnail(id, kind, controller.signal).then(url => { if (!controller.signal.aborted) setThumbnail({ key, url }) })
        .catch(error => {
          if (controller.signal.aborted) return
          if (error instanceof ThumbnailQueueFullError) { requested = false; retry = setTimeout(request, 100) }
          else setThumbnail({ key, failed: true })
        })
    }
    const observer = new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) { request(); observer.disconnect() } }, { rootMargin: '64px' })
    if (ref.current) observer.observe(ref.current)
    return () => { controller.abort(); observer.disconnect(); clearTimeout(retry) }
  }, [id, kind, key])
  const current = thumbnail?.key === key ? thumbnail : undefined
  return <span ref={ref} className={`item-icon ${current?.url ? '' : 'icon-placeholder'} ${current?.failed ? 'unavailable' : ''}`}
    title={current?.failed ? `Model unavailable: ${id}` : undefined} aria-hidden="true">
    {current?.url ? <img src={current.url} alt="" draggable={false} style={{ width: '100%', height: '100%', objectFit: 'contain' }} /> : current?.failed ? '?' : '·'}
  </span>
}
