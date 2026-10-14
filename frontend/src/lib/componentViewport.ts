import type { ComponentTextContext } from '../types.ts'

export const COMPONENT_SCALE = 2
export const COMPONENT_MIN_HEIGHT = 40
export const COMPONENT_MAX_HEIGHT = 2052

export function componentPreviewMode(context?: ComponentTextContext, approximate = false): 'pending' | 'native' | 'approximate' {
  if (context?.pending) return 'pending'
  return context?.source === 'local-client' && !approximate ? 'native' : 'approximate'
}

export function resizedComponentHeight(initial: number, delta: number): number {
  return Math.max(COMPONENT_MIN_HEIGHT, Math.min(COMPONENT_MAX_HEIGHT, Math.round(initial + delta)))
}

export function componentPreviewHeight(viewportHeight: number): number {
  return Math.min(1024, Math.ceil((viewportHeight + 64) / 128) * 128)
}

export function fittedComponentHeight(contentHeight: number): number {
  return Math.max(COMPONENT_MIN_HEIGHT, Math.min(COMPONENT_MAX_HEIGHT, Math.ceil(contentHeight * COMPONENT_SCALE) + 4))
}
