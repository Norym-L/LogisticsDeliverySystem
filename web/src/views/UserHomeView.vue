<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { http } from '@/api/client'
import { formatTime, formatYuan, remainingLabel, type CreatedOrder, type OrderBrief, type PriceQuote, type StationView } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import StatusBadge from '@/components/StatusBadge.vue'

const router = useRouter()
const stations = ref<StationView[]>([])
const stationsReady = ref(false)
const mode = ref<'ship' | 'lookup'>('ship')

const senderName = ref('')
const senderPhone = ref('')
const receiverName = ref('')
const receiverPhone = ref('')
const fromStationId = ref<number | ''>('')
const toStationId = ref<number | ''>('')
const goodsDesc = ref('')
const weight = ref<number | ''>('')
const volumeCm3 = ref<number | ''>('')
const quote = ref<PriceQuote | null>(null)
const quoteError = ref('')
let quoteSeq = 0

watch([fromStationId, toStationId, weight, volumeCm3], async () => {
  const seq = ++quoteSeq
  quote.value = null
  quoteError.value = ''
  const mass = Number(weight.value)
  const volume = Number(volumeCm3.value)
  if (!fromStationId.value || !toStationId.value || !(mass > 0) || !(volume > 0)) return
  try {
    const result = await http.post<PriceQuote>('/pricing/quote', {
      weightKg: mass,
      volumeCm3: volume,
      fromStationId: fromStationId.value,
      toStationId: toStationId.value
    })
    if (seq === quoteSeq) quote.value = result
  } catch (e) {
    if (seq === quoteSeq) quoteError.value = e instanceof Error ? e.message : '试算失败'
  }
})

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
      weight: weight.value === '' ? null : Number(weight.value),
      volumeCm3: volumeCm3.value === '' ? null : Number(volumeCm3.value)
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
    mode.value = 'lookup'
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
        <p>填写收寄信息即可下单，无需注册；凭手机号查单，凭收件人手机号签收。</p>
      </div>
    </header>

    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="message" class="hint">{{ message }}</p>

    <div class="workspace" :class="{ 'is-compose': mode === 'ship' }">
      <div class="pane-list">
        <div class="tabs" role="tablist">
          <button type="button" :class="{ 'is-on': mode === 'ship' }" @click="mode = 'ship'">寄件</button>
          <button type="button" :class="{ 'is-on': mode === 'lookup' }" @click="mode = 'lookup'">查单</button>
        </div>

        <form v-if="mode === 'ship'" class="login-form" style="margin-top: 24px" @submit.prevent="place">
          <div class="form-split">
            <fieldset>
              <legend>寄件人</legend>
              <div class="field">
                <label for="sender-name">姓名</label>
                <input id="sender-name" v-model="senderName" required maxlength="32" />
              </div>
              <div class="field">
                <label for="sender-phone">手机号</label>
                <input id="sender-phone" v-model="senderPhone" required maxlength="11" placeholder="11 位手机号" />
              </div>
              <div class="field">
                <label for="from-station">始发站</label>
                <select id="from-station" v-model="fromStationId" required>
                  <option v-for="station in stations" :key="station.id" :value="station.id">{{ station.name }}</option>
                </select>
              </div>
            </fieldset>

            <fieldset>
              <legend>收件人</legend>
              <div class="field">
                <label for="receiver-name">姓名</label>
                <input id="receiver-name" v-model="receiverName" required maxlength="32" />
              </div>
              <div class="field">
                <label for="receiver-phone">手机号</label>
                <input id="receiver-phone" v-model="receiverPhone" required maxlength="11" />
              </div>
              <div class="field">
                <label for="to-station">目的站</label>
                <select id="to-station" v-model="toStationId" required>
                  <option v-for="station in stations" :key="station.id" :value="station.id">{{ station.name }}</option>
                </select>
              </div>
            </fieldset>
          </div>

          <div class="form-goods">
            <div class="field">
              <label for="goods">货物描述</label>
              <input id="goods" v-model="goodsDesc" maxlength="255" placeholder="例如教材" />
            </div>
            <div class="field">
              <label for="weight">质量（kg）</label>
              <input id="weight" v-model="weight" type="number" min="0" step="0.01" />
            </div>
            <div class="field">
              <label for="volume">体积（立方厘米）</label>
              <input id="volume" v-model="volumeCm3" type="number" min="0" step="1" placeholder="例如 12000" />
            </div>
          </div>

          <div class="compose-actions">
            <button type="submit" :disabled="busy">{{ busy ? '提交中…' : '提交订单' }}</button>
          </div>
        </form>

        <div v-else style="margin-top: 24px">
          <form class="filters" @submit.prevent="lookup">
            <label class="hint" for="lookup-phone">手机号</label>
            <input id="lookup-phone" v-model="phone" placeholder="寄件人或收件人手机号" maxlength="11" />
            <button class="small" type="submit" :disabled="busy">查询</button>
          </form>

          <EmptyState v-if="found && found.length === 0" title="没有相关订单" hint="手机号需要和寄件人或收件人完全一致。" />
          <p v-else-if="!found" class="hint">在左侧输入手机号查询，点开一单可查看轨迹和运费。</p>

          <div v-else class="list">
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
                  <span v-if="order.priceConfirmed && order.price != null">运费 {{ formatYuan(order.price) }}</span>
                  <span v-else-if="order.quotedPrice != null">预估 {{ formatYuan(order.quotedPrice) }}</span>
                </div>
              </div>
              <StatusBadge :state="order.state" />
            </RouterLink>
          </div>
        </div>
      </div>

      <aside class="pane-detail">
        <div v-if="mode === 'ship'" class="fare-panel">
          <h2>运费预估</h2>
          <template v-if="quote">
            <div class="fare-rows">
              <div>
                <span>里程</span>
                <b>{{ quote.distanceKm }} km</b>
              </div>
              <div>
                <span>计费重量</span>
                <b>{{ quote.chargeableWeightKg }} kg</b>
              </div>
            </div>
            <div class="fare-total">
              <span>预估价</span>
              <b>{{ formatYuan(quote.freight) }}</b>
            </div>
          </template>
          <p v-else-if="quoteError" class="error" role="alert">{{ quoteError }}</p>
          <p v-else class="hint">起止站、质量和体积都填写后，会按现有计价规则试算。</p>
          <p class="fare-note">最终价由快递员取件时核对。</p>
        </div>

        <div v-else class="pane-empty">
          <EmptyState title="查询结果在左侧" hint="点开一单即可查看轨迹、预估价和最终运费。" />
        </div>
      </aside>
    </div>
  </section>
</template>
