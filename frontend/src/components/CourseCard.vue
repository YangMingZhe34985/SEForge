<script setup lang="ts">
import type { CourseSummary } from '@/types/domain'

defineProps<{ course: CourseSummary; active?: boolean }>()
defineEmits<{ click: [] }>()
</script>

<template>
  <button type="button" class="course-card" :class="{ active }" @click="$emit('click')">
    <span class="course-card__code">{{ course.code }}</span>
    <strong>{{ course.name }}</strong>
    <p>{{ course.description || '暂无课程简介' }}</p>
    <span v-if="course.status === 'ARCHIVED'" class="course-card__archived">已归档</span>
    <span class="course-card__meta">{{ course.semesterName || '未设置学期' }} · {{ course.memberCount }} 人 · {{ course.role || '—' }}</span>
  </button>
</template>

<style scoped>
.course-card { min-height: 180px; display: flex; flex-direction: column; align-items: flex-start; border: 1px solid var(--line); border-radius: 13px; padding: 19px; background: #fff; color: var(--ink); text-align: left; transition: transform .15s, border .15s, box-shadow .15s; }
.course-card:hover, .course-card.active { transform: translateY(-2px); border-color: #8799ec; box-shadow: 0 10px 24px rgba(35,62,153,.1); }
.course-card.active { box-shadow: inset 0 3px #3554dc, 0 10px 24px rgba(35,62,153,.08); }
.course-card__code { color: var(--brand); font-size: 11px; font-weight: 800; letter-spacing: .08em; }
.course-card strong { margin-top: 9px; font-size: 18px; }
.course-card p { flex: 1; margin: 8px 0; color: var(--muted); line-height: 1.5; }
.course-card__archived { border: 1px solid rgba(230,162,60,.4); background: #fdf6ec; color: #b88230; border-radius: 999px; padding: 2px 9px; font-size: 11px; margin-bottom: 6px; }
.course-card__meta { color: #8b94a7; font-size: 11px; }
</style>
