<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { http } from '@/api/client'
import { formatTime, formatYuan, type OrderDetail, type OrderStatus, type PriceQuote, type StationView } from '@/api/types'
import OrderQrCode from '@/components/OrderQrCode.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import TrackMap from '@/components/TrackMap.vue'
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
const cancelPhone = ref('')
const qrContent = ref('')
const senderName = ref('')
const senderPhone = ref('')
const receiverName = ref('')
const receiverPhoneEdit = ref('')
const fromStationId = ref<number | ''>('')
const toStationId = ref<number | ''>('')
const goodsDesc = ref('')
const weight = ref<number | ''>('')
const volumeCm3 = ref<number | ''>('')
const quote = ref<PriceQuote | null>(null)
const quoteError = ref('')
let quoteSeq = 0

const orderNo = computed(() => String(route.params.orderNo ?? ''))
const isReturn = computed(() => Boolean(detail.value?.originalOrderNo))
const canCancel = computed(() =>
  detail.value != null
  && ['WAITING', 'AWAITING_RECEIPT', 'APPROVED'].includes(detail.value.state))
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
  volumeCm3.value = order.volumeCm3 ?? ''
}

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

async function load() {
  error.value = ''
  detail.value = await http.get<OrderDetail>(`/user/orders/${encodeURIComponent(orderNo.value)}`)
  fillForm(detail.value)
  qrContent.value = ''
  http.get<{ content: string }>(`/user/orders/${encodeURIComponent(orderNo.value)}/qrcode`)
    .then((code) => { qrContent.value = code.content })
    .catch(() => undefined)
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
      weight: weight.value === '' ? null : Number(weight.value),
      volumeCm3: volumeCm3.value === '' ? null : Number(volumeCm3.value)
    })
    message.value = '已重新提交，等待审批'
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '重提失败'
  } finally {
    busy.value = false
  }
}

async function cancelOrder() {
  if (busy.value || !detail.value) return
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    await http.post(`/user/orders/${detail.value.orderNo}/cancel`, {
      senderPhone: cancelPhone.value.trim()
    })
    message.value = '订单已取消'
    stopPoll()
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '取消失败'
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

    <div v-if="detail" class="workspace" :class="{ 'is-compose': detail.state === 'REJECTED' }">
      <div class="pane-list">
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
            <span>始发站</span>
            <b>{{ detail.fromStation?.name ?? '—' }}</b>
          </div>
          <div>
            <span>目的站</span>
            <b>{{ detail.toStation?.name ?? '—' }}</b>
          </div>
          <div v-if="detail.estimatedDays">
            <span>预计时效</span>
            <b>{{ detail.estimatedDays }} 天<template v-if="detail.estimatedDeliveryAt"> · {{ formatTime(detail.estimatedDeliveryAt) }}</template></b>
          </div>
          <div>
            <span>货物</span>
            <b>{{ detail.goodsDesc ?? '—' }} · {{ detail.weight ?? '—' }} kg<template v-if="detail.volumeCm3 != null"> · {{ detail.volumeCm3 }} cm³</template></b>
          </div>
        </div>

        <p v-if="detail.rejectReason" class="hint">拒绝原因：{{ detail.rejectReason }}</p>
        <p v-if="detail.state === 'CANCELLED'" class="hint">
          订单已取消<template v-if="detail.events.cancelledAt">（{{ formatTime(detail.events.cancelledAt) }}）</template>。
        </p>
        <p v-if="detail.state === 'AWAITING_RECEIPT'" class="hint">
          路线已批准，等待始发站确认收件并核价。
        </p>

        <div class="detail-block">
          <h2>轨迹地图</h2>
          <TrackMap
            :nodes="detail.trajectory"
            :position="detail.currentPosition"
            :from-station="detail.fromStation"
            :to-station="detail.toStation"
          />
          <p v-if="detail.trajectory.length === 0" class="hint">审批通过后才会生成途经站，当前仅显示起止站。</p>
          <Trajectory v-else :nodes="detail.trajectory" />
        </div>

        <form v-if="detail.state === 'REJECTED'" class="login-form detail-block" @submit.prevent="resubmit">
          <h2>修改后重新提交</h2>
          <p class="hint">寄件人手机号必须与当前订单一致。退回单不能改收发件人和起止站。</p>
          <div class="form-split">
            <fieldset>
              <legend>寄件人</legend>
              <div class="field">
                <label for="resubmit-sender-name">姓名</label>
                <input id="resubmit-sender-name" v-model="senderName" :disabled="isReturn" required maxlength="32" />
              </div>
              <div class="field">
                <label for="resubmit-sender-phone">手机号</label>
                <input id="resubmit-sender-phone" v-model="senderPhone" :disabled="isReturn" required maxlength="11" />
              </div>
              <div class="field">
                <label>始发站</label>
                <select v-model="fromStationId" :disabled="isReturn">
                  <option v-for="station in stations" :key="station.id" :value="station.id">{{ station.name }}</option>
                </select>
              </div>
            </fieldset>
            <fieldset>
              <legend>收件人</legend>
              <div class="field">
                <label for="resubmit-receiver-name">姓名</label>
                <input id="resubmit-receiver-name" v-model="receiverName" :disabled="isReturn" required maxlength="32" />
              </div>
              <div class="field">
                <label for="resubmit-receiver-phone">手机号</label>
                <input id="resubmit-receiver-phone" v-model="receiverPhoneEdit" :disabled="isReturn" required maxlength="11" />
              </div>
              <div class="field">
                <label>目的站</label>
                <select v-model="toStationId" :disabled="isReturn">
                  <option v-for="station in stations" :key="station.id" :value="station.id">{{ station.name }}</option>
                </select>
              </div>
            </fieldset>
          </div>
          <div class="form-goods">
            <div class="field">
              <label>货物描述</label>
              <input v-model="goodsDesc" maxlength="255" />
            </div>
            <div class="field">
              <label>质量（kg）</label>
              <input v-model="weight" type="number" min="0" step="0.01" />
            </div>
            <div class="field">
              <label>体积（立方厘米）</label>
              <input v-model="volumeCm3" type="number" min="0" step="1" />
            </div>
          </div>
          <div class="compose-actions">
            <button type="submit" :disabled="busy">重新提交</button>
          </div>
        </form>
      </div>

      <aside class="pane-detail">
        <div class="fare-panel">
          <h2>运费</h2>
          <div class="fare-rows">
            <div>
              <span>预估价</span>
              <b>{{ formatYuan(detail.quotedPrice) }}</b>
            </div>
            <div>
              <span>最终运费</span>
              <b>{{ detail.priceConfirmed ? formatYuan(detail.price) : '尚未核价' }}</b>
            </div>
            <div v-if="detail.estimatedDays">
              <span>预计时效</span>
              <b>{{ detail.estimatedDays }} 天</b>
            </div>
          </div>
          <template v-if="detail.state === 'REJECTED' && quote">
            <div class="fare-total">
              <span>修改后预估</span>
              <b>{{ formatYuan(quote.freight) }}</b>
            </div>
            <div class="fare-rows" style="border: none; padding-top: 16px">
              <div>
                <span>里程</span>
                <b>{{ quote.distanceKm }} km</b>
              </div>
              <div>
                <span>计费重量</span>
                <b>{{ quote.chargeableWeightKg }} kg</b>
              </div>
              <div v-if="quote.estimatedDays">
                <span>预计时效</span>
                <b>{{ quote.estimatedDays }} 天</b>
              </div>
            </div>
          </template>
          <p v-else-if="detail.state === 'REJECTED' && quoteError" class="error" role="alert">{{ quoteError }}</p>
          <p class="fare-note">最终运费在始发站确认收件并核价后锁定，目的站不再改价。</p>
        </div>

        <OrderQrCode v-if="qrContent" :content="qrContent" :label="`订单 ${detail.orderNo} 二维码`" />

        <form v-if="canCancel" class="login-form detail-block" @submit.prevent="cancelOrder">
          <h2>取消订单</h2>
          <p class="hint">订单首次发运前可取消；已发运、派送中、已签收或已拒收的订单不能取消。</p>
          <div class="field">
            <label for="cancel-phone">寄件人手机号</label>
            <input id="cancel-phone" v-model="cancelPhone" required maxlength="11" placeholder="用于确认寄件人身份" />
          </div>
          <div class="compose-actions">
            <button class="ghost small" type="submit" :disabled="busy">取消订单</button>
          </div>
        </form>

        <form v-if="detail.state === 'DELIVERING'" class="login-form detail-block" @submit.prevent="sign">
          <h2>签收或拒收</h2>
          <div class="field">
            <label for="sign-phone">收件人手机号</label>
            <input id="sign-phone" v-model="receiverPhone" required maxlength="11" />
          </div>
          <div class="field">
            <label for="refuse-reason">拒收原因（拒收时填写）</label>
            <input id="refuse-reason" v-model="refuseReason" maxlength="255" />
          </div>
          <div class="compose-actions">
            <button type="submit" :disabled="busy">签收</button>
            <button class="ghost" type="button" :disabled="busy" @click="refuse">拒收并生成退回单</button>
          </div>
        </form>
      </aside>
    </div>
  </section>
</template>
