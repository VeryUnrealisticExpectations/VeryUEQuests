import { useEffect, useRef, useState } from 'react'
import type { ChatComponent, ComponentTextContext } from '../types.ts'
import { hasComponentContent } from '../lib/components.ts'
import type { LocalizedField } from '../lib/questLanguages.ts'
import { ComponentEditor } from './ComponentEditor.tsx'
import { ComponentText } from './ComponentText.tsx'

export interface LocalizedTextFields<Field extends string> {
  locale: string
  languageName: string
  englishContext: ComponentTextContext
  fields: Record<Field, LocalizedField>
}

export type LocalizedTextUpdates<Field extends string> = Partial<Record<Field, ChatComponent | null>>

type LocalizedTextFieldProps = Parameters<typeof ComponentEditor>[0] & {
  locale: string
  english?: ChatComponent
  englishContext: ComponentTextContext
  serverText?: ChatComponent
  canReset: boolean
  onReset: () => void
}

export function LanguageEditingNotice({ locale, languageName }: { locale: string; languageName: string }) {
  if (locale === 'en_us') {
    return null
  }
  return <p className="language-editing-notice">Editing text in <strong><bdi>{languageName}</bdi></strong> <code>{locale}</code>.</p>
}

function TextReference({ title, value, textContext }: { title: string; value?: ChatComponent; textContext: ComponentTextContext }) {
  const content = useRef<HTMLDivElement>(null)
  const [wrapWidth, setWrapWidth] = useState(96)
  useEffect(() => {
    const element = content.current
    if (!element) {
      return
    }
    const observer = new ResizeObserver(([entry]) => {
      setWrapWidth(Math.max(32, Math.floor(entry.contentRect.width / 2)))
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  return <section className="language-reference" aria-label={title}>
    <h4>{title} <code>{textContext.language}</code></h4>
    <div ref={content} className="language-reference-text" lang={textContext.language.replaceAll('_', '-')} dir={textContext.rightToLeft ? 'rtl' : 'ltr'}>
      {value === undefined ? <small>No English text yet.</small>
        : hasComponentContent(value) ? <ComponentText value={value} textContext={textContext} wrapWidth={wrapWidth} />
        : <small>Empty</small>}
    </div>
  </section>
}

export function LocalizedTextField({ locale, english, englishContext, serverText, canReset, onReset, ...editor }: LocalizedTextFieldProps) {
  const input = useRef<HTMLDivElement>(null)
  const showReference = locale !== 'en_us'
  const showServer = serverText !== undefined && !!editor.textContext
  return <div className="localized-text-field" data-compare={showReference || showServer || undefined} hidden={editor.visible === false}>
    <div ref={input} className="localized-text-input">
      {showServer && <small className="locale-shared-label">Changes here update your local config. The server resource pack supplies the text shown in game.</small>}
      <ComponentEditor {...editor} />
      {canReset && <button type="button" className="mc-button mc-button--small localized-text-reset" onClick={() => {
        onReset()
        input.current?.querySelector<HTMLTextAreaElement>('.component-input')?.focus()
      }}
        aria-label={`Reset ${editor.label.toLowerCase()} to inherited text`}>Reset to inherited text</button>}
    </div>
    {serverText !== undefined && editor.textContext && editor.visible !== false &&
      <TextReference title="Server resource pack" value={serverText} textContext={editor.textContext} />}
    {showReference && editor.visible !== false && <TextReference title="English reference" value={english} textContext={englishContext} />}
  </div>
}
