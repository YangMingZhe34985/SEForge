<script setup lang="ts">
import { computed } from 'vue'
import { Odometer, Tickets, UserFilled } from '@element-plus/icons-vue'
import WorkspaceShell from './WorkspaceShell.vue'
import type { WorkspaceNavItem } from './navigation'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const navigation = computed<WorkspaceNavItem[]>(() => [
  { label: '管理概览', icon: Odometer, to: { name: 'admin-home' } },
  { label: '用户与学期', icon: UserFilled, to: { name: 'admin-users' } },
  { label: '审计日志', icon: Tickets, to: { name: 'admin-audit' } },
])
</script>

<template>
  <WorkspaceShell
    :navigation="navigation"
    workspace-label="管理控制台"
    :switch-links="auth.canTeach ? [{ label: '教学工作台', to: { name: 'teacher-home' } }] : []"
  />
</template>
