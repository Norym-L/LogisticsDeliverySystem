<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { http } from '@/api/client'
import { formatTime, remainingLabel, type CreatedOrder, type OrderBrief, type StationView } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import StatusBadge from '@/components/StatusBadge.vue'

const router = useRouter()
const stations = ref<StationView[]>([])
const stationsReady = ref(false)

const senderName = ref('')
const senderPhone = ref('')
const receiverName = ref('')
const receiverPhone = ref('')
const fromStationId = ref<number | ''>('')
const toStationId = ref<number | ''>('')
const goodsDesc = ref('')
const weight = ref<number | ''>('')

const phone = ref('')
const found = ref<OrderBrief[] | null>(null)
const message = ref('')
const error = ref('')
const busy = ref(false)

async function ensureStations() {
  if (stationsReady.value) return
  stations.value = await http.get<StationView[]>('/station/list')
  stationsReady.value = true
  if (stations.value.length >= 2) {
    fromStationId.value = stations.value[0].id
    toStationId.value = stations.value[stations.value.length - 1].id
  }
}

ensureStations().catch((e) => {
  error.value = e instanceof Error ? e.message : '驿站加载失败'
})

async function place() {
  if (busy.value) return
  error.value = ''
  message.value = ''
  busy.value = true
  try {
    const created = await http.post<CreatedOrder>('/user/orders', {
      senderName: senderName.value.trim(),
      senderPhone: senderPhone.value.trim(),
      receiverName: receiverName.value.trim(),
      receiverPhone: receiverPhone.value.trim(),
      fromStationId: fromStationId.value,
      toStationId: toStationId.value,
      goodsDesc: goodsDesc.value.trim(),
      weight: weight.value === '' ? null : Number(weight.value)
    })
    message.value = `已下单 ${created.orderNo}`
    await router.push({ name: 'user-order', params: { orderNo: created.orderNo } })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '下单失败'
  } finally {
    busy.value = false
  }
}

async function lookup() {
  if (busy.value) return
  error.value = ''
  message.value = ''
  busy.value = true
  try {
    found.value = await http.get<OrderBrief[]>(`/user/orders?phone=${encodeURIComponent(phone.value.trim())}`)
  } catch (e) {
    found.value = null
    error.value = e instanceof Error ? e.message : '查询失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <h1>寄件</h1>
        <p>填写收寄信息即可下单。不需要注册，凭手机号查单，凭收件人手机号签收或拒收。</p>
      </div>
    </header>

    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="message" class="hint">{{ message }}</p>

    <div class="card rise" style="padding: 22px; margin-bottom: 18px">
      <h2 style="font-size: 16px; margin: 0 0 12px">发起订单</h2>
      <form class="login-form" @submit.prevent="place">
        <div class="field">
          <label for="sender-name">寄件人</label>
          <input id="sender-name" v-model="senderName" required maxlength="32" />
        </div>
        <div class="field">
          <label for="sender-phone">寄件人手机号</label>
          <input id="sender-phone" v-model="senderPhone" required maxlength="11" placeholder="11 位手机号" />
        </div>
        <div class="field">
          <label for="receiver-name">收件人</label>
          <input id="receiver-name" v-model="receiverName" required maxlength="32" />
        </div>
        <div class="field">
          <label for="receiver-phone">收件人手机号</label>
          <input id="receiver-phone" v-model="receiverPhone" required maxlength="11" />
        </div>
        <div class="field">
          <label for="from-station">始发站</label>
          <select id="from-station" v-model="fromStationId" required>
            <option v-for="station in stations" :key="station.id" :value="station.id">{{ station.name }}</option>
          </select>
        </div>
        <div class="field">
          <label for="to-station">目的站</label>
          <select id="to-station" v-model="toStationId" required>
            <option v-for="station in stations" :key="station.id" :value="station.id">{{ station.name }}</option>
          </select>
        </div>
        <div class="field">
          <label for="goods">货物描述</label>
          <input id="goods" v-model="goodsDesc" maxlength="255" placeholder="例如教材" />
        </div>
        <div class="field">
          <label for="weight">重量（kg）</label>
          <input id="weight" v-model="weight" type="number" min="0" step="0.01" />
        </div>
        <button type="submit" :disabled="busy">{{ busy ? '提交中…' : '提交订单' }}</button>
      </form>
    </div>

    <div class="card rise" style="padding: 22px">
      <h2 style="font-size: 16px; margin: 0 0 12px">按手机号查询</h2>
      <form class="filters" @submit.prevent="lookup">
        <input v-model="phone" placeholder="寄件人或收件人手机号" maxlength="11" />
        <button class="small" type="submit" :disabled="busy">查询</button>
      </form>

      <EmptyState v-if="found && found.length === 0" title="没有相关订单" hint="手机号需要和寄件人或收件人完全一致。" />

      <div v-else-if="found" class="list">
        <RouterLink
          v-for="order in found"
          :key="order.orderNo"
          class="card clickable order-row"
          :to="{ name: 'user-order', params: { orderNo: order.orderNo } }"
        >
          <div>
            <div class="route-line">
              <span>{{ order.fromStation ?? '—' }}</span>
              <span class="arrow">→</span>
              <span>{{ order.toStation ?? '—' }}</span>
            </div>
            <div class="meta">
              <b>{{ order.orderNo }}</b>
              <span>{{ formatTime(order.createTime) }}</span>
              <span v-if="order.state === 'WAITING'">剩余 {{ remainingLabel(order.approveDeadline) }}</span>
            </div>
          </div>
          <StatusBadge :state="order.state" />
        </RouterLink>
      </div>
    </div>
  </section>
</template>
