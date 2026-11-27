export type PortalId = 'path' | 'station' | 'courier' | 'user'

const TITLES: Record<PortalId, string> = {
  path: '通驿 · 路径管理台',
  station: '通驿 · 驿站工作台',
  courier: '通驿 · 快递员工作台',
  user: '通驿 · 寄件查询'
}

/** Map route meta to a portal so the document title and TYSHA skin stay in sync. */
export function resolvePortal(shell: unknown, portal: unknown): PortalId {
  if (portal === 'path' || portal === 'station' || portal === 'courier' || portal === 'user') return portal
  if (shell === 'path' || shell === 'station' || shell === 'courier' || shell === 'user') return shell
  return 'path'
}

/**
 * Every portal uses the ink / bone / brass skin.
 * Call this on every route change so titles never leak across portals.
 */
export function syncPortalChrome(shell: unknown, portal: unknown): void {
  if (typeof document === 'undefined') return

  const id = resolvePortal(shell, portal)
  const root = document.documentElement
  const themeMeta = document.querySelector('meta[name="theme-color"]')

  document.title = TITLES[id]
  root.dataset.portal = id
  root.dataset.theme = 'sf'
  root.style.colorScheme = 'light'
  themeMeta?.setAttribute('content', '#FFFFFF')
}
