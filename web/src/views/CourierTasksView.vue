<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { http } from '@/api/client'
import { formatTime, stateLabel, type PostmanTask } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'

const tab = ref<'pending' | 'completed'>('pending')
const tasks = ref<PostmanTask[]>([])
const loading = ref(false)
const busyNo = ref('')
const error = ref('')
const message = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    tasks.value = await http.get<PostmanTask[]>(`/postman/orders?type=${tab.value}`)
  } catch (e) {
    tasks.value = []
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function pickup(task: PostmanTask) {
  if (busyNo.value) return
  busyNo.value = task.orderNo
  error.value = ''
  message.value = ''
  try {
    await http.post(`/postman/orders/${task.orderNo}/pickup`)
    message.value = `${task.orderNo} 已取件，开始派送`
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '取件失败'
  } finally {
    busyNo.value = ''
  }
}

watch(tab, load)
onMounted(load)
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <h1>我的任务</h1>
        <p>货到目的站并派给你之后出现在这里。取件后订单进入派送中，由收件人签收或拒收。</p>
      </div>
      <div class="head-actions">
        <button class="ghost small" type="button" :disabled="loading" @click="load">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>
    </header>

    <div class="tabs" role="tablist" style="margin-bottom: 16px">
      <button type="button" :class="{ 'is-on': tab === 'pending' }" @click="tab = 'pending'">待取件 / 派送中</button>
      <button type="button" :class="{ 'is-on': tab === 'completed' }" @click="tab = 'completed'">已完成</button>
    </div>

    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="message" class="hint">{{ message }}</p>
    <p v-if="loading && tasks.length === 0" class="hint">正在读取任务…</p>

    <div v-else-if="tasks.length === 0" class="card">
      <EmptyState
        :title="tab === 'pending' ? '暂时没有任务' : '还没有完成的单'"
        :hint="tab === 'pending' ? '目的站把最后一公里派给你之后，订单会出现在这里。' : '签收或拒收后会归档到这里。'"
      />
    </div>

    <div v-else class="list">
      <article v-for="task in tasks" :key="task.orderNo" class="task">
        <b>{{ task.fromStationName }} → {{ task.toStationName }}</b>
        <small>{{ task.orderNo }} · {{ stateLabel(task.orderState) }}</small>
        <small>收件人 {{ task.receiverName }} {{ task.receiverPhone }} · {{ task.goodsDesc ?? '货物未填写' }}</small>
        <small v-if="task.arriveTime">到站 {{ formatTime(task.arriveTime) }}</small>
        <div v-if="tab === 'pending' && task.orderState === 'SENDING'">
          <button class="small" type="button" :disabled="busyNo !== ''" @click="pickup(task)">取件开始派送</button>
        </div>
        <small v-else-if="task.orderState === 'DELIVERING'">已取件，等待收件人签收或拒收</small>
      </article>
    </div>
  </section>
</template>
