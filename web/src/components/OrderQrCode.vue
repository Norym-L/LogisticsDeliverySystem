<script setup lang="ts">
import { ref, watch } from 'vue'
import QRCode from 'qrcode'

const props = defineProps<{ content: string; label?: string }>()
const dataUrl = ref('')
const failed = ref(false)

watch(
  () => props.content,
  async (content) => {
    failed.value = false
    if (!content) {
      dataUrl.value = ''
      return
    }
    try {
      dataUrl.value = await QRCode.toDataURL(content, {
        width: 220,
        margin: 1,
        errorCorrectionLevel: 'M',
        color: { dark: '#1f1d19', light: '#ffffff' }
      })
    } catch {
      dataUrl.value = ''
      failed.value = true
    }
  },
  { immediate: true }
)
</script>

<template>
  <div class="qr-panel">
    <img v-if="dataUrl" class="qr-image" :src="dataUrl" :alt="label ?? '运单二维码'" />
    <p v-else-if="failed" class="hint">二维码生成失败，可凭单号查询。</p>
    <p v-else class="hint">正在生成二维码…</p>
    <small class="hint">{{ content }}</small>
  </div>
</template>
