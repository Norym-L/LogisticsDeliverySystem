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
  return loginAs('path', username, password)
}

/** 三个入口分开登录：路径管理员、节点管理员走操作员接口，快递员走自己的接口 */
export async function loginAs(
  portal: 'path' | 'station' | 'courier',
  username: string,
  password: string
): Promise<LoginView> {
  const user = portal === 'courier'
    ? await http.post<LoginView>('/auth/postman/login', { username, password })
    : await http.post<LoginView>('/auth/operator/login', { username, password })
  const expected = portal === 'courier' ? 'POSTMAN' : portal === 'station' ? 'STATION_ADMIN' : 'PATH_ADMIN'
  if (user.role !== expected) {
    await http.post('/auth/logout').catch(() => undefined)
    session.user = null
    const message = portal === 'path'
      ? '请使用路径管理员账号（admin）登录'
      : portal === 'station'
        ? '请使用节点管理员账号（st001 到 st008）登录'
        : '请使用快递员账号（pm001 到 pm016）登录'
    throw new Error(message)
  }
  session.user = user
  session.ready = true
  return user
}

export async function logout(): Promise<void> {
  await http.post('/auth/logout').catch(() => undefined)
  session.user = null
}
