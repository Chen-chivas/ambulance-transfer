<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { query, type Page } from '../api/business'
import { request, messageOf } from '../api/http'

export interface Person { id: number; companyId: number; name: string; phone: string; role: 'DRIVER' | 'NURSE'; username: string | null; certNo: string; certExpireAt: string; enabled: boolean; busy: boolean }
const props = defineProps<{ companyId?: number }>()
const base = computed(() => props.companyId ? `/admin/companies/${props.companyId}/staff` : '/company/staff')
const rows = ref<Person[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const role = ref('')
const loading = ref(false)
const error = ref('')
const editing = ref(false)
const editId = ref<number | null>(null)
const saving = ref(false)
const deleting = ref<number | null>(null)
const formRef = ref<FormInstance>()
const defaults = () => ({ name: '', phone: '', role: 'DRIVER' as 'DRIVER' | 'NURSE', certNo: '', certExpireAt: '', username: '', password: '', enabled: true })
const form = reactive(defaults())
const rules = computed<FormRules>(() => ({
  name: [{ required: true, whitespace: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ required: true, pattern: /^[0-9+() -]{5,30}$/, message: '请输入有效联系电话', trigger: 'blur' }],
  certNo: [{ required: true, whitespace: true, message: '请输入证书编号', trigger: 'blur' }],
  certExpireAt: [{ required: true, message: '请选择证书到期日', trigger: 'change' }],
  ...(!editId.value && form.role === 'DRIVER' ? {
    username: [{ required: true, pattern: /^[A-Za-z0-9_.-]{3,50}$/, message: '3–50 位字母、数字、点、横线或下划线', trigger: 'blur' }],
    password: [{ required: true, min: 10, max: 72, message: '密码至少 10 个字符', trigger: 'blur' }, { validator: (_rule, value, cb) => cb(new TextEncoder().encode(value).length > 72 ? new Error('密码最多 72 字节') : undefined), trigger: 'blur' }],
  } : {}),
}))
let sequence = 0
async function load() {
  const seq = ++sequence; loading.value = true; error.value = ''
  try { const value = await request<Page<Person>>(`${base.value}?${query({ page: page.value, size: size.value, keyword: keyword.value, role: role.value })}`); if (seq !== sequence) return; rows.value = value.items; total.value = value.total; if (page.value > 1 && !value.items.length) { page.value--; await load() } }
  catch (e) { if (seq === sequence) { rows.value = []; total.value = 0; error.value = messageOf(e) } }
  finally { if (seq === sequence) loading.value = false }
}
function search() { page.value = 1; void load() }
function edit(row?: Person) { editId.value = row?.id ?? null; Object.assign(form, defaults(), row || {}, { password: '', username: row?.username || '' }); editing.value = true }
async function save() {
  if (saving.value || !(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  const data = editId.value ? { name: form.name, phone: form.phone, certNo: form.certNo, certExpireAt: form.certExpireAt, enabled: form.enabled }
    : { name: form.name, phone: form.phone, role: form.role, certNo: form.certNo, certExpireAt: form.certExpireAt, ...(form.role === 'DRIVER' ? { username: form.username, password: form.password } : {}) }
  try { await request(`${base.value}${editId.value ? `/${editId.value}` : ''}`, editId.value ? 'PUT' : 'POST', data); form.password = ''; editing.value = false; ElMessage.success('人员资料已保存'); await load() }
  catch (e) { ElMessage.error(messageOf(e)) }
  finally { saving.value = false }
}
async function remove(row: Person) {
  try { await ElMessageBox.confirm(`确定删除“${row.name}”？存在历史任务记录的人员无法删除。`, '删除人员', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) } catch { return }
  deleting.value = row.id
  try { await request(`${base.value}/${row.id}`, 'DELETE'); ElMessage.success('人员已删除'); await load() }
  catch (e) { ElMessage.error(messageOf(e)) }
  finally { deleting.value = null }
}
function expired(value: string) { return !value || value < new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date()) }
watch(() => props.companyId, () => { page.value = 1; void load() }, { immediate: true })
onUnmounted(() => { sequence++ })
</script>

<template>
  <div class="toolbar"><form class="filter-form" @submit.prevent="search"><el-input v-model="keyword" placeholder="搜索姓名 / 电话 / 证书" aria-label="搜索人员" clearable maxlength="100" /><el-select v-model="role" placeholder="全部角色" aria-label="人员角色" clearable @change="search"><el-option label="司机" value="DRIVER" /><el-option label="护士" value="NURSE" /></el-select><el-button native-type="submit" :loading="loading">查询</el-button></form><el-button type="primary" @click="edit()">新增人员</el-button></div>
  <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="form-alert"><el-button text @click="load">重试</el-button></el-alert>
  <el-table v-loading="loading" :data="rows" stripe empty-text="暂无司机或护士，请新增人员">
    <el-table-column prop="name" label="姓名" min-width="110" /><el-table-column label="角色" width="80"><template #default="{ row }">{{ row.role === 'DRIVER' ? '司机' : '护士' }}</template></el-table-column><el-table-column prop="phone" label="联系电话" min-width="140" /><el-table-column label="登录账号" min-width="130"><template #default="{ row }">{{ row.username || '无需登录' }}</template></el-table-column><el-table-column prop="certNo" label="证书编号" min-width="130" show-overflow-tooltip /><el-table-column label="证书到期日" min-width="135"><template #default="{ row }"><el-tag :type="expired(row.certExpireAt) ? 'danger' : 'success'" effect="plain">{{ row.certExpireAt || '未填写' }}</el-tag></template></el-table-column><el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.busy ? 'warning' : row.enabled ? 'success' : 'info'">{{ row.busy ? '执行任务' : row.enabled ? '已启用' : '已停用' }}</el-tag></template></el-table-column><el-table-column label="操作" width="130" fixed="right"><template #default="{ row }"><el-button link type="primary" :disabled="row.busy" @click="edit(row)">编辑</el-button><el-button link type="danger" :disabled="row.busy || deleting !== null" :loading="deleting === row.id" @click="remove(row)">删除</el-button></template></el-table-column>
  </el-table>
  <div class="table-footer"><p>执行任务中的人员不可修改或停用，送达后自动恢复可用。</p><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10,20,50]" layout="total, prev, pager, next, sizes" @current-change="load" @size-change="search" /></div>
  <el-dialog v-model="editing" :title="editId ? '编辑人员资料' : '新增司机 / 护士'" width="540px" destroy-on-close append-to-body :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" @closed="form.password = ''">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving"><el-form-item label="人员角色" required><el-radio-group v-model="form.role" :disabled="!!editId"><el-radio value="DRIVER">司机</el-radio><el-radio value="NURSE">护士</el-radio></el-radio-group></el-form-item><div class="form-columns"><el-form-item label="姓名" prop="name"><el-input v-model="form.name" maxlength="50" /></el-form-item><el-form-item label="联系电话" prop="phone"><el-input v-model="form.phone" maxlength="30" /></el-form-item></div><el-form-item label="证书编号" prop="certNo"><el-input v-model="form.certNo" maxlength="100" /></el-form-item><el-form-item label="证书到期日" prop="certExpireAt"><el-date-picker v-model="form.certExpireAt" value-format="YYYY-MM-DD" type="date" style="width:100%" /></el-form-item><template v-if="!editId && form.role === 'DRIVER'"><el-form-item label="司机登录账号" prop="username"><el-input v-model="form.username" maxlength="50" autocomplete="off" /></el-form-item><el-form-item label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password maxlength="72" autocomplete="new-password" placeholder="至少 10 个字符" /></el-form-item></template><el-form-item v-if="editId" label="账号状态"><el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" /></el-form-item></el-form>
    <template #footer><el-button :disabled="saving" @click="editing = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存人员</el-button></template>
  </el-dialog>
</template>
