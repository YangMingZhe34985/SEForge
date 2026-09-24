<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import PageHeader from '@/components/PageHeader.vue'

const auth = useAuthStore()
</script>

<template>
  <div>
    <PageHeader title="个人资料" description="账号资料由平台保存，学号是学生登录标识。" />
    <section v-if="auth.user" class="panel profile-grid">
      <div><span>姓名</span><strong>{{ auth.user.displayName }}</strong></div>
      <div><span>学号</span><strong>{{ auth.user.studentNo || '待补录' }}</strong></div>
      <div><span>用户名</span><strong>{{ auth.user.username }}</strong></div>
      <div><span>联系邮箱</span><strong>{{ auth.user.email }}</strong></div>
    </section>
  </div>
</template>

<style scoped>
.profile-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 20px; }
.profile-grid div { display: grid; gap: 8px; }
.profile-grid span { color: var(--muted); font-size: 12px; }
.profile-grid strong { overflow-wrap: anywhere; }
</style>
