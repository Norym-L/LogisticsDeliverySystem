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
    if (selected.value) {
      const current = people.value.find((person) => person.id === selected.value?.id)
      if (current) selected.value = current
      else selected.value = people.value[0] ?? null
    } else if (people.value[0]) {
      selected.value = people.value[0]
    }
    if (selected.value) await loadTasks()
    else tasks.value = []
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
        <p class="page-index">03 / 03 · 快递员</p>
        <h1>快递员</h1>
        <p>每站两名，只负责最后一公里。点开左侧档案查看手里的单。</p>
      </div>
      <div class="head-actions">
        <button class="ghost small" type="button" :disabled="loading" @click="loadPeople">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>
    </header>

    <div class="workspace">
      <div class="pane-list">
        <div class="pane-toolbar">
          <div class="filters">
            <label class="hint" for="station">所属驿站</label>
            <select id="station" v-model="stationId">
              <option value="">全部驿站</option>
              <option v-for="station in stations" :key="station.id" :value="station.id">
                {{ station.name }}
              </option>
            </select>
          </div>
          <span class="pane-count">共 {{ people.length }} 人</span>
        </div>

        <div v-if="loading && people.length === 0" class="pane-empty">
          <p class="hint" style="margin: 0">正在读取快递员档案…</p>
        </div>

        <div v-else-if="people.length === 0" class="pane-empty">
          <EmptyState title="该驿站还没有快递员" hint="换一个驿站看看，或在数据库里补充人员。" />
        </div>

        <div v-else class="pm-list">
          <article
            v-for="person in people"
            :key="person.id"
            class="pm-card"
            :class="{ 'is-selected': selected?.id === person.id }"
            role="button"
            tabindex="0"
            @click="openPerson(person)"
            @keydown.enter.prevent="openPerson(person)"
            @keydown.space.prevent="openPerson(person)"
          >
            <span class="pm-avatar" aria-hidden="true">{{ person.name.slice(0, 1) }}</span>
            <div class="pm-main">
              <div class="pm-line1">
                <strong class="pm-name">{{ person.name }}</strong>
                <span class="badge" :class="person.status === 1 ? 'tone-ok' : 'tone-bad'">
                  {{ person.status === 1 ? '在岗' : '离岗' }}
                </span>
              </div>
              <div class="pm-line2">
                {{ person.username }} · {{ person.stationName ?? '未分配站点' }} · {{ person.phone }}
              </div>
            </div>
            <div class="pm-counts">
              <div title="手里的单"><b>{{ person.activeTasks }}</b><span>手里</span></div>
              <div title="已完成"><b>{{ person.doneTasks }}</b><span>完成</span></div>
            </div>
          </article>
        </div>
      </div>

      <aside class="pane-detail">
        <div v-if="!selected" class="pane-empty">
          <EmptyState title="从左侧选择一位快递员" hint="货到达目的站并派给他之后，任务会出现在这里。" />
        </div>

        <template v-else>
          <div>
            <h2>{{ selected.name }}</h2>
            <p class="hint">
              {{ selected.username }} · {{ selected.stationName ?? '未分配站点' }} · {{ selected.phone }}
            </p>
          </div>

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
        </template>
      </aside>
    </div>

    <div v-if="toast" class="toast" role="status">{{ toast }}</div>
  </section>
</template>
