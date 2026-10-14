import { useEffect, useId, useRef, useState } from 'react'
import type { CSSProperties } from 'react'
import type { ChatComponent, ComponentTextContext } from '../types.ts'
import { COMPONENT_COLORS, hasObfuscatedComponent, plainComponentText, projectComponent } from '../lib/components.ts'
import { componentLabelPreview, decodeComponentPreview } from '../lib/componentPreview.ts'

export function ComponentText({ value, textContext, className = '', useInterfaceFont = false }:
  { value: ChatComponent; textContext?: ComponentTextContext; className?: string; useInterfaceFont?: boolean }) {
  const id = useId()
  const target = useRef<HTMLSpanElement>(null)
  const [visible, setVisible] = useState(true)
  const [animationTick, setAnimationTick] = useState(0)
  const [native, setNative] = useState<{ key: string; png: string; width: number; height: number }>()
  const key = JSON.stringify([value, textContext?.assetRevision])
  const projection = projectComponent(value)
  const needsNative = !useInterfaceFont && projection.leaves.some(leaf => Object.keys(leaf.style).length || leaf.kind === 'atomic')
  const animated = hasObfuscatedComponent(value)
  useEffect(() => {
    if (!target.current) return
    const observer = new IntersectionObserver(entries => setVisible(entries[0]?.isIntersecting ?? false))
    observer.observe(target.current)
    return () => observer.disconnect()
  }, [])
  useEffect(() => {
    if (useInterfaceFont || !animated || !visible || textContext?.source !== 'local-client') return
    const timer = window.setInterval(() => setAnimationTick(previous => previous + 1), 250)
    return () => window.clearInterval(timer)
  }, [animated, visible, textContext?.source, useInterfaceFont])
  useEffect(() => {
    if (!needsNative || !visible || textContext?.source !== 'local-client') return
    let current = true
    const request = { assetRevision: textContext.assetRevision, previewId: `label-${id}`, requestId: 1,
      width: 512, scale: 2, viewportTop: 0, viewportHeight: 256, fragments: projectComponent(value).fragments }
    componentLabelPreview(request).then(async result => {
      await decodeComponentPreview(result.png)
      if (current) setNative({ key, png: result.png, width: result.width, height: result.height })
    }).catch(() => {})
    return () => { current = false }
  }, [key, needsNative, visible, textContext, id, value, animationTick])
  const plain = plainComponentText(value, textContext)
  if (!useInterfaceFont && native?.key === key) return <span ref={target} className={`component-text ${className}`} style={{ display: 'inline-block', width: native.width * 2,
    height: native.height * 2, flexShrink: 0, verticalAlign: 'middle' }}><img src={`data:image/png;base64,${native.png}`} alt={plain}
    width={native.width * 2} height={native.height * 2} style={{ display: 'block', width: native.width * 2, height: native.height * 2,
      maxWidth: 'none', imageRendering: 'pixelated' }} /></span>
  return <span ref={target} className={`component-text ${className}`} aria-label={plain}>{projection.leaves.map(leaf => {
    const style: CSSProperties = { color: COMPONENT_COLORS[leaf.style.color ?? 'white'] ?? leaf.style.color,
      fontWeight: leaf.style.bold ? 'bold' : undefined, fontStyle: leaf.style.italic ? 'italic' : undefined,
      textDecoration: [leaf.style.underlined ? 'underline' : '', leaf.style.strikethrough ? 'line-through' : ''].filter(Boolean).join(' ') || undefined }
    const text = leaf.kind === 'literal' ? leaf.text : plainComponentText(leaf.own, textContext)
    return <span key={leaf.id} style={style}>{leaf.style.obfuscated ? text.replace(/\S/g, '▧') : text}</span>
  })}</span>
}
