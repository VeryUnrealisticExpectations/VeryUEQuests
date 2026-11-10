import { isAdvancementId } from '../lib/taskEditor.ts'
import { ResourceIdPicker } from './ResourceIdPicker.tsx'

export function AdvancementPicker({ entries, value, onChoose }:
  { entries: string[]; value: string; onChoose: (id: string) => void }) {
  return <ResourceIdPicker kind="advancements" entries={entries.map(id => ({ id, name: id }))}
    value={value} onChoose={onChoose} validId={isAdvancementId} />
}
