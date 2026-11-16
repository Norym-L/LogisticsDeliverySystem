<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '@/stores/session'

const router = useRouter()
const route = useRoute()

const username = ref('admin')
const password = ref('123456')
const error = ref('')
const busy = ref(false)

async function submit() {
  if (busy.value) return
  error.value = ''
  busy.value = true
  try {
    await login(username.value.trim(), password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登录失败，请重试'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <section class="login-card rise">
      <div class="brand">
        <div class="brand-mark" aria-hidden="true">通</div>
        <div class="brand-text">
          <strong>通驿</strong>
          <small>路径管理台</small>
        </div>
      </div>

      <h1>检查请求，定下路线</h1>
      <p>审批订单、查看系统推荐的最短路径，确认后即固化途经节点。</p>

      <form class="login-form" @submit.prevent="submit">
        <div class="field">
          <label for="username">管理员账号</label>
          <input id="username" v-model="username" autocomplete="username" placeholder="例如 admin" />
        </div>

        <div class="field">
          <label for="password">密码</label>
          <input
            id="password"
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="初始密码 123456"
          />
        </div>

        <p v-if="error" class="error" role="alert">{{ error }}</p>

        <button type="submit" :disabled="busy">{{ busy ? '正在登录…' : '进入管理台' }}</button>
      </form>

      <p class="hint" style="margin-top: 18px">演示账号 admin / 123456；节点管理员账号无法进入本台。</p>
    </section>
  </div>
</template>
