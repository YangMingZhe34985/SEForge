<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { assignmentApi } from '@/api/assignments'
import type { AssignmentMedia } from '@/types/domain'
const props = defineProps<{ assignmentId: string; ids?: string[] }>()
const rows = ref<(AssignmentMedia & { url: string })[]>([])
const error = ref('')
let version = 0
function clear() { for (const row of rows.value) URL.revokeObjectURL(row.url); rows.value = [] }
watch(() => [props.assignmentId, props.ids] as const, async () => {
  const current = ++version; clear(); error.value = ''
  try {
    for (const id of props.ids || []) {
      const meta = await assignmentApi.media(props.assignmentId, id)
      const blob = await assignmentApi.mediaBlob(props.assignmentId, id)
      if (current !== version) return
      rows.value.push({ ...meta, url: URL.createObjectURL(blob) })
    }
  } catch (e) { if (current === version) error.value = e instanceof Error ? e.message : '附件加载失败' }
}, { immediate: true, deep: true })
onBeforeUnmount(() => { version++; clear() })
</script>
<template>
  <div class="media-list"><p v-if="error" role="alert">{{ error }}</p>
    <figure v-for="row in rows" :key="row.id">
      <img v-if="['image/png','image/jpeg'].includes(row.mediaType)" :src="row.url" :alt="row.fileName" />
      <figcaption><a :href="row.url" :download="row.fileName">{{ row.fileName }}</a></figcaption>
    </figure>
  </div>
</template>
<style scoped>.media-list{display:flex;gap:12px;flex-wrap:wrap}figure{margin:8px 0;max-width:100%}img{max-width:100%;max-height:320px;object-fit:contain;border:1px solid #ddd}figcaption{font-size:13px}</style>
