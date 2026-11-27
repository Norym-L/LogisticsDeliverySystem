<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { http } from '@/api/client'
import { type PostmanInfo, type StationInboxItem } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import StatusBadge from '@/components/StatusBadge.vue'

const items = ref<StationInboxItem[]>([])
const postmen = ref<PostmanInfo[]>([])
const loading = ref(false)
const busyNo = ref('')
const error = ref('')
const message = ref('')
const trips = reactive<Record<string, string>>({})
const couriers = reactive<Record<string, number | ''>>({})
const selectedKey = ref('')

const departures = computed(() => items.value.filter((item) => item.bucket === 'DEPART'))
const arrivals = computed(() => items.value.filter((item) => item.bucket === 'ARRIVE'))
const dispatches = computed(() => items.value.filter((item) => item.bucket === 'DISPATCH'))
const queue = computed(() => [...departures.value, ...arrivals.value, ...dispatches.value])

const selected = computed(() => queue.value.find((item) => keyOf(item) === selectedKey.value) ?? null)

function keyOf(item: StationInboxItem) {
  return `${item.bucket}-${item.orderNo}`
}

function bucketLabel(bucket: StationInboxItem['bucket']) {
  if (bucket === 'DEPART') return '待发运'
  if (bucket === 'ARRIVE') return '待到货'
  return '待派单'
}

function open(item: StationInboxItem) {
  selectedKey.value = keyOf(item)
}

function bindSelection() {
  if (queue.value.some((item) => keyOf(item) === selectedKey.value)) return
  selectedKey.value = queue.value[0] ? keyOf(queue.value[0]) : ''
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [inbox, people] = await Promise.all([
      http.get<StationInboxItem[]>('/admin/orders/inbox'),
      http.get<PostmanInfo[]>('/admin/postmen')
    ])
    items.value = inbox
    postmen.value = people.filter((person) => person.status === 1)
    for (const item of inbox) {
      if (item.tripNo && !trips[item.orderNo]) trips[item.orderNo] = item.tripNo
    }
    bindSelection()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function run(orderNo: string, action: () => Promise<void>, ok: string) {
  if (busyNo.value) return
  busyNo.value = orderNo
  error.value = ''
  message.value = ''
  try {
    await action()
    message.value = ok
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作失败'
  } finally {
    busyNo.value = ''
  }
}

function registerTrip(item: StationInboxItem) {
  return run(item.orderNo, () => http.post(`/admin/orders/${item.orderNo}/trip`, {
    tripNo: (trips[item.orderNo] ?? '').trim()
  }), `${item.orderNo} 已登记车次`)
}

function depart(item: StationInboxItem) {
  return run(item.orderNo, () => http.post(`/admin/orders/${item.orderNo}/depart`), `${item.orderNo} 已发运`)
}

function arrive(item: StationInboxItem) {
  return run(item.orderNo, () => http.post(`/admin/orders/${item.orderNo}/arrive`), `${item.orderNo} 已确认到货`)
}

function dispatch(item: StationInboxItem) {
  return run(item.orderNo, () => http.post(`/admin/orders/${item.orderNo}/courier`, {
    postmanId: couriers[item.orderNo]
  }), `${item.orderNo} 已派给快递员`)
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <h1>本站待办</h1>
        <p>本站要处理的单：登记车次后发运，下一站确认到货，目的站派给快递员。</p>
      </div>
      <div class="head-actions">
        <span class="pane-count">发运 {{ departures.length }} · 到货 {{ arrivals.length }} · 派单 {{ dispatches.length }}</span>
        <button class="ghost small" type="button" :disabled="loading" @click="load">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>
    </header>

    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="message" class="hint">{{ message }}</p>

    <div class="workspace">
      <div class="pane-list">
        <div v-if="loading && queue.length === 0" class="pane-empty">
          <p class="hint" style="margin: 0">正在读取本站待办…</p>
        </div>

        <div v-else-if="queue.length === 0" class="pane-empty">
          <EmptyState title="本站暂时没有待办" hint="路径管理员批准后，始发站会在这里看到待发运的订单。" />
        </div>

        <div v-else class="list">
          <article
            v-for="item in queue"
            :key="keyOf(item)"
            class="card clickable order-row"
            :class="{ 'is-selected': selectedKey === keyOf(item) }"
            role="button"
            tabindex="0"
            @click="open(item)"
            @keydown.enter.prevent="open(item)"
            @keydown.space.prevent="open(item)"
          >
            <div style="min-width: 0">
              <span class="bucket-tag">{{ bucketLabel(item.bucket) }}</span>
              <div class="route-line" style="margin-top: 8px">
                <span>{{ item.fromStation }}</span>
                <span class="arrow">→</span>
                <span>{{ item.nextStation ?? item.toStation }}</span>
              </div>
              <div class="meta">
                <b>{{ item.orderNo }}</b>
                <span>收件人 {{ item.receiverName }}</span>
              </div>
            </div>
            <StatusBadge :state="item.state" />
          </article>
        </div>
      </div>

      <aside class="pane-detail">
        <div v-if="!selected" class="pane-empty">
          <EmptyState title="从左侧选择一单" hint="登记车次、发运、确认到货或派给本站快递员，都在右侧完成。" />
        </div>

        <template v-else>
          <div>
            <span class="bucket-tag">{{ bucketLabel(selected.bucket) }}</span>
            <h2 style="margin-top: 12px">{{ selected.fromStation }} → {{ selected.nextStation ?? selected.toStation }}</h2>
            <p class="hint">{{ selected.orderNo }} · {{ selected.goodsDesc ?? '货物未填写' }}</p>
          </div>

          <div class="kv">
            <div>
              <span>状态</span>
              <b><StatusBadge :state="selected.state" /></b>
            </div>
            <div>
              <span>收件人</span>
              <b>{{ selected.receiverName }} {{ selected.receiverPhone }}</b>
            </div>
            <div>
              <span>车次</span>
              <b>{{ selected.tripNo ?? '尚未登记' }}</b>
            </div>
          </div>

          <div v-if="selected.bucket === 'DEPART'" class="detail-block">
            <h2>发运</h2>
            <div class="field">
              <label :for="`trip-${selected.orderNo}`">车次</label>
              <input :id="`trip-${selected.orderNo}`" v-model="trips[selected.orderNo]" maxlength="32" placeholder="例如 T001" />
            </div>
            <div class="compose-actions">
              <button class="small" type="button" :disabled="busyNo !== ''" @click="registerTrip(selected)">登记车次</button>
              <button class="small" type="button" :disabled="busyNo !== '' || !selected.tripNo" @click="depart(selected)">发运</button>
            </div>
            <p v-if="!selected.tripNo" class="hint">发运前需要先登记下一段车次。</p>
          </div>

          <div v-else-if="selected.bucket === 'ARRIVE'" class="detail-block">
            <h2>确认到货</h2>
            <p class="hint">车次 {{ selected.tripNo ?? '尚未登记' }}</p>
            <div class="compose-actions">
              <button class="small" type="button" :disabled="busyNo !== '' || !selected.tripNo" @click="arrive(selected)">确认到货</button>
            </div>
            <p v-if="!selected.tripNo" class="hint">出发站还没登记车次，暂时不能确认到货。</p>
          </div>

          <div v-else class="detail-block">
            <h2>派给快递员</h2>
            <div class="field">
              <label :for="`courier-${selected.orderNo}`">本站快递员</label>
              <select :id="`courier-${selected.orderNo}`" v-model="couriers[selected.orderNo]">
                <option value="">选择快递员</option>
                <option v-for="person in postmen" :key="person.id" :value="person.id">
                  {{ person.name }}（{{ person.username }}）
                </option>
              </select>
            </div>
            <div class="compose-actions">
              <button class="small" type="button" :disabled="busyNo !== '' || couriers[selected.orderNo] === '' || couriers[selected.orderNo] == null" @click="dispatch(selected)">
                派单
              </button>
            </div>
          </div>
        </template>
      </aside>
    </div>
  </section>
</template>
