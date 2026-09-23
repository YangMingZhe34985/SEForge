<script setup lang="ts">
import EmptyState from '@/components/EmptyState.vue'
</script>

<template>
  <main class="not-found"><EmptyState title="页面不存在" description="该链接可能已失效，或当前版本尚未开放此功能。"><router-link to="/courses"><el-button type="primary">返回课程</el-button></router-link></EmptyState></main>
</template>

<style scoped>.not-found { min-height: 100vh; display: grid; place-items: center; background: #f4f6fa; }</style>
