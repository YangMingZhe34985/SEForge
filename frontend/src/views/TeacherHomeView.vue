<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import CourseCard from '@/components/CourseCard.vue'
import { useAuthStore } from '@/stores/auth'
import { useCourseStore } from '@/stores/courses'

const router = useRouter()
const auth = useAuthStore()
const store = useCourseStore()
const createVisible = ref(false)
const courseForm = reactive({ name: '', description: '', semesterId: '' })
const canCreateCourse = computed(() => auth.canTeach)

async function createCourse() {
  if (!courseForm.name.trim() || !courseForm.semesterId) {
    return ElMessage.warning('请填写课程名称和学期')
  }
  try {
    const course = await store.create({ ...courseForm })
    createVisible.value = false
    Object.assign(courseForm, { name: '', description: '', semesterId: '' })
    ElMessage.success(`课程已创建，编号：${course.code}`)
    await router.push({ name: 'teacher-course', params: { courseId: course.id } })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建失败')
  }
}

function openCourse(courseId: string) {
  void router.push({ name: 'teacher-course', params: { courseId } })
}

onMounted(async () => {
  if (store.courses.length) return
  try {
    await store.load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '课程加载失败')
  }
})
</script>

<template>
  <div>
    <PageHeader title="教学工作台" description="管理你的课程、教学班、邀请码与课程内容。">
      <el-button v-if="canCreateCourse" type="primary" @click="createVisible = true">创建课程</el-button>
    </PageHeader>

    <el-alert
      v-if="!store.loading && !store.semesters.length"
      class="semester-alert"
      title="尚无可用学期：课程必须归属一个学期，请联系平台管理员先在“用户与学期”中创建学期。"
      type="warning"
      :closable="false"
      show-icon
    />

    <div v-if="store.courses.length" class="course-strip">
      <CourseCard
        v-for="course in store.courses"
        :key="course.id"
        :course="course"
        :active="course.id === store.selectedCourseId"
        @click="openCourse(course.id)"
      />
    </div>
    <div v-else class="panel">
      <EmptyState title="还没有课程" description="创建第一门课程后，即可管理教学班、邀请码与课程内容。" />
    </div>

    <el-dialog v-model="createVisible" title="创建课程" width="min(520px, 94vw)">
      <el-form label-position="top">
        <div class="form-grid">
          <p class="muted">课程编号将在创建后由系统生成。</p>
          <el-form-item label="课程名称"><el-input v-model="courseForm.name" /></el-form-item>
        </div>
        <el-form-item label="所属学期">
          <el-select v-model="courseForm.semesterId" style="width:100%">
            <el-option v-for="item in store.semesters" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="简介"><el-input v-model="courseForm.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="createCourse">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.semester-alert { margin-bottom: 16px; }
.course-strip { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 14px; margin-bottom: 20px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
@media (max-width: 600px) { .form-grid { grid-template-columns: 1fr; gap: 0; } }
</style>
