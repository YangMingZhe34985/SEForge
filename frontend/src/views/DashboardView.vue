<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { analyticsApi } from '@/api/analytics'
import { courseApi } from '@/api/courses'
import { useCourseStore } from '@/stores/courses'
import type { CourseClass, CourseDashboard, NamedValue } from '@/types/domain'

const courseStore = useCourseStore()
const dashboard = ref<CourseDashboard | null>(null)
const loading = ref(false)
const snapshotting = ref(false)
const courseClasses = ref<CourseClass[]>([])
const selectedClassId = ref('')
const maxTrend = computed(() => Math.max(1, ...(dashboard.value?.errorTrends.map((item) => item.value) || [1])))

const tutorLabels: Record<string, string> = {
  HINT: '提示',
  EXPLAIN: '知识点解释',
  CHECK_REASONING: '思路检查',
  ANALYZE_ERROR: '错误分析',
  EVALUATE_DRAFT: '草稿评价',
  FULL_SOLUTION: '完整解析',
}

async function load() {
  if (!courseStore.selectedCourseId) {
    dashboard.value = null
    return
  }
  loading.value = true
  try {
    const classId = selectedClassId.value || undefined
    const [current, snapshotPage] = await Promise.all([
      analyticsApi.dashboard(courseStore.selectedCourseId, classId),
      analyticsApi.snapshots(courseStore.selectedCourseId, classId, 0, 30),
    ])
    const trends = analyticsApi.snapshotTrends(snapshotPage.items)
    dashboard.value = { ...current, errorTrends: trends.length ? trends : current.errorTrends }
  }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '教学分析加载失败') }
  finally { loading.value = false }
}

async function loadClasses() {
  courseClasses.value = []
  selectedClassId.value = ''
  if (!courseStore.selectedCourseId) return
  try { courseClasses.value = await courseApi.classes(courseStore.selectedCourseId) }
  catch (error) { ElMessage.error(error instanceof Error ? error.message : '教学班加载失败') }
}

async function changeCourse() {
  await loadClasses()
  await load()
}

async function createSnapshot() {
  if (!courseStore.selectedCourseId || snapshotting.value) return
  snapshotting.value = true
  try {
    let job = await analyticsApi.requestSnapshot(
      courseStore.selectedCourseId,
      selectedClassId.value || undefined,
      crypto.randomUUID(),
    )
    for (let attempt = 0; attempt < 20 && !['COMPLETED', 'FAILED', 'DEAD_LETTER', 'CANCELLED'].includes(job.status); attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 750))
      job = await analyticsApi.job(job.id)
    }
    if (job.status !== 'COMPLETED') throw new Error(job.error || `快照任务未完成：${job.status}`)
    await load()
    ElMessage.success('教学分析快照已生成')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '快照生成失败')
  } finally {
    snapshotting.value = false
  }
}

function percent(value: number): string {
  return `${Math.min(100, Math.max(0, value))}%`
}

function sorted(items: NamedValue[]): NamedValue[] {
  return [...items].sort((a, b) => b.value - a.value)
}

watch(selectedClassId, load)
watch(() => courseStore.selectedCourseId, changeCourse)
onMounted(changeCourse)
</script>

<template>
  <div>
    <PageHeader title="教学 Dashboard" description="从成绩、知识点、问答和 Tutor 事件生成课程级统计快照。">
      <div class="header-actions">
        <el-select v-model="selectedClassId" placeholder="全部教学班" clearable style="width: 190px">
          <el-option v-for="item in courseClasses" :key="item.id" :label="`${item.name} (${item.code})`" :value="item.id" />
        </el-select>
        <el-button @click="load">刷新数据</el-button>
        <el-button type="primary" :loading="snapshotting" @click="createSnapshot">生成快照</el-button>
      </div>
    </PageHeader>
    <div v-if="dashboard" v-loading="loading" class="dashboard-grid">
      <section class="metrics-grid dashboard-span">
        <article v-for="metric in dashboard.metrics" :key="metric.label" class="metric-card"><span>{{ metric.label }}</span><div><strong>{{ metric.value }}</strong><small>{{ metric.unit }}</small></div><em v-if="metric.delta !== undefined" :class="{ down: metric.delta < 0 }">{{ metric.delta > 0 ? '+' : '' }}{{ metric.delta }}%</em></article>
      </section>

      <section class="panel"><div class="panel__header"><h2>成绩分布</h2></div><div class="panel__body bar-list"><div v-for="item in dashboard.gradeDistribution" :key="item.name"><label><span>{{ item.name }}</span><b>{{ item.value }}</b></label><i><em :style="{ width: percent(item.value) }" /></i></div></div></section>
      <section class="panel"><div class="panel__header"><h2>知识点正确率</h2></div><div class="panel__body bar-list"><div v-for="item in sorted(dashboard.knowledgePointAccuracy)" :key="item.name"><label><span>{{ item.name }}</span><b>{{ item.value }}%</b></label><i><em :style="{ width: percent(item.value) }" /></i></div></div></section>

      <section class="panel"><div class="panel__header"><h2>薄弱知识点</h2></div><div class="panel__body ranking"><article v-for="(item,index) in dashboard.weakKnowledgePoints" :key="item.name"><span>{{ index + 1 }}</span><strong>{{ item.name }}</strong><b>{{ item.value }}%</b></article></div></section>
      <section class="panel"><div class="panel__header"><h2>高频问题</h2></div><div class="panel__body ranking"><article v-for="(item,index) in dashboard.frequentQuestions" :key="item.name"><span>{{ index + 1 }}</span><strong>{{ item.name }}</strong><b>{{ item.value }} 次</b></article></div></section>

      <section class="panel"><div class="panel__header"><h2>AI Tutor 使用分布</h2></div><div class="panel__body ranking"><article v-for="(item,index) in sorted(dashboard.tutorUsage)" :key="item.name"><span>{{ index + 1 }}</span><strong>{{ tutorLabels[item.name] || item.name }}</strong><b>{{ item.value }} 次</b></article><EmptyState v-if="!dashboard.tutorUsage.length" title="暂无 Tutor 记录" description="学生使用 AI Tutor 后将在此汇总。" /></div></section>
      <section class="panel"><div class="panel__header"><h2>课程问答反馈</h2></div><div class="panel__body feedback-summary"><strong>{{ dashboard.qaFeedback.helpfulRate }}%</strong><span>有用率</span><small>{{ dashboard.qaFeedback.helpful }} 条有用 · {{ dashboard.qaFeedback.notHelpful }} 条无用</small></div></section>

      <section class="panel dashboard-span"><div class="panel__header"><h2>错误趋势</h2><span class="muted">{{ new Date(dashboard.generatedAt).toLocaleString() }} 更新</span></div><div class="trend-chart"><div v-for="item in dashboard.errorTrends" :key="item.date" class="trend-column"><div><i :style="{ height: `${Math.max(3, item.value / maxTrend * 100)}%` }" /><b>{{ item.value }}</b></div><span>{{ item.date.slice(5, 10) }}</span></div></div></section>
    </div>
    <section v-else class="panel" v-loading="loading"><EmptyState title="暂无教学分析" description="请选择课程；统计快照生成后会显示完成率、成绩、薄弱点和 Tutor 使用情况。" /></section>
  </div>
</template>

<style scoped>
.header-actions { display: flex; flex-wrap: wrap; gap: 8px; }.dashboard-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }.dashboard-span { grid-column: 1 / -1; }.metrics-grid { display: grid; grid-template-columns: repeat(auto-fit,minmax(180px,1fr)); gap: 14px; }.metric-card { position: relative; display: grid; gap: 9px; border: 1px solid var(--line); border-radius: 12px; padding: 18px; background: #fff; }.metric-card > span { color: var(--muted); font-size: 11px; }.metric-card strong { font-size: 29px; }.metric-card small { margin-left: 4px; color: var(--muted); }.metric-card em { position: absolute; top: 18px; right: 18px; color: #07816a; font-size: 11px; font-style: normal; }.metric-card em.down { color: #bd3842; }
.bar-list { display: grid; gap: 15px; }.bar-list label { display: flex; justify-content: space-between; margin-bottom: 6px; font-size: 12px; }.bar-list i { height: 8px; display: block; overflow: hidden; border-radius: 999px; background: #eef1f6; }.bar-list em { height: 100%; display: block; border-radius: inherit; background: linear-gradient(90deg,#3f5adc,#36bca4); }
.ranking { display: grid; gap: 8px; }.ranking article { display: grid; grid-template-columns: 28px 1fr auto; align-items: center; gap: 9px; padding: 9px; border-radius: 8px; background: #f7f8fb; }.ranking article > span { width: 23px; height: 23px; display: grid; place-items: center; border-radius: 6px; background: #e5eafa; color: var(--brand); font-size: 10px; }.ranking strong { font-size: 12px; }.ranking b { color: var(--muted); font-size: 11px; }
.feedback-summary { min-height: 145px; display: grid; place-content: center; justify-items: center; gap: 6px; }.feedback-summary strong { color: var(--brand); font-size: 42px; }.feedback-summary span { color: var(--text); font-weight: 700; }.feedback-summary small { color: var(--muted); }
.trend-chart { height: 240px; display: flex; align-items: flex-end; gap: 8px; padding: 22px 25px 18px; }.trend-column { min-width: 20px; flex: 1; text-align: center; }.trend-column > div { position: relative; height: 180px; display: flex; align-items: flex-end; justify-content: center; }.trend-column i { width: min(32px,70%); min-height: 3px; border-radius: 5px 5px 0 0; background: #4963de; }.trend-column b { position: absolute; top: 0; font-size: 10px; }.trend-column span { color: var(--muted); font-size: 9px; }
@media(max-width:800px){.dashboard-grid{grid-template-columns:1fr}.dashboard-span{grid-column:auto}.trend-chart{overflow-x:auto}.trend-column{min-width:36px}}
</style>
