<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { query, vehicleBase, vehicleLabels, type Page, type Vehicle, type VehicleInput } from '../api/business'
import { request, messageOf } from '../api/http'

const props = defineProps<{ companyId?: number }>()
const rows = ref<Vehicle[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const keyword = ref('')
const status = ref('')
const loading = ref(false)
const error = ref('')
const editing = ref(false)
const editId = ref<number | null>(null)
const saving = ref(false)
const deleting = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<VehicleInput>({ plateNo: '', serviceLevel: 'BASIC', status: 'IDLE' })
const rules: FormRules = { plateNo: [{ required: true, whitespace: true, message: '请输入车牌号', trigger: 'blur' }, { max: 20, message: '最多 20 个字符', trigger: 'blur' }] }
let requestId = 0
async function load() {
  const id = ++requestId
  loading.value = true; error.value = ''
  try {
    const result = await request<Page<Vehicle>>(`${vehicleBase(props.companyId)}?${query({ page: page.value, size: size.value, keyword: keyword.value, status: status.value })}`)
    if (id !== requestId) return
    rows.value = result.items; total.value = result.total
    if (page.value > 1 && !result.items.length) { page.value--; await load() }
  } catch (e) { if (id === requestId) { rows.value = []; total.value = 0; error.value = messageOf(e) } }
  finally { if (id === requestId) loading.value = false }
}
function search() { page.value = 1; void load() }
function edit(row?: Vehicle) {
  editId.value = row?.id ?? null
  Object.assign(form, { plateNo: row?.plateNo || '', serviceLevel: row?.serviceLevel || 'BASIC', status: row?.status === 'MAINTENANCE' ? 'MAINTENANCE' : 'IDLE' })
  editing.value = true
}
async function save() {
  if (saving.value || !(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try {
    await request(`${vehicleBase(props.companyId)}${editId.value ? `/${editId.value}` : ''}`, editId.value ? 'PUT' : 'POST', form)
    editing.value = false; ElMessage.success('车辆资料已保存'); await load()
  } catch (e) { ElMessage.error(messageOf(e)) }
  finally { saving.value = false }
}
async function remove(row: Vehicle) {
  try { await ElMessageBox.confirm(`确定删除车辆 ${row.plateNo}？有关联业务的车辆无法删除。`, '删除车辆', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) }
  catch { return }
  deleting.value = row.id
  try { await request(`${vehicleBase(props.companyId)}/${row.id}`, 'DELETE'); ElMessage.success('车辆已删除'); await load() }
  catch (e) { ElMessage.error(messageOf(e)) }
  finally { deleting.value = null }
}
watch(() => props.companyId, () => { page.value = 1; keyword.value = ''; status.value = ''; void load() }, { immediate: true })
</script>

<template>
  <div class="toolbar">
    <form class="filter-form" @submit.prevent="search"><el-input v-model="keyword" aria-label="搜索车牌号" placeholder="搜索车牌号" clearable maxlength="100" /><el-select v-model="status" aria-label="车辆状态" placeholder="全部状态" clearable @change="search"><el-option v-for="(label, value) in vehicleLabels" :key="value" :label="label" :value="value" /></el-select><el-button native-type="submit" :loading="loading">查询</el-button></form>
    <el-button type="primary" @click="edit()">新增车辆</el-button>
  </div>
  <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-alert"><el-button text @click="load">重试</el-button></el-alert>
  <el-table v-loading="loading" :data="rows" empty-text="暂无车辆，请新增车辆资料" stripe>
    <el-table-column prop="plateNo" label="车牌号" min-width="140" />
    <el-table-column label="服务等级" min-width="120"><template #default="{ row }">{{ row.serviceLevel === 'MONITORING' ? '监护型' : '基础型' }}</template></el-table-column>
    <el-table-column label="车辆状态" min-width="120"><template #default="{ row }"><el-tag :type="row.status === 'IDLE' ? 'success' : row.status === 'TASK' ? 'warning' : 'info'">{{ vehicleLabels[row.status as keyof typeof vehicleLabels] }}</el-tag></template></el-table-column>
    <el-table-column label="操作" width="160" fixed="right"><template #default="{ row }"><el-button link type="primary" :disabled="row.status === 'TASK'" @click="edit(row)">编辑</el-button><el-button link type="danger" :disabled="row.status === 'TASK' || deleting !== null" :loading="deleting === row.id" @click="remove(row)">删除</el-button></template></el-table-column>
  </el-table>
  <div class="table-footer"><p>执行任务中的车辆由履约流程维护状态。</p><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10, 20, 50]" layout="total, prev, pager, next, sizes" @current-change="load" @size-change="search" /></div>
  <el-dialog v-model="editing" :title="editId ? '编辑车辆' : '新增车辆'" width="480px" destroy-on-close :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving" @submit.prevent="save">
      <el-form-item label="车牌号" prop="plateNo"><el-input v-model="form.plateNo" maxlength="20" placeholder="请输入车牌号" /></el-form-item>
      <el-form-item label="服务等级" required><el-radio-group v-model="form.serviceLevel"><el-radio value="BASIC">基础型</el-radio><el-radio value="MONITORING">监护型</el-radio></el-radio-group></el-form-item>
      <el-form-item label="车辆状态" required><el-radio-group v-model="form.status"><el-radio value="IDLE">空闲</el-radio><el-radio value="MAINTENANCE">维修中</el-radio></el-radio-group></el-form-item>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="editing = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
  </el-dialog>
</template>
