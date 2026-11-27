<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { http } from '@/api/client'
import { formatTime, formatYuan, stateLabel, type PostmanTask, type PriceQuote } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'

const tab = ref<'pending' | 'completed'>('pending')
const tasks = ref<PostmanTask[]>([])
const selectedNo = ref('')
const loading = ref(false)
const busyNo = ref('')
const error = ref('')
const message = ref('')
const measures = reactive<Record<string, { volume: number | ''; weight: number | '' }>>({})
const quotes = reactive<Record<string, PriceQuote | null>>({})
const quoteErrors = reactive<Record<string, string>>({})
const quoteSeq = reactive<Record<string, number>>({})

const selected = ref<PostmanTask | null>(null)

function measureOf(task: PostmanTask) {
  if (!measures[task.orderNo]) {
    measures[task.orderNo] = {
      volume: task.actualVolumeCm3 ?? task.volumeCm3 ?? '',
      weight: task.actualWeight ?? task.weight ?? ''
    }
  }
  return measures[task.orderNo]
}

function bindSelection() {
  const current = tasks.value.find((task) => task.orderNo === selectedNo.value)
  if (current) {
    selected.value = current
    return
  }
  selected.value = tasks.value[0] ?? null
  selectedNo.value = selected.value?.orderNo ?? ''
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    tasks.value = await http.get<PostmanTask[]>(`/postman/orders?type=${tab.value}`)
    for (const task of tasks.value) measureOf(task)
    bindSelection()
  } catch (e) {
    tasks.value = []
    selected.value = null
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function open(task: PostmanTask) {
  selectedNo.value = task.orderNo
  selected.value = task
}

async function preview(task: PostmanTask) {
  const measure = measureOf(task)
  const volume = Number(measure.volume)
  const weight = Number(measure.weight)
  quotes[task.orderNo] = null
  quoteErrors[task.orderNo] = ''
  if (!(volume > 0) || !(weight > 0)) {
    quoteErrors[task.orderNo] = '请先录入体积和质量'
    return
  }
  const seq = (quoteSeq[task.orderNo] ?? 0) + 1
  quoteSeq[task.orderNo] = seq
  try {
    const result = await http.post<PriceQuote>('/pricing/quote', {
      weightKg: weight,
      volumeCm3: volume,
      orderNo: task.orderNo
    })
    if (quoteSeq[task.orderNo] === seq) quotes[task.orderNo] = result
  } catch (e) {
    if (quoteSeq[task.orderNo] === seq) {
      quoteErrors[task.orderNo] = e instanceof Error ? e.message : '试算失败'
    }
  }
}

async function confirm(task: PostmanTask) {
  if (busyNo.value) return
  const measure = measureOf(task)
  const volume = Number(measure.volume)
  const weight = Number(measure.weight)
  if (!(volume > 0) || !(weight > 0)) {
    error.value = '请先录入体积和质量'
    return
  }
  busyNo.value = task.orderNo
  error.value = ''
  message.value = ''
  try {
    const result = await http.post<PriceQuote>(`/postman/orders/${task.orderNo}/price`, {
      volumeCm3: volume,
      weightKg: weight
    })
    quotes[task.orderNo] = result
    message.value = `${task.orderNo} 已核价 ${formatYuan(result.freight)}`
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '核价失败'
  } finally {
    busyNo.value = ''
  }
}

async function pickup(task: PostmanTask) {
  if (busyNo.value) return
  if (!task.priceConfirmed) {
    error.value = '请先核对运费再取件'
    return
  }
  busyNo.value = task.orderNo
  error.value = ''
  message.value = ''
  try {
    await http.post(`/postman/orders/${task.orderNo}/pickup`, {})
    message.value = `${task.orderNo} 已取件，开始派送`
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '取件失败'
  } finally {
    busyNo.value = ''
  }
}

watch(tab, () => {
  selectedNo.value = ''
  load()
})
onMounted(load)
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <h1>我的任务</h1>
        <p>派给你的单在左侧。取件前先在右侧录入实测体积和质量，确认运费。</p>
      </div>
      <div class="head-actions">
        <button class="ghost small" type="button" :disabled="loading" @click="load">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>
    </header>

    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="message" class="hint">{{ message }}</p>

    <div class="workspace">
      <div class="pane-list">
        <div class="tabs" role="tablist">
          <button type="button" :class="{ 'is-on': tab === 'pending' }" @click="tab = 'pending'">待取件 / 派送中</button>
          <button type="button" :class="{ 'is-on': tab === 'completed' }" @click="tab = 'completed'">已完成</button>
        </div>

        <p v-if="loading && tasks.length === 0" class="hint" style="margin-top: 20px">正在读取任务…</p>

        <div v-else-if="tasks.length === 0" class="pane-empty">
          <EmptyState
            :title="tab === 'pending' ? '暂时没有任务' : '还没有完成的单'"
            :hint="tab === 'pending' ? '目的站把最后一公里派给你之后，订单会出现在这里。' : '签收或拒收后会归档到这里。'"
          />
        </div>

        <div v-else class="list">
          <article
            v-for="task in tasks"
            :key="task.orderNo"
            class="task clickable"
            :class="{ 'is-selected': selected?.orderNo === task.orderNo }"
            role="button"
            tabindex="0"
            @click="open(task)"
            @keydown.enter.prevent="open(task)"
            @keydown.space.prevent="open(task)"
          >
            <b>{{ task.fromStationName }} → {{ task.toStationName }}</b>
            <small>{{ task.orderNo }} · {{ stateLabel(task.orderState) }}</small>
            <small>收件人 {{ task.receiverName }} {{ task.receiverPhone }}</small>
            <small v-if="task.priceConfirmed">已核价 {{ formatYuan(task.price) }}</small>
            <small v-else-if="tab === 'pending'">尚未核价，不能取件</small>
          </article>
        </div>
      </div>

      <aside class="pane-detail">
        <div v-if="!selected" class="pane-empty">
          <EmptyState title="从左侧选择一单" hint="录入实测体积和质量后，系统会算出运费，确认后才能取件。" />
        </div>

        <template v-else>
          <div>
            <h2>{{ selected.fromStationName }} → {{ selected.toStationName }}</h2>
            <p class="hint">{{ selected.orderNo }} · {{ stateLabel(selected.orderState) }}</p>
          </div>

          <div class="kv">
            <div>
              <span>收件人</span>
              <b>{{ selected.receiverName }} {{ selected.receiverPhone }}</b>
            </div>
            <div>
              <span>货物</span>
              <b>{{ selected.goodsDesc ?? '货物未填写' }}</b>
            </div>
            <div v-if="selected.arriveTime">
              <span>到站</span>
              <b>{{ formatTime(selected.arriveTime) }}</b>
            </div>
            <div>
              <span>申报预估</span>
              <b>{{ formatYuan(selected.quotedPrice) }}</b>
            </div>
            <div>
              <span>最终运费</span>
              <b>{{ selected.priceConfirmed ? formatYuan(selected.price) : '尚未核价' }}</b>
            </div>
          </div>

          <form
            v-if="tab === 'pending' && (selected.orderState === 'SENDING' || selected.orderState === 'DELIVERING')"
            class="login-form detail-block"
            @submit.prevent="confirm(selected)"
          >
            <h2>核对运费</h2>
            <div class="field">
              <label :for="`vol-${selected.orderNo}`">实测体积（立方厘米）</label>
              <input
                :id="`vol-${selected.orderNo}`"
                v-model="measureOf(selected).volume"
                type="number"
                min="0"
                step="1"
                required
              />
            </div>
            <div class="field">
              <label :for="`wt-${selected.orderNo}`">实测质量（kg）</label>
              <input
                :id="`wt-${selected.orderNo}`"
                v-model="measureOf(selected).weight"
                type="number"
                min="0"
                step="0.01"
                required
              />
            </div>

            <div v-if="quotes[selected.orderNo]" class="fare-panel" style="margin-top: 8px">
              <div class="fare-rows">
                <div>
                  <span>里程</span>
                  <b>{{ quotes[selected.orderNo]?.distanceKm }} km</b>
                </div>
                <div>
                  <span>计费重量</span>
                  <b>{{ quotes[selected.orderNo]?.chargeableWeightKg }} kg</b>
                </div>
              </div>
              <div class="fare-total">
                <span>系统计价</span>
                <b>{{ formatYuan(quotes[selected.orderNo]?.freight) }}</b>
              </div>
            </div>
            <p v-else-if="quoteErrors[selected.orderNo]" class="hint">{{ quoteErrors[selected.orderNo] }}</p>

            <div class="compose-actions">
              <button class="ghost small" type="button" :disabled="busyNo !== ''" @click="preview(selected)">试算运费</button>
              <button class="small" type="submit" :disabled="busyNo !== ''">
                {{ selected.priceConfirmed ? '重新确认运费' : '确认运费' }}
              </button>
              <button
                v-if="selected.orderState === 'SENDING'"
                class="small"
                type="button"
                :disabled="busyNo !== '' || !selected.priceConfirmed"
                @click="pickup(selected)"
              >
                取件开始派送
              </button>
            </div>
            <p class="fare-note">未核价不能取件，确认运费后再取件。</p>
          </form>

          <p v-else-if="selected.orderState === 'DELIVERING'" class="hint detail-block">已取件，等待收件人签收或拒收。</p>
        </template>
      </aside>
    </div>
  </section>
</template>
