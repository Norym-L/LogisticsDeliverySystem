<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { logout, session } from '@/stores/session'
import { getTheme, setTheme, type ThemeMode } from '@/lib/theme-controller'

const router = useRouter()
const theme = ref<ThemeMode>(getTheme())

const nav = [
  { name: 'pending', label: '待审批', hint: '检查请求与路线' },
  { name: 'postmen', label: '快递员', hint: '档案与手里的单' }
]

function pick(mode: ThemeMode) {
  setTheme(mode)
  theme.value = mode
}

async function signOut() {
  await logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="app">
    <aside class="sidebar panel rise">
      <div class="brand">
        <div class="brand-mark" aria-hidden="true">通</div>
        <div class="brand-text">
          <strong>通驿</strong>
          <small>路径管理台</small>
        </div>
      </div>

      <nav class="nav" aria-label="主导航">
        <RouterLink
          v-for="item in nav"
          :key="item.name"
          :to="{ name: item.name }"
          class="nav-item"
          active-class="is-active"
        >
          <span class="nav-label">{{ item.label }}</span>
          <small class="nav-hint">{{ item.hint }}</small>
        </RouterLink>
      </nav>

      <div class="sidebar-foot">
        <div class="theme-pick" role="group" aria-label="配色">
          <button
            v-for="mode in (['light', 'milk', 'dark'] as ThemeMode[])"
            :key="mode"
            class="theme-dot"
            :class="{ 'is-on': theme === mode }"
            type="button"
            :aria-pressed="theme === mode"
            @click="pick(mode)"
          >
            {{ mode === 'light' ? '纸白' : mode === 'milk' ? '乳白' : '石墨' }}
          </button>
        </div>

        <div class="who">
          <div class="who-text">
            <strong>{{ session.user?.name ?? '未登录' }}</strong>
            <small>{{ session.user?.username }} · 路径管理员</small>
          </div>
          <button class="ghost small" type="button" @click="signOut">退出</button>
        </div>
      </div>
    </aside>

    <main class="main" tabindex="-1">
      <RouterView />
    </main>
  </div>
</template>
