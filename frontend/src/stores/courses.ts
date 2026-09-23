import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { courseApi, type CreateCourseInput } from '@/api/courses'
import type { CourseSummary, Semester } from '@/types/domain'

export const useCourseStore = defineStore('courses', () => {
  const courses = ref<CourseSummary[]>([])
  const semesters = ref<Semester[]>([])
  const loading = ref(false)
  const selectedCourseId = ref<string | null>(null)

  const selectedCourse = computed(() => courses.value.find((course) => course.id === selectedCourseId.value) ?? null)
  const canManageSelected = computed(() => ['TEACHER', 'TA'].includes(selectedCourse.value?.role || ''))

  async function load(): Promise<void> {
    loading.value = true
    try {
      const [coursePage, semesterList] = await Promise.all([courseApi.list(), courseApi.semesters()])
      courses.value = coursePage.items
      semesters.value = semesterList
      if (!selectedCourseId.value || !courses.value.some((course) => course.id === selectedCourseId.value)) {
        selectedCourseId.value = courses.value[0]?.id ?? null
      }
    } finally {
      loading.value = false
    }
  }

  function select(courseId: string): void {
    selectedCourseId.value = courseId
  }

  async function create(input: CreateCourseInput): Promise<CourseSummary> {
    const course = await courseApi.create(input)
    courses.value.unshift(course)
    select(course.id)
    return course
  }

  async function join(inviteCode: string): Promise<CourseSummary> {
    const course = await courseApi.join(inviteCode)
    const index = courses.value.findIndex((item) => item.id === course.id)
    if (index >= 0) courses.value[index] = course
    else courses.value.unshift(course)
    select(course.id)
    return course
  }

  function reset(): void {
    courses.value = []
    semesters.value = []
    selectedCourseId.value = null
  }

  return { courses, semesters, loading, selectedCourseId, selectedCourse, canManageSelected, load, select, create, join, reset }
})
