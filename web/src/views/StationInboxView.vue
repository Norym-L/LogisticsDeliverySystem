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

const departures = computed(() => items.value.filter((item) => item.bucket === 'DEPART'))
const arrivals = computed(() => items.value.filter((item) => item.bucket === 'ARRIVE'))
const dispatches = computed(() => items.value.filter((item) => item.bucket === 'DISPATCH'))

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
        <p>只显示当前驿站要处理的单：先登记车次再发运，下一站确认到货，目的站把最后一公里派给快递员。</p>
      </div>
      <div class="head-actions">
        <button class="ghost small" type="button" :disabled="loading" @click="load">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>
    </header>

    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="message" class="hint">{{ message }}</p>

    <h2 style="font-size: 16px">待发运（{{ departures.length }}）</h2>
    <div v-if="departures.length === 0" class="card" style="margin-bottom: 16px">
      <EmptyState title="没有待发运的货" hint="路径管理员批准后，始发站会在这里看到订单。" />
    </div>
    <div v-else class="list" style="margin-bottom: 18px">
      <article v-for="item in departures" :key="`d-${item.orderNo}`" class="card" style="padding: 16px">
        <div class="route-line">
          <span>{{ item.fromStation }}</span>
          <span class="arrow">→</span>
          <span>{{ item.nextStation ?? item.toStation }}</span>
          <StatusBadge :state="item.state" />
        </div>
        <p class="hint" style="margin: 6px 0">{{ item.orderNo }} · 收件人 {{ item.receiverName }} · {{ item.goodsDesc ?? '货物未填写' }}</p>
        <div class="filters">
          <input v-model="trips[item.orderNo]" maxlength="32" placeholder="车次，例如 T001" />
          <button class="small" type="button" :disabled="busyNo !== ''" @click="registerTrip(item)">登记车次</button>
          <button class="small" type="button" :disabled="busyNo !== '' || !item.tripNo" @click="depart(item)">发运</button>
        </div>
        <p v-if="!item.tripNo" class="hint">发运前需要先登记下一段车次。</p>
      </article>
    </div>

    <h2 style="font-size: 16px">待到货（{{ arrivals.length }}）</h2>
    <div v-if="arrivals.length === 0" class="card" style="margin-bottom: 16px">
      <EmptyState title="没有待确认的到货" hint="上一站发运之后，货物会出现在这里。" />
    </div>
    <div v-else class="list" style="margin-bottom: 18px">
      <article v-for="item in arrivals" :key="`a-${item.orderNo}`" class="card" style="padding: 16px">
        <div class="route-line">
          <span>{{ item.fromStation }}</span>
          <span class="arrow">→</span>
          <span>{{ item.nextStation }}</span>
        </div>
        <p class="hint" style="margin: 6px 0">{{ item.orderNo }} · 车次 {{ item.tripNo ?? '尚未登记' }}</p>
        <button class="small" type="button" :disabled="busyNo !== '' || !item.tripNo" @click="arrive(item)">确认到货</button>
        <p v-if="!item.tripNo" class="hint">出发站还没登记车次，暂时不能确认到货。</p>
      </article>
    </div>

    <h2 style="font-size: 16px">待派单（{{ dispatches.length }}）</h2>
    <div v-if="dispatches.length === 0" class="card">
      <EmptyState title="没有等待派送的货" hint="货物到达目的站后，在这里指定本站快递员。" />
    </div>
    <div v-else class="list">
      <article v-for="item in dispatches" :key="`c-${item.orderNo}`" class="card" style="padding: 16px">
        <div class="route-line">
          <span>{{ item.toStation }}</span>
          <span class="arrow">→</span>
          <span>收件人 {{ item.receiverName }}</span>
        </div>
        <p class="hint" style="margin: 6px 0">{{ item.orderNo }} · {{ item.receiverPhone }} · {{ item.goodsDesc ?? '货物未填写' }}</p>
        <div class="filters">
          <select v-model="couriers[item.orderNo]">
            <option value="">选择快递员</option>
            <option v-for="person in postmen" :key="person.id" :value="person.id">
              {{ person.name }}（{{ person.username }}）
            </option>
          </select>
          <button class="small" type="button" :disabled="busyNo !== '' || couriers[item.orderNo] === '' || couriers[item.orderNo] == null" @click="dispatch(item)">
            派单
          </button>
        </div>
      </article>
    </div>
  </section>
</template>
