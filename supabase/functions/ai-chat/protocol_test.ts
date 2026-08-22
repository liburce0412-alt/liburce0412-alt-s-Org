import assert from 'node:assert/strict'
import { AI_EVENT_NAMES, modelForMode, thinkingForMode } from './protocol.ts'

Deno.test('DeepSeek FAST and DEEP mappings remain frozen', () => {
  assert.equal(modelForMode('fast'), 'deepseek-v4-flash')
  assert.equal(modelForMode('deep'), 'deepseek-v4-pro')
  assert.deepEqual(thinkingForMode('fast'), { type: 'disabled' })
  assert.deepEqual(thinkingForMode('deep'), { type: 'enabled' })
})

Deno.test('SSE protocol remains compatible', () => {
  assert.deepEqual(AI_EVENT_NAMES, ['meta', 'status', 'delta', 'done', 'error'])
})
