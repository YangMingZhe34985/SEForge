<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: string }>()
const normalized = computed(() => props.status.toLowerCase().replaceAll('_', '-'))
</script>

<template>
  <span class="status-badge" :class="`status-badge--${normalized}`">{{ status.replaceAll('_', ' ') }}</span>
</template>
