<script setup lang="ts">
import { ref } from 'vue'
import { http } from '@/api/client'
import { formatTime, formatYuan, type OrderDetail } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import Trajectory from '@/components/Trajectory.vue'

const orderNo = ref('')
const history = ref<OrderDetail[]>([])
const detail = ref<OrderDetail | null>(null)
const loading = ref(false)
const error = ref('')

function remember(order: OrderDetail) {
  const rest = history.value.filter((item) => item.orderNo !== order.orderNo)
  history.value = [order, ...rest]
}

async function search() {
  const no = orderNo.value.trim()
  if (!no || loading.value) return
  loading.value = true
  error.value = ''
  try {
    const found = await http.get<OrderDetail>(`/admin/orders/${encodeURIComponent(no)}`)
    detail.value = found
    remember(found)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '查询失败'
  } finally {
    loading.value = false
  }
}

function open(order: OrderDetail) {
  detail.value = order
  orderNo.value = order.orderNo
  error.value = ''
}
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <p class="page-index">02 / 03 · 查单</p>
        <h1>查单</h1>
        <p>按单号回看已审批订单的路线、预估价和最终运费。</p>
      </div>
    </header>

    <div class="workspace">
      <div class="pane-list">
        <form class="filters" @submit.prevent="search">
          <label class="hint" for="order-no">订单号</label>
          <input id="order-no" v-model="orderNo" placeholder="例如 LD20261116…" />
          <button class="small" type="submit" :disabled="loading">{{ loading ? '查询中…' : '查询' }}</button>
        </form>

        <p v-if="error" class="error" role="alert">{{ error }}</p>

        <div v-if="history.length === 0" class="pane-empty">
          <EmptyState title="输入单号查看" hint="可以是待审批、运送中或已经签收的订单。查过的单会留在这份列表里。" />
        </div>

        <div v-else class="list">
          <article
            v-for="order in history"
            :key="order.orderNo"
            class="card clickable order-row"
            :class="{ 'is-selected': detail?.orderNo === order.orderNo }"
            role="button"
            tabindex="0"
            @click="open(order)"
            @keydown.enter.prevent="open(order)"
            @keydown.space.prevent="open(order)"
          >
            <div style="min-width: 0">
              <div class="route-line">
                <span>{{ order.fromStation?.name ?? '—' }}</span>
                <span class="arrow">→</span>
                <span>{{ order.toStation?.name ?? '—' }}</span>
              </div>
              <div class="meta">
                <b>{{ order.orderNo }}</b>
                <span v-if="order.priceConfirmed">运费 {{ formatYuan(order.price) }}</span>
                <span v-else-if="order.quotedPrice != null">预估 {{ formatYuan(order.quotedPrice) }}</span>
              </div>
            </div>
            <StatusBadge :state="order.state" />
          </article>
        </div>
      </div>

      <aside class="pane-detail">
        <div v-if="!detail" class="pane-empty">
          <EmptyState title="从左侧查询一单" hint="详情里可以看到固化路线、预估价和最终运费。" />
        </div>

        <template v-else>
          <div class="kv">
            <div>
              <span>单号</span>
              <b>{{ detail.orderNo }}</b>
            </div>
            <div>
              <span>状态</span>
              <b><StatusBadge :state="detail.state" /></b>
            </div>
            <div>
              <span>始发</span>
              <b>{{ detail.fromStation?.name ?? '—' }}</b>
            </div>
            <div>
              <span>目的</span>
              <b>{{ detail.toStation?.name ?? '—' }}</b>
            </div>
            <div>
              <span>当前所在</span>
              <b>{{ detail.currentStation?.name ?? '—' }}</b>
            </div>
            <div v-if="detail.estimatedDays">
              <span>预计时效</span>
              <b>{{ detail.estimatedDays }} 天<template v-if="detail.estimatedDeliveryAt"> · {{ formatTime(detail.estimatedDeliveryAt) }}</template></b>
            </div>
            <div>
              <span>货物</span>
              <b>{{ detail.goodsDesc ?? '—' }} · {{ detail.weight ?? '—' }} kg<template v-if="detail.volumeCm3 != null"> · {{ detail.volumeCm3 }} cm³</template></b>
            </div>
            <div>
              <span>预估价</span>
              <b>{{ formatYuan(detail.quotedPrice) }}</b>
            </div>
            <div>
              <span>最终运费</span>
              <b>{{ detail.priceConfirmed ? formatYuan(detail.price) : '尚未核价' }}</b>
            </div>
            <div>
              <span>收件人</span>
              <b>{{ detail.receiverName }} {{ detail.receiverPhone }}</b>
            </div>
            <div>
              <span>下单时间</span>
              <b>{{ formatTime(detail.events?.createdAt) }}</b>
            </div>
          </div>

          <p v-if="detail.rejectReason" class="hint">拒绝原因：{{ detail.rejectReason }}</p>
          <p v-if="detail.returnOrderNo" class="hint">退回单号：{{ detail.returnOrderNo }}</p>
          <p v-if="detail.originalOrderNo" class="hint">这是退回单，原单号 {{ detail.originalOrderNo }}</p>

          <div class="detail-block">
            <h2>固化路线</h2>
            <p v-if="detail.trajectory.length === 0" class="hint">路线尚未生成。</p>
            <Trajectory v-else :nodes="detail.trajectory" />
          </div>
        </template>
      </aside>
    </div>
  </section>
</template>
