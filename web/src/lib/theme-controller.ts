import { computed, onMounted, onUnmounted, ref } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'milk'
export const THEME_STORAGE_KEY = 'milk-design-theme'
export const THEME_EVENT = 'milk-theme-change'
const INK = '#FFFFFF'
let current: ThemeMode = 'light'

function storedTheme(value: string | null | undefined): ThemeMode {
  return value === 'dark' || value === 'milk' ? value : 'light'
}

/** Exact RGB complement; alpha and geometry are never changed. */
export function invertHex(color: string): string {
  const match = /^#([\da-f]{3,4}|[\da-f]{6}|[\da-f]{8})$/i.exec(color)
  if (!match) return color
  let hex = match[1]!
  if (hex.length < 5) hex = [...hex].map(c => c + c).join('')
  return '#' + [0, 2, 4].map(i => (255 - parseInt(hex.slice(i, i + 2), 16)).toString(16).padStart(2, '0')).join('') + hex.slice(6)
}

export function getTheme(): ThemeMode {
  return current
}
export function isDark(): boolean { return false }

/** For Canvas, WebGL inputs and chart libraries that require resolved colors. */
export function themeColor(color: string): string {
  return color
}

function applyInk() {
  if (typeof document === 'undefined') return
  document.documentElement.dataset.theme = 'sf'
  document.documentElement.style.colorScheme = 'light'
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', INK)
}

/** Kept for older callers; the product no longer switches paper / milk / graphite. */
export function setTheme(mode: ThemeMode) {
  current = mode
  try { localStorage.setItem(THEME_STORAGE_KEY, mode) } catch { /* Private/blocked storage still permits this session. */ }
  applyInk()
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent(THEME_EVENT, { detail: 'sf' }))
  }
}

export function initTheme() {
  let mode: ThemeMode = 'light'
  try { mode = storedTheme(localStorage.getItem(THEME_STORAGE_KEY)) } catch { /* Default remains light. */ }
  current = mode
  applyInk()
}

export function useTheme() {
  const theme = ref<ThemeMode>(getTheme())
  const sync = () => { theme.value = getTheme() }
  const storage = (event: StorageEvent) => {
    if (event.key === THEME_STORAGE_KEY || event.key === null) current = storedTheme(event.newValue)
  }
  onMounted(() => { sync(); window.addEventListener(THEME_EVENT, sync); window.addEventListener('storage', storage) })
  onUnmounted(() => { window.removeEventListener(THEME_EVENT, sync); window.removeEventListener('storage', storage) })
  return { theme, isDark: computed(() => false), setTheme, toggle: () => setTheme(getTheme() === 'dark' ? 'light' : 'dark') }
}
