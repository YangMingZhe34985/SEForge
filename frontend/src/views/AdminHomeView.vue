<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import { UserFilled, School, Reading, FolderOpened, ArrowRight, Lightning } from '@element-plus/icons-vue'
import EmptyState from '@/components/EmptyState.vue'
import { adminApi } from '@/api/admin'
import type { Semester } from '@/types/domain'
import type { AuditLogEntry } from '@/types/domain'

const router = useRouter()
const loading = ref(false)
const errorMessage = ref('')
const loaded = ref(false)
const stats = ref({ teachers: 0, students: 0, activeCourses: 0, archivedCourses: 0, currentSemester: null as Semester | null })
const recentAudit = ref<AuditLogEntry[]>([])

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [overview, audit] = await Promise.all([
      adminApi.overview(),
      adminApi.auditLogs(0, 8),
    ])
    stats.value = overview
    recentAudit.value = audit.items
    loaded.value = true
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '管理概览加载失败'
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
    <AdminPageHeader title="管理概览" description="平台账号、学期、课程与审计事件的统一入口。" />
    <el-alert v-if="errorMessage" class="admin-error" type="error" :closable="false" :title="errorMessage" show-icon><el-button link type="primary" @click="load">重新加载</el-button></el-alert>

    <div class="stat-grid" :aria-busy="loading">
      <article><span class="stat-icon"><el-icon><UserFilled /></el-icon></span><div><span>教师</span><strong>{{ loaded ? stats.teachers : '—' }}</strong><small>已注册教师账号</small></div></article>
      <article><span class="stat-icon stat-icon--green"><el-icon><School /></el-icon></span><div><span>学生</span><strong>{{ loaded ? stats.students : '—' }}</strong><small>已注册学生账号</small></div></article>
      <article><span class="stat-icon stat-icon--purple"><el-icon><Reading /></el-icon></span><div><span>活跃课程</span><strong>{{ loaded ? stats.activeCourses : '—' }}</strong><small>平台进行中的课程</small></div></article>
      <article><span class="stat-icon stat-icon--orange"><el-icon><FolderOpened /></el-icon></span><div><span>归档课程</span><strong>{{ loaded ? stats.archivedCourses : '—' }}</strong><small>已归档的课程</small></div></article>
    </div>
    <p class="semester-note">当前学期 <strong>{{ loaded ? (stats.currentSemester?.name || '未设置') : '—' }}</strong></p>

    <section class="panel" v-loading="loading">
      <div class="section-heading">
        <div><h2>最近审计事件</h2><p class="muted">最新 8 条平台审计记录，完整列表见“审计日志”。</p></div>
        <el-button link type="primary" @click="router.push({ name: 'admin-audit' })">查看全部</el-button>
      </div>
      <el-table v-if="recentAudit.length" :data="recentAudit" stripe>
        <el-table-column label="时间" min-width="170"><template #default="scope">{{ formatTime(scope.row.occurredAt) }}</template></el-table-column>
        <el-table-column label="操作者" min-width="130"><template #default="scope">{{ actorOf(scope.row) }}</template></el-table-column>
        <el-table-column prop="action" label="动作" min-width="180" />
        <el-table-column label="结果" width="145"><template #default="scope"><el-tag :type="outcomeTag(scope.row.outcome)" effect="plain">{{ scope.row.outcome }}</el-tag></template></el-table-column>
      </el-table>
      <EmptyState v-else-if="!loading && !errorMessage" title="暂无审计事件" description="管理操作与关键业务动作会记录在这里。" />
    </section>
    <section class="panel quick-actions">
      <div><h2><el-icon><Lightning /></el-icon> 快速操作</h2><p class="muted">常用管理功能，快速进入对应页面。</p></div>
      <router-link :to="{ name: 'admin-users' }"><span class="stat-icon"><el-icon><UserFilled /></el-icon></span><span><strong>用户与学期管理</strong><small>管理平台用户、学期信息及权限</small></span><el-icon><ArrowRight /></el-icon></router-link>
      <router-link :to="{ name: 'admin-audit' }"><span class="stat-icon stat-icon--purple"><el-icon><Reading /></el-icon></span><span><strong>查看审计日志</strong><small>追溯系统操作与关键业务动作</small></span><el-icon><ArrowRight /></el-icon></router-link>
    </section>
  </div>
</template>

<style scoped>
.stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 18px; }
.stat-grid article { display: flex; align-items: flex-start; gap: 16px; border: 1px solid var(--line); border-radius: var(--admin-radius); padding: 22px 18px; background: var(--surface); box-shadow: var(--admin-shadow); }
.stat-grid article > div { min-width: 0; }
.stat-grid strong { display: block; font-size: 32px; margin: 8px 0; }
.stat-icon { flex: 0 0 52px; width: 52px; height: 52px; display: grid; place-items: center; border-radius: 15px; color: var(--brand); background: #edf4ff; font-size: 28px; }
.stat-icon--green { color: #13ad90; background: #e9f8f3; }
.stat-icon--purple { color: #8954e5; background: #f2edff; }
.stat-icon--orange { color: #ec9836; background: #fff4e9; }
small { display: block; color: var(--muted); font-size: 12px; line-height: 1.7; }
.semester-note { color: var(--muted); margin: 18px 0 22px; font-size: 13px; }
.semester-note strong { color: var(--ink); margin-left: 12px; font-weight: 500; }
.quick-actions { display: grid; grid-template-columns: .8fr 1fr 1fr; gap: 20px; align-items: center; }
.quick-actions h2 { margin: 0; font-size: 21px; }
.quick-actions h2 .el-icon { color: var(--brand); vertical-align: middle; }
.quick-actions p { font-size: 13px; line-height: 1.7; }
.quick-actions a { display: flex; align-items: center; gap: 14px; padding: 18px; border: 1px solid var(--line); border-radius: 12px; text-decoration: none; }
.quick-actions a:hover { border-color: var(--brand); background: #f7faff; }
.quick-actions a > .el-icon { margin-left: auto; color: var(--brand); }
.quick-actions small { margin-top: 6px; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.section-heading h2, .section-heading p { margin: 0; }
.section-heading p { margin-top: 5px; }
@media (max-width: 1200px) { .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .quick-actions { grid-template-columns: 1fr; } }
@media (max-width: 480px) { .stat-grid { gap: 10px; } .stat-grid article { padding: 14px; flex-direction: column; gap: 10px; } }
</style>
