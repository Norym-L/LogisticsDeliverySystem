<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { http } from '@/api/client'
import type { StationView } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'

const stations = ref<StationView[]>([])
const keyword = ref('')
const selected = ref<StationView | null>(null)
const loading = ref(false)
const error = ref('')

const filtered = computed(() => {
  const key = keyword.value.trim()
  if (!key) return stations.value
  return stations.value.filter((station) =>
    station.name.includes(key)
    || station.code.includes(key)
    || (station.address ?? '').includes(key))
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    stations.value = await http.get<StationView[]>('/station/list')
    selected.value = stations.value[0] ?? null
  } catch (e) {
    error.value = e instanceof Error ? e.message : '网点加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-head">
      <div>
        <h1>网点查询</h1>
        <p>通驿各转运中心的名称与地址。下单时可选择始发站和目的站。</p>
      </div>
      <div class="head-actions">
        <button class="ghost small" type="button" :disabled="loading" @click="load">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>
    </header>

    <p v-if="error" class="error" role="alert">{{ error }}</p>

    <div class="workspace">
      <div class="pane-list">
        <form class="filters" @submit.prevent>
          <label class="hint" for="station-key">搜索</label>
          <input id="station-key" v-model="keyword" placeholder="站名、编号或地址" />
        </form>

        <div v-if="loading && stations.length === 0" class="pane-empty">
          <p class="hint" style="margin: 0">正在读取网点…</p>
        </div>

        <div v-else-if="filtered.length === 0" class="pane-empty">
          <EmptyState title="没有匹配的网点" hint="换一个关键词试试，或清空搜索查看全部。" />
        </div>

        <div v-else class="list">
          <article
            v-for="station in filtered"
            :key="station.id"
            class="card clickable order-row"
            :class="{ 'is-selected': selected?.id === station.id }"
            role="button"
            tabindex="0"
            @click="selected = station"
            @keydown.enter.prevent="selected = station"
            @keydown.space.prevent="selected = station"
          >
            <div style="min-width: 0">
              <div class="route-line">
                <span>{{ station.name }}</span>
              </div>
              <div class="meta">
                <b>{{ station.code }}</b>
                <span>{{ station.address ?? '地址未维护' }}</span>
              </div>
            </div>
          </article>
        </div>
      </div>

      <aside class="pane-detail">
        <div v-if="!selected" class="pane-empty">
          <EmptyState title="从左侧选择网点" hint="这里只展示站名和地址，不提供地图导航。" />
        </div>

        <template v-else>
          <div>
            <h2>{{ selected.name }}</h2>
            <p class="hint">{{ selected.code }}</p>
          </div>

          <div class="kv">
            <div>
              <span>网点名称</span>
              <b>{{ selected.name }}</b>
            </div>
            <div>
              <span>网点编号</span>
              <b>{{ selected.code }}</b>
            </div>
            <div>
              <span>地址</span>
              <b>{{ selected.address ?? '地址未维护' }}</b>
            </div>
          </div>

          <p class="hint detail-block">下单时在寄件页选择该站作为始发站或目的站即可，无需填写门到门地址。</p>
        </template>
      </aside>
    </div>
  </section>
</template>
