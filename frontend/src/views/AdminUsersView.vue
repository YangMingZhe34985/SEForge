<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { adminApi } from '@/api/admin'
import { courseApi } from '@/api/courses'
import type { CourseSummary, PlatformRole, Semester, SemesterStatus, User } from '@/types/domain'

const users = ref<User[]>([])
const semesters = ref<Semester[]>([])
const courses = ref<CourseSummary[]>([])
const loading = ref(false)
const createVisible = ref(false)
const semesterVisible = ref(false)
const courseEditVisible = ref(false)
const courseForm = reactive({ id: '', name: '', description: '', status: 'ACTIVE' as 'ACTIVE' | 'ARCHIVED' })
const form = reactive({
  email: '',
  username: '',
  displayName: '',
  password: '',
  accountType: 'TEACHER' as 'TEACHER' | 'STUDENT',
  platformRole: 'USER' as PlatformRole,
})
const semesterForm = reactive({ code: '', name: '', startsOn: '', endsOn: '', status: 'PLANNED' as SemesterStatus })

async function load() {
  loading.value = true
  try {
    const [userPage, semesterList, coursePage] = await Promise.all([
      adminApi.users(),
      courseApi.semesters(),
      courseApi.list(0, 100),
    ])
    users.value = userPage.items
    semesters.value = semesterList
    courses.value = coursePage.items
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '管理数据加载失败') }
  finally { loading.value = false }
}

async function createUser() {
  if (!form.email || !form.username || !form.displayName || form.password.length < 10) return ElMessage.warning('请完整填写账号信息，密码至少 10 位')
  try {
    users.value.unshift(await adminApi.createUser({
      email: form.email,
      username: form.username,
      displayName: form.displayName,
      password: form.password,
      accountType: form.accountType,
      roles: [form.platformRole],
    }))
    createVisible.value = false
    Object.assign(form, { email: '', username: '', displayName: '', password: '', accountType: 'TEACHER', platformRole: 'USER' })
    ElMessage.success('账号已创建')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '创建失败') }
}

async function createSemester() {
  if (!semesterForm.code.trim() || !semesterForm.name.trim() || !semesterForm.startsOn || !semesterForm.endsOn) {
    return ElMessage.warning('请完整填写学期信息')
  }
  if (semesterForm.endsOn <= semesterForm.startsOn) return ElMessage.warning('结束日期必须晚于开始日期')
  try {
    const semester = await courseApi.createSemester({ ...semesterForm })
    semesters.value.unshift(semester)
    semesters.value.sort((a, b) => b.startsOn.localeCompare(a.startsOn))
    semesterVisible.value = false
    Object.assign(semesterForm, { code: '', name: '', startsOn: '', endsOn: '', status: 'PLANNED' })
    ElMessage.success('学期已创建，现在可以创建课程')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '学期创建失败') }
}

async function toggleEnabled(user: User) {
  const next = !user.enabled
  try {
    await ElMessageBox.confirm(`确定${next ? '启用' : '停用'} ${user.displayName} 的账号吗？`, '账号状态', { type: 'warning' })
    Object.assign(user, await adminApi.setEnabled(user.id, next))
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '操作失败') }
}

async function toggleAdmin(user: User) {
  const isAdmin = user.roles.includes('ADMIN')
  try {
    await ElMessageBox.confirm(
      `确定${isAdmin ? '移除' : '授予'} ${user.displayName} 的平台管理员角色吗？`,
      '平台角色',
      { type: 'warning' },
    )
    const roles: Array<'ADMIN' | 'USER'> = isAdmin ? ['USER'] : ['USER', 'ADMIN']
    Object.assign(user, await adminApi.setRoles(user.id, roles))
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '操作失败') }
}

function openCourseEdit(course: CourseSummary) {
  Object.assign(courseForm, {
    id: course.id,
    name: course.name,
    description: course.description || '',
    status: course.status,
  })
  courseEditVisible.value = true
}

async function saveCourseEdit() {
  if (!courseForm.name.trim()) return ElMessage.warning('请填写课程名称')
  try {
    const updated = await courseApi.update(courseForm.id, {
      name: courseForm.name.trim(),
      description: courseForm.description,
      status: courseForm.status,
    })
    const index = courses.value.findIndex((course) => course.id === updated.id)
    if (index >= 0) courses.value[index] = { ...courses.value[index], ...updated }
    courseEditVisible.value = false
    ElMessage.success('课程已更新')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '课程更新失败') }
}

async function toggleCourseArchive(course: CourseSummary) {
  const archiving = course.status !== 'ARCHIVED'
  try {
    await ElMessageBox.confirm(
      archiving
        ? `归档后“${course.name}”将不再接受新成员加入，确定归档吗？`
        : `确定恢复“${course.name}”为进行中吗？`,
      archiving ? '归档课程' : '恢复课程',
      { type: 'warning' },
    )
    const updated = await courseApi.update(course.id, { status: archiving ? 'ARCHIVED' : 'ACTIVE' })
    const index = courses.value.findIndex((item) => item.id === updated.id)
    if (index >= 0) courses.value[index] = { ...courses.value[index], ...updated }
    ElMessage.success(archiving ? '课程已归档' : '课程已恢复')
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '操作失败') }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="平台管理" description="先建立学期，再创建教师账号和课程；所有管理操作均由服务端审计。">
      <div class="button-row"><el-button @click="semesterVisible = true">创建学期</el-button><el-button type="primary" @click="createVisible = true">创建账号</el-button></div>
    </PageHeader>
    <section class="panel semester-panel" v-loading="loading">
      <div class="section-heading"><div><h2>学期</h2><p class="muted">课程必须归属一个已登记的学期。</p></div><el-button @click="semesterVisible = true">新建学期</el-button></div>
      <el-table v-if="semesters.length" :data="semesters" stripe>
        <el-table-column prop="code" label="代码" min-width="120" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="startsOn" label="开始日期" width="130" />
        <el-table-column prop="endsOn" label="结束日期" width="130" />
        <el-table-column label="状态" width="110"><template #default="scope"><el-tag effect="plain">{{ scope.row.status }}</el-tag></template></el-table-column>
      </el-table>
      <EmptyState v-else title="暂无学期" description="请先创建学期，教师才能创建课程。" />
    </section>
    <section class="panel audit-panel" v-loading="loading">
      <div class="section-heading"><div><h2>课程审计</h2><p class="muted">管理员可查看全部课程的归属学期、状态与成员规模。</p></div></div>
      <el-table v-if="courses.length" :data="courses" stripe>
        <el-table-column prop="code" label="课程代码" min-width="130" />
        <el-table-column prop="name" label="课程名称" min-width="190" />
        <el-table-column prop="semesterName" label="学期" min-width="160" />
        <el-table-column prop="memberCount" label="成员数" width="100" />
        <el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">{{ scope.row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="190" />
        <el-table-column label="操作" width="150"><template #default="scope"><el-button link type="primary" @click="openCourseEdit(scope.row)">编辑</el-button><el-button link :type="scope.row.status === 'ARCHIVED' ? 'success' : 'warning'" @click="toggleCourseArchive(scope.row)">{{ scope.row.status === 'ARCHIVED' ? '恢复' : '归档' }}</el-button></template></el-table-column>
      </el-table>
      <EmptyState v-else title="暂无课程" description="教师创建课程后会在这里显示。" />
    </section>
    <section class="panel" v-loading="loading">
      <div class="section-heading"><div><h2>用户</h2><p class="muted">学生可自助注册；教师与管理员账号由管理员创建。</p></div></div>
      <el-table v-if="users.length" :data="users" stripe><el-table-column prop="displayName" label="姓名" min-width="130" /><el-table-column prop="username" label="用户名" min-width="130" /><el-table-column prop="email" label="邮箱" min-width="210" /><el-table-column prop="accountType" label="账号类型" width="120" /><el-table-column label="角色" min-width="150"><template #default="scope"><el-tag v-for="role in scope.row.roles" :key="role" size="small" effect="plain">{{ role }}</el-tag></template></el-table-column><el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.enabled ? 'success' : 'danger'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="210"><template #default="scope"><el-button link type="primary" @click="toggleAdmin(scope.row)">{{ scope.row.roles.includes('ADMIN') ? '移除管理员' : '授予管理员' }}</el-button><el-button link :type="scope.row.enabled ? 'danger' : 'primary'" @click="toggleEnabled(scope.row)">{{ scope.row.enabled ? '停用' : '启用' }}</el-button></template></el-table-column></el-table>
      <EmptyState v-else title="暂无用户" />
    </section>
    <el-dialog v-model="createVisible" title="创建平台账号" width="min(520px,94vw)"><el-form label-position="top"><div class="form-grid"><el-form-item label="姓名"><el-input v-model="form.displayName" /></el-form-item><el-form-item label="账号类型"><el-select v-model="form.accountType"><el-option label="教师" value="TEACHER" /><el-option label="学生" value="STUDENT" /></el-select></el-form-item></div><el-form-item label="平台角色"><el-select v-model="form.platformRole" style="width:100%"><el-option label="普通用户" value="USER" /><el-option label="平台管理员" value="ADMIN" /></el-select></el-form-item><el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item><el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item><el-form-item label="初始密码"><el-input v-model="form.password" type="password" show-password /></el-form-item></el-form><template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" @click="createUser">创建</el-button></template></el-dialog>
    <el-dialog v-model="semesterVisible" title="创建学期" width="min(540px,94vw)">
      <el-form label-position="top">
        <div class="form-grid"><el-form-item label="学期代码"><el-input v-model="semesterForm.code" placeholder="2026-FALL" /></el-form-item><el-form-item label="学期名称"><el-input v-model="semesterForm.name" placeholder="2026 秋季学期" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="开始日期"><el-date-picker v-model="semesterForm.startsOn" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item><el-form-item label="结束日期"><el-date-picker v-model="semesterForm.endsOn" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></div>
        <el-form-item label="初始状态"><el-select v-model="semesterForm.status" style="width:100%"><el-option label="规划中" value="PLANNED" /><el-option label="进行中" value="ACTIVE" /><el-option label="已结束" value="CLOSED" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="semesterVisible=false">取消</el-button><el-button type="primary" @click="createSemester">创建学期</el-button></template>
    </el-dialog>
    <el-dialog v-model="courseEditVisible" title="编辑课程" width="min(520px,94vw)">
      <el-form label-position="top">
        <el-form-item label="课程名称"><el-input v-model="courseForm.name" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="courseForm.description" type="textarea" /></el-form-item>
        <el-form-item label="课程状态">
          <el-select v-model="courseForm.status" style="width:100%">
            <el-option label="进行中（ACTIVE）" value="ACTIVE" />
            <el-option label="已归档（ARCHIVED）" value="ARCHIVED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="courseEditVisible=false">取消</el-button><el-button type="primary" @click="saveCourseEdit">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>.semester-panel,.audit-panel{margin-bottom:18px}.section-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:14px}.section-heading h2,.section-heading p{margin:0}.section-heading p{margin-top:5px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.el-tag+.el-tag{margin-left:5px}@media(max-width:560px){.form-grid{grid-template-columns:1fr;gap:0}.section-heading{align-items:stretch;flex-direction:column}}</style>
