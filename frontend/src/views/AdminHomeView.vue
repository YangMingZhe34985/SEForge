<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { adminApi } from '@/api/admin'
import { courseApi } from '@/api/courses'
import type { AuditLogEntry } from '@/types/domain'

const router = useRouter()
const loading = ref(false)
const stats = ref({ users: 0, courses: 0, semesters: 0 })
const recentAudit = ref<AuditLogEntry[]>([])

async function load() {
  loading.value = true
  try {
    const [users, courses, semesters, audit] = await Promise.all([
      adminApi.users(),
      courseApi.list(0, 100),
      courseApi.semesters(),
      adminApi.auditLogs(0, 8),
    ])
    stats.value = { users: users.total, courses: courses.total, semesters: semesters.length }
    recentAudit.value = audit.items
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '管理概览加载失败')
  } finally {
    loading.value = false
  }
}

function outcomeTag(outcome: string): 'success' | 'danger' | 'warning' | 'info' {
  if (outcome === 'SUCCEEDED') return 'success'
  if (outcome === 'FAILED') return 'danger'
  if (outcome === 'REJECTED') return 'warning'
  return 'info'
}

function formatTime(value: string): string {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function actorOf(entry: AuditLogEntry): string {
  return entry.actorUsername || (entry.actorId ? `#${entry.actorId}` : '系统')
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="管理概览" description="平台账号、学期、课程与审计事件的统一入口。">
      <div class="button-row">
        <el-button @click="router.push({ name: 'admin-users' })">用户与学期</el-button>
        <el-button type="primary" @click="router.push({ name: 'admin-audit' })">审计日志</el-button>
      </div>
    </PageHeader>

    <div class="stat-grid">
      <article><span>平台用户</span><strong>{{ stats.users }}</strong></article>
      <article><span>课程</span><strong>{{ stats.courses }}</strong></article>
      <article><span>学期</span><strong>{{ stats.semesters }}</strong></article>
    </div>

    <section class="panel" v-loading="loading">
      <div class="section-heading">
        <div><h2>最近审计事件</h2><p class="muted">最新 8 条平台审计记录，完整列表见“审计日志”。</p></div>
        <el-button link type="primary" @click="router.push({ name: 'admin-audit' })">查看全部</el-button>
      </div>
      <el-table v-if="recentAudit.length" :data="recentAudit" stripe>
        <el-table-column label="时间" min-width="170"><template #default="scope">{{ formatTime(scope.row.occurredAt) }}</template></el-table-column>
        <el-table-column label="操作者" min-width="130"><template #default="scope">{{ actorOf(scope.row) }}</template></el-table-column>
        <el-table-column prop="action" label="动作" min-width="180" />
        <el-table-column label="结果" width="110"><template #default="scope"><el-tag :type="outcomeTag(scope.row.outcome)" effect="plain">{{ scope.row.outcome }}</el-tag></template></el-table-column>
      </el-table>
      <EmptyState v-else title="暂无审计事件" description="管理操作与关键业务动作会记录在这里。" />
    </section>
  </div>
</template>

<style scoped>
.stat-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 18px; }
.stat-grid article { display: grid; gap: 7px; border: 1px solid var(--line); border-radius: 12px; padding: 18px; background: #fff; }
.stat-grid span { color: var(--muted); font-size: 11px; }
.stat-grid strong { font-size: 25px; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.section-heading h2, .section-heading p { margin: 0; }
.section-heading p { margin-top: 5px; }
@media (max-width: 600px) { .stat-grid { grid-template-columns: 1fr; } }
</style>
