<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { http } from '@/api/client'
import { formatTime, formatYuan, stateLabel, type PostmanTask } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'

const tab = ref<'pending' | 'completed'>('pending')
const tasks = ref<PostmanTask[]>([])
const selectedNo = ref('')
const selected = ref<PostmanTask | null>(null)
const loading = ref(false)
const busyNo = ref('')
const error = ref('')
const message = ref('')
const locateNo = ref('')

const measures = reactive<Record<string, { volume: number | ''; weight: number | '' }>>({})

function isReceipt(task: PostmanTask | null): boolean {
  return task?.taskType === 'RECEIVE'
}

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

async function locate() {
  const no = locateNo.value.trim()
  if (!no || busyNo.value) return
  busyNo.value = no
  error.value = ''
  message.value = ''
  try {
    const task = await http.get<PostmanTask>(`/postman/orders/${encodeURIComponent(no)}`)
    if (tab.value !== 'pending') tab.value = 'pending'
    await load()
    const found = tasks.value.find((item) => item.orderNo === task.orderNo) ?? task
    selectedNo.value = found.orderNo
    selected.value = found
    message.value = `已定位 ${found.orderNo}`
  } catch (e) {
    error.value = e instanceof Error ? e.message : '没有找到该运单'
  } finally {
    busyNo.value = ''
  }
}

async function receiveAtOrigin(task: PostmanTask) {
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
    await http.post(`/postman/orders/${task.orderNo}/receive`, {
      volumeCm3: volume,
      weightKg: weight
    })
    message.value = `${task.orderNo} 已确认收件并完成核价`
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '确认收件失败'
  } finally {
    busyNo.value = ''
  }
}

async function pickup(task: PostmanTask) {
  if (busyNo.value) return
  if (!task.priceConfirmed) {
    error.value = '运费尚未在始发站确认，不能取件'
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

async function deliver(task: PostmanTask, outcome: 'DELIVERED' | 'FAILED', method?: 'SELF' | 'PHONE', reason?: 'UNREACHABLE' | 'WRONG_ADDRESS') {
  if (busyNo.value) return
  busyNo.value = task.orderNo
  error.value = ''
  message.value = ''
  try {
    await http.post(`/postman/orders/${task.orderNo}/deliver`, { outcome, method, reason })
    message.value = outcome === 'DELIVERED'
      ? `${task.orderNo} 已妥投`
      : `${task.orderNo} 已标记派送失败，订单回到目的站待再派`
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '妥投操作失败'
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
        <p>始发站待收件、派给你的最后一公里都在左侧。可手工输入单号定位，最终运费已在始发站锁定。</p>
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
        <form class="filters" @submit.prevent="locate">
          <label class="hint" for="locate-no">扫码/单号</label>
          <input id="locate-no" v-model="locateNo" placeholder="输入运单号定位任务" maxlength="32" />
          <button class="small" type="submit" :disabled="busyNo !== ''">定位</button>
        </form>

        <div class="tabs" role="tablist">
          <button type="button" :class="{ 'is-on': tab === 'pending' }" @click="tab = 'pending'">待处理</button>
          <button type="button" :class="{ 'is-on': tab === 'completed' }" @click="tab = 'completed'">已完成</button>
        </div>

        <p v-if="loading && tasks.length === 0" class="hint" style="margin-top: 20px">正在读取任务…</p>

        <div v-else-if="tasks.length === 0" class="pane-empty">
          <EmptyState
            :title="tab === 'pending' ? '暂时没有任务' : '还没有完成的单'"
            :hint="tab === 'pending' ? '始发站批准待收件、目的站派最后一公里后，订单会出现在这里。' : '签收或拒收后会归档到这里。'"
          />
        </div>

        <div v-else class="list">
          <article
            v-for="task in tasks"
            :key="`${task.orderNo}-${task.taskType}`"
            class="task clickable"
            :class="{ 'is-selected': selected?.orderNo === task.orderNo }"
            role="button"
            tabindex="0"
            @click="open(task)"
            @keydown.enter.prevent="open(task)"
            @keydown.space.prevent="open(task)"
          >
            <b>{{ task.fromStationName }} → {{ task.toStationName }}</b>
            <small>
              {{ task.orderNo }} ·
              {{ isReceipt(task) ? '始发站待收件' : stateLabel(task.orderState) }}
            </small>
            <small>收件人 {{ task.receiverName }} {{ task.receiverPhone }}</small>
            <small v-if="task.priceConfirmed">最终运费 {{ formatYuan(task.price) }}</small>
            <small v-else-if="isReceipt(task)">确认收件时录入体积和质量并核价</small>
            <small v-else-if="tab === 'pending'">运费尚未确认</small>
            <small v-if="task.remark">{{ task.remark }}</small>
          </article>
        </div>
      </div>

      <aside class="pane-detail">
        <div v-if="!selected" class="pane-empty">
          <EmptyState title="从左侧选择一单" hint="始发站收件可核价；最后一公里可取件、妥投或登记派送失败。" />
        </div>

        <template v-else>
          <div>
            <h2>{{ selected.fromStationName }} → {{ selected.toStationName }}</h2>
            <p class="hint">
              {{ selected.orderNo }} · {{ isReceipt(selected) ? '始发站待收件' : stateLabel(selected.orderState) }}
            </p>
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
            <div v-if="selected.weight != null || selected.volumeCm3 != null">
              <span>申报货物</span>
              <b>{{ selected.weight ?? '—' }} kg · {{ selected.volumeCm3 ?? '—' }} cm³</b>
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
              <b>{{ selected.priceConfirmed ? formatYuan(selected.price) : '始发站收件核价后锁定' }}</b>
            </div>
            <div v-if="selected.remark">
              <span>备注</span>
              <b>{{ selected.remark }}</b>
            </div>
          </div>

          <form
            v-if="isReceipt(selected)"
            class="login-form detail-block"
            @submit.prevent="receiveAtOrigin(selected)"
          >
            <h2>确认收件并核价</h2>
            <p class="hint">录入实测体积和质量，系统按已固化路线计算最终运费。确认后订单转待发运。</p>
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
            <div class="compose-actions">
              <button class="small" type="submit" :disabled="busyNo !== ''">
                {{ busyNo === selected.orderNo ? '提交中…' : '确认收件并核价' }}
              </button>
            </div>
          </form>

          <div v-else-if="tab === 'pending' && selected.orderState === 'SENDING'" class="detail-block">
            <h2>取件</h2>
            <p class="hint">运费已在始发站确认，取件后进入派送中。</p>
            <div class="compose-actions">
              <button class="small" type="button" :disabled="busyNo !== '' || !selected.priceConfirmed" @click="pickup(selected)">
                取件开始派送
              </button>
            </div>
            <p v-if="!selected.priceConfirmed" class="hint">运费尚未确认，不能取件。</p>
          </div>

          <div v-else-if="tab === 'pending' && selected.orderState === 'DELIVERING'" class="detail-block">
            <h2>妥投 / 派送失败</h2>
            <p class="hint">妥投与收件人自助签收二选一，谁先完成谁生效；派送失败会回到目的站待再派。</p>
            <div class="compose-actions">
              <button class="small" type="button" :disabled="busyNo !== ''" @click="deliver(selected, 'DELIVERED', 'SELF')">
                本人签收
              </button>
              <button class="small" type="button" :disabled="busyNo !== ''" @click="deliver(selected, 'DELIVERED', 'PHONE')">
                电话确认妥投
              </button>
            </div>
            <div class="compose-actions" style="margin-top: 10px">
              <button class="ghost small" type="button" :disabled="busyNo !== ''" @click="deliver(selected, 'FAILED', undefined, 'UNREACHABLE')">
                联系不上，退回站点
              </button>
              <button class="ghost small" type="button" :disabled="busyNo !== ''" @click="deliver(selected, 'FAILED', undefined, 'WRONG_ADDRESS')">
                地址有误，退回站点
              </button>
            </div>
          </div>

          <p v-else-if="selected.orderState === 'RECEIVED'" class="hint detail-block">已签收。</p>
          <p v-else-if="selected.orderState === 'REFUSED'" class="hint detail-block">已拒收，退回单已生成。</p>
        </template>
      </aside>
    </div>
  </section>
</template>
