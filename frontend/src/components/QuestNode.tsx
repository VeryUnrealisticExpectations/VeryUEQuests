import type { MouseEvent, PointerEvent } from 'react'
import type { ComponentTextContext, Quest } from '../types.ts'
import { ComponentText } from './ComponentText.tsx'
import { plainComponentText } from '../lib/components.ts'
import { QuestNodeIcon } from './QuestNodeIcon.tsx'

export function QuestNode({ quest, selected, prerequisite, connecting, disabled, epoch, textContext, onPointerDown, onContextMenu, onSelect, onEdit }:
  { quest: Quest; selected: boolean; prerequisite: boolean; connecting: boolean; disabled: boolean; epoch: number;
    textContext: ComponentTextContext; onPointerDown: (event: PointerEvent<HTMLButtonElement>) => void;
    onContextMenu: (event: MouseEvent<HTMLButtonElement>) => void; onSelect: (event?: MouseEvent<HTMLButtonElement>) => void;
    onEdit: (event?: MouseEvent<HTMLButtonElement>) => void }) {
  const title = plainComponentText(quest.display.title, textContext)
  return <button type="button" className={`quest-node${selected ? ' selected' : ''}${prerequisite ? ' connection-source' : ''}`}
    disabled={disabled} style={{ left: quest.position.x, top: quest.position.y }} aria-label={title} aria-pressed={selected}
    title={quest.display.tooltip || title} onPointerDown={onPointerDown} onContextMenu={onContextMenu} onClick={onSelect} onDoubleClick={onEdit}
    onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); if (connecting) onSelect(); else onEdit() } }}>
    <QuestNodeIcon display={quest.display} epoch={epoch} revision={textContext.assetRevision} />
    <span className="quest-label"><ComponentText value={quest.display.title} textContext={textContext} useInterfaceFont /></span>
  </button>
}
