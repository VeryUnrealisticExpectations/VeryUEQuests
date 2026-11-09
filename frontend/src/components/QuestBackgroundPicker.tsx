import { useId, useLayoutEffect, useRef, useState } from 'react'
import { DEFAULT_QUEST_BACKGROUND } from '../lib/quests.ts'
import { QuestBackgroundSprite } from './QuestBackgroundSprite.tsx'

function spriteLabel(id: string): string {
  if (id === DEFAULT_QUEST_BACKGROUND) return 'Default square'
  return id.split(':').at(-1)!.split('/').at(-1)!.replace(/^background_icon_/, '').replaceAll('_', ' ')
}

export function QuestBackgroundPicker({ value, backgrounds, revision, onChange, panel = false }:
  { value: string; backgrounds: string[]; revision: string; onChange: (id: string) => void; panel?: boolean }) {
  const id = useId()
  const activeOption = useRef<HTMLButtonElement>(null)
  const [active, setActive] = useState<string>()
  const open = panel || active !== undefined
  const choices = [...new Set([DEFAULT_QUEST_BACKGROUND, ...backgrounds, value])]
  const highlighted = active !== undefined && choices.includes(active) ? active : value
  const index = choices.indexOf(highlighted)
  const choose = (choice: string) => {
    onChange(choice)
    setActive(undefined)
  }
  useLayoutEffect(() => {
    activeOption.current?.scrollIntoView({ block: 'nearest' })
  }, [open, highlighted])

  return <div className="field quest-background-picker" onBlur={event => {
    if (!event.currentTarget.contains(event.relatedTarget)) setActive(undefined)
  }}>
    {!panel && <><label htmlFor={id}>Quest background</label>
    <button id={id} type="button" className="mc-button quest-background-trigger" role="combobox"
      title={value} aria-describedby={`${id}-value`} aria-expanded={open} aria-controls={open ? `${id}-options` : undefined}
      aria-activedescendant={open ? `${id}-option-${index}` : undefined}
      onClick={event => {
        event.currentTarget.focus()
        setActive(open ? undefined : value)
      }}
      onKeyDown={event => {
        const key = event.key
        if (key === 'Escape' && open) {
          event.preventDefault()
          event.stopPropagation()
          setActive(undefined)
        } else if (key === 'Enter' || key === ' ') {
          event.preventDefault()
          if (open) choose(highlighted)
          else setActive(value)
        } else if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(key)) {
          event.preventDefault()
          let next = index
          if (key === 'Home') next = 0
          else if (key === 'End') next = choices.length - 1
          else if (open) next += key === 'ArrowUp' || key === 'ArrowLeft' ? -1 : 1
          setActive(choices[Math.max(0, Math.min(choices.length - 1, next))])
        }
      }}>
      <QuestBackgroundSprite id={value} revision={revision} />
      <span id={`${id}-value`} className="sr-only">{spriteLabel(value)}</span>
      <span className="quest-background-chevron" aria-hidden="true" />
    </button></>}
    {open && <div id={`${id}-options`} className={`quest-background-options${panel ? ' quest-background-options--panel' : ''}`}
      role={panel ? 'group' : 'listbox'} aria-label="Quest backgrounds">
      {choices.map((choice, optionIndex) => <button key={choice} id={`${id}-option-${optionIndex}`}
        ref={!panel && choice === highlighted ? activeOption : undefined} type="button" className="mc-button quest-background-option"
        data-active={choice === highlighted || undefined} role={panel ? undefined : 'option'} tabIndex={panel ? undefined : -1}
        aria-label={spriteLabel(choice)} aria-pressed={panel ? choice === value : undefined}
        aria-selected={panel ? undefined : choice === value} title={choice} onMouseDown={event => { if (!panel) event.preventDefault() }}
        onClick={() => choose(choice)}>
        <QuestBackgroundSprite id={choice} revision={revision} />
      </button>)}
    </div>}
  </div>
}
