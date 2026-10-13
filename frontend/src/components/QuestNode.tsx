import type { PointerEvent } from 'react'
import type { Quest } from '../types.ts'
import { ItemIcon } from './ItemIcon.tsx'

export function QuestNode({ quest, selected, prerequisite, epoch, onPointerDown, onSelect, onEdit }:
  { quest: Quest; selected: boolean; prerequisite: boolean; epoch: number; onPointerDown: (event: PointerEvent<HTMLButtonElement>) => void;
    onSelect: () => void; onEdit: () => void }) {
  return <button type="button" className={`quest-node ${selected ? 'selected' : ''} ${prerequisite ? 'connection-source' : ''}`}
    style={{ left: quest.position.x, top: quest.position.y }} aria-label={quest.display.title} aria-pressed={selected}
    title={quest.display.tooltip || quest.display.title} onPointerDown={onPointerDown} onClick={onSelect} onDoubleClick={onEdit}
    onKeyDown={event => { if (event.key === 'Enter') { event.preventDefault(); onEdit() } }}>
    <ItemIcon id={quest.display.icon} epoch={epoch} /><span className="quest-label">{quest.display.title}</span>
  </button>
}
