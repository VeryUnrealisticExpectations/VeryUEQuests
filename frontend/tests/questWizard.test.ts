import test from 'node:test'
import assert from 'node:assert/strict'
import { dependencyProblem, newQuest, newSection, newTask } from '../src/lib/quests.ts'
import { blockedPrerequisites, integerInput, questIssues, sectionsWithDraft, taskInputId, taskRowsFor } from '../src/lib/questWizard.ts'

test('travel coordinate drafts remain invalid when an unfinished input keeps its committed value', () => {
  const section = newSection()
  const draft = newQuest(section.section_id, { x: 0, y: 0 })
  draft.objectives = [newTask('objective', 'vuequests:travel_coordinates')]
  const rows = { objective: taskRowsFor('objective', draft.objectives), reward: [] }
  const target = taskInputId(rows.objective[0].key, 'min_x')
  assert.deepEqual(questIssues(draft, rows, { [target]: '-' }, [section]).map(issue => issue.target), [target])
  assert.equal(draft.objectives[0].min_x, 0)
})

test('review validates closed stages and incomplete input without overwriting committed task values', () => {
  const section = newSection()
  const draft = newQuest(section.section_id, { x: -10, y: 12 })
  const item = newTask('objective', 'vuequests:collect_item')
  const custom = { ...newTask('reward', 'example:tokens'), quantity: 5 }
  draft.objectives = [item, newTask('objective', 'vuequests:advancement')]
  draft.rewards = [custom]
  const rows = { objective: taskRowsFor('objective', draft.objectives), reward: taskRowsFor('reward', draft.rewards) }
  const count = taskInputId(rows.objective[0].key, 'count')
  const fields = taskInputId(rows.reward[0].key, 'fields')
  const issues = questIssues(draft, rows, { 'quest-x': '-', [count]: '', [fields]: '{"quantity":' }, [section])
  assert.deepEqual(issues.map(issue => [issue.stage, issue.target]), [
    ['details', 'quest-x'],
    ['objectives', count],
    ['objectives', taskInputId(rows.objective[1].key, 'target')],
    ['rewards', fields],
  ])
  assert.deepEqual(draft.position, { x: -10, y: 12 })
  assert.equal(item.count, 1)
  assert.equal(custom.quantity, 5)
})

test('position input accepts signed Minecraft integer coordinates', () => {
  const parse = (value: string) => integerInput(value, -2_147_483_648, 2_147_483_647)
  assert.equal(parse('-2147483648'), -2_147_483_648)
  assert.equal(parse('2147483647'), 2_147_483_647)
  for (const value of ['', '-', '1.5', '0x20', '2147483648']) assert.equal(parse(value), undefined, value)
})

test('draft prerequisite selection rejects cross-section cycles without reversing links', () => {
  const home = newSection(), other = newSection()
  const edited = newQuest(home.section_id, { x: 0, y: 0 })
  const dependent = newQuest(other.section_id, { x: 2, y: 2 })
  const root = newQuest(other.section_id, { x: 4, y: 4 })
  const downstream = newQuest(other.section_id, { x: 6, y: 6 })
  dependent.dependencies = [edited.quest_id]
  downstream.dependencies = [dependent.quest_id]
  home.quests = [edited]
  other.quests = [dependent, root, downstream]
  const original = [home, other]

  const blocked = blockedPrerequisites(original, edited.quest_id)
  assert.deepEqual(blocked, new Set([edited.quest_id, dependent.quest_id, downstream.quest_id]))
  const valid = sectionsWithDraft(original, { ...edited, dependencies: [root.quest_id] })
  assert.equal(dependencyProblem(valid), null)
  assert.deepEqual(valid[0].quests[0].dependencies, [root.quest_id])
  assert.deepEqual(valid[1].quests[0].dependencies, [edited.quest_id])
  assert.deepEqual(edited.dependencies, [])
  const cycle = sectionsWithDraft(original, { ...edited, dependencies: [downstream.quest_id] })
  assert.match(dependencyProblem(cycle) ?? '', /cycle/)
})

test('new quest prerequisites are validated before insertion and missing ones can be removed', () => {
  const section = newSection()
  const draft = newQuest(section.section_id, { x: 0, y: 0 })
  const rows = { objective: [], reward: [] }
  draft.dependencies = ['missing']
  assert.equal(questIssues(draft, rows, {}, [section])[0]?.target, 'quest-prerequisites')
  draft.dependencies = []
  assert.deepEqual(questIssues(draft, rows, {}, [section]), [])
  assert.deepEqual(sectionsWithDraft([section], draft)[0].quests, [draft])
  assert.deepEqual(section.quests, [])
})
