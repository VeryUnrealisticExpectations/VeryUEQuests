import {useState} from 'react'
import type {DragEvent} from 'react'
import type {ComponentTextContext, Section} from '../types.ts'
import {ItemIcon} from './ItemIcon.tsx'
import {ButtonSymbol} from './ButtonSymbol.tsx'
import {ComponentText} from './ComponentText.tsx'
import {plainComponentText} from '../lib/components.ts'

export function SectionSidebar({sections, selected, epoch, textContext, disabled, onSelect, onAdd, onEdit, onMove}:
                               {
                                 sections: Section[];
                                 selected?: string;
                                 epoch: number;
                                 textContext: ComponentTextContext;
                                 disabled: boolean;
                                 onSelect: (id: string) => void;
                                 onAdd: () => void;
                                 onEdit: (id: string) => void;
                                 onMove: (id: string, targetId: string, position: 'before' | 'after') => void
                               }) {
  const [dragged, setDragged] = useState<string>()
  const [dropTarget, setDropTarget] = useState<{id: string; position: 'before' | 'after'}>()
  const dropPosition = (event: DragEvent<HTMLDivElement>) => {
    const {top, height} = event.currentTarget.getBoundingClientRect()
    return event.clientY < top + height / 2 ? 'before' : 'after'
  }
  const endDrag = () => { setDragged(undefined); setDropTarget(undefined) }
  return <aside className="section-sidebar">
    <div className="sidebar-heading"><h2>Sections <span>{sections.length}</span></h2>
      <button className="mc-button mc-button--icon mc-button--green" type="button" disabled={disabled}
              aria-label="Add section" onClick={onAdd}><ButtonSymbol symbol="plus"/>
      </button>
    </div>
    <nav aria-label="Quest sections" className="section-list" onDragLeave={event => {
      if (!(event.relatedTarget instanceof Node) || !event.currentTarget.contains(event.relatedTarget)) {
        setDropTarget(undefined)
      }
    }}>
      {sections.map((section, index) =>
          <div key={section.section_id} className={`section-row ${selected === section.section_id ? 'selected' : ''} ${dragged === section.section_id && !disabled ? 'is-dragging' : ''}`}
               draggable={!disabled} title={disabled ? undefined : 'Drag to reorder section (Alt+↑/↓)'}
               data-drop-position={!disabled && dropTarget?.id === section.section_id ? dropTarget.position : undefined}
               onDragStart={event => {
                 if (disabled) { event.preventDefault(); return }
                 event.dataTransfer.effectAllowed = 'move'
                 event.dataTransfer.setData('application/x-vuequests-section', section.section_id)
                 setDragged(section.section_id)
               }}
               onDragOver={event => {
                 if (disabled || !dragged) {
                   return
                 }
                 event.preventDefault()
                 event.dataTransfer.dropEffect = 'move'
                 if (dragged === section.section_id) { setDropTarget(undefined); return }
                 const position = dropPosition(event)
                 setDropTarget(previous => previous?.id === section.section_id && previous.position === position
                   ? previous : {id: section.section_id, position})
               }}
               onDrop={event => {
                 event.preventDefault()
                 if (!disabled && dragged && event.dataTransfer.getData('application/x-vuequests-section') === dragged) {
                   onMove(dragged, section.section_id, dropPosition(event))
                 }
                 endDrag()
               }} onDragEnd={endDrag}>
            <button type="button" className="mc-button mc-button--section section-select"
                    aria-current={selected === section.section_id ? 'page' : undefined}
                    aria-keyshortcuts="Alt+ArrowUp Alt+ArrowDown"
                    onKeyDown={event => {
                      if (disabled || !event.altKey || event.key !== 'ArrowUp' && event.key !== 'ArrowDown') {
                        return
                      }
                      event.preventDefault()
                      const target = sections[index + (event.key === 'ArrowUp' ? -1 : 1)]
                      if (target) {
                        onMove(section.section_id, target.section_id, event.key === 'ArrowUp' ? 'before' : 'after')
                      }
                    }}
          onClick={() => onSelect(section.section_id)}><ItemIcon id={section.icon} epoch={epoch} />
              <span><strong><ComponentText value={section.name}
                                           textContext={textContext}/></strong><small>{section.quests.length} {section.quests.length === 1 ? 'quest' : 'quests'}{section.disabled ? ' · Disabled' : ''}</small></span>
            </button>
            <button type="button" className="mc-button mc-button--small mc-button--icon section-edit"
                    disabled={disabled} aria-label={`Edit ${plainComponentText(section.name, textContext)}`}
                    onClick={() => onEdit(section.section_id)}>⋯
            </button>
      </div>)}
      {!sections.length && <div className="sidebar-empty"><p>Your quest book starts here.</p>
        <button className="mc-button mc-button--green" type="button" disabled={disabled} onClick={onAdd}>+ Add section
        </button>
      </div>}
    </nav>
  </aside>
}
