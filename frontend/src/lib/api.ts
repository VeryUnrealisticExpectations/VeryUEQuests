import type { ComponentTextContext, EditorSnapshot, Section } from '../types.ts'
import { normalizeSections } from './quests.ts'

async function request(options?: RequestInit): Promise<EditorSnapshot> {
  const response = await fetch('/api/editor', options)
  const body = await response.json().catch(() => ({ error: 'The Minecraft editor server did not return quest data.' }))
  if (!response.ok) throw new Error(body.error ?? `Editor request failed (${response.status}).`)
  if (!Array.isArray(body.sections) || !body.catalog || typeof body.revision !== 'string') throw new Error('The editor server returned an incomplete document.')
  return { ...body, sections: normalizeSections(body.sections) }
}

export const loadEditor = (signal?: AbortSignal) => request({ signal })
export const saveEditor = (sections: Section[], revision: string) => request({
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sections, revision }),
})

export async function loadEditorResources(etag?: string, signal?: AbortSignal): Promise<{ context?: ComponentTextContext; etag?: string }> {
  const response = await fetch('/api/editor/resources', { signal, headers: etag ? { 'If-None-Match': etag } : undefined })
  if (response.status === 304) return { etag }
  if (!response.ok) throw new Error('Could not read Minecraft text resources.')
  const context: ComponentTextContext = await response.json()
  if (typeof context.assetRevision !== 'string' || !Array.isArray(context.fonts) || !Array.isArray(context.translations) || !Array.isArray(context.keybinds)) {
    throw new Error('Minecraft returned incomplete text resources.')
  }
  return { context, etag: response.headers.get('ETag') ?? undefined }
}
