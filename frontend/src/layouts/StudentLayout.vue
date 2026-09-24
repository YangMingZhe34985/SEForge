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
  User,
} from '@element-plus/icons-vue'
import WorkspaceShell from './WorkspaceShell.vue'
import type { WorkspaceNavItem } from './navigation'
import { useCourseStore } from '@/stores/courses'

const courseStore = useCourseStore()

const navigation = computed<WorkspaceNavItem[]>(() => {
  const courseId = courseStore.selectedCourseId
  const courseRoute = (suffix: '' | '-assistant' | '-assignments' | '-reviews' | '-dashboard') =>
    courseId ? { name: `student-course${suffix}`, params: { courseId } } : { name: 'student-home' }
  // Teaching assistants keep their review/dashboard entries inside the student workspace;
  // the server remains the authority for what they may actually do.
  const isCourseStaff = courseStore.canManageSelected
  return [
    { label: '我的课程', icon: HomeFilled, to: { name: 'student-home' } },
    { label: '个人资料', icon: User, to: { name: 'student-profile' } },
    { label: '课程内容', icon: Collection, to: courseRoute(''), enabled: Boolean(courseId) },
    { label: '课程助手', icon: ChatDotRound, to: courseRoute('-assistant'), enabled: Boolean(courseId) },
    { label: '作业与 Tutor', icon: Reading, to: courseRoute('-assignments'), enabled: Boolean(courseId) },
    { label: '智能评审', icon: DocumentChecked, to: courseRoute('-reviews'), visible: isCourseStaff, enabled: Boolean(courseId) },
    { label: '教学 Dashboard', icon: DataAnalysis, to: courseRoute('-dashboard'), visible: isCourseStaff, enabled: Boolean(courseId) },
    { label: '成绩与反馈', icon: Histogram, to: { name: 'student-grades' } },
  ]
})
</script>

<template>
  <WorkspaceShell :navigation="navigation" workspace-label="学习工作台" show-course-switcher />
</template>
