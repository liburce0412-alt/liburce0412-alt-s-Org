import { createClient } from '@supabase/supabase-js'

const url = import.meta.env.VITE_SUPABASE_URL as string | undefined
const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined

export const supabase = url && anonKey && !anonKey.startsWith('replace-')
  ? createClient(url, anonKey, { auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true } })
  : null

export const isSupabaseConfigured = Boolean(supabase)

export async function hasAdminRole(userId: string) {
  if (!supabase) return false
  const { data, error } = await supabase
    .from('profiles')
    .select('role,is_blocked')
    .eq('id', userId)
    .single()
  return !error && !data.is_blocked && ['moderator', 'admin', 'super_admin'].includes(data.role)
}
