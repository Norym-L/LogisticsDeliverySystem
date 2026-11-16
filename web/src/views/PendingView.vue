<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { http } from '@/api/client'
import { formatTime, type OrderBrief, type RoutePlan } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import StatusBadge from '@/components/StatusBadge.vue'

const orders = ref<OrderBrief[]>([])
const loading = ref(false)
const selected = ref<OrderBrief | null>(null)
const plan = ref<RoutePlan | null>(null)
const planLoading = ref(false)
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
  } catch (e) {
    say(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function open(order: OrderBrief) {
  selected.value = order
  plan.value = null
  rejecting.value = false
  reason.value = ''
  planLoading.value = true
  try {
    plan.value = await http.get<RoutePlan>(`/admin/orders/${order.orderNo}/plan`)
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
    await http.post(`/admin/orders/${selected.value.orderNo}/approve`, {})
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
  const minutes = Math.max(0, Math.round((Date.now() - new Date(createTime).getTime()) / 60000))
  return minutes < 1 ? '刚刚提交' : `已等待 ${minutes} 分钟`
}

const totalKm = computed(() => (plan.value ? plan.value.totalKm.toFixed(1) : '—'))
const stopCount = computed(() => (plan.value ? Math.max(1, plan.value.stations.length - 1) : 0))

onMounted(load)
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <h1>待审批请求</h1>
        <p>系统已按驿站线路算出最短路径，批准后路线固定，随后由各站按车次运输。</p>
      </div>
      <div class="head-actions">
        <button class="ghost small" type="button" :disabled="loading" @click="load">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>
    </header>

    <div class="stats">
      <div class="stat">
        <b>{{ orders.length }}</b>
        <span>待审批订单</span>
      </div>
      <div class="stat">
        <b>60s</b>
        <span>审批超时后自动拒绝</span>
      </div>
      <div class="stat">
        <b>Dijkstra</b>
        <span>最短路径算法</span>
      </div>
    </div>

    <div v-if="loading && orders.length === 0" class="card rise" style="padding: 32px">
      <p class="hint" style="margin: 0">正在读取待审批列表…</p>
    </div>

    <div v-else-if="orders.length === 0" class="card rise">
      <EmptyState title="没有待审批的请求" hint="新订单提交后会出现在这里，审批超时的订单会被系统自动拒绝。" />
    </div>

    <div v-else class="list">
      <article
        v-for="(order, index) in orders"
        :key="order.orderNo"
        class="card clickable order-row rise"
        :style="{ animationDelay: `${Math.min(index, 6) * 80}ms` }"
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
          </div>
        </div>

        <div class="meta">
          <b>{{ formatTime(order.createTime) }}</b>
          <span>{{ waited(order.createTime) }}</span>
        </div>

        <div class="row-end">
          <StatusBadge :state="order.state" />
          <button class="small" type="button" @click.stop="open(order)">查看路线</button>
        </div>
      </article>
    </div>

    <Teleport to="body">
      <template v-if="selected">
        <div class="overlay" @click="close" />
        <aside class="drawer" role="dialog" aria-modal="true" aria-labelledby="drawer-title">
          <header class="drawer-head">
            <div>
              <h2 id="drawer-title">检查请求</h2>
              <p class="hint" style="margin: 0">{{ selected.orderNo }}</p>
            </div>
            <button class="ghost small" type="button" aria-label="关闭" @click="close">关闭</button>
          </header>

          <div class="drawer-body">
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

            <div>
              <div style="display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 8px">
                <strong style="font-size: 14px">推荐路线</strong>
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

              <p v-if="plan" class="hint" style="margin-top: 12px">
                批准后按此顺序生成 {{ plan.stations.length }} 个途经节点；各站需为该段登记车次后才能发运。
              </p>
            </div>

            <div v-if="rejecting" class="field">
              <label for="reason">拒绝原因</label>
              <textarea id="reason" v-model="reason" placeholder="例如：货物描述不完整，请补充后再提交" />
              <span class="hint">原因会展示给用户，用户可修改信息后重提，最多 3 次。</span>
            </div>
          </div>

          <footer class="drawer-foot">
            <template v-if="rejecting">
              <button class="ghost small" type="button" :disabled="busy" @click="rejecting = false">返回</button>
              <button class="small" type="button" :disabled="busy" @click="reject">
                {{ busy ? '正在提交…' : '确认拒绝' }}
              </button>
            </template>
            <template v-else>
              <button class="ghost small" type="button" :disabled="busy" @click="rejecting = true">拒绝</button>
              <button class="small" type="button" :disabled="busy || planLoading" @click="approve">
                {{ busy ? '正在批准…' : '批准并固化路线' }}
              </button>
            </template>
          </footer>
        </aside>
      </template>
    </Teleport>

    <div v-if="toast" class="toast" role="status">{{ toast }}</div>
  </section>
</template>
