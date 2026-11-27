<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { loginAs } from '@/stores/session'

const router = useRouter()
const route = useRoute()

const portal = computed(() => route.meta.portal ?? 'path')
const username = ref(portal.value === 'station' ? 'st001' : portal.value === 'courier' ? 'pm001' : 'admin')
const password = ref('123456')
const error = ref('')
const busy = ref(false)

const copy = {
  path: {
    sub: '路径管理台',
    kicker: '检查请求，',
    title: '定下路线',
    lead: '审批订单、调整途经节点，确认后即固化路线。',
    account: '管理员账号',
    hint: '演示账号 admin / 123456'
  },
  station: {
    sub: '驿站工作台',
    kicker: '登记车次，',
    title: '确认到货',
    lead: '处理本站待发运、待到货和最后一公里派单。',
    account: '节点账号',
    hint: '演示账号 st001 到 st008 / 123456'
  },
  courier: {
    sub: '快递员工作台',
    kicker: '取件，',
    title: '开始派送',
    lead: '查看分给自己的单，核对运费后再取件，由收件人签收。',
    account: '快递员账号',
    hint: '演示账号 pm001 到 pm016 / 123456'
  }
} as const

const text = computed(() => copy[portal.value])

watch(portal, (value) => {
  username.value = value === 'station' ? 'st001' : value === 'courier' ? 'pm001' : 'admin'
  password.value = '123456'
  error.value = ''
})

async function submit() {
  if (busy.value) return
  error.value = ''
  busy.value = true
  try {
    await loginAs(portal.value, username.value.trim(), password.value)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const home = portal.value === 'station' ? '/station' : portal.value === 'courier' ? '/courier' : '/'
    await router.replace(redirect || home)
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
          <small>{{ text.sub }}</small>
        </div>
      </div>

      <h1>
        <span>{{ text.kicker }}</span>
        <span>{{ text.title }}</span>
      </h1>
      <p>{{ text.lead }}</p>

      <form class="login-form" @submit.prevent="submit">
        <div class="field">
          <label for="username">{{ text.account }}</label>
          <input id="username" v-model="username" autocomplete="username" />
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

        <button type="submit" :disabled="busy">{{ busy ? '正在登录…' : '进入' }}</button>
      </form>

      <p class="hint" style="margin-top: 18px">{{ text.hint }}</p>
      <p class="hint">
        <RouterLink to="/user">我要寄件</RouterLink>
        ·
        <RouterLink to="/login">路径管理</RouterLink>
        ·
        <RouterLink to="/station/login">驿站工作台</RouterLink>
        ·
        <RouterLink to="/courier/login">快递员</RouterLink>
      </p>
    </section>
  </div>
</template>
