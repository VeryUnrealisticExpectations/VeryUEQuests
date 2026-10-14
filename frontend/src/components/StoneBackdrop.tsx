import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { Position } from '../types.ts'
import { gameTextureUrl, STONE_SHADE_SIZE, STONE_TILE_SIZE, stoneTileDarkness } from '../lib/terrain.ts'

export function StoneBackdrop({ assetRevision, size, pan, zoom }:
  { assetRevision: string; size: Position; pan: Position; zoom: number }) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const [texture, setTexture] = useState<{ revision: string; image: HTMLCanvasElement }>()
  const pattern = useRef<{ image: HTMLCanvasElement; value: CanvasPattern | null }>(undefined)
  useEffect(() => {
    if (!assetRevision) return
    const image = new Image()
    image.onload = () => {
      const tile = document.createElement('canvas')
      tile.width = tile.height = STONE_TILE_SIZE
      const context = tile.getContext('2d')!
      context.imageSmoothingEnabled = false
      const side = Math.min(image.naturalWidth, image.naturalHeight)
      context.drawImage(image, 0, 0, side, side, 0, 0, STONE_TILE_SIZE, STONE_TILE_SIZE)
      setTexture({ revision: assetRevision, image: tile })
    }
    image.src = gameTextureUrl('stone', assetRevision)
    return () => { image.onload = null }
  }, [assetRevision])
  useLayoutEffect(() => {
    const element = canvas.current!, context = element.getContext('2d')
    if (!context || !size.x || !size.y) return
    const ratio = window.devicePixelRatio || 1
    const width = Math.ceil(size.x * ratio), height = Math.ceil(size.y * ratio)
    if (element.width !== width) element.width = width
    if (element.height !== height) element.height = height
    context.setTransform(ratio, 0, 0, ratio, 0, 0)
    context.imageSmoothingEnabled = false
    context.fillStyle = '#36383a'
    context.fillRect(0, 0, size.x, size.y)
    const origin = { x: size.x / 2 + pan.x, y: size.y / 2 + pan.y }
    const left = -origin.x / zoom, top = -origin.y / zoom
    if (texture?.revision === assetRevision) {
      if (pattern.current?.image !== texture.image) pattern.current = { image: texture.image, value: context.createPattern(texture.image, 'repeat') }
      if (pattern.current.value) {
        context.save()
        context.translate(origin.x, origin.y)
        context.scale(zoom, zoom)
        context.fillStyle = pattern.current.value
        context.fillRect(left, top, size.x / zoom, size.y / zoom)
        context.restore()
      }
    }
    const firstX = Math.floor(left / STONE_SHADE_SIZE), firstY = Math.floor(top / STONE_SHADE_SIZE)
    const lastX = Math.floor((left + size.x / zoom) / STONE_SHADE_SIZE)
    const lastY = Math.floor((top + size.y / zoom) / STONE_SHADE_SIZE)
    for (let y = firstY; y <= lastY; y++) for (let x = firstX; x <= lastX; x++) {
      const x1 = Math.round(origin.x + x * STONE_SHADE_SIZE * zoom)
      const y1 = Math.round(origin.y + y * STONE_SHADE_SIZE * zoom)
      const x2 = Math.round(origin.x + (x + 1) * STONE_SHADE_SIZE * zoom)
      const y2 = Math.round(origin.y + (y + 1) * STONE_SHADE_SIZE * zoom)
      context.fillStyle = `rgba(0, 0, 0, ${stoneTileDarkness(x, y)})`
      context.fillRect(x1, y1, x2 - x1, y2 - y1)
    }
  }, [assetRevision, size, pan, zoom, texture])
  return <canvas ref={canvas} className="canvas-stone" aria-hidden="true" />
}
