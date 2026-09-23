<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { reviewApi } from '@/api/reviews'
import { useAuthStore } from '@/stores/auth'
import { useCourseStore } from '@/stores/courses'
import type { GradeRecord } from '@/types/domain'

const auth = useAuthStore()
const courseStore = useCourseStore()
const grades = ref<GradeRecord[]>([])
const loading = ref(false)
const canTeachCourse = computed(() => auth.isAdmin || ['TEACHER', 'TA'].includes(courseStore.selectedCourse?.role || ''))
const average = computed(() => grades.value.length ? Math.round(grades.value.reduce((sum, item) => sum + item.score, 0) / grades.value.length * 10) / 10 : 0)

async function load() {
  loading.value = true
  try { grades.value = (await reviewApi.grades(courseStore.selectedCourseId || undefined)).items }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '成绩加载失败') }
  finally { loading.value = false }
}

watch(() => courseStore.selectedCourseId, load)
onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="成绩与反馈" :description="canTeachCourse ? '查看课程成绩确认状态；AI 建议不会自动成为最终成绩。' : '查看教师已确认的成绩与反馈。'"><el-button @click="load">刷新</el-button></PageHeader>
    <div class="grade-summary"><article><span>记录数</span><strong>{{ grades.length }}</strong></article><article><span>平均得分</span><strong>{{ average }}</strong></article><article><span>最终成绩</span><strong>{{ grades.filter(item => item.status === 'FINAL').length }}</strong></article></div>
    <section class="panel" v-loading="loading">
      <el-table v-if="grades.length" :data="grades" stripe>
        <el-table-column prop="assignmentTitle" label="作业" min-width="200" />
        <el-table-column v-if="canTeachCourse" prop="studentName" label="学生" min-width="140" />
        <el-table-column label="分数" width="130"><template #default="scope"><strong>{{ scope.row.score }}</strong> / {{ scope.row.maxScore }}</template></el-table-column>
        <el-table-column label="状态" width="170"><template #default="scope"><StatusBadge :status="scope.row.status" /></template></el-table-column>
        <el-table-column prop="feedback" label="教师反馈" min-width="260" show-overflow-tooltip />
        <el-table-column label="时间" width="180"><template #default="scope">{{ scope.row.gradedAt ? new Date(scope.row.gradedAt).toLocaleString() : '—' }}</template></el-table-column>
      </el-table>
      <EmptyState v-else title="暂无成绩记录" description="作业完成评审并由教师确认后，最终成绩会显示在这里。" />
    </section>
  </div>
</template>

<style scoped>.grade-summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 18px; }.grade-summary article { display: grid; gap: 7px; border: 1px solid var(--line); border-radius: 12px; padding: 18px; background: #fff; }.grade-summary span { color: var(--muted); font-size: 11px; }.grade-summary strong { font-size: 25px; }@media(max-width:600px){.grade-summary{grid-template-columns:1fr;}}</style>
