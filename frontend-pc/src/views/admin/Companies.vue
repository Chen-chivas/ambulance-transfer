<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { auditLabels, auditTypes, listCompanies, saveCompany, type Company, type CompanyInput, type AuditStatus } from '../../api/business'
import { request, messageOf } from '../../api/http'
import VehicleManager from '../../components/VehicleManager.vue'
import DispatcherManager from '../../components/DispatcherManager.vue'
import StaffManager from '../../components/StaffManager.vue'

const route = useRoute()
const rows = ref<Company[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const status = ref(typeof route.query.status === 'string' && route.query.status in auditLabels ? route.query.status : '')
const loading = ref(false)
const error = ref('')
const editing = ref(false)
const editId = ref<number | null>(null)
const saving = ref(false)
const deleting = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<CompanyInput>({ name: '', licenseNo: '', licenseExpireAt: null, contactPhone: '' })
const rules: FormRules = {
  name: [{ required: true, whitespace: true, message: '请输入车行名称', trigger: 'blur' }],
  licenseNo: [{ required: true, whitespace: true, message: '请输入许可证号', trigger: 'blur' }],
  contactPhone: [{ required: true, pattern: /^[0-9+() -]{5,30}$/, message: '请输入有效联系电话', trigger: 'blur' }],
}
const auditing = ref<Company | null>(null)
const auditForm = reactive({ status: 'APPROVED', remark: '' })
const auditOpen = ref(false)
const auditSaving = ref(false)
const selected = ref<Company | null>(null)
const drawer = ref(false)
const activeTab = ref('vehicles')
let requestId = 0
async function load() {
  const id = ++requestId
  loading.value = true; error.value = ''
  try {
    const result = await listCompanies({ page: page.value, size: size.value, keyword: keyword.value, status: status.value })
    if (id !== requestId) return
    rows.value = result.items; total.value = result.total
    if (page.value > 1 && !result.items.length) { page.value--; await load() }
  } catch (e) { if (id === requestId) { rows.value = []; total.value = 0; error.value = messageOf(e) } }
  finally { if (id === requestId) loading.value = false }
}
function search() { page.value = 1; void load() }
function edit(row?: Company) {
  editId.value = row?.id ?? null
  Object.assign(form, { name: row?.name || '', licenseNo: row?.licenseNo || '', licenseExpireAt: row?.licenseExpireAt || null, contactPhone: row?.contactPhone || '' })
  editing.value = true
}
async function save() {
  if (saving.value || !(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try { await saveCompany(editId.value, form); editing.value = false; ElMessage.success('车行资料已保存，等待审核'); await load() }
  catch (e) { ElMessage.error(messageOf(e)) }
  finally { saving.value = false }
}
function openAudit(row: Company) { auditing.value = row; Object.assign(auditForm, { status: 'APPROVED', remark: '' }); auditOpen.value = true }
async function audit() {
  if (!auditing.value || auditSaving.value) return
  if (auditForm.status === 'REJECTED' && !auditForm.remark.trim()) { ElMessage.warning('请填写驳回原因'); return }
  auditSaving.value = true
  try { await request(`/admin/companies/${auditing.value.id}/audit`, 'POST', auditForm); auditOpen.value = false; ElMessage.success('审核结果已保存'); await load() }
  catch (e) { ElMessage.error(messageOf(e)) }
  finally { auditSaving.value = false }
}
async function remove(row: Company) {
  try { await ElMessageBox.confirm(`确定删除“${row.name}”？已有车辆、人员或业务记录的车行无法删除。`, '删除车行', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) }
  catch { return }
  deleting.value = row.id
  try { await request(`/admin/companies/${row.id}`, 'DELETE'); ElMessage.success('车行已删除'); await load() }
  catch (e) { ElMessage.error(messageOf(e)) }
  finally { deleting.value = null }
}
function openDetails(row: Company) { selected.value = row; activeTab.value = 'vehicles'; drawer.value = true }
onMounted(load)
</script>

<template>
  <section class="page-heading"><div><div class="eyebrow">COMPANY DIRECTORY</div><h1>车行管理</h1><p>维护车行资料，审核转运资质，统一管理车辆与调度账号。</p></div><el-button type="primary" @click="edit()">新增车行</el-button></section>
  <el-card shadow="never" class="business-card">
    <div class="toolbar"><form class="filter-form" @submit.prevent="search"><el-input v-model="keyword" placeholder="搜索车行名称 / 许可证号" aria-label="搜索车行" clearable maxlength="100" /><el-select v-model="status" placeholder="全部审核状态" aria-label="审核状态" clearable @change="search"><el-option v-for="(label, value) in auditLabels" :key="value" :label="label" :value="value" /></el-select><el-button native-type="submit" :loading="loading">查询</el-button><el-button @click="keyword = ''; status = ''; search()">重置</el-button></form><span class="muted">共 {{ total }} 家车行</span></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-alert"><el-button text @click="load">重试</el-button></el-alert>
    <el-table v-loading="loading" :data="rows" stripe empty-text="暂无车行资料，请新增车行">
      <el-table-column label="车行名称" min-width="190"><template #default="{ row }"><el-button link type="primary" @click="openDetails(row)">{{ row.name }}</el-button></template></el-table-column>
      <el-table-column prop="licenseNo" label="许可证号" min-width="150" show-overflow-tooltip />
      <el-table-column prop="contactPhone" label="联系电话" min-width="140" />
      <el-table-column prop="licenseExpireAt" label="资质到期日" min-width="125"><template #default="{ row }">{{ row.licenseExpireAt || '未填写' }}</template></el-table-column>
      <el-table-column label="审核状态" min-width="115"><template #default="{ row }"><el-tooltip :content="row.auditRemark || '暂无审核备注'"><el-tag :type="auditTypes[row.auditStatus as AuditStatus]">{{ auditLabels[row.auditStatus as AuditStatus] }}</el-tag></el-tooltip></template></el-table-column>
      <el-table-column label="操作" width="235" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="edit(row)">编辑</el-button><el-button link type="primary" :disabled="row.auditStatus !== 'PENDING'" @click="openAudit(row)">审核</el-button><el-button link type="primary" @click="openDetails(row)">车辆 / 账号</el-button><el-button link type="danger" :disabled="deleting !== null" :loading="deleting === row.id" @click="remove(row)">删除</el-button></template></el-table-column>
    </el-table>
    <div class="table-footer"><p>修改资料后需重新审核。</p><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10,20,50]" layout="total, prev, pager, next, sizes" @current-change="load" @size-change="search" /></div>
  </el-card>
  <el-dialog v-model="editing" :title="editId ? '编辑车行' : '新增车行'" width="520px" destroy-on-close :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
    <el-alert v-if="editId" title="保存后将重新进入待审核状态。" type="info" :closable="false" class="form-alert" />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving">
      <el-form-item label="车行名称" prop="name"><el-input v-model="form.name" maxlength="100" /></el-form-item>
      <el-form-item label="许可证号" prop="licenseNo"><el-input v-model="form.licenseNo" maxlength="100" /></el-form-item>
      <el-form-item label="资质到期日"><el-date-picker v-model="form.licenseExpireAt" type="date" value-format="YYYY-MM-DD" placeholder="通过审核前须填写有效日期" style="width:100%" /></el-form-item>
      <el-form-item label="联系电话" prop="contactPhone"><el-input v-model="form.contactPhone" maxlength="30" /></el-form-item>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="editing = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存并送审</el-button></template>
  </el-dialog>
  <el-dialog v-model="auditOpen" title="车行资质审核" width="520px" :close-on-click-modal="false" :close-on-press-escape="!auditSaving" :show-close="!auditSaving">
    <h3>{{ auditing?.name }}</h3><p class="form-alert">许可证：{{ auditing?.licenseNo }} · 到期日：{{ auditing?.licenseExpireAt || '未填写' }}</p>
    <el-form label-position="top" :disabled="auditSaving"><el-form-item label="审核结果"><el-radio-group v-model="auditForm.status"><el-radio value="APPROVED">通过</el-radio><el-radio value="REJECTED">驳回</el-radio></el-radio-group></el-form-item><el-form-item :label="auditForm.status === 'REJECTED' ? '驳回原因（必填）' : '审核备注'"><el-input v-model="auditForm.remark" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item></el-form>
    <template #footer><el-button :disabled="auditSaving" @click="auditOpen = false">取消</el-button><el-button type="primary" :loading="auditSaving" @click="audit">提交审核</el-button></template>
  </el-dialog>
  <el-drawer v-model="drawer" :title="selected?.name" size="85%" destroy-on-close>
    <template v-if="selected"><div class="company-summary"><el-tag :type="auditTypes[selected.auditStatus]">{{ auditLabels[selected.auditStatus] }}</el-tag><span>{{ selected.contactPhone }}</span><span>资质到期：{{ selected.licenseExpireAt || '未填写' }}</span></div><el-alert v-if="selected.auditRemark" :title="`审核备注：${selected.auditRemark}`" type="info" :closable="false" class="form-alert" /><el-tabs v-model="activeTab"><el-tab-pane label="车辆资料" name="vehicles"><VehicleManager :company-id="selected.id" /></el-tab-pane><el-tab-pane label="司机 / 护士" name="staff" lazy><StaffManager :company-id="selected.id" /></el-tab-pane><el-tab-pane label="调度账号" name="accounts" lazy><DispatcherManager :company-id="selected.id" /></el-tab-pane></el-tabs></template>
  </el-drawer>
</template>
