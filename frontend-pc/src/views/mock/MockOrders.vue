<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { request, messageOf } from '../../api/http'
import type { TransferOrder } from '../../api/orders'
import OrderBoard from '../../components/OrderBoard.vue'

const enabled = ref<boolean | null>(null)
const checking = ref(false)
const error = ref('')
const creating = ref(false)
const saving = ref(false)
const board = ref<InstanceType<typeof OrderBoard>>()
const formRef = ref<FormInstance>()
const initial = () => ({ userName: '', userPhone: '', originAddress: '', destAddress: '', serviceLevel: 'BASIC', patientNote: '' })
const form = reactive(initial())
const rules: FormRules = {
  userName: [{ required: true, whitespace: true, message: '请输入联系人姓名', trigger: 'blur' }],
  userPhone: [{ required: true, pattern: /^[0-9+() -]{5,30}$/, message: '请输入有效联系电话', trigger: 'blur' }],
  originAddress: [{ required: true, whitespace: true, message: '请输入转运起点', trigger: 'blur' }],
  destAddress: [{ required: true, whitespace: true, message: '请输入转运终点', trigger: 'blur' }, { validator: (_rule, value, cb) => cb(value.trim() === form.originAddress.trim() ? new Error('起点与终点不能相同') : undefined), trigger: 'blur' }],
}
async function checkFeature() {
  checking.value = true; error.value = ''
  try { enabled.value = (await request<{ mockOrdersEnabled: boolean }>('/admin/features')).mockOrdersEnabled }
  catch (e) { error.value = messageOf(e) }
  finally { checking.value = false }
}
function open() { Object.assign(form, initial()); creating.value = true }
async function create() {
  if (saving.value || !(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try { const order = await request<TransferOrder>('/mock/orders', 'POST', form); creating.value = false; ElMessage.success('转运需求已提交'); await board.value?.created(order) }
  catch (e) { ElMessage.error(messageOf(e)) }
  finally { saving.value = false }
}
onMounted(checkFeature)
</script>

<template>
  <section class="page-heading"><div><div class="eyebrow">TRANSFER REQUEST</div><h1>模拟下单</h1><p>提交转运需求，等待车行报价，再选择合适的转运服务。</p></div><el-button v-if="enabled" type="primary" @click="open">新建转运需求</el-button></section>
  <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-alert"><el-button text @click="checkFeature">重试</el-button></el-alert>
  <el-card v-if="enabled === false" shadow="never"><el-empty description="模拟下单尚未启用，请联系系统管理员。" /></el-card>
  <el-skeleton v-if="checking && enabled === null" :rows="5" animated />
  <template v-if="enabled"><el-alert title="此处仅显示当前账号创建的模拟订单，报价和选标结果会保存到业务数据库。" type="info" show-icon :closable="false" class="form-alert" /><OrderBoard ref="board" mode="MOCK" /></template>
  <el-dialog v-model="creating" title="新建转运需求" width="600px" destroy-on-close :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving"><div class="form-columns"><el-form-item label="联系人姓名" prop="userName"><el-input v-model="form.userName" maxlength="50" autocomplete="off" /></el-form-item><el-form-item label="联系电话" prop="userPhone"><el-input v-model="form.userPhone" maxlength="30" /></el-form-item></div><el-form-item label="转运起点" prop="originAddress"><el-input v-model="form.originAddress" maxlength="255" placeholder="医院、院区及详细上车位置" /></el-form-item><el-form-item label="转运终点" prop="destAddress"><el-input v-model="form.destAddress" maxlength="255" placeholder="目的地医院或详细地址" /></el-form-item><el-form-item label="服务等级" required><el-radio-group v-model="form.serviceLevel"><el-radio value="BASIC">基础型</el-radio><el-radio value="MONITORING">监护型</el-radio></el-radio-group></el-form-item><el-form-item label="患者备注"><el-input v-model="form.patientNote" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="行动能力、陪同人数与转运注意事项" /></el-form-item></el-form>
    <template #footer><el-button :disabled="saving" @click="creating = false">取消</el-button><el-button type="primary" :loading="saving" @click="create">提交转运需求</el-button></template>
  </el-dialog>
</template>
