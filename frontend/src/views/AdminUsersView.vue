<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { adminApi, type AdminUserMembership, type UserImportPreview, type UserImportResult } from '@/api/admin'
import { courseApi } from '@/api/courses'
import type { CourseSummary, PlatformRole, Semester, SemesterStatus, User } from '@/types/domain'

const users = ref<User[]>([])
const activeSection = ref('users')
const userPage = ref(1)
const userTotal = ref(0)
const userSearch = ref('')
const userType = ref<'' | 'TEACHER' | 'STUDENT'>('')
const selectedUser = ref<User | null>(null)
const memberships = ref<AdminUserMembership[]>([])
const userDetailVisible = ref(false)
const profileForm = reactive({ displayName: '', studentNo: '' })
const resetToken = ref('')
const resetExpiresAt = ref('')
const importFile = ref<File | null>(null)
const importPreview = ref<UserImportPreview | null>(null)
const importResult = ref<UserImportResult | null>(null)
const importVisible = ref(false)
const importBusy = ref(false)
const semesters = ref<Semester[]>([])
const courses = ref<CourseSummary[]>([])
const coursePage = ref(1)
const courseTotal = ref(0)
const courseSearch = ref('')
const courseStatus = ref<'' | 'ACTIVE' | 'ARCHIVED'>('')
const courseSemesterId = ref('')
const loading = ref(false)
const createVisible = ref(false)
const semesterVisible = ref(false)
const editingSemesterId = ref<string | null>(null)
const courseEditVisible = ref(false)
const courseForm = reactive({ id: '', name: '', description: '', status: 'ACTIVE' as 'ACTIVE' | 'ARCHIVED' })
const form = reactive({
  email: '',
  username: '',
  studentNo: '',
  displayName: '',
  password: '',
  accountType: 'TEACHER' as 'TEACHER' | 'STUDENT',
  platformRole: 'USER' as PlatformRole,
})
const semesterForm = reactive({ code: '', name: '', startsOn: '', endsOn: '', status: 'PLANNED' as SemesterStatus })

async function load() {
  loading.value = true
  try {
    const [userResult, semesterList, courseResult] = await Promise.all([
      adminApi.users(userPage.value - 1, 20, userType.value || undefined, userSearch.value.trim() || undefined),
      courseApi.semesters(),
      courseApi.list(coursePage.value - 1, 20, {
        search: courseSearch.value.trim() || undefined,
        status: courseStatus.value || undefined,
        semesterId: courseSemesterId.value || undefined,
      }),
    ])
    users.value = userResult.items
    userTotal.value = userResult.total
    semesters.value = semesterList
    courses.value = courseResult.items
    courseTotal.value = courseResult.total
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '管理数据加载失败') }
  finally { loading.value = false }
}

async function loadCourses() {
  try {
    const result = await courseApi.list(coursePage.value - 1, 20, {
      search: courseSearch.value.trim() || undefined,
      status: courseStatus.value || undefined,
      semesterId: courseSemesterId.value || undefined,
    })
    courses.value = result.items
    courseTotal.value = result.total
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '课程列表加载失败') }
}

async function loadUsers() {
  try {
    const result = await adminApi.users(userPage.value - 1, 20, userType.value || undefined, userSearch.value.trim() || undefined)
    users.value = result.items
    userTotal.value = result.total
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '账号列表加载失败') }
}

async function createUser() {
  if (!form.email || !form.username || !form.displayName || form.password.length < 10) return ElMessage.warning('请完整填写账号信息，密码至少 10 位')
  if (form.accountType === 'STUDENT' && !/^[A-Za-z0-9_-]{3,64}$/.test(form.studentNo.trim())) return ElMessage.warning('请输入有效且唯一的学号')
  try {
    await adminApi.createUser({
      email: form.email,
      username: form.username,
      studentNo: form.accountType === 'STUDENT' ? form.studentNo.trim() : undefined,
      displayName: form.displayName,
      password: form.password,
      accountType: form.accountType,
      roles: [form.platformRole],
    })
    userPage.value = 1
    await loadUsers()
    createVisible.value = false
    Object.assign(form, { email: '', username: '', studentNo: '', displayName: '', password: '', accountType: 'TEACHER', platformRole: 'USER' })
    ElMessage.success('账号已创建')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '创建失败') }
}

async function previewCsv(files: FileList | null) {
  const file = files?.item(0)
  if (!file) return
  importBusy.value = true
  importFile.value = file
  importResult.value = null
  importPreview.value = null
  importVisible.value = true
  try {
    importPreview.value = await adminApi.previewImport(file)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : 'CSV 校验失败') }
  finally { importBusy.value = false }
}

async function confirmCsv() {
  if (!importFile.value || !importPreview.value || importPreview.value.valid === 0) return
  importBusy.value = true
  try {
    importResult.value = await adminApi.confirmImport(importFile.value, importPreview.value.digest)
    importPreview.value = null
    await loadUsers()
    ElMessage.success(`导入完成：创建 ${importResult.value.created}，跳过 ${importResult.value.skipped}，失败 ${importResult.value.failed}`)
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '导入失败') }
  finally { importBusy.value = false }
}

function downloadImportResult() {
  if (!importResult.value) return
  const cell = (value: string | number | null) => {
    const text = String(value ?? '')
    const safe = /^[\s]*[=+\-@]/.test(text) ? `'${text}` : text
    return `"${safe.replaceAll('"', '""')}"`
  }
  const csv = [
    ['line', 'status', 'message', 'accountType', 'studentNo', 'username', 'email', 'displayName', 'initialPassword'].join(','),
    ...importResult.value.rows.map((row) => [row.line, row.status, row.message, row.accountType, row.studentNo,
      row.username, row.email, row.displayName, row.initialPassword].map(cell).join(',')),
  ].join('\r\n')
  const url = URL.createObjectURL(new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = 'seforge-user-import-result.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function createSemester() {
  if (!semesterForm.code.trim() || !semesterForm.name.trim() || !semesterForm.startsOn || !semesterForm.endsOn) {
    return ElMessage.warning('请完整填写学期信息')
  }
  if (semesterForm.endsOn <= semesterForm.startsOn) return ElMessage.warning('结束日期必须晚于开始日期')
  try {
    const wasEditing = Boolean(editingSemesterId.value)
    const semester = editingSemesterId.value
      ? await courseApi.updateSemester(editingSemesterId.value, { ...semesterForm })
      : await courseApi.createSemester({ ...semesterForm })
    const index = semesters.value.findIndex((item) => item.id === semester.id)
    if (index >= 0) semesters.value[index] = semester
    else semesters.value.unshift(semester)
    semesters.value.sort((a, b) => b.startsOn.localeCompare(a.startsOn))
    semesterVisible.value = false
    editingSemesterId.value = null
    Object.assign(semesterForm, { code: '', name: '', startsOn: '', endsOn: '', status: 'PLANNED' })
    ElMessage.success(wasEditing ? '学期已更新' : '学期已创建，现在可以创建课程')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '学期创建失败') }
}

function editSemester(semester: Semester) {
  editingSemesterId.value = semester.id
  Object.assign(semesterForm, { code: semester.code, name: semester.name, startsOn: semester.startsOn, endsOn: semester.endsOn, status: semester.status })
  semesterVisible.value = true
}

async function openUser(user: User) {
  try {
    const [detail, assignments] = await Promise.all([adminApi.user(user.id), adminApi.memberships(user.id)])
    selectedUser.value = detail
    memberships.value = assignments
    Object.assign(profileForm, { displayName: detail.displayName, studentNo: detail.studentNo || '' })
    resetToken.value = ''
    userDetailVisible.value = true
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '账号详情加载失败') }
}

async function saveProfile() {
  if (!selectedUser.value || !profileForm.displayName.trim()) return ElMessage.warning('请输入姓名')
  try {
    const updated = await adminApi.updateProfile(selectedUser.value.id, {
      displayName: profileForm.displayName.trim(),
      studentNo: selectedUser.value.accountType === 'STUDENT' ? profileForm.studentNo.trim() || undefined : undefined,
    })
    Object.assign(selectedUser.value, updated)
    const row = users.value.find((item) => item.id === updated.id)
    if (row) Object.assign(row, updated)
    ElMessage.success('基础信息已更新')
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '基础信息更新失败') }
}

async function issueReset() {
  if (!selectedUser.value) return
  try {
    await ElMessageBox.confirm('将生成一次性 15 分钟重置令牌。请通过安全渠道交给账号本人。', '重置登录凭据', { type: 'warning' })
    const result = await adminApi.issuePasswordReset(selectedUser.value.id)
    resetToken.value = result.token
    resetExpiresAt.value = result.expiresAt
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '令牌生成失败') }
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
    const updated = await adminApi.updateCourse(courseForm.id, {
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
    const updated = await adminApi.updateCourse(course.id, { status: archiving ? 'ARCHIVED' : 'ACTIVE' })
    const index = courses.value.findIndex((item) => item.id === updated.id)
    if (index >= 0) courses.value[index] = { ...courses.value[index], ...updated }
    ElMessage.success(archiving ? '课程已归档' : '课程已恢复')
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '操作失败') }
}

async function transferOwner(course: CourseSummary) {
  try {
    const result = await ElMessageBox.prompt('输入新课程负责人的用户 ID（必须为已启用教师账号）', '调整课程负责人', {
      inputPattern: /^[1-9][0-9]*$/,
      inputErrorMessage: '请输入有效的用户 ID',
    })
    const updated = await adminApi.transferOwner(course.id, result.value)
    Object.assign(course, updated)
    ElMessage.success('课程负责人已调整')
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error instanceof Error ? error.message : '负责人调整失败') }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="平台管理" description="先建立学期，再创建教师账号和课程；所有管理操作均由服务端审计。">
      <div class="button-row"><el-button @click="semesterVisible = true">创建学期</el-button><el-button type="primary" @click="createVisible = true">创建账号</el-button></div>
    </PageHeader>
    <el-tabs v-model="activeSection" class="admin-tabs">
    <el-tab-pane label="账号管理" name="users">
    <section class="panel" v-loading="loading">
      <div class="section-heading"><div><h2>用户</h2><p class="muted">学生可自助注册；教师与管理员账号由管理员创建。</p></div><div class="button-row"><el-input v-model="userSearch" placeholder="姓名、账号、学号或邮箱" clearable @keyup.enter="userPage = 1; loadUsers()" /><el-select v-model="userType" style="width:125px" @change="userPage = 1; loadUsers()"><el-option label="全部" value="" /><el-option label="教师" value="TEACHER" /><el-option label="学生" value="STUDENT" /></el-select><el-button @click="userPage = 1; loadUsers()">搜索</el-button><label class="import-button">导入 CSV<input type="file" accept=".csv,text/csv" @change="previewCsv(($event.target as HTMLInputElement).files); ($event.target as HTMLInputElement).value = ''" /></label></div></div>
      <el-table v-if="users.length" :data="users" stripe><el-table-column prop="displayName" label="姓名" min-width="130" /><el-table-column prop="username" label="用户名" min-width="130" /><el-table-column prop="studentNo" label="学号" min-width="130"><template #default="scope">{{ scope.row.accountType === 'STUDENT' ? (scope.row.studentNo || '待补录') : '—' }}</template></el-table-column><el-table-column prop="email" label="邮箱" min-width="210" /><el-table-column prop="accountType" label="账号类型" width="120" /><el-table-column label="角色" min-width="150"><template #default="scope"><el-tag v-for="role in scope.row.roles" :key="role" size="small" effect="plain">{{ role }}</el-tag></template></el-table-column><el-table-column label="状态" width="100"><template #default="scope"><el-tag :type="scope.row.enabled ? 'success' : 'danger'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="280"><template #default="scope"><el-button link type="primary" @click="openUser(scope.row)">查看</el-button><el-button link type="primary" @click="toggleAdmin(scope.row)">{{ scope.row.roles.includes('ADMIN') ? '移除管理员' : '授予管理员' }}</el-button><el-button link :type="scope.row.enabled ? 'danger' : 'primary'" @click="toggleEnabled(scope.row)">{{ scope.row.enabled ? '停用' : '启用' }}</el-button></template></el-table-column></el-table>
      <EmptyState v-else title="暂无用户" />
      <el-pagination v-if="userTotal > 20" v-model:current-page="userPage" background layout="prev, pager, next" :page-size="20" :total="userTotal" @current-change="loadUsers" />
    </section>
    </el-tab-pane>
    <el-tab-pane label="学期管理" name="semesters">
    <section class="panel semester-panel" v-loading="loading">
      <div class="section-heading"><div><h2>学期</h2><p class="muted">课程必须归属一个已登记的学期。</p></div><el-button @click="semesterVisible = true">新建学期</el-button></div>
      <el-table v-if="semesters.length" :data="semesters" stripe>
        <el-table-column prop="code" label="代码" min-width="120" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="startsOn" label="开始日期" width="130" />
        <el-table-column prop="endsOn" label="结束日期" width="130" />
        <el-table-column label="状态" width="110"><template #default="scope"><el-tag effect="plain">{{ scope.row.status }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="90"><template #default="scope"><el-button link @click="editSemester(scope.row)">修改</el-button></template></el-table-column>
      </el-table>
      <EmptyState v-else title="暂无学期" description="请先创建学期，教师才能创建课程。" />
    </section>
    </el-tab-pane>
    <el-tab-pane label="全局课程" name="courses">
    <section class="panel audit-panel" v-loading="loading">
      <div class="section-heading"><div><h2>全局课程治理</h2><p class="muted">按学期、状态和名称检索课程，治理负责人与归档状态。</p></div><div class="button-row"><el-input v-model="courseSearch" placeholder="课程名称或代码" clearable @keyup.enter="coursePage = 1; loadCourses()" /><el-select v-model="courseSemesterId" clearable placeholder="学期" style="width:135px" @change="coursePage = 1; loadCourses()"><el-option v-for="item in semesters" :key="item.id" :label="item.name" :value="item.id" /></el-select><el-select v-model="courseStatus" style="width:105px" @change="coursePage = 1; loadCourses()"><el-option label="全部" value="" /><el-option label="活跃" value="ACTIVE" /><el-option label="归档" value="ARCHIVED" /></el-select><el-button @click="coursePage = 1; loadCourses()">搜索</el-button></div></div>
      <el-table v-if="courses.length" :data="courses" stripe>
        <el-table-column prop="code" label="课程代码" min-width="130" />
        <el-table-column prop="name" label="课程名称" min-width="190" />
        <el-table-column prop="semesterName" label="学期" min-width="160" />
        <el-table-column prop="ownerId" label="负责人 ID" min-width="110" />
        <el-table-column prop="memberCount" label="成员数" width="100" />
        <el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">{{ scope.row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="190" />
        <el-table-column label="操作" width="210"><template #default="scope"><el-button link type="primary" @click="openCourseEdit(scope.row)">编辑</el-button><el-button link @click="transferOwner(scope.row)">负责人</el-button><el-button link :type="scope.row.status === 'ARCHIVED' ? 'success' : 'warning'" @click="toggleCourseArchive(scope.row)">{{ scope.row.status === 'ARCHIVED' ? '恢复' : '归档' }}</el-button></template></el-table-column>
      </el-table>
      <EmptyState v-else title="暂无课程" description="教师创建课程后会在这里显示。" />
      <el-pagination v-if="courseTotal > 20" v-model:current-page="coursePage" background layout="prev, pager, next" :page-size="20" :total="courseTotal" @current-change="loadCourses" />
    </section>
    </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="createVisible" title="创建平台账号" width="min(520px,94vw)"><el-form label-position="top"><div class="form-grid"><el-form-item label="姓名"><el-input v-model="form.displayName" /></el-form-item><el-form-item label="账号类型"><el-select v-model="form.accountType"><el-option label="教师" value="TEACHER" /><el-option label="学生" value="STUDENT" /></el-select></el-form-item></div><el-form-item label="平台角色"><el-select v-model="form.platformRole" style="width:100%"><el-option label="普通用户" value="USER" /><el-option label="平台管理员" value="ADMIN" /></el-select></el-form-item><el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item><el-form-item v-if="form.accountType === 'STUDENT'" label="学号"><el-input v-model="form.studentNo" /></el-form-item><el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item><el-form-item label="初始密码"><el-input v-model="form.password" type="password" show-password /></el-form-item></el-form><template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" @click="createUser">创建</el-button></template></el-dialog>
    <el-dialog v-model="semesterVisible" :title="editingSemesterId ? '修改学期' : '创建学期'" width="min(540px,94vw)">
      <el-form label-position="top">
        <div class="form-grid"><el-form-item label="学期代码"><el-input v-model="semesterForm.code" placeholder="2026-FALL" /></el-form-item><el-form-item label="学期名称"><el-input v-model="semesterForm.name" placeholder="2026 秋季学期" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="开始日期"><el-date-picker v-model="semesterForm.startsOn" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item><el-form-item label="结束日期"><el-date-picker v-model="semesterForm.endsOn" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></div>
        <el-form-item label="初始状态"><el-select v-model="semesterForm.status" style="width:100%"><el-option label="规划中" value="PLANNED" /><el-option label="进行中" value="ACTIVE" /><el-option label="已结束" value="CLOSED" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="semesterVisible=false">取消</el-button><el-button type="primary" @click="createSemester">保存学期</el-button></template>
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
    <el-dialog v-model="userDetailVisible" title="账号详情" width="min(620px,94vw)">
      <template v-if="selectedUser">
        <p class="muted">账号 #{{ selectedUser.id }} · {{ selectedUser.username }} · {{ selectedUser.accountType }}</p>
        <el-form label-position="top"><el-form-item label="姓名"><el-input v-model="profileForm.displayName" /></el-form-item><el-form-item v-if="selectedUser.accountType === 'STUDENT'" label="学号"><el-input v-model="profileForm.studentNo" placeholder="存量学生可在此补录或校正" /></el-form-item><el-form-item label="联系邮箱"><el-input :model-value="selectedUser.email" disabled /></el-form-item></el-form>
        <el-button type="primary" @click="saveProfile">保存基础信息</el-button>
        <el-divider />
        <h3>课程/班级归属</h3>
        <el-table v-if="memberships.length" :data="memberships"><el-table-column prop="courseName" label="课程" /><el-table-column prop="role" label="课程角色" /><el-table-column prop="classId" label="教学班 ID" /></el-table>
        <EmptyState v-else title="暂无课程归属" />
        <el-divider />
        <el-button @click="issueReset">生成一次性密码重置令牌</el-button>
        <el-alert v-if="resetToken" type="warning" :closable="false" title="此令牌只显示在当前对话框，请安全转交账号本人"><p><code>{{ resetToken }}</code></p><p>有效期至 {{ new Date(resetExpiresAt).toLocaleString() }}；账号本人可在登录页完成重置。</p></el-alert>
      </template>
    </el-dialog>
    <el-dialog v-model="importVisible" title="受控账号导入" width="min(900px,96vw)" :close-on-click-modal="!importBusy">
      <p class="muted">UTF-8 CSV 表头：<code>accountType,studentNo,username,email,displayName</code>。教师学号留空；最多 1000 行、1 MB。预览不写库，确认后逐行创建；初始随机密码仅在本次结果中显示，请安全交付。</p>
      <div v-loading="importBusy">
        <el-alert v-if="importPreview" :title="`待创建 ${importPreview.valid} 行，拒绝 ${importPreview.rejected} 行`" :type="importPreview.rejected ? 'warning' : 'success'" :closable="false" />
        <el-alert v-if="importResult" :title="`创建 ${importResult.created}，跳过 ${importResult.skipped}，失败 ${importResult.failed}`" type="success" :closable="false" />
        <el-table v-if="importPreview || importResult" :data="(importResult?.rows || importPreview?.rows || [])" max-height="420" stripe>
          <el-table-column prop="line" label="行" width="55" /><el-table-column prop="accountType" label="类型" width="90" /><el-table-column prop="studentNo" label="学号" min-width="115" /><el-table-column prop="username" label="账号" min-width="120" /><el-table-column prop="email" label="邮箱" min-width="180" /><el-table-column prop="status" label="结果" width="90" /><el-table-column prop="message" label="原因" min-width="190" /><el-table-column v-if="importResult" prop="initialPassword" label="一次性展示初始密码" min-width="230" />
        </el-table>
      </div>
      <template #footer><el-button @click="importVisible=false">关闭</el-button><el-button v-if="importResult" @click="downloadImportResult">下载结果（含凭据）</el-button><el-button v-if="importPreview?.valid" type="primary" :loading="importBusy" @click="confirmCsv">确认写入</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>.semester-panel,.audit-panel{margin-bottom:18px}.section-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:14px}.section-heading h2,.section-heading p{margin:0}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.el-tag+.el-tag{margin-left:5px}.import-button{display:inline-flex;align-items:center;padding:0 12px;border:1px solid #dcdfe6;border-radius:4px;cursor:pointer;white-space:nowrap}.import-button input{display:none}@media(max-width:560px){.form-grid{grid-template-columns:1fr;gap:0}.section-heading{align-items:stretch;flex-direction:column}}</style>
