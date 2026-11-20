import { useEffect, useRef, useState } from 'react'
import type { ChatComponent, EditorSnapshot, QuestEditorLanguage, QuestLanguageCatalog, QuestLanguageText, QuestLocaleField, Section } from '../types.ts'
import { listQuestLanguages, loadEditor, loadQuestLanguage, saveEditor, saveQuestLanguage } from '../lib/api.ts'
import { dependencyProblem } from '../lib/quests.ts'

const ENGLISH = 'en_us'

interface LanguageDraft extends QuestLanguageCatalog {
  edits: QuestLanguageText
  create?: boolean
}

const makeDraft = (catalog: QuestLanguageCatalog): LanguageDraft => ({ ...catalog, edits: catalog.overrides })

function languageChanged(draft: LanguageDraft) {
  return draft.create || JSON.stringify(draft.edits) !== JSON.stringify(draft.overrides)
}

function patchFields<T extends string>(original: Partial<Record<T, ChatComponent>> | undefined, updates: Partial<Record<T, ChatComponent | null>>) {
  const result: Partial<Record<T, ChatComponent>> = { ...original }
  for (const field of Object.keys(updates) as T[]) {
    const value = updates[field]
    if (value === null) {
      delete result[field]
    } else if (value !== undefined) {
      result[field] = value
    }
  }
  return result
}

export function useQuestEditor() {
  const [snapshot, setSnapshot] = useState<EditorSnapshot>()
  const [saved, setSaved] = useState('')
  const [locale, setLocale] = useState(ENGLISH)
  const [languages, setLanguages] = useState<QuestEditorLanguage[]>([])
  const [drafts, setDrafts] = useState<Partial<Record<string, LanguageDraft>>>({})
  const [loading, setLoading] = useState(true)
  const [languagesLoading, setLanguagesLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const operation = useRef(false)
  const accept = (value: EditorSnapshot) => { setSnapshot(value); setSaved(JSON.stringify(value.sections)) }
  useEffect(() => {
    const controller = new AbortController()
    Promise.all([loadEditor(controller.signal), listQuestLanguages(controller.signal), loadQuestLanguage(ENGLISH, controller.signal)]).then(([quests, list, english]) => {
      if (controller.signal.aborted) {
        return
      }
      accept(quests)
      setLanguages(list)
      setDrafts({ [ENGLISH]: makeDraft(english) })
    }).catch(failure => {
      if (!controller.signal.aborted) {
        setError(failure instanceof Error ? failure.message : 'Could not connect to Minecraft.')
      }
    }).finally(() => { if (!controller.signal.aborted) { setLoading(false) } })
    return () => controller.abort()
  }, [])
  const questsChanged = !!snapshot && saved !== JSON.stringify(snapshot.sections)
  const changedLanguages = Object.values(drafts).filter((draft): draft is LanguageDraft => !!draft && !!languageChanged(draft))
  const dirty = questsChanged || changedLanguages.length > 0
  useEffect(() => {
    if (!dirty) {
      return
    }
    const prevent = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', prevent)
    return () => window.removeEventListener('beforeunload', prevent)
  }, [dirty])
  const reload = async () => {
    if (loading || operation.current) {
      return
    }
    operation.current = true
    setLoading(true); setError(''); setNotice('')
    try {
      const [quests, list, english] = await Promise.all([loadEditor(), listQuestLanguages(), loadQuestLanguage(ENGLISH)])
      const selected = list.some(language => language.code === locale) ? locale : ENGLISH
      const refreshed: Record<string, LanguageDraft> = { [ENGLISH]: makeDraft(english) }
      if (selected !== ENGLISH) {
        refreshed[selected] = makeDraft(await loadQuestLanguage(selected))
      }
      accept(quests)
      setLanguages(list)
      setDrafts(refreshed)
      setLocale(selected)
      setNotice('Loaded the latest quests and translations.')
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'Could not reload the quest editor.') }
    finally { operation.current = false; setLoading(false) }
  }
  const selectLanguage = async (code: string, create: boolean) => {
    if (loading || operation.current) {
      return
    }
    operation.current = true
    setLanguagesLoading(true); setError('')
    try {
      const current = drafts[code] ?? makeDraft(await loadQuestLanguage(code))
      setDrafts(previous => ({ ...previous, [code]: { ...current, create: current.create || create } }))
      setLocale(code)
      if (create) {
        setNotice('')
      }
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'Could not open the quest translation.') }
    finally { operation.current = false; setLanguagesLoading(false) }
  }
  const save = async () => {
    if (!snapshot || loading || operation.current || !dirty) {
      return
    }
    const problem = questsChanged && dependencyProblem(snapshot.sections)
    if (problem) { setError(problem); return }
    operation.current = true
    setSaving(true); setError(''); setNotice('')
    try {
      if (questsChanged) {
        accept(await saveEditor(snapshot.sections, snapshot.revision))
      }
      const failures: string[] = []
      for (const draft of changedLanguages) {
        try {
          const catalog = await saveQuestLanguage(draft.locale, draft.revision, draft.edits)
          setDrafts(previous => ({ ...previous, [catalog.locale]: makeDraft(catalog) }))
          setLanguages(previous => previous.map(language => language.code === catalog.locale ? { ...language, hasCatalog: true } : language))
        } catch (failure) {
          failures.push(`${draft.locale}: ${failure instanceof Error ? failure.message : 'Could not save translation.'}`)
        }
      }
      if (failures.length) {
        setError(`Translations still pending. ${failures.join(' ')}`)
      } else if (questsChanged && changedLanguages.length) {
        setNotice('Saved quests and translations.')
      } else {
        setNotice(questsChanged ? 'Saved quests.' : 'Saved translations.')
      }
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'Could not save quests.') }
    finally { operation.current = false; setSaving(false) }
  }
  const edit = (update: (sections: Section[]) => Section[]) => {
    if (loading || operation.current) {
      return
    }
    setSnapshot(previous => previous ? { ...previous, sections: update(previous.sections) } : previous)
    setNotice('')
  }
  const editTranslation = (update: (text: QuestLanguageText) => QuestLanguageText) => {
    if (loading || operation.current) {
      return
    }
    setDrafts(previous => {
      const draft = previous[locale]
      return draft ? { ...previous, [locale]: { ...draft, edits: update(draft.edits) } } : previous
    })
    setNotice('')
  }
  const editSectionTranslation = (id: string, updates: Partial<Record<'name', ChatComponent | null>>) => {
    if (!Object.keys(updates).length) {
      return
    }
    editTranslation(text => {
      const sections = { ...text.sections }
      const fields = patchFields(sections[id], updates)
      if (Object.keys(fields).length) {
        sections[id] = fields
      } else {
        delete sections[id]
      }
      return { ...text, sections }
    })
  }
  const editQuestTranslation = (id: string, updates: Partial<Record<QuestLocaleField, ChatComponent | null>>) => {
    if (!Object.keys(updates).length) {
      return
    }
    editTranslation(text => {
      const quests = { ...text.quests }
      const fields = patchFields(quests[id], updates)
      if (Object.keys(fields).length) {
        quests[id] = fields
      } else {
        delete quests[id]
      }
      return { ...text, quests }
    })
  }
  return { snapshot, locale, languages: languages.map(language => drafts[language.code]?.create ? { ...language, hasCatalog: true } : language),
    activeLanguage: drafts[locale], englishLanguage: drafts[ENGLISH], loading, languagesLoading, saving, dirty, error, notice,
    edit, editSectionTranslation, editQuestTranslation, selectLanguage, reload, save, setError }
}
