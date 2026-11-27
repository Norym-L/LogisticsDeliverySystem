<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { http } from '@/api/client'
import { type OrderDetail, type OrderStatus, type StationView } from '@/api/types'
import StatusBadge from '@/components/StatusBadge.vue'
import Trajectory from '@/components/Trajectory.vue'

const route = useRoute()
const detail = ref<OrderDetail | null>(null)
const status = ref<OrderStatus | null>(null)
const stations = ref<StationView[]>([])
const error = ref('')
const message = ref('')
const busy = ref(false)

const receiverPhone = ref('')
const refuseReason = ref('')
const senderName = ref('')
const senderPhone = ref('')
const receiverName = ref('')
const receiverPhoneEdit = ref('')
const fromStationId = ref<number | ''>('')
const toStationId = ref<number | ''>('')
const goodsDesc = ref('')
const weight = ref<number | ''>('')

const orderNo = computed(() => String(route.params.orderNo ?? ''))
const isReturn = computed(() => Boolean(detail.value?.originalOrderNo))
let timer: number | undefined

function stopPoll() {
  window.clearInterval(timer)
  timer = undefined
}

function fillForm(order: OrderDetail) {
  senderName.value = order.senderName
  senderPhone.value = order.senderPhone
  receiverName.value = order.receiverName
  receiverPhoneEdit.value = order.receiverPhone
  fromStationId.value = order.fromStation?.id ?? ''
  toStationId.value = order.toStation?.id ?? ''
  goodsDesc.value = order.goodsDesc ?? ''
  weight.value = order.weight ?? ''
}

async function load() {
  error.value = ''
  detail.value = await http.get<OrderDetail>(`/user/orders/${encodeURIComponent(orderNo.value)}`)
  fillForm(detail.value)
  if (detail.value.state === 'REJECTED') {
    await ensureStations()
  }
  if (detail.value.state === 'WAITING') {
    await refreshStatus()
    if (!timer) {
      timer = window.setInterval(() => {
        refreshStatus().catch(() => undefined)
      }, 2000)
    }
  } else {
    stopPoll()
  }
}

async function refreshStatus() {
  status.value = await http.get<OrderStatus>(`/user/orders/${encodeURIComponent(orderNo.value)}/status`)
  if (status.value.state !== 'WAITING') {
    stopPoll()
    detail.value = await http.get<OrderDetail>(`/user/orders/${encodeURIComponent(orderNo.value)}`)
    fillForm(detail.value)
    if (detail.value.state === 'REJECTED') {
      await ensureStations()
    }
  } else if (detail.value) {
    detail.value = { ...detail.value, state: status.value.state, rejectReason: status.value.rejectReason }
  }
}

async function ensureStations() {
  if (stations.value.length > 0) return
  stations.value = await http.get<StationView[]>('/station/list')
}

watch(orderNo, () => {
  stopPoll()
  message.value = ''
  load().catch((e) => {
    error.value = e instanceof Error ? e.message : '订单加载失败'
  })
}, { immediate: true })

onUnmounted(stopPoll)

function countdown(): string {
  const seconds = status.value?.remainingSeconds
  if (seconds == null) return '…'
  if (seconds <= 0) return '已到截止时间'
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return minutes > 0 ? `${minutes}分${rest}秒` : `${rest}秒`
}

async function sign() {
  if (busy.value || !detail.value) return
  busy.value = true
  error.value = ''
  try {
    await http.post(`/user/orders/${detail.value.orderNo}/sign`, { receiverPhone: receiverPhone.value.trim() })
    message.value = '已签收'
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '签收失败'
  } finally {
    busy.value = false
  }
}

async function refuse() {
  if (busy.value || !detail.value) return
  busy.value = true
  error.value = ''
  try {
    const created = await http.post<{ orderNo: string }>(`/user/orders/${detail.value.orderNo}/refuse`, {
      receiverPhone: receiverPhone.value.trim(),
      reason: refuseReason.value.trim()
    })
    message.value = `已拒收，退回单号 ${created.orderNo}`
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '拒收失败'
  } finally {
    busy.value = false
  }
}

async function resubmit() {
  if (busy.value || !detail.value) return
  busy.value = true
  error.value = ''
  try {
    await ensureStations()
    await http.put(`/user/orders/${detail.value.orderNo}`, {
      senderName: senderName.value.trim(),
      senderPhone: senderPhone.value.trim(),
      receiverName: receiverName.value.trim(),
      receiverPhone: receiverPhoneEdit.value.trim(),
      fromStationId: fromStationId.value,
      toStationId: toStationId.value,
      goodsDesc: goodsDesc.value.trim(),
      weight: weight.value === '' ? null : Number(weight.value)
    })
    message.value = '已重新提交，等待审批'
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '重提失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <h1>订单 {{ orderNo }}</h1>
        <p>
          <RouterLink to="/user">返回寄件</RouterLink>
          <template v-if="detail?.returnOrderNo">
            · <RouterLink :to="{ name: 'user-order', params: { orderNo: detail.returnOrderNo } }">查看退回单</RouterLink>
          </template>
          <template v-if="detail?.originalOrderNo">
            · 退回自 <RouterLink :to="{ name: 'user-order', params: { orderNo: detail.originalOrderNo } }">{{ detail.originalOrderNo }}</RouterLink>
          </template>
        </p>
      </div>
    </header>

    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="message" class="hint">{{ message }}</p>
    <p v-if="!detail && !error" class="hint">正在读取订单…</p>

    <div v-if="detail" class="card rise" style="padding: 22px">
      <div class="kv">
        <div>
          <span>状态</span>
          <b><StatusBadge :state="detail.state" /></b>
        </div>
        <div v-if="detail.state === 'WAITING'">
          <span>审批剩余</span>
          <b>{{ countdown() }}</b>
        </div>
        <div>
          <span>寄件人</span>
          <b>{{ detail.senderName }} {{ detail.senderPhone }}</b>
        </div>
        <div>
          <span>收件人</span>
          <b>{{ detail.receiverName }} {{ detail.receiverPhone }}</b>
        </div>
        <div>
          <span>始发 / 目的</span>
          <b>{{ detail.fromStation?.name ?? '—' }} → {{ detail.toStation?.name ?? '—' }}</b>
        </div>
        <div>
          <span>货物</span>
          <b>{{ detail.goodsDesc ?? '—' }} · {{ detail.weight ?? '—' }} kg</b>
        </div>
      </div>

      <p v-if="detail.rejectReason" class="hint">拒绝原因：{{ detail.rejectReason }}</p>

      <h2 style="font-size: 15px; margin: 18px 0 8px">轨迹</h2>
      <p v-if="detail.trajectory.length === 0" class="hint">审批通过后才会生成路线。</p>
      <Trajectory v-else :nodes="detail.trajectory" />

      <form v-if="detail.state === 'DELIVERING'" class="login-form" style="margin-top: 18px" @submit.prevent="sign">
        <h2 style="font-size: 15px; margin: 0">签收或拒收</h2>
        <div class="field">
          <label for="sign-phone">收件人手机号</label>
          <input id="sign-phone" v-model="receiverPhone" required maxlength="11" />
        </div>
        <div class="field">
          <label for="refuse-reason">拒收原因（拒收时填写）</label>
          <input id="refuse-reason" v-model="refuseReason" maxlength="255" />
        </div>
        <div class="head-actions">
          <button type="submit" :disabled="busy">签收</button>
          <button class="ghost" type="button" :disabled="busy" @click="refuse">拒收并生成退回单</button>
        </div>
      </form>

      <form v-if="detail.state === 'REJECTED'" class="login-form" style="margin-top: 18px" @submit.prevent="resubmit">
        <h2 style="font-size: 15px; margin: 0">修改后重新提交</h2>
        <p class="hint">寄件人手机号必须与当前订单一致。退回单不能改收发件人和起止站。</p>
        <div class="field">
          <label>寄件人</label>
          <input v-model="senderName" :disabled="isReturn" required maxlength="32" />
        </div>
        <div class="field">
          <label>寄件人手机号</label>
          <input v-model="senderPhone" :disabled="isReturn" required maxlength="11" />
        </div>
        <div class="field">
          <label>收件人</label>
          <input v-model="receiverName" :disabled="isReturn" required maxlength="32" />
        </div>
        <div class="field">
          <label>收件人手机号</label>
          <input v-model="receiverPhoneEdit" :disabled="isReturn" required maxlength="11" />
        </div>
        <div class="field">
          <label>始发站</label>
          <select v-model="fromStationId" :disabled="isReturn">
            <option v-for="station in stations" :key="station.id" :value="station.id">{{ station.name }}</option>
          </select>
        </div>
        <div class="field">
          <label>目的站</label>
          <select v-model="toStationId" :disabled="isReturn">
            <option v-for="station in stations" :key="station.id" :value="station.id">{{ station.name }}</option>
          </select>
        </div>
        <div class="field">
          <label>货物描述</label>
          <input v-model="goodsDesc" maxlength="255" />
        </div>
        <div class="field">
          <label>重量（kg）</label>
          <input v-model="weight" type="number" min="0" step="0.01" />
        </div>
        <button type="submit" :disabled="busy">重新提交</button>
      </form>
    </div>
  </section>
</template>
