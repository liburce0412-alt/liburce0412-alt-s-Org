export type Mode = 'fast' | 'deep'
export const AI_EVENT_NAMES = ['meta', 'status', 'delta', 'done', 'error'] as const

export function modelForMode(mode: Mode) {
  return mode === 'fast' ? 'deepseek-v4-flash' : 'deepseek-v4-pro'
}

export function thinkingForMode(mode: Mode) {
  return { type: mode === 'deep' ? 'enabled' : 'disabled' } as const
}
