<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { request, messageOf } from '../api/http'
import { accepting, money, orderBase, orderLabels, orderTime, quoteLabels, type OrderDetail, type OrderMode, type Quote } from '../api/orders'
import { usePolling } from '../composables/usePolling'
import DispatchForm from './DispatchForm.vue'

const props = defineProps<{ id: number; mode: OrderMode }>()
const emit = defineEmits<{ changed: [] }>()
const detail = ref<OrderDetail | null>(null)
const error = ref('')
const loading = ref(false)
const selecting = ref(false)
const quoteOpen = ref(false)
const saving = ref(false)
const operating = ref(false)
const dispatchBusy = ref(false)
const driverAction = computed(() => {
  const steps: Record<string, { action: string; label: string; prompt: string }> = {
    DISPATCHED: { action: 'depart', label: '确认出发', prompt: '确认已驾驶分配车辆，出发前往上车点？' },
    EN_ROUTE: { action: 'arrive', label: '到达上车点', prompt: '确认已到达订单上车点？' },
    ARRIVED: { action: 'pickup', label: '确认患者上车', prompt: '确认患者已上车，开始前往转运终点？' },
    ON_BOARD: { action: 'complete', label: '确认送达', prompt: '确认患者已安全送达终点？完成后将释放车辆和人员。' },
  }
  return props.mode === 'DRIVER' && detail.value ? steps[detail.value.order.status] : undefined
})
const formRef = ref<FormInstance>()
const form = reactive({ amount: undefined as number | undefined, remark: '' })
const rules: FormRules = { amount: [{ required: true, message: '请输入报价金额', trigger: 'blur' }, { validator: (_rule, value, cb) => cb(typeof value !== 'number' || !Number.isFinite(value) || value < .01 || value > 9999999999.99 ? new Error('请输入 0.01 至 9999999999.99 的金额') : undefined), trigger: 'blur' }] }
const companyView = computed(() => props.mode === 'COMPANY' || props.mode === 'DISPATCH')
const ownQuote = computed(() => companyView.value ? detail.value?.quotes[0] : undefined)
const canBid = computed(() => props.mode === 'COMPANY' && detail.value && accepting(detail.value.order.status) && (!ownQuote.value || (ownQuote.value.status === 'PENDING' && ownQuote.value.eligible)))
let sequence = 0
async function load() {
  const seq = ++sequence
  loading.value = true; error.value = ''
  try { const value = await request<OrderDetail>(`${orderBase(props.mode)}/${props.id}`); if (seq === sequence) detail.value = value }
  catch (e) { if (seq === sequence) error.value = messageOf(e) }
  finally { if (seq === sequence) loading.value = false }
}
function openBid() { form.amount = ownQuote.value?.amount; form.remark = ownQuote.value?.remark || ''; quoteOpen.value = true }
async function saveBid() {
  if (saving.value || !(await formRef.value?.validate().catch(() => false))) return
  saving.value = true
  try { await request(`/company/orders/${props.id}/quotes`, 'POST', form); quoteOpen.value = false; ElMessage.success('报价已保存'); await load(); emit('changed') }
  catch (e) { ElMessage.error(messageOf(e)); await load() }
  finally { saving.value = false }
}
async function select(quote: Quote) {
  if (selecting.value) return
  selecting.value = true
  try {
    try { await ElMessageBox.confirm(`选择“${quote.companyName}”，确认转运金额 ${money(quote.amount)}？`, '确认车行与报价', { type: 'warning', confirmButtonText: '确认选标', cancelButtonText: '再看看', closeOnClickModal: false }) }
    catch { return }
    try { await request(`/mock/orders/${props.id}/select`, 'POST', { quoteId: quote.id, amount: quote.amount }); ElMessage.success('订单已确认'); await load(); emit('changed') }
    catch (e) { ElMessage.error(messageOf(e)); await load() }
  } finally { selecting.value = false }
}
async function advance() {
  const step = driverAction.value
  if (!step || operating.value) return
  operating.value = true
  try {
    try { await ElMessageBox.confirm(step.prompt, step.label, { confirmButtonText: step.label, cancelButtonText: '取消', closeOnClickModal: false }) } catch { return }
    try { await request(`/driver/tasks/${props.id}/${step.action}`, 'POST'); ElMessage.success('任务进度已更新'); await load(); emit('changed') }
    catch (e) { ElMessage.error(messageOf(e)); await load() }
  } finally { operating.value = false }
}
async function dispatched() { await load(); emit('changed') }
watch(() => props.id, () => { detail.value = null; void load() }, { immediate: true })
usePolling(() => { if (!loading.value && !saving.value && !selecting.value && !operating.value && !dispatchBusy.value && !quoteOpen.value) void load() })
onUnmounted(() => { sequence++ })
</script>

<template>
  <div v-loading="loading && !detail">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="form-alert"><el-button text @click="load">重试</el-button></el-alert>
    <template v-if="detail">
      <div class="order-detail-heading"><div><span class="eyebrow">{{ detail.order.orderNo }}</span><h2>{{ orderLabels[detail.order.status] }}</h2></div><el-button :loading="loading" :disabled="selecting || saving || operating || dispatchBusy" @click="load">刷新详情</el-button></div>
      <div v-if="detail.order.status === 'CONFIRMED'" class="confirmation-panel"><strong>已选定 {{ detail.order.assignedCompanyName }}</strong><span>确认金额 {{ money(detail.order.finalAmount) }} · 等待车行派车</span></div>
      <el-descriptions :column="1" border class="order-descriptions"><el-descriptions-item label="联系人">{{ detail.order.userName }} · {{ detail.order.userPhone }}</el-descriptions-item><el-descriptions-item label="转运起点">{{ detail.order.originAddress }}</el-descriptions-item><el-descriptions-item label="转运终点">{{ detail.order.destAddress }}</el-descriptions-item><el-descriptions-item label="服务等级">{{ detail.order.serviceLevel === 'BASIC' ? '基础型' : '监护型' }}</el-descriptions-item><el-descriptions-item label="患者备注">{{ detail.order.patientNote || '无' }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ orderTime(detail.order.createdAt) }}</el-descriptions-item></el-descriptions>
      <el-descriptions v-if="detail.order.assignedVehicleId" :column="1" border class="order-descriptions"><el-descriptions-item label="派车车辆">{{ detail.order.assignedPlateNo }}</el-descriptions-item><el-descriptions-item label="任务司机">{{ detail.order.assignedDriverName }}</el-descriptions-item><el-descriptions-item label="随车护士">{{ detail.order.assignedNurseName || '未配备' }}</el-descriptions-item></el-descriptions>
      <DispatchForm v-if="mode === 'DISPATCH' && detail.order.status === 'CONFIRMED' && !error" :order="detail.order" @changed="dispatched" @busy="dispatchBusy = $event" />
      <div v-if="driverAction" class="confirmation-panel"><strong>下一步：{{ driverAction.label }}</strong><el-button type="primary" :loading="operating" :disabled="!!error || loading" @click="advance">{{ driverAction.label }}</el-button></div>
      <template v-if="mode !== 'DRIVER'">
      <div class="section-heading"><h2>{{ companyView ? '本车行报价' : '收到的报价' }}</h2><el-button v-if="canBid" type="primary" :disabled="!!error" @click="openBid">{{ ownQuote ? '修改报价' : '提交报价' }}</el-button><span v-else-if="accepting(detail.order.status)">每 10 秒刷新</span></div>
      <el-empty v-if="!detail.quotes.length" :description="companyView ? '尚未提交报价' : '等待符合条件的车行报价'" :image-size="70" />
      <div v-for="quote in detail.quotes" :key="quote.id" class="quote-card" :class="{ winner: quote.status === 'WON' }">
        <div class="quote-card-top"><strong>{{ quote.companyName }}</strong><el-tag :type="quote.status === 'WON' ? 'success' : quote.status === 'LOST' ? 'info' : 'warning'">{{ quoteLabels[quote.status] }}</el-tag></div>
        <div class="quote-amount">{{ money(quote.amount) }}</div><p>{{ quote.remark || '暂无报价说明' }}</p><small>更新于 {{ orderTime(quote.updatedAt) }}</small>
        <p v-if="!quote.eligible && accepting(detail.order.status)" class="quote-unavailable">当前资质或车辆条件不满足，暂不可选</p>
        <el-button v-if="mode === 'MOCK' && detail.order.status === 'QUOTING' && quote.status === 'PENDING'" type="primary" :disabled="!quote.eligible || !!error || selecting" :loading="selecting" @click="select(quote)">选择该报价</el-button>
      </div>
      </template>
      <div class="section-heading"><h2>订单记录</h2><span>时间按本地时区显示</span></div>
      <el-timeline><el-timeline-item v-for="log in detail.timeline" :key="log.id" :timestamp="orderTime(log.createdAt)" :type="log.eventType === 'STATUS_CHANGE' ? 'primary' : 'info'"><strong>{{ log.eventType === 'FEE_CHANGE' ? '报价留痕' : orderLabels[log.toStatus] }}</strong><p>{{ log.remark }}</p></el-timeline-item></el-timeline>
    </template>
  </div>
  <el-dialog v-model="quoteOpen" :title="ownQuote ? '修改车行报价' : '提交车行报价'" width="480px" append-to-body destroy-on-close :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="saving"><el-form-item label="报价金额（元）" prop="amount"><el-input-number v-model="form.amount" :min=".01" :max="9999999999.99" :precision="2" :step="10" :controls="false" style="width:100%" /></el-form-item><el-form-item label="报价说明"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明报价包含的服务内容" /></el-form-item></el-form>
    <p>订单确认前可修改报价；确认后不可更改。</p>
    <template #footer><el-button :disabled="saving" @click="quoteOpen = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveBid">保存报价</el-button></template>
  </el-dialog>
</template>
