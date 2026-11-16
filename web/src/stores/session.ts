import { reactive } from 'vue'
import { http } from '@/api/client'
import type { LoginView } from '@/api/types'

export const session = reactive<{
  user: LoginView | null
  ready: boolean
}>({ user: null, ready: false })

/** 启动时恢复登录态；未登录不抛错，交给路由守卫处理 */
export async function loadSession(): Promise<void> {
  if (session.ready) return
  try {
    session.user = await http.get<LoginView>('/auth/me')
  } catch {
    session.user = null
  } finally {
    session.ready = true
  }
}

export async function login(username: string, password: string): Promise<LoginView> {
  const user = await http.post<LoginView>('/auth/operator/login', { username, password })
  if (user.role !== 'PATH_ADMIN') {
    await http.post('/auth/logout').catch(() => undefined)
    throw new Error('该账号是节点管理员，请使用路径管理员账号（admin）登录')
  }
  session.user = user
  session.ready = true
  return user
}

export async function logout(): Promise<void> {
  await http.post('/auth/logout').catch(() => undefined)
  session.user = null
}
