<script setup lang="ts">
import { computed } from 'vue'
import {
  ChatDotRound,
  Collection,
  DataAnalysis,
  DocumentChecked,
  Histogram,
  HomeFilled,
  Reading,
} from '@element-plus/icons-vue'
import WorkspaceShell from './WorkspaceShell.vue'
import type { WorkspaceNavItem, WorkspaceSwitchLink } from './navigation'
import { useAuthStore } from '@/stores/auth'
import { useCourseStore } from '@/stores/courses'

const auth = useAuthStore()
const courseStore = useCourseStore()

const navigation = computed<WorkspaceNavItem[]>(() => {
  const courseId = courseStore.selectedCourseId
  const courseRoute = (suffix: '' | '-assistant' | '-assignments' | '-reviews' | '-dashboard') =>
    courseId ? { name: `teacher-course${suffix}`, params: { courseId } } : { name: 'teacher-home' }
  return [
    { label: '教学工作台', icon: HomeFilled, to: { name: 'teacher-home' } },
    { label: '课程内容', icon: Collection, to: courseRoute(''), enabled: Boolean(courseId) },
    { label: '课程助手', icon: ChatDotRound, to: courseRoute('-assistant'), enabled: Boolean(courseId) },
    { label: '作业与 Tutor', icon: Reading, to: courseRoute('-assignments'), enabled: Boolean(courseId) },
    { label: '智能评审', icon: DocumentChecked, to: courseRoute('-reviews'), enabled: Boolean(courseId) },
    { label: '教学 Dashboard', icon: DataAnalysis, to: courseRoute('-dashboard'), enabled: Boolean(courseId) },
    { label: '成绩与反馈', icon: Histogram, to: { name: 'teacher-grades' } },
  ]
})

const switchLinks = computed<WorkspaceSwitchLink[]>(() =>
  auth.isAdmin ? [{ label: '管理控制台', to: { name: 'admin-home' } }] : [])
</script>

<template>
  <WorkspaceShell
    :navigation="navigation"
    workspace-label="教学工作台"
    show-course-switcher
    :switch-links="switchLinks"
  />
</template>
