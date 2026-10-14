import type { ComponentProjection, ComponentSelection } from './components.ts'
import { leafAtOffset } from './components.ts'

export interface ComponentAnnotation {
  kind: 'translate' | 'keybind' | 'hover'
  letter: 'T' | 'K' | 'H'
  title: string
  leafId: string
}

export function componentAnnotations(projection: ComponentProjection, selection: ComponentSelection): ComponentAnnotation[] {
  const leaves = selection.start === selection.end ? [leafAtOffset(projection, selection.start)].filter(leaf => !!leaf)
    : projection.leaves.filter(leaf => leaf.start < selection.end && leaf.end > selection.start)
  const annotations = new Map<ComponentAnnotation['kind'], ComponentAnnotation>()
  for (const leaf of leaves) {
    if (!leaf) continue
    if (typeof leaf.own !== 'string' && leaf.own.translate) annotations.set('translate', {
      kind: 'translate', letter: 'T', title: `Translation: ${leaf.own.translate}`, leafId: leaf.id,
    })
    if (typeof leaf.own !== 'string' && leaf.own.keybind) annotations.set('keybind', {
      kind: 'keybind', letter: 'K', title: `Keybind: ${leaf.own.keybind}`, leafId: leaf.id,
    })
    if (leaf.style.hoverEvent !== undefined) annotations.set('hover', {
      kind: 'hover', letter: 'H', title: 'Hover event attached to this text', leafId: leaf.id,
    })
  }
  return [...annotations.values()]
}
