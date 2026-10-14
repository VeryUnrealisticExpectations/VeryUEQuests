import type {ComponentTextContext, Section} from '../types.ts'
import {ItemIcon} from './ItemIcon.tsx'
import {ButtonSymbol} from './ButtonSymbol.tsx'
import {ComponentText} from './ComponentText.tsx'
import {plainComponentText} from '../lib/components.ts'

export function SectionSidebar({sections, selected, epoch, textContext, disabled, onSelect, onAdd, onEdit}:
                               {
                                 sections: Section[];
                                 selected?: string;
                                 epoch: number;
                                 textContext: ComponentTextContext;
                                 disabled: boolean;
                                 onSelect: (id: string) => void;
                                 onAdd: () => void;
                                 onEdit: (id: string) => void
                               }) {
  return <aside className="section-sidebar">
    <div className="sidebar-heading"><h2>Sections <span>{sections.length}</span></h2>
      <button className="mc-button mc-button--icon mc-button--green" type="button" disabled={disabled}
              aria-label="Add section" onClick={onAdd}><ButtonSymbol symbol="plus"/>
      </button>
    </div>
    <nav aria-label="Quest sections" className="section-list">
      {[...sections].sort((a, b) => plainComponentText(a.name, textContext).localeCompare(plainComponentText(b.name, textContext))).map(section =>
          <div key={section.section_id} className={`section-row ${selected === section.section_id ? 'selected' : ''}`}>
            <button type="button" className="mc-button mc-button--section section-select"
                    aria-current={selected === section.section_id ? 'page' : undefined}
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
