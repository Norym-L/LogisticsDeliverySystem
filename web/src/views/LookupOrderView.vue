<script setup lang="ts">
import { ref } from 'vue'
import { http } from '@/api/client'
import { formatTime, type OrderDetail } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import Trajectory from '@/components/Trajectory.vue'

const orderNo = ref('')
const detail = ref<OrderDetail | null>(null)
const loading = ref(false)
const error = ref('')

async function search() {
  const no = orderNo.value.trim()
  if (!no || loading.value) return
  loading.value = true
  error.value = ''
  detail.value = null
  try {
    detail.value = await http.get<OrderDetail>(`/admin/orders/${encodeURIComponent(no)}`)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '查询失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <h1>查单</h1>
        <p>批准或拒绝之后，订单会离开待审批列表。在这里按单号回看路线和当前状态。</p>
      </div>
    </header>

    <form class="filters" @submit.prevent="search">
      <label class="hint" for="order-no">订单号</label>
      <input id="order-no" v-model="orderNo" placeholder="例如 LD20261116…" style="min-width: 280px" />
      <button class="small" type="submit" :disabled="loading">{{ loading ? '查询中…' : '查询' }}</button>
    </form>

    <p v-if="error" class="error" role="alert">{{ error }}</p>

    <div v-else-if="!detail" class="card rise">
      <EmptyState title="输入单号查看" hint="可以是待审批、运送中或已经签收的订单。" />
    </div>

    <div v-else class="card rise" style="padding: 22px">
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
        <div>
          <span>货物</span>
          <b>{{ detail.goodsDesc ?? '—' }} · {{ detail.weight ?? '—' }} kg</b>
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

      <h2 style="font-size: 15px; margin: 18px 0 8px">固化路线</h2>
      <p v-if="detail.trajectory.length === 0" class="hint">路线尚未生成。</p>
      <Trajectory v-else :nodes="detail.trajectory" />
    </div>
  </section>
</template>
