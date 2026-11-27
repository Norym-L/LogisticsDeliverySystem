<script setup lang="ts">
import { formatTime, legLabel, type RouteNode } from '@/api/types'

defineProps<{ nodes: RouteNode[] }>()
</script>

<template>
  <div class="rail">
    <div
      v-for="(node, index) in nodes"
      :key="node.seq"
      class="stop"
      :class="{ 'is-last': index === nodes.length - 1 }"
    >
      <span>{{ node.stationName }}</span>
      <small>
        {{ legLabel(node.status) }}
        <template v-if="node.tripNo"> · 车次 {{ node.tripNo }}</template>
        <template v-if="node.arriveTime"> · 到站 {{ formatTime(node.arriveTime) }}</template>
        <template v-if="node.departTime"> · 发出 {{ formatTime(node.departTime) }}</template>
      </small>
    </div>
  </div>
</template>
