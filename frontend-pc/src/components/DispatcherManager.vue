<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { request, messageOf } from '../api/http'
import type { User } from '../api/session'

const props = defineProps<{ companyId: number }>()
const rows = ref<User[]>([])
const error = ref('')
const loading = ref(false)
const saving = ref(false)
const creating = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ name: '', phone: '', username: '', password: '' })
const rules: FormRules = {
  name: [{ required: true, whitespace: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ required: true, pattern: /^[0-9+() -]{5,30}$/, message: '请输入有效联系电话', trigger: 'blur' }],
  username: [{ required: true, pattern: /^[A-Za-z0-9_.-]{3,50}$/, message: '3–50 位字母、数字、点、横线或下划线', trigger: 'blur' }],
  password: [{ required: true, min: 10, max: 72, message: '密码至少 10 个字符', trigger: 'blur' }, { validator: (_rule, value, callback) => callback(new TextEncoder().encode(value).length > 72 ? new Error('密码最多 72 字节') : undefined), trigger: 'blur' }],
}
async function load() {
  loading.value = true; error.value = ''
  try { rows.value = await request<User[]>(`/admin/companies/${props.companyId}/dispatchers`) }
  catch (e) { error.value = messageOf(e) }
  finally { loading.value = false }
}
function open() { Object.assign(form, { name: '', phone: '', username: '', password: '' }); creating.value = true }
async function save() {
  if (saving.value || !(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try { await request(`/admin/companies/${props.companyId}/dispatchers`, 'POST', form); form.password = ''; creating.value = false; ElMessage.success('调度账号已创建'); await load() }
  catch (e) { ElMessage.error(messageOf(e)) }
  finally { saving.value = false }
}
onMounted(load)
</script>
<template>
  <div class="toolbar"><p>调度员登录后可维护本车行车辆。</p><el-button type="primary" @click="open">创建调度账号</el-button></div>
  <el-alert v-if="error" :title="error" type="error" :closable="false" class="form-alert"><el-button text @click="load">重试</el-button></el-alert>
  <el-table v-loading="loading" :data="rows" empty-text="暂无调度账号"><el-table-column prop="name" label="姓名" /><el-table-column prop="username" label="登录账号" /></el-table>
  <el-dialog v-model="creating" title="创建调度账号" width="480px" destroy-on-close append-to-body :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" @closed="form.password = ''">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving">
      <el-form-item label="姓名" prop="name"><el-input v-model="form.name" maxlength="50" /></el-form-item>
      <el-form-item label="联系电话" prop="phone"><el-input v-model="form.phone" maxlength="30" /></el-form-item>
      <el-form-item label="登录账号" prop="username"><el-input v-model="form.username" maxlength="50" autocomplete="off" /></el-form-item>
      <el-form-item label="初始密码" prop="password"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" maxlength="72" placeholder="至少 10 个字符" /></el-form-item>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="creating = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">创建账号</el-button></template>
  </el-dialog>
</template>
