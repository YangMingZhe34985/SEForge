<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import CourseCard from '@/components/CourseCard.vue'
import { useCourseStore } from '@/stores/courses'

const router = useRouter()
const store = useCourseStore()
const joinVisible = ref(false)
const joinCode = ref('')

async function joinCourse() {
  if (!joinCode.value.trim()) return
  try {
    const course = await store.join(joinCode.value.trim())
    joinVisible.value = false
    joinCode.value = ''
    ElMessage.success('已加入课程')
    await router.push({ name: 'student-course', params: { courseId: course.id } })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加入失败')
  }
}

function openCourse(courseId: string) {
  void router.push({ name: 'student-course', params: { courseId } })
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
    <PageHeader title="我的课程" description="课程是问答、作业与成绩的安全边界；使用教师发放的邀请码加入课程。">
      <el-button type="primary" @click="joinVisible = true">使用邀请码加入</el-button>
    </PageHeader>

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
      <EmptyState title="还没有课程" description="使用教师发放的邀请码加入第一门课程。" />
    </div>

    <el-dialog v-model="joinVisible" title="加入课程" width="min(420px, 94vw)">
      <el-input v-model="joinCode" placeholder="请输入课程邀请码" @keyup.enter="joinCourse" />
      <template #footer>
        <el-button @click="joinVisible = false">取消</el-button>
        <el-button type="primary" @click="joinCourse">加入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.course-strip { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 14px; margin-bottom: 20px; }
</style>
