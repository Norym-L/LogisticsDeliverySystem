<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { http } from '@/api/client'
import { formatTime, type PostmanInfo, type PostmanTask, type StationView } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'

const stations = ref<StationView[]>([])
const stationId = ref<number | ''>('')
const people = ref<PostmanInfo[]>([])
const loading = ref(false)

const selected = ref<PostmanInfo | null>(null)
const tab = ref<'pending' | 'completed'>('pending')
const tasks = ref<PostmanTask[]>([])
const tasksLoading = ref(false)

const toast = ref('')
let toastTimer: number | undefined

function say(text: string) {
  toast.value = text
  window.clearTimeout(toastTimer)
  toastTimer = window.setTimeout(() => (toast.value = ''), 2800)
}

async function loadStations() {
  try {
    stations.value = await http.get<StationView[]>('/station/list')
  } catch {
    stations.value = []
  }
}

async function loadPeople() {
  loading.value = true
  try {
    const query = stationId.value === '' ? '' : `?stationId=${stationId.value}`
    people.value = await http.get<PostmanInfo[]>(`/admin/postmen${query}`)
  } catch (e) {
    say(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadTasks() {
  if (!selected.value) return
  tasksLoading.value = true
  try {
    tasks.value = await http.get<PostmanTask[]>(
      `/admin/postmen/${selected.value.id}/tasks?type=${tab.value}`
    )
  } catch (e) {
    tasks.value = []
    say(e instanceof Error ? e.message : '任务加载失败')
  } finally {
    tasksLoading.value = false
  }
}

async function openPerson(person: PostmanInfo) {
  selected.value = person
  tab.value = 'pending'
  await loadTasks()
}

function close() {
  selected.value = null
  tasks.value = []
}

watch(tab, loadTasks)
watch(stationId, loadPeople)

onMounted(async () => {
  await loadStations()
  await loadPeople()
})
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <h1>快递员</h1>
        <p>每站两名，只负责最后一公里。这里能看到在岗状态与手里的单量。</p>
      </div>
      <div class="head-actions">
        <button class="ghost small" type="button" :disabled="loading" @click="loadPeople">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>
    </header>

    <div class="filters">
      <label class="hint" for="station">所属驿站</label>
      <select id="station" v-model="stationId">
        <option value="">全部驿站</option>
        <option v-for="station in stations" :key="station.id" :value="station.id">
          {{ station.name }}
        </option>
      </select>
      <span class="hint">共 {{ people.length }} 人</span>
    </div>

    <div v-if="loading && people.length === 0" class="card rise" style="padding: 32px">
      <p class="hint" style="margin: 0">正在读取快递员档案…</p>
    </div>

    <div v-else-if="people.length === 0" class="card rise">
      <EmptyState title="该驿站还没有快递员" hint="换一个驿站看看，或在数据库里补充人员。" />
    </div>

    <div v-else class="people">
      <article
        v-for="(person, index) in people"
        :key="person.id"
        class="card person clickable rise"
        :style="{ animationDelay: `${Math.min(index, 6) * 60}ms` }"
        role="button"
        tabindex="0"
        @click="openPerson(person)"
        @keydown.enter.prevent="openPerson(person)"
      >
        <div class="person-top">
          <div class="person-name">
            <span class="avatar" aria-hidden="true">{{ person.name.slice(0, 1) }}</span>
            <div style="min-width: 0">
              <strong>{{ person.name }}</strong>
              <small class="hint">{{ person.username }} · {{ person.stationName ?? '未分配站点' }}</small>
            </div>
          </div>
          <span class="badge" :class="person.status === 1 ? 'tone-ok' : 'tone-bad'">
            {{ person.status === 1 ? '在岗' : '离岗' }}
          </span>
        </div>

        <div class="person-stats">
          <div>
            <b>{{ person.activeTasks }}</b>
            <span>手里的单</span>
          </div>
          <div>
            <b>{{ person.doneTasks }}</b>
            <span>已完成</span>
          </div>
          <div>
            <b style="font-size: 13px; line-height: 1.8">{{ person.phone }}</b>
            <span>联系电话</span>
          </div>
        </div>
      </article>
    </div>

    <Teleport to="body">
      <template v-if="selected">
        <div class="overlay" @click="close" />
        <aside class="drawer" role="dialog" aria-modal="true" aria-labelledby="person-title">
          <header class="drawer-head">
            <div>
              <h2 id="person-title">{{ selected.name }}</h2>
              <p class="hint" style="margin: 0">
                {{ selected.username }} · {{ selected.stationName ?? '未分配站点' }} · {{ selected.phone }}
              </p>
            </div>
            <button class="ghost small" type="button" aria-label="关闭" @click="close">关闭</button>
          </header>

          <div class="drawer-body">
            <div class="tabs" role="tablist">
              <button
                type="button"
                role="tab"
                :aria-selected="tab === 'pending'"
                :class="{ 'is-on': tab === 'pending' }"
                @click="tab = 'pending'"
              >
                手里的单（{{ selected.activeTasks }}）
              </button>
              <button
                type="button"
                role="tab"
                :aria-selected="tab === 'completed'"
                :class="{ 'is-on': tab === 'completed' }"
                @click="tab = 'completed'"
              >
                已完成（{{ selected.doneTasks }}）
              </button>
            </div>

            <p v-if="tasksLoading" class="hint">正在读取任务…</p>

            <EmptyState
              v-else-if="tasks.length === 0"
              :title="tab === 'pending' ? '手里暂时没有单' : '还没有完成的单'"
              :hint="tab === 'pending' ? '货到达目的站并派给他之后，会出现在这里。' : '签收或拒收之后，记录会归档到这里。'"
            />

            <div v-else class="list" style="gap: 10px">
              <div v-for="task in tasks" :key="`${task.orderNo}-${task.seq}`" class="task">
                <b>{{ task.fromStationName }} → {{ task.toStationName }}</b>
                <small>{{ task.orderNo }}</small>
                <small>
                  {{ task.lastMile ? '最后一公里' : `第 ${task.seq} 段` }}
                  · 车次 {{ task.tripNo ?? '—' }}
                  · {{ task.arriveTime ? `到站 ${formatTime(task.arriveTime)}` : '未到站' }}
                </small>
                <small>收件人 {{ task.receiverName }} {{ task.receiverPhone }}</small>
              </div>
            </div>
          </div>
        </aside>
      </template>
    </Teleport>

    <div v-if="toast" class="toast" role="status">{{ toast }}</div>
  </section>
</template>
