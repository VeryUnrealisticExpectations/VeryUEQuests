import { useEffect, useMemo, useState } from 'react'
import type { ComponentTextContext } from '../types.ts'
import { loadEditorResources } from '../lib/api.ts'

export function useEditorResources(assetRevision: string, connected: boolean, locale: string): ComponentTextContext {
  const fallback = useMemo<ComponentTextContext>(() => ({
    assetRevision, source: 'server-assets', language: locale, rightToLeft: false, pending: true,
    fonts: [{ id: 'minecraft:default', loaded: false }], translations: [], keybinds: [],
  }), [assetRevision, locale])
  const [resources, setResources] = useState<{ editorRevision: string; locale: string; context: ComponentTextContext }>()
  useEffect(() => {
    if (!connected) {
      return
    }
    const controller = new AbortController()
    let timer: ReturnType<typeof setTimeout> | undefined
    let etag: string | undefined
    const refresh = async () => {
      try {
        const result = await loadEditorResources(locale, etag, controller.signal)
        if (controller.signal.aborted) {
          return
        }
        etag = result.etag
        if (result.context) {
          setResources({ editorRevision: assetRevision, locale, context: result.context })
        }
      } catch {
        if (!controller.signal.aborted) {
          setResources(previous => previous?.editorRevision === assetRevision && previous.locale === locale ? previous
            : { editorRevision: assetRevision, locale, context: { ...fallback, pending: false } })
        }
      } finally {
        if (!controller.signal.aborted) {
          timer = setTimeout(() => void refresh(), 5000)
        }
      }
    }
    void refresh()
    return () => { controller.abort(); clearTimeout(timer) }
  }, [assetRevision, connected, fallback, locale])
  return connected && resources?.editorRevision === assetRevision && resources.locale === locale ? resources.context : fallback
}
