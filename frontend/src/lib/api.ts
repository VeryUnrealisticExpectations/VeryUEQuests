import type { EditorSnapshot, Section } from '../types.ts'
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
