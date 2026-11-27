<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { logout, session } from '@/stores/session'
import { getTheme, setTheme, type ThemeMode } from '@/lib/theme-controller'

const router = useRouter()
const route = useRoute()
const theme = ref<ThemeMode>(getTheme())

const profiles = {
  path: {
    sub: '路径管理台',
    role: '路径管理员',
    auth: true,
    login: 'login',
    nav: [
      { name: 'pending', label: '待审批', hint: '检查请求与路线' },
      { name: 'lookup', label: '查单', hint: '已审批与在途' },
      { name: 'postmen', label: '快递员', hint: '档案与手里的单' }
    ]
  },
  station: {
    sub: '节点工作台',
    role: '节点管理员',
    auth: true,
    login: 'station-login',
    nav: [{ name: 'station-inbox', label: '本站待办', hint: '车次、到货、派单' }]
  },
  courier: {
    sub: '快递员',
    role: '快递员',
    auth: true,
    login: 'courier-login',
    nav: [{ name: 'courier-tasks', label: '我的任务', hint: '取件与派送' }]
  },
  user: {
    sub: '寄件查询',
    role: '用户',
    auth: false,
    login: 'login',
    nav: [{ name: 'user-home', label: '寄件', hint: '下单与按手机号查询' }]
  }
} as const

const profile = computed(() => profiles[(route.meta.shell as keyof typeof profiles) || 'path'])

function pick(mode: ThemeMode) {
  setTheme(mode)
  theme.value = mode
}

async function signOut() {
  await logout()
  router.push({ name: profile.value.login })
}
</script>

<template>
  <div class="app">
    <aside class="sidebar panel rise">
      <div class="brand">
        <div class="brand-mark" aria-hidden="true">通</div>
        <div class="brand-text">
          <strong>通驿</strong>
          <small>{{ profile.sub }}</small>
        </div>
      </div>

      <nav class="nav" aria-label="主导航">
        <RouterLink
          v-for="item in profile.nav"
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
            <strong>{{ profile.auth ? (session.user?.name ?? '未登录') : '访客' }}</strong>
            <small v-if="profile.auth">{{ session.user?.username }} · {{ profile.role }}</small>
            <small v-else>下单、查单、签收</small>
          </div>
          <button v-if="profile.auth" class="ghost small" type="button" @click="signOut">退出</button>
          <button v-else class="ghost small" type="button" @click="router.push('/login')">工作人员</button>
        </div>
      </div>
    </aside>

    <main class="main" tabindex="-1">
      <RouterView />
    </main>
  </div>
</template>
