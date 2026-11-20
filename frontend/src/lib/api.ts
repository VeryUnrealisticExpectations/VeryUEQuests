import type { ComponentTextContext, EditorSnapshot, QuestEditorLanguage, QuestLanguageCatalog, QuestLanguageText, Section } from '../types.ts'
import { normalizeSections } from './quests.ts'

async function request(options?: RequestInit): Promise<EditorSnapshot> {
  const response = await fetch('/api/editor', options)
  const body = await response.json().catch(() => ({ error: 'The Minecraft editor server did not return quest data.' }))
  if (!response.ok) {
    throw new Error(body.error ?? `Editor request failed (${response.status}).`)
  }
  if (!Array.isArray(body.sections) || !body.catalog || typeof body.revision !== 'string') {
    throw new Error('The editor server returned an incomplete document.')
  }
  return { ...body, sections: normalizeSections(body.sections) }
}

export const loadEditor = (signal?: AbortSignal) => request({ signal })
export const saveEditor = (sections: Section[], revision: string) => request({
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sections, revision }),
})

export async function loadEditorResources(locale: string, etag?: string, signal?: AbortSignal): Promise<{ context?: ComponentTextContext; etag?: string }> {
  const response = await fetch(`/api/editor/resources?locale=${encodeURIComponent(locale)}`, { signal, headers: etag ? { 'If-None-Match': etag } : undefined })
  if (response.status === 304) {
    return { etag }
  }
  if (!response.ok) {
    throw new Error('Could not read Minecraft text resources.')
  }
  const context: ComponentTextContext = await response.json()
  if (typeof context.assetRevision !== 'string' || !Array.isArray(context.fonts) || !Array.isArray(context.translations) || !Array.isArray(context.keybinds)) {
    throw new Error('Minecraft returned incomplete text resources.')
  }
  return { context, etag: response.headers.get('ETag') ?? undefined }
}

async function requestLanguages<T>(locale?: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`/api/editor/languages${locale ? `/${encodeURIComponent(locale)}` : ''}`, options)
  const body = await response.json().catch(() => ({ error: 'Minecraft did not return a quest language catalog.' }))
  if (!response.ok) {
    throw new Error(body.error ?? `Quest language request failed (${response.status}).`)
  }
  return body
}

export async function listQuestLanguages(signal?: AbortSignal): Promise<QuestEditorLanguage[]> {
  const result = await requestLanguages<{ languages: QuestEditorLanguage[] }>(undefined, { signal })
  if (!Array.isArray(result.languages)) {
    throw new Error('Minecraft returned an incomplete language list.')
  }
  return result.languages
}

async function questLanguage(locale: string, options?: RequestInit): Promise<QuestLanguageCatalog> {
  const catalog = await requestLanguages<Omit<QuestLanguageCatalog, 'server'> & { server?: QuestLanguageText }>(locale, options)
  const server = catalog.server ?? { sections: {}, quests: {} }
  if (catalog.locale !== locale || typeof catalog.revision !== 'string' || !catalog.assets?.sections || !catalog.assets.quests
      || !catalog.overrides?.sections || !catalog.overrides.quests || !server.sections || !server.quests) {
    throw new Error('Minecraft returned an incomplete quest language catalog.')
  }
  return { ...catalog, server }
}

export const loadQuestLanguage = (locale: string, signal?: AbortSignal) => questLanguage(locale, { signal })

export const saveQuestLanguage = (locale: string, revision: string, overrides: QuestLanguageText) => questLanguage(locale, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ revision, overrides }),
})
