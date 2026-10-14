import { useEffect, useId, useRef, useState } from 'react'
import type { Catalog, ChatComponent, ComponentObject, ComponentTextContext } from '../types.ts'
import { isChatComponent, plainComponentText } from '../lib/components.ts'
import { validateComponent } from '../lib/componentPreview.ts'
import { ComponentEditor } from './ComponentEditor.tsx'
import { RegistryPicker } from './RegistryPicker.tsx'
import { TranslationKeyPicker } from './TranslationKeyPicker.tsx'
import { ComponentEditingScope } from './ComponentEditingScope.tsx'

export type ComponentInspectorKind = 'translate' | 'keybind' | 'hover' | 'json'
export function ComponentInspector({ kind, initial, textContext, catalog, epoch = 0, onApply, onClose }:
  { kind: ComponentInspectorKind; initial: ChatComponent; textContext?: ComponentTextContext; catalog?: Catalog; epoch?: number;
    onApply: (value: ChatComponent) => void; onClose: () => void }) {
  const [draft, setDraft] = useState<ChatComponent>(() => structuredClone(initial))
  const [json, setJson] = useState(() => JSON.stringify(initial, null, 2))
  const [error, setError] = useState('')
  const [checking, setChecking] = useState(false)
  const id = useId()
  const panel = useRef<HTMLElement>(null)
  const validation = useRef<AbortController | undefined>(undefined)
  const version = useRef(0)
  useEffect(() => () => { validation.current?.abort(); validation.current = undefined }, [])
  const object: ComponentObject = typeof draft === 'object' && !Array.isArray(draft) ? draft : {}
  const changeDraft = (next: ChatComponent) => { version.current++; setDraft(next); setError('') }
  const update = (change: ComponentObject) => changeDraft({ ...object, ...change })
  const hover = object.hoverEvent && typeof object.hoverEvent === 'object' ? object.hoverEvent as Record<string, unknown> : { action: 'show_text', contents: '' }
  const action = String(hover.action ?? 'show_text')
  const contents = hover.contents ?? hover.value ?? ''
  const hoverObject = contents && typeof contents === 'object' && !Array.isArray(contents) ? contents as Record<string, unknown> : {}
  const updateHover = (next: unknown) => update({ hoverEvent: { ...hover, contents: next } })
  const submit = async () => {
    if (checking) return
    const invalid = panel.current?.querySelector<HTMLInputElement | HTMLTextAreaElement>('input:invalid,textarea:invalid,select:invalid')
    if (invalid) { invalid.reportValidity(); return }
    setChecking(true); setError('')
    const controller = new AbortController()
    validation.current?.abort(); validation.current = controller
    const submittedVersion = version.current
    try {
      const next: unknown = kind === 'json' ? JSON.parse(json) : draft
      if (!isChatComponent(next)) throw new Error('Enter a Minecraft text component.')
      await validateComponent(next, controller.signal)
      if (controller.signal.aborted || validation.current !== controller) return
      if (version.current !== submittedVersion) throw new Error('The component changed while it was being checked. Apply it again.')
      onApply(next)
    } catch (failure) { if (!controller.signal.aborted && validation.current === controller) setError(failure instanceof Error ? failure.message : 'The component is invalid.') }
    finally { if (!controller.signal.aborted && validation.current === controller) { validation.current = undefined; setChecking(false) } }
  }
  const close = () => { validation.current?.abort(); validation.current = undefined; onClose() }
  return <ComponentEditingScope><section ref={panel} className="component-inspector" aria-label={kind === 'json' ? 'Component JSON' : `Edit ${kind}`} onKeyDown={event => {
    if (!event.defaultPrevented && event.key === 'Enter' && !event.nativeEvent.isComposing && (event.target instanceof HTMLInputElement || event.target instanceof HTMLSelectElement)) {
      event.preventDefault(); event.stopPropagation(); void submit()
    }
  }}>
    {kind === 'translate' && <>
      <TranslationKeyPicker value={object.translate ?? ''} onChange={translate => update({ translate })} textContext={textContext} />
      <label className="check-field"><input type="checkbox" checked={object.fallback !== undefined} onChange={event => {
        const next = { ...object }; if (event.target.checked) next.fallback = ''; else delete next.fallback; changeDraft(next)
      }} />Use fallback text when the translation is missing</label>
      {object.fallback !== undefined && <label className="field">Fallback text<input value={object.fallback} onChange={event => update({ fallback: event.target.value })} /></label>}
      <div className="component-arguments"><span>Arguments</span>{(object.with ?? []).map((argument, index) => <div key={index} className="component-argument">
        {isChatComponent(argument) ? <ComponentEditor label={`Argument ${index + 1}`} value={argument} onChange={next => update({ with: (object.with ?? []).map((entry, i) => i === index ? next : entry) })}
          textContext={textContext} catalog={catalog} epoch={epoch} multiline /> : <label className="field">Argument {index + 1} JSON<textarea defaultValue={JSON.stringify(argument)} onChange={event => {
          try { const next: unknown = JSON.parse(event.target.value); event.target.setCustomValidity(''); update({ with: (object.with ?? []).map((entry, i) => i === index ? next : entry) }) }
          catch { event.target.setCustomValidity('Enter a valid JSON value.') }
        }} /></label>}
        <button type="button" className="mc-button mc-button--red mc-button--small" onClick={() => update({ with: (object.with ?? []).filter((_, i) => i !== index) })}>Remove argument</button>
      </div>)}<button type="button" className="mc-button mc-button--green mc-button--small" onClick={() => update({ with: [...(object.with ?? []), ''] })}>+ Argument</button></div>
    </>}
    {kind === 'keybind' && <><label className="field">Keybind<input required value={object.keybind ?? ''} list={`${id}-keybind-keys`}
      onChange={event => update({ keybind: event.target.value })} placeholder="key.jump" /></label>
      <datalist id={`${id}-keybind-keys`}>{textContext?.keybinds.map(entry => <option key={entry.key} value={entry.key}>{entry.label} · {entry.resolved}</option>)}</datalist>
      <small className="muted">Current key: {plainComponentText(object, textContext)}</small></>}
    {kind === 'hover' && <>
      <label className="field">Hover type<select value={action} onChange={event => update({ hoverEvent: { action: event.target.value,
        contents: event.target.value === 'show_text' ? '' : event.target.value === 'show_item' ? { id: 'minecraft:book', count: 1 }
          : { type: 'minecraft:pig', id: crypto.randomUUID() } } })}>
        <option value="show_text">Text</option><option value="show_item">Item</option><option value="show_entity">Entity</option>
      </select></label>
      {action === 'show_text' && <ComponentEditor label="Hover text" value={isChatComponent(contents) ? contents : ''} onChange={updateHover}
        textContext={textContext} catalog={catalog} epoch={epoch} multiline />}
      {action === 'show_item' && <>
        {catalog ? <RegistryPicker label="Hover item" value={String(hoverObject.id ?? 'minecraft:book')} onChange={id => updateHover({ ...hoverObject, id })}
          catalog={catalog} epoch={epoch} /> : <label className="field">Hover item<input value={String(hoverObject.id ?? 'minecraft:book')}
            onChange={event => updateHover({ ...hoverObject, id: event.target.value })} /></label>}
        <label className="field">Count<input type="number" min={1} step={1} value={Number(hoverObject.count ?? 1)} onChange={event => updateHover({ ...hoverObject, count: Number(event.target.value) })} /></label>
        <label className="field">Item components JSON<textarea defaultValue={JSON.stringify(hoverObject.components ?? {}, null, 2)} onChange={event => {
          try { const components: unknown = JSON.parse(event.target.value); if (!components || typeof components !== 'object' || Array.isArray(components)) throw new Error();
            event.target.setCustomValidity(''); updateHover({ ...hoverObject, components }) }
          catch { event.target.setCustomValidity('Enter a JSON object.') }
        }} /></label>
      </>}
      {action === 'show_entity' && <>
        {catalog ? <RegistryPicker label="Entity type" kind="entities" value={String(hoverObject.type ?? 'minecraft:pig')} onChange={type => updateHover({ ...hoverObject, type })}
          catalog={catalog} epoch={epoch} /> : <label className="field">Entity type<input value={String(hoverObject.type ?? 'minecraft:pig')}
            onChange={event => updateHover({ ...hoverObject, type: event.target.value })} /></label>}
        <label className="field">Entity UUID<input value={String(hoverObject.id ?? '')} onChange={event => updateHover({ ...hoverObject, id: event.target.value })} /></label>
        <label className="check-field"><input type="checkbox" checked={hoverObject.name !== undefined} onChange={event => {
          const next = { ...hoverObject }; if (event.target.checked) next.name = ''; else delete next.name; updateHover(next)
        }} />Include an entity name</label>
        {hoverObject.name !== undefined && <ComponentEditor label="Entity name" value={isChatComponent(hoverObject.name) ? hoverObject.name : ''}
          onChange={name => updateHover({ ...hoverObject, name })} textContext={textContext} catalog={catalog} epoch={epoch} />}
      </>}
      {!['show_text', 'show_item', 'show_entity'].includes(action) && <p className="muted">Edit this hover event in Advanced JSON to preserve its native fields.</p>}
    </>}
    {kind === 'json' && <label className="field component-json">Native Component JSON<textarea rows={8} spellCheck={false} value={json} onChange={event => {
      version.current++; setJson(event.target.value); setError('')
    }} /></label>}
    {error && <p className="field-error" role="alert">{error}</p>}
    <div className="component-inspector-actions"><button type="button" className="mc-button mc-button--small" onClick={close}>Cancel</button>
      <button type="button" className="mc-button mc-button--green mc-button--small" disabled={checking} onClick={() => void submit()}>{checking ? 'Checking…' : 'Apply'}</button></div>
  </section></ComponentEditingScope>
}
