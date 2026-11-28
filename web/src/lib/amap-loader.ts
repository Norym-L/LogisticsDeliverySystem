/**
 * 高德地图 JS API 2.0 加载器。
 *
 * Key 只从 Vite 环境变量读取（VITE_AMAP_JS_API_KEY，构建时也兼容进程环境变量
 * AMAP_JS_API_KEY），不写死在源码里；缺失时由调用方展示明确提示。
 */

const AMAP_JS_API_URL = 'https://webapi.amap.com/maps?v=2.0&key='

export type AmapLoadErrorCode = 'MISSING_KEY' | 'NETWORK' | 'TIMEOUT'

export class AmapLoadError extends Error
{
  readonly code: AmapLoadErrorCode

  constructor(code: AmapLoadErrorCode, message: string)
  {
    super(message)
    this.name = 'AmapLoadError'
    this.code = code
  }
}

let pending: Promise<typeof AMap> | null = null

export function amapJsApiKey(): string
{
  return String(import.meta.env.VITE_AMAP_JS_API_KEY ?? '').trim()
}

/** 确保映射到 window 的高德命名空间可用；重复调用复用同一个加载 Promise。 */
export function loadAmap(): Promise<typeof AMap>
{
  if (typeof window === 'undefined')
  {
    return Promise.reject(new AmapLoadError('NETWORK', '当前环境无法加载高德地图'))
  }
  if (window.AMap)
  {
    return Promise.resolve(window.AMap)
  }
  if (pending)
  {
    return pending
  }

  const key = amapJsApiKey()
  if (!key)
  {
    return Promise.reject(new AmapLoadError('MISSING_KEY', '未配置高德地图 Key'))
  }

  pending = new Promise<typeof AMap>((resolve, reject) =>
  {
    const callbackName = `__tongyiAmapReady_${Date.now()}_${Math.random().toString(36).slice(2)}`
    const script = document.createElement('script')
    let timer: number | undefined
    let settled = false

    const cleanup = () =>
    {
      if (timer !== undefined)
      {
        window.clearTimeout(timer)
        timer = undefined
      }
      delete (window as unknown as Record<string, unknown>)[callbackName]
      script.onerror = null
      script.onload = null
    }

    const fail = (error: AmapLoadError) =>
    {
      if (settled) return
      settled = true
      cleanup()
      script.remove()
      pending = null
      reject(error)
    }

    ;(window as unknown as Record<string, unknown>)[callbackName] = () =>
    {
      if (settled) return
      settled = true
      cleanup()
      pending = null
      resolve(window.AMap)
    }

    script.async = true
    script.src = `${AMAP_JS_API_URL}${encodeURIComponent(key)}&callback=${callbackName}`
    script.onerror = () => fail(new AmapLoadError('NETWORK', '高德地图脚本加载失败'))
    timer = window.setTimeout(
      () => fail(new AmapLoadError('TIMEOUT', '高德地图脚本加载超时')),
      20000
    )
    document.head.appendChild(script)
  })

  return pending
}
