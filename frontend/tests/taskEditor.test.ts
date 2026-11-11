import assert from 'node:assert/strict'
import test from 'node:test'
import type { Task } from '../src/types.ts'
import { newTask } from '../src/lib/quests.ts'
import { changeTaskType, customTaskFields, defaultTaskType, objectiveCount, taskDisplayIcon, taskEditorId, taskProblem, updateCustomTaskFields } from '../src/lib/taskEditor.ts'

test('new objectives retain separate targets and validate travel and statistic configuration', () => {
  const submission = newTask('objective', 'vuequests:submit_item')
  assert.equal(objectiveCount({ ...submission, count: 1000 }), 1000)
  assert.equal(taskProblem({ ...submission, count: 1000 }), undefined)
  assert.equal(taskProblem(newTask('objective', 'vuequests:acknowledgement')), undefined)
  assert.equal(objectiveCount(newTask('objective', 'vuequests:acknowledgement')), undefined)
  assert.equal(taskProblem(newTask('objective', 'vuequests:experience_levels')), undefined)
  for (const [type, field, value] of [
    ['travel_dimension', 'dimension', 'example:moon'], ['travel_biome', 'biome', 'example:forest'],
    ['travel_structure', 'structure', 'example:fortress'],
  ]) {
    const task = newTask('objective', `vuequests:${type}`)
    assert.ok(taskProblem(task))
    assert.equal(taskProblem({ ...task, [field]: value }), undefined)
  }
  const statistic = newTask('objective', 'vuequests:statistic')
  assert.ok(taskProblem(statistic))
  assert.equal(taskProblem({ ...statistic, statistic: 'minecraft:jump', count: 100 }), undefined)
  assert.ok(taskProblem({ ...statistic, statistic: 'minecraft:jump', count: 0 }))
  const box = newTask('objective', 'vuequests:travel_coordinates')
  assert.equal(taskProblem(box), undefined)
  assert.ok(taskProblem({ ...box, min_x: 1, max_x: 0 }))
  assert.ok(taskProblem({ ...box, max_y: 2_147_483_648 }))
  assert.ok(taskProblem({ ...box, min_z: 0.5 }))
})

test('validation catches invalid tasks even when their controls are collapsed', () => {
  const hidden: Task = { type: 'vuequests:collect_item', item: { id: 'minecraft:stone', count: 0 } }
  assert.ok(taskProblem(hidden))
  assert.equal(taskProblem({ ...hidden, item: { ...hidden.item!, count: 4 } }), undefined)
  assert.ok(taskProblem({ type: 'vuequests:experience', experience: -1 }))
  assert.equal(taskProblem({ type: 'custom:objective', fields: { keep: true } }), undefined)
})

test('new task defaults prefer item types and fall back to registered custom types', () => {
  const types = [{ id: 'custom:first', fields: [] }, { id: 'vuequests:item', fields: [] }, { id: 'vuequests:collect_item', fields: [] }]
  assert.equal(defaultTaskType('objective', types), 'vuequests:collect_item')
  assert.equal(defaultTaskType('reward', types), 'vuequests:item')
  assert.equal(defaultTaskType('reward', [types[0]]), 'custom:first')
  assert.equal(defaultTaskType('objective', []), undefined)
})

test('collect and slay targets support large counts independently of item stacks', () => {
  const collect: Task = { type: 'vuequests:collect_item', item: { id: 'minecraft:stone', count: 4 } }
  const slay: Task = { type: 'vuequests:entity', entity: 'minecraft:zombie' }
  assert.equal(objectiveCount(collect), 4)
  assert.equal(objectiveCount(slay), 1)
  assert.equal(objectiveCount({ ...collect, count: 128 }), 128)
  assert.equal(taskProblem({ ...collect, count: 128 }), undefined)
  assert.equal(taskProblem({ ...slay, count: 2_147_483_647 }), undefined)
  assert.ok(taskProblem({ ...slay, count: 2_147_483_648 }))
  assert.ok(taskProblem({ ...collect, count: 1.5 }))
  assert.ok(taskProblem({ type: 'vuequests:item', item: { id: 'minecraft:diamond', count: 100 } }))
})

test('compact task icons derive from items or fixed types and ignore legacy objective icon overrides', () => {
  const objective: Task = { type: 'vuequests:collect_item', icon: 'example:icon', item: { id: 'minecraft:stone', count: 3 } }
  assert.equal(taskDisplayIcon('objective', objective), 'minecraft:stone')
  assert.equal(taskDisplayIcon('objective', { ...objective, item: { id: 'example:ore_block', count: 3 } }), 'example:ore_block')
  assert.equal(taskDisplayIcon('objective', { type: 'vuequests:entity', entity: 'minecraft:zombie', icon: 'minecraft:zombie_spawn_egg' }), 'minecraft:iron_sword')
  assert.equal(taskDisplayIcon('objective', { type: 'vuequests:entity', entity: 'minecraft:creeper' }), 'minecraft:iron_sword')
  assert.equal(taskDisplayIcon('objective', { type: 'custom:objective' }), 'minecraft:book')
  const reward: Task = { type: 'vuequests:item', item: { id: 'minecraft:diamond', count: 2 } }
  assert.equal(taskDisplayIcon('reward', reward), 'minecraft:diamond')
  assert.equal(taskDisplayIcon('reward', { type: 'vuequests:experience', experience: 5 }), 'minecraft:experience_bottle')
  assert.equal(taskDisplayIcon('reward', { type: 'custom:reward' }), 'minecraft:chest')
  assert.equal('icon' in reward, false)
  assert.equal(objective.icon, 'example:icon')
})

test('advancement objectives store a resource location and have no quantity', () => {
  const objective = changeTaskType('objective', {
    type: 'vuequests:entity', objective_id: 'stable-id', entity: 'minecraft:zombie', count: 10,
  }, 'vuequests:advancement')
  assert.deepEqual(objective, { type: 'vuequests:advancement', objective_id: 'stable-id', advancement: '' })
  assert.ok(taskProblem(objective))
  for (const advancement of ['minecraft:story/mine_stone', 'mod-name:chapter/first', 'story/mine_stone', ':story/mine_stone']) {
    assert.equal(taskProblem({ ...objective, advancement }), undefined)
  }
  for (const advancement of ['example:', 'Bad Namespace:foo', 'example:has spaces', 'example:extra:colon']) {
    assert.ok(taskProblem({ ...objective, advancement }))
  }
  assert.equal(taskDisplayIcon('objective', objective), 'minecraft:book')
  assert.equal(objectiveCount(objective), undefined)
})

test('active task keys stay stable across reorder and keep identical UUIDs in separate columns distinct', () => {
  const objective: Task = { type: 'vuequests:collect_item', objective_id: 'same-id' }
  const reward: Task = { type: 'vuequests:item', reward_id: 'same-id' }
  assert.equal(taskEditorId('objective', objective, 0), taskEditorId('objective', objective, 4))
  assert.notEqual(taskEditorId('objective', objective, 0), taskEditorId('reward', reward, 0))
  assert.notEqual(taskEditorId('objective', objective, 0), taskEditorId('objective', { ...objective, objective_id: 'other-id' }, 0))
})

test('changing a task type keeps its UUID and choosing the existing type preserves custom payloads', () => {
  const task: Task = { type: 'custom:objective', objective_id: 'objective-id', icon: 'minecraft:book', extra: { component: { text: 'custom' } } }
  assert.strictEqual(changeTaskType('objective', task, task.type), task)
  const changed = changeTaskType('objective', task, 'vuequests:collect_item')
  assert.equal(changed.objective_id, 'objective-id')
  assert.equal(changed.item?.id, 'minecraft:cobblestone')
  assert.equal(changed.extra, undefined)
  const reward = changeTaskType('reward', { type: 'custom:reward', reward_id: 'reward-id' }, 'vuequests:experience')
  assert.equal(reward.reward_id, 'reward-id')
  assert.equal(reward.experience, 1)
  assert.deepEqual(task.extra, { component: { text: 'custom' } })
})

test('custom task JSON editing preserves reserved identity and unknown fields when the editor is reopened', () => {
  const objective: Task = { type: 'absent:objective', objective_id: 'objective-id', icon: 'minecraft:book', config: { depth: 4 }, reward_id: 'custom-payload' }
  const fields = customTaskFields('objective', objective)
  assert.deepEqual(fields, { icon: 'minecraft:book', config: { depth: 4 }, reward_id: 'custom-payload' })
  assert.deepEqual(updateCustomTaskFields('objective', objective, fields), objective)
  const reward: Task = { type: 'absent:reward', reward_id: 'reward-id', icon: 'custom:reward_icon', chance: 0.5 }
  const rewardFields = customTaskFields('reward', reward)
  assert.equal(rewardFields.icon, 'custom:reward_icon')
  assert.deepEqual(updateCustomTaskFields('reward', reward, rewardFields), reward)
  assert.deepEqual(updateCustomTaskFields('reward', reward, { icon: 'custom:reward_icon' }),
    { type: 'absent:reward', reward_id: 'reward-id', icon: 'custom:reward_icon' })
})
