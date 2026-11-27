<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { http } from '@/api/client'
import { formatTime, remainingLabel, type OrderBrief, type RoutePlan, type StationView } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import StatusBadge from '@/components/StatusBadge.vue'

const orders = ref<OrderBrief[]>([])
const stations = ref<StationView[]>([])
const loading = ref(false)
const selected = ref<OrderBrief | null>(null)
const plan = ref<RoutePlan | null>(null)
const editableIds = ref<number[]>([])
const planLoading = ref(false)
const now = ref(Date.now())
let clock: number | undefined
const rejecting = ref(false)
const reason = ref('')
const busy = ref(false)
const toast = ref('')
let toastTimer: number | undefined

function say(text: string) {
  toast.value = text
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => (toast.value = ''), 2800)
}

async function load() {
  loading.value = true
  try {
    orders.value = await http.get<OrderBrief[]>('/admin/orders/pending')
    if (selected.value) {
      const current = orders.value.find((order) => order.orderNo === selected.value?.orderNo)
      if (current) selected.value = current
      else close()
    }
    if (!selected.value && orders.value[0]) await open(orders.value[0])
  } catch (e) {
    say(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadStations() {
  try {
    stations.value = await http.get<StationView[]>('/station/list')
  } catch {
    stations.value = []
  }
}

async function open(order: OrderBrief) {
  selected.value = order
  plan.value = null
  editableIds.value = []
  rejecting.value = false
  reason.value = ''
  planLoading.value = true
  try {
    plan.value = await http.get<RoutePlan>(`/admin/orders/${order.orderNo}/plan`)
    editableIds.value = plan.value.stations.map((station) => station.id)
  } catch (e) {
    say(e instanceof Error ? e.message : '路线加载失败')
  } finally {
    planLoading.value = false
  }
}

function close() {
  selected.value = null
  plan.value = null
  rejecting.value = false
}

async function approve() {
  if (!selected.value || busy.value) return
  busy.value = true
  try {
    await http.post(`/admin/orders/${selected.value.orderNo}/approve`, {
      stationIds: editableIds.value
    })
    say('已批准，路线已固化')
    close()
    await load()
  } catch (e) {
    say(e instanceof Error ? e.message : '批准失败')
  } finally {
    busy.value = false
  }
}

async function reject() {
  if (!selected.value || busy.value) return
  busy.value = true
  try {
    await http.post(`/admin/orders/${selected.value.orderNo}/reject`, {
      reason: reason.value.trim() || '管理员拒绝'
    })
    say('已拒绝，用户可修改后重提')
    close()
    await load()
  } catch (e) {
    say(e instanceof Error ? e.message : '拒绝失败')
  } finally {
    busy.value = false
  }
}

function waited(createTime: string): string {
  const minutes = Math.max(0, Math.round((now.value - new Date(createTime).getTime()) / 60000))
  return minutes < 1 ? '刚刚提交' : `已等待 ${minutes} 分钟`
}

function stationName(id: number): string {
  return stations.value.find((station) => station.id === id)?.name
    ?? plan.value?.stations.find((station) => station.id === id)?.name
    ?? String(id)
}

function setStop(index: number, event: Event) {
  const value = Number((event.target as HTMLSelectElement).value)
  const next = editableIds.value.slice()
  next[index] = value
  editableIds.value = next
}

function moveStop(index: number, delta: number) {
  const target = index + delta
  if (target <= 0 || target >= editableIds.value.length - 1) return
  const next = editableIds.value.slice()
  const [item] = next.splice(index, 1)
  next.splice(target, 0, item)
  editableIds.value = next
}

function removeStop(index: number) {
  if (index <= 0 || index >= editableIds.value.length - 1) return
  editableIds.value = editableIds.value.filter((_, i) => i !== index)
}

function addStop() {
  if (editableIds.value.length < 2) return
  const used = new Set(editableIds.value)
  const candidate = stations.value.find((station) => !used.has(station.id)) ?? stations.value[0]
  if (!candidate) return
  const next = editableIds.value.slice()
  next.splice(next.length - 1, 0, candidate.id)
  editableIds.value = next
}

const totalKm = computed(() => (plan.value ? plan.value.totalKm.toFixed(1) : '—'))
const stopCount = computed(() => (plan.value ? Math.max(1, plan.value.stations.length - 1) : 0))
const soonest = computed(() => {
  const deadlines = orders.value
    .map((order) => order.approveDeadline)
    .filter((value): value is string => Boolean(value))
  if (deadlines.length === 0) return '—'
  const nearest = deadlines.reduce((left, right) =>
    new Date(left).getTime() <= new Date(right).getTime() ? left : right
  )
  return remainingLabel(nearest, now.value)
})

onMounted(() => {
  load()
  loadStations()
  clock = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onUnmounted(() => window.clearInterval(clock))
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <p class="page-index">01 / 03 · 待审批</p>
        <h1>待审批</h1>
        <p>点开左侧一单，在右侧检查推荐路线、调整途经站，再批准或拒绝。</p>
      </div>
      <div class="head-actions">
        <span class="pane-count">{{ orders.length }} 单 · 最近剩余 {{ soonest }}</span>
        <button class="ghost small" type="button" :disabled="loading" @click="load">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>
    </header>

    <div class="workspace">
      <div class="pane-list">
        <div v-if="loading && orders.length === 0" class="pane-empty">
          <p class="hint" style="margin: 0">正在读取待审批列表…</p>
        </div>

        <div v-else-if="orders.length === 0" class="pane-empty">
          <EmptyState title="没有待审批的请求" hint="新订单提交后会出现在这里，审批超时的订单会被系统自动拒绝。" />
        </div>

        <div v-else class="list">
          <article
            v-for="order in orders"
            :key="order.orderNo"
            class="card clickable order-row"
            :class="{ 'is-selected': selected?.orderNo === order.orderNo }"
            role="button"
            tabindex="0"
            @click="open(order)"
            @keydown.enter.prevent="open(order)"
            @keydown.space.prevent="open(order)"
          >
            <div style="min-width: 0">
              <div class="route-line">
                <span>{{ order.fromStation ?? '—' }}</span>
                <span class="arrow">→</span>
                <span>{{ order.toStation ?? '—' }}</span>
              </div>
              <div class="meta">
                <b>{{ order.orderNo }}</b>
                <span>{{ waited(order.createTime) }}</span>
                <span>剩余 {{ remainingLabel(order.approveDeadline, now) }}</span>
              </div>
            </div>
            <StatusBadge :state="order.state" />
          </article>
        </div>
      </div>

      <aside class="pane-detail">
        <div v-if="!selected" class="pane-empty">
          <EmptyState title="从左侧选择一单" hint="系统已按驿站线路算出最短路径，批准后路线固定。" />
        </div>

        <template v-else>
          <div class="detail-block" style="margin-top: 0; padding-top: 0; border: none">
            <div class="detail-head">
              <h2>检查请求</h2>
              <div class="compose-actions">
                <template v-if="rejecting">
                  <button class="ghost small" type="button" :disabled="busy" @click="rejecting = false">返回</button>
                  <button class="small danger" type="button" :disabled="busy" @click="reject">
                    {{ busy ? '正在提交…' : '确认拒绝' }}
                  </button>
                </template>
                <template v-else>
                  <button class="ghost small" type="button" :disabled="busy" @click="rejecting = true">拒绝</button>
                  <button class="small" type="button" :disabled="busy || planLoading" @click="approve">
                    {{ busy ? '正在批准…' : '批准并固化路线' }}
                  </button>
                </template>
              </div>
            </div>
            <p class="hint" style="margin: 6px 0 12px">{{ selected.orderNo }}</p>
            <div v-if="rejecting" class="field" style="margin-bottom: 14px">
              <label for="reason">拒绝原因</label>
              <textarea id="reason" v-model="reason" maxlength="255" placeholder="例如：货物描述不完整，请补充后再提交" />
              <span class="hint">原因会展示给用户，用户可修改信息后重提，最多 3 次。</span>
            </div>
            <div class="kv">
              <div>
                <span>始发站</span>
                <b>{{ selected.fromStation ?? '—' }}</b>
              </div>
              <div>
                <span>目的站</span>
                <b>{{ selected.toStation ?? '—' }}</b>
              </div>
              <div>
                <span>提交时间</span>
                <b>{{ formatTime(selected.createTime) }}</b>
              </div>
              <div>
                <span>当前状态</span>
                <b><StatusBadge :state="selected.state" /></b>
              </div>
            </div>
          </div>

          <div class="detail-block">
            <div style="display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 8px">
              <h2 style="margin: 0">推荐路线</h2>
              <small class="hint">{{ stopCount }} 段 · {{ totalKm }} km</small>
            </div>

            <p v-if="planLoading" class="hint">正在计算最短路径…</p>

            <div v-else-if="plan" class="rail">
              <div
                v-for="(station, i) in plan.stations"
                :key="station.id"
                class="stop"
                :class="{ 'is-last': i === plan.stations.length - 1 }"
              >
                <span>{{ station.name }}</span>
                <small>{{ station.code }}</small>
              </div>
            </div>

            <p v-if="plan" class="hint" style="margin-top: 8px">
              可增删中间站，批准时按下面顺序固化；相邻站之间须有线路。
            </p>

            <div v-if="editableIds.length" class="list" style="gap: 8px; margin-top: 12px">
              <div
                v-for="(id, index) in editableIds"
                :key="`${index}-${id}`"
                class="stop-editor"
              >
                <select
                  :value="id"
                  :disabled="index === 0 || index === editableIds.length - 1"
                  @change="setStop(index, $event)"
                >
                  <option v-for="station in stations" :key="station.id" :value="station.id">
                    {{ station.name }}
                  </option>
                  <option v-if="!stations.some((station) => station.id === id)" :value="id">
                    {{ stationName(id) }}
                  </option>
                </select>
                <button
                  v-if="index > 0 && index < editableIds.length - 1"
                  class="ghost tiny"
                  type="button"
                  @click="moveStop(index, -1)"
                >
                  上移
                </button>
                <button
                  v-if="index > 0 && index < editableIds.length - 1"
                  class="ghost tiny"
                  type="button"
                  @click="moveStop(index, 1)"
                >
                  下移
                </button>
                <button
                  v-if="index > 0 && index < editableIds.length - 1"
                  class="ghost tiny"
                  type="button"
                  @click="removeStop(index)"
                >
                  移除
                </button>
              </div>
              <button class="ghost small" type="button" @click="addStop">加入途经站</button>
            </div>
          </div>

        </template>
      </aside>
    </div>

    <div v-if="toast" class="toast" role="status">{{ toast }}</div>
  </section>
</template>
