import type { Section } from '../types.ts'
import { ItemIcon } from './ItemIcon.tsx'

export function SectionSidebar({ sections, selected, epoch, disabled, onSelect, onAdd, onEdit }:
  { sections: Section[]; selected?: string; epoch: number; disabled: boolean; onSelect: (id: string) => void; onAdd: () => void; onEdit: (id: string) => void }) {
  return <aside className="section-sidebar">
    <div className="sidebar-heading"><h2>Sections <span>{sections.length}</span></h2>
      <button className="icon-button" type="button" disabled={disabled} aria-label="Add section" onClick={onAdd}>+</button></div>
    <nav aria-label="Quest sections" className="section-list">
      {[...sections].sort((a, b) => a.name.localeCompare(b.name)).map(section => <div key={section.section_id} className={`section-row ${selected === section.section_id ? 'selected' : ''}`}>
        <button type="button" className="section-select" aria-current={selected === section.section_id ? 'page' : undefined}
          onClick={() => onSelect(section.section_id)}><ItemIcon id={section.icon} epoch={epoch} />
          <span><strong>{section.name}</strong><small>{section.quests.length} {section.quests.length === 1 ? 'quest' : 'quests'}{section.disabled ? ' · Disabled' : ''}</small></span></button>
        <button type="button" className="icon-button section-edit" disabled={disabled} aria-label={`Edit ${section.name}`} onClick={() => onEdit(section.section_id)}>⋯</button>
      </div>)}
      {!sections.length && <div className="sidebar-empty"><p>Your quest book starts here.</p><button type="button" disabled={disabled} onClick={onAdd}>+ Add section</button></div>}
    </nav>
    <footer className="sidebar-footer"><span className="keycap">Double-click</span> a quest to edit it.<br />Drag quests to arrange the map.</footer>
  </aside>
}
