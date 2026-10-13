import { useEffect, useState } from 'react'
import type { EditorSnapshot, Section } from '../types.ts'
import { loadEditor, saveEditor } from '../lib/api.ts'
import { dependencyProblem } from '../lib/quests.ts'

export function useQuestEditor() {
  const [snapshot, setSnapshot] = useState<EditorSnapshot>()
  const [saved, setSaved] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const accept = (value: EditorSnapshot) => { setSnapshot(value); setSaved(JSON.stringify(value.sections)) }
  useEffect(() => {
    const controller = new AbortController()
    loadEditor(controller.signal).then(accept).catch(failure => {
      if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : 'Could not connect to Minecraft.')
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [])
  const dirty = !!snapshot && saved !== JSON.stringify(snapshot.sections)
  useEffect(() => {
    if (!dirty) return
    const prevent = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', prevent)
    return () => window.removeEventListener('beforeunload', prevent)
  }, [dirty])
  const reload = async () => {
    if (loading || saving) return
    setLoading(true); setError(''); setNotice('')
    try { accept(await loadEditor()); setNotice('Loaded the latest quests.') }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Could not reload quests.') }
    finally { setLoading(false) }
  }
  const save = async () => {
    if (!snapshot || loading || saving) return
    const problem = dependencyProblem(snapshot.sections)
    if (problem) { setError(problem); return }
    setSaving(true); setError(''); setNotice('')
    try { accept(await saveEditor(snapshot.sections, snapshot.revision)); setNotice('Saved to the world datapack and reloaded Minecraft.') }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'Could not save quests.') }
    finally { setSaving(false) }
  }
  const edit = (update: (sections: Section[]) => Section[]) => {
    if (loading || saving) return
    setSnapshot(previous => previous ? { ...previous, sections: update(previous.sections) } : previous)
    setNotice('')
  }
  return { snapshot, loading, saving, dirty, error, notice, edit, reload, save, setError }
}
