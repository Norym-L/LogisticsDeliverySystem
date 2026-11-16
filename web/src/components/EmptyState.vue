<script setup lang="ts">
defineProps<{ title: string; hint: string }>()
</script>

<template>
  <div class="empty">
    <svg width="140" height="92" viewBox="0 0 140 92" fill="none" aria-hidden="true">
      <path d="M18 74h104" stroke="var(--milk-edge)" stroke-width="1.5" stroke-linecap="round" />
      <rect x="38" y="28" width="56" height="40" rx="9" stroke="var(--milk-edge)" stroke-width="1.5" />
      <path d="M38 43h56" stroke="var(--milk-edge)" stroke-width="1.2" />
      <path d="M52 28v-8h28v8" stroke="var(--milk-edge)" stroke-width="1.5" stroke-linecap="round" />
      <circle cx="104" cy="26" r="5" stroke="var(--milk-edge)" stroke-width="1.2" />
      <path d="M22 32l7 5-7 5" stroke="var(--milk-edge)" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" />
      <path d="M28 62h8M32 58v8" stroke="var(--milk-edge)" stroke-width="1.2" stroke-linecap="round" />
    </svg>
    <strong>{{ title }}</strong>
    <span>{{ hint }}</span>
  </div>
</template>
