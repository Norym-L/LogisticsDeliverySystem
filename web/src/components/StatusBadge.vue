<script setup lang="ts">
import { stateLabel, stateTone } from '@/api/types'

defineProps<{ state: string }>()
</script>

<template>
  <span class="badge" :class="stateTone(state)">{{ stateLabel(state) }}</span>
</template>
