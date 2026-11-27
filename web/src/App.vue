<script setup lang="ts">
import { useRoute } from 'vue-router'
import AppShell from '@/components/AppShell.vue'

const route = useRoute()
</script>

<template>
  <RouterView v-if="route.meta.shell === 'none'" />
  <AppShell v-else />
</template>
