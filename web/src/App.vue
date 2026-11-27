<script setup lang="ts">
import { watch } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '@/components/AppShell.vue'
import { syncPortalChrome } from '@/lib/portal-chrome'

const route = useRoute()

watch(
  () => [route.meta.shell, route.meta.portal],
  () => syncPortalChrome(route.meta.shell, route.meta.portal),
  { immediate: true }
)
</script>

<template>
  <RouterView v-if="route.meta.shell === 'none'" />
  <AppShell v-else />
</template>
