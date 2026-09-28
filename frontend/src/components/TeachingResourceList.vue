<script setup lang="ts">
import type { CourseResource, KnowledgeDocument } from '@/types/domain'
import StatusBadge from './StatusBadge.vue'
const props = defineProps<{ resources: CourseResource[]; documents: KnowledgeDocument[]; manage: boolean;
  status: (d: KnowledgeDocument) => string; error: (d: KnowledgeDocument) => string | undefined;
  cancelable: (d: KnowledgeDocument) => boolean; cancelling: (d: KnowledgeDocument) => boolean;
  formatBytes: (n: number) => string }>()
defineEmits<{ download: [CourseResource]; remove: [CourseResource]; edit: [CourseResource]; include: [CourseResource]; exclude: [KnowledgeDocument]; reindex: [KnowledgeDocument]; cancel: [KnowledgeDocument] }>()
function documentFor(resource: CourseResource) { return props.documents.find(d => String(d.resourceId) === String(resource.id)) }
function supports(resource: CourseResource) { return !resource.externalUrl && /\.(pdf|pptx?|docx|md|txt)$/i.test(resource.name) }
function safeUrl(url?: string) { if (!url) return undefined; try { const value = new URL(url); return ['https:', 'http:'].includes(value.protocol) ? value.href : undefined } catch { return undefined } }
</script>
<template>
  <el-table v-if="resources.length" :data="resources" class="document-table">
    <el-table-column prop="name" label="名称" min-width="150" />
    <el-table-column prop="resourceType" label="类型" width="100" />
    <el-table-column label="大小" width="100"><template #default="{ row }">{{ formatBytes(row.sizeBytes || 0) }}</template></el-table-column>
    <el-table-column label="知识库 / 摄取状态" min-width="130"><template #default="{ row }"><StatusBadge v-if="documentFor(row)" :status="status(documentFor(row)!)" /><span v-else>普通资源</span></template></el-table-column>
    <el-table-column label="说明" min-width="170"><template #default="{ row }">{{ documentFor(row) ? error(documentFor(row)!) : row.description }}</template></el-table-column>
    <el-table-column label="操作" min-width="320"><template #default="{ row }">
      <a v-if="safeUrl(row.externalUrl)" :href="safeUrl(row.externalUrl)" target="_blank" rel="noopener noreferrer">打开链接</a><el-button v-else link @click="$emit('download', row)">下载</el-button>
      <template v-if="manage"><el-button link @click="$emit('edit', row)">编辑资料</el-button>
        <template v-if="documentFor(row)"><el-button link @click="$emit('reindex', documentFor(row)!)">重建索引 / 重试</el-button><el-button link @click="$emit('exclude', documentFor(row)!)">移出知识库</el-button><el-button v-if="cancelable(documentFor(row)!)" link :disabled="cancelling(documentFor(row)!)" @click="$emit('cancel', documentFor(row)!)">取消任务</el-button></template>
        <el-button v-else-if="supports(row)" link @click="$emit('include', row)">加入知识库</el-button>
        <el-button link type="danger" @click="$emit('remove', row)">删除资料</el-button>
      </template>
    </template></el-table-column>
  </el-table>
  <el-empty v-else description="暂无资料，可上传文件或添加参考链接" />
</template>
