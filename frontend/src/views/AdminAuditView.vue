<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { adminApi } from '@/api/admin'
import type { AuditLogEntry } from '@/types/domain'

const items = ref<AuditLogEntry[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 20
const total = ref(0)
const filters = reactive({ action: '', outcome: '', actorId: '', from: '', to: '' })

async function load() {
  loading.value = true
  try {
    const result = await adminApi.auditLogs(page.value - 1, pageSize, {
      action: filters.action || undefined,
      outcome: filters.outcome || undefined,
      actorId: filters.actorId || undefined,
      from: filters.from ? new Date(filters.from).toISOString() : undefined,
      to: filters.to ? new Date(filters.to).toISOString() : undefined,
    })
    items.value = result.items
    total.value = result.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审计日志加载失败')
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

function targetOf(entry: AuditLogEntry): string {
  if (!entry.targetType) return '—'
  return entry.targetId ? `${entry.targetType} #${entry.targetId}` : entry.targetType
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="审计日志" description="平台审计事件按时间倒序排列，可用于追溯管理操作与关键业务动作。">
      <el-button @click="load">刷新</el-button>
    </PageHeader>
    <section class="panel" v-loading="loading">
      <div class="audit-filters"><el-input v-model="filters.action" placeholder="Action" clearable /><el-select v-model="filters.outcome" placeholder="结果" clearable><el-option label="成功" value="SUCCEEDED" /><el-option label="失败" value="FAILED" /><el-option label="拒绝" value="REJECTED" /></el-select><el-input v-model="filters.actorId" placeholder="用户 ID" clearable /><el-date-picker v-model="filters.from" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="开始时间" /><el-date-picker v-model="filters.to" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="结束时间" /><el-button type="primary" @click="page = 1; load()">筛选</el-button></div>
      <el-table v-if="items.length" :data="items" stripe>
        <el-table-column label="时间" min-width="170"><template #default="scope">{{ formatTime(scope.row.occurredAt) }}</template></el-table-column>
        <el-table-column label="操作者" min-width="130"><template #default="scope">{{ actorOf(scope.row) }}</template></el-table-column>
        <el-table-column prop="action" label="动作" min-width="190" />
        <el-table-column label="目标" min-width="160"><template #default="scope">{{ targetOf(scope.row) }}</template></el-table-column>
        <el-table-column label="课程" width="90"><template #default="scope">{{ scope.row.courseId ? `#${scope.row.courseId}` : '—' }}</template></el-table-column>
        <el-table-column label="结果" width="115"><template #default="scope"><el-tag :type="outcomeTag(scope.row.outcome)" effect="plain">{{ scope.row.outcome }}</el-tag></template></el-table-column>
        <el-table-column label="Trace ID" min-width="230" show-overflow-tooltip><template #default="scope"><code class="trace">{{ scope.row.traceId }}</code></template></el-table-column>
      </el-table>
      <EmptyState v-else title="暂无审计事件" />
      <el-pagination
        v-if="total > pageSize"
        v-model:current-page="page"
        class="audit-pagination"
        background
        layout="prev, pager, next"
        :page-size="pageSize"
        :total="total"
        @current-change="load"
      />
    </section>
  </div>
</template>

<style scoped>
.trace { color: var(--muted); font-size: 12px; }
.audit-pagination { justify-content: flex-end; margin-top: 16px; }
.audit-filters { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
.audit-filters > * { min-width: 120px; max-width: 200px; }
</style>
