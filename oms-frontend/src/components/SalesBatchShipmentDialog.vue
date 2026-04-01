<template>
  <el-dialog
    :model-value="modelValue"
    title="整单批量发货"
    width="1120px"
    @close="emit('update:modelValue', false)"
  >
    <div v-if="targetRows.length > 0" class="batch-shipment-dialog">
      <el-alert
        :title="`将对 ${targetRows.length} 条商品行写入同一份发货要求与物流信息，SN 仍按商品行单独维护`"
        type="info"
        :closable="false"
        show-icon
      />

      <div class="batch-group-summary">
        <span>主单/整单标识：{{ batchGroupLabel }}</span>
        <span>OMS订单号：{{ omsSummary }}</span>
        <span>商品行数：{{ targetRows.length }}</span>
      </div>

      <el-form :model="sharedForm" label-width="130px" class="mt-16">
        <div class="section-card">
          <div class="section-title">发货要求</div>
          <el-row :gutter="16">
            <el-col :span="24">
              <el-form-item label="甲方送货单模板">
                <div class="upload-row">
                  <el-upload
                    action="/api/upload"
                    :show-file-list="false"
                    :before-upload="beforeUpload"
                    :on-success="handleDeliveryNoteUploadSuccess"
                  >
                    <el-button type="primary">上传文件</el-button>
                  </el-upload>
                  <el-button v-if="sharedForm.deliveryNoteUrl" link type="primary" @click="previewFile(sharedForm.deliveryNoteUrl)">网页预览</el-button>
                </div>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="需要签收单回传">
                <el-switch v-model="sharedForm.needReceiptReturn" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="送货单打印数量">
                <el-input-number v-model="sharedForm.printQuantity" :min="1" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="24">
              <el-form-item label="哪些快递不能用">
                <el-input v-model="sharedForm.forbiddenCouriers" type="textarea" :rows="2" placeholder="请输入禁用快递说明" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="打印箱唛">
                <el-switch v-model="sharedForm.printBoxLabel" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="打印128条码">
                <el-switch v-model="sharedForm.printBarcode128" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row v-if="sharedForm.printBoxLabel" :gutter="16">
            <el-col :span="24">
              <el-form-item label="箱唛附件">
                <div class="upload-stack">
                  <el-upload
                    action="/api/upload"
                    :show-file-list="false"
                    :before-upload="beforeUpload"
                    :on-success="handleBoxLabelUploadSuccess"
                    multiple
                  >
                    <el-button type="primary">上传箱唛</el-button>
                  </el-upload>
                  <div v-if="boxLabelFileList.length > 0" class="file-chip-wrap">
                    <span v-for="item in boxLabelFileList" :key="item.url" class="file-chip">
                      <el-button link type="primary" @click="previewFile(item.url)">{{ item.name }}</el-button>
                      <el-button link type="danger" @click="removeBoxLabel(item.url)">删除</el-button>
                    </span>
                  </div>
                </div>
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <div class="section-card mt-16">
          <div class="section-title">物流信息</div>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="配送方式">
                <el-select v-model="sharedForm.deliveryMethod" style="width: 100%" placeholder="请选择">
                  <el-option label="商家联系物流" value="商家联系物流" />
                  <el-option label="自主车辆配送" value="自主车辆配送" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <template v-if="sharedForm.deliveryMethod === '商家联系物流'">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="物流公司">
                  <el-input v-model="sharedForm.logisticsCompany" placeholder="请输入物流公司" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="母单物流单号">
                  <el-input v-model="sharedForm.trackingNumber" placeholder="请输入母单物流单号" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row v-if="sharedForm.needReceiptReturn" :gutter="16">
              <el-col :span="12">
                <el-form-item label="回单物流单号">
                  <el-input v-model="sharedForm.returnReceiptTrackingNumber" placeholder="请输入回单物流单号" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="回单收件人手机">
                  <el-input v-model="sharedForm.returnReceiptReceiverPhone" placeholder="顺丰查轨迹需手机号后四位" />
                </el-form-item>
              </el-col>
            </el-row>
          </template>

          <template v-if="sharedForm.deliveryMethod === '自主车辆配送'">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="送货车牌号">
                  <el-input v-model="sharedForm.vehiclePlate" placeholder="请输入送货车牌号" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="物流联系人">
                  <el-input v-model="sharedForm.logisticsContact" placeholder="请输入物流联系人" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="物流联系方式">
                  <el-input v-model="sharedForm.logisticsPhone" placeholder="请输入物流联系方式" />
                </el-form-item>
              </el-col>
            </el-row>
          </template>

          <el-row :gutter="16">
            <el-col :span="24">
              <el-form-item label="发货地址">
                <el-input v-model="sharedForm.shippingAddress" type="textarea" :rows="2" placeholder="请输入发货地址" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="发货联系人">
                <el-input v-model="sharedForm.shippingContact" placeholder="请输入发货联系人" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="发货联系方式">
                <el-input v-model="sharedForm.shippingPhone" placeholder="请输入发货联系方式" />
              </el-form-item>
            </el-col>
          </el-row>
        </div>
      </el-form>

      <div class="line-section">
        <div class="line-section-head">
          <span class="line-section-title">商品行 SN 录入</span>
          <span class="line-section-tip">一行一个商品，支持换行或逗号分隔多个 SN；保存时会与当前行数量做校验。</span>
        </div>
        <el-table v-loading="loading" :data="lineRows" border stripe max-height="420">
          <el-table-column type="index" label="#" width="60" align="center" />
          <el-table-column prop="omsOrderNo" label="OMS订单号" min-width="170" show-overflow-tooltip />
          <el-table-column prop="model" label="型号" min-width="180" show-overflow-tooltip />
          <el-table-column prop="quantity" label="数量" width="80" align="center" />
          <el-table-column label="当前SN数" width="100" align="center">
            <template #default="{ row }">{{ serialCountMap[String(row.id)] || 0 }}</template>
          </el-table-column>
          <el-table-column label="本次录入SN" min-width="320">
            <template #default="{ row }">
              <el-input
                v-model="serialDrafts[String(row.id)]"
                type="textarea"
                :rows="3"
                placeholder="请输入SN，支持换行或逗号分隔"
              />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { apiBase } from '@/utils/apiBase'

const props = defineProps<{
  modelValue: boolean
  rows: any[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'saved'): void
}>()

const loading = ref(false)
const submitting = ref(false)
const serialDrafts = ref<Record<string, string>>({})
const serialCountMap = ref<Record<string, number>>({})
const boxLabelFileList = ref<Array<{ name: string; url: string }>>([])

const sharedForm = reactive({
  deliveryNoteUrl: '',
  needReceiptReturn: true,
  printQuantity: 1,
  forbiddenCouriers: '',
  printBoxLabel: false,
  printBarcode128: false,
  boxLabelUrls: '',
  deliveryMethod: '商家联系物流',
  logisticsCompany: '',
  trackingNumber: '',
  returnReceiptTrackingNumber: '',
  returnReceiptReceiverPhone: '',
  vehiclePlate: '',
  logisticsContact: '',
  logisticsPhone: '',
  shippingAddress: '',
  shippingContact: '',
  shippingPhone: ''
})

const targetRows = computed(() => Array.isArray(props.rows) ? props.rows.filter(Boolean) : [])
const lineRows = computed(() => targetRows.value)
const batchGroupLabel = computed(() => {
  const first = targetRows.value[0]
  if (!first) return '-'
  if (first.masterId) return `主单 ${first.masterId}`
  const oms = String(first.omsOrderNo || '').trim()
  return oms || `订单 ${first.id || '-'}`
})
const omsSummary = computed(() => Array.from(new Set(
  targetRows.value.map(row => String(row?.omsOrderNo || '').trim()).filter(Boolean)
)).join(' / ') || '-')

watch(() => props.modelValue, async (visible) => {
  if (!visible) return
  await initDialog()
}, { immediate: true })

watch(() => sharedForm.deliveryMethod, (value) => {
  if (value === '商家联系物流') {
    sharedForm.vehiclePlate = ''
    sharedForm.logisticsContact = ''
    sharedForm.logisticsPhone = ''
    return
  }
  sharedForm.logisticsCompany = ''
  sharedForm.trackingNumber = ''
  sharedForm.returnReceiptTrackingNumber = ''
  sharedForm.returnReceiptReceiverPhone = ''
})

watch(() => sharedForm.needReceiptReturn, (value) => {
  if (!value) {
    sharedForm.returnReceiptTrackingNumber = ''
    sharedForm.returnReceiptReceiverPhone = ''
  }
})

const initDialog = async () => {
  serialCountMap.value = {}
  serialDrafts.value = {}
  boxLabelFileList.value = []
  resetSharedForm()
  if (targetRows.value.length === 0) return
  loading.value = true
  try {
    const first = targetRows.value[0]
    sharedForm.deliveryNoteUrl = String(first?.deliveryNoteUrl || '').trim()
    sharedForm.needReceiptReturn = Boolean(first?.needReceiptSlip ?? true)
    sharedForm.printQuantity = Number(first?.deliveryNotePrintQuantity || 1) || 1
    sharedForm.forbiddenCouriers = String(first?.forbiddenCouriers || '').trim()
    sharedForm.printBoxLabel = Boolean(first?.printBoxLabel)
    sharedForm.printBarcode128 = Boolean(first?.printBarcode128)
    sharedForm.boxLabelUrls = String(first?.boxLabelUrls || '').trim()
    sharedForm.deliveryMethod = String(first?.deliveryMethod || '商家联系物流').trim() || '商家联系物流'
    sharedForm.logisticsCompany = String(first?.logisticsCompany || '').trim()
    sharedForm.trackingNumber = String(first?.trackingNumber || '').trim()
    sharedForm.returnReceiptTrackingNumber = String(first?.returnReceiptTrackingNumber || '').trim()
    sharedForm.returnReceiptReceiverPhone = String(first?.returnReceiptReceiverPhone || '').trim()

    if (sharedForm.boxLabelUrls) {
      boxLabelFileList.value = sharedForm.boxLabelUrls.split(',').map((url, index) => ({
        name: `箱唛${index + 1}`,
        url: url.trim()
      })).filter(item => item.url)
    }

    await Promise.all(targetRows.value.map(async (row) => {
      const key = String(row.id)
      const serialItems: any = await request.get('/sales-serial-items', { params: { salesOrderId: row.id } })
      const items = Array.isArray(serialItems) ? serialItems : []
      serialCountMap.value[key] = items.length
      serialDrafts.value[key] = buildInitialSerialText(row, items)
      if (!sharedForm.shippingAddress) sharedForm.shippingAddress = String(row?.shippingAddress || '').trim()
      if (!sharedForm.shippingContact) sharedForm.shippingContact = String(row?.shippingContact || '').trim()
      if (!sharedForm.shippingPhone) sharedForm.shippingPhone = String(row?.shippingPhone || '').trim()
    }))
  } finally {
    loading.value = false
  }
}

const resetSharedForm = () => {
  sharedForm.deliveryNoteUrl = ''
  sharedForm.needReceiptReturn = true
  sharedForm.printQuantity = 1
  sharedForm.forbiddenCouriers = ''
  sharedForm.printBoxLabel = false
  sharedForm.printBarcode128 = false
  sharedForm.boxLabelUrls = ''
  sharedForm.deliveryMethod = '商家联系物流'
  sharedForm.logisticsCompany = ''
  sharedForm.trackingNumber = ''
  sharedForm.returnReceiptTrackingNumber = ''
  sharedForm.returnReceiptReceiverPhone = ''
  sharedForm.vehiclePlate = ''
  sharedForm.logisticsContact = ''
  sharedForm.logisticsPhone = ''
  sharedForm.shippingAddress = ''
  sharedForm.shippingContact = ''
  sharedForm.shippingPhone = ''
}

const buildInitialSerialText = (row: any, items: any[]) => {
  if (items.length > 0) {
    return items.map(item => String(item?.snCode || '').trim()).filter(Boolean).join('\n')
  }
  return String(row?.snCode || '').trim().split(/[\n,，;；]+/).map(item => item.trim()).filter(Boolean).join('\n')
}

const parseSnCodes = (value: string) => Array.from(new Set(
  String(value || '')
    .split(/[\n,，;；]+/)
    .map(item => item.trim())
    .filter(Boolean)
))

const beforeUpload = (file: File) => {
  const isImageOrDoc = file.type.includes('image') || file.type.includes('pdf') || file.type.includes('word') || file.name.endsWith('.doc') || file.name.endsWith('.docx') || file.type.includes('excel') || file.name.endsWith('.xls') || file.name.endsWith('.xlsx') || file.name.endsWith('.svg')
  if (!isImageOrDoc) {
    ElMessage.error('只能上传图片、PDF、Word、Excel或SVG文档')
    return false
  }
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    ElMessage.error('文件大小不能超过10MB')
    return false
  }
  return true
}

const handleDeliveryNoteUploadSuccess = (response: any, file: File) => {
  if (response?.url) {
    sharedForm.deliveryNoteUrl = response.url || file.name
    ElMessage.success('送货单模板上传成功')
    return
  }
  ElMessage.error(response?.message || '上传失败')
}

const handleBoxLabelUploadSuccess = (response: any, file: File) => {
  if (response?.url) {
    boxLabelFileList.value.push({ name: file.name, url: response.url })
    updateBoxLabelUrls()
    ElMessage.success('箱唛附件上传成功')
    return
  }
  ElMessage.error(response?.message || '上传失败')
}

const removeBoxLabel = (url: string) => {
  boxLabelFileList.value = boxLabelFileList.value.filter(item => item.url !== url)
  updateBoxLabelUrls()
}

const updateBoxLabelUrls = () => {
  sharedForm.boxLabelUrls = boxLabelFileList.value.map(item => item.url).join(',')
}

const previewFile = (fileUrl: string) => {
  if (!fileUrl) return
  const fullUrl = fileUrl.startsWith('http') ? fileUrl : `${apiBase()}${fileUrl}`
  window.open(fullUrl, '_blank')
}

const submit = async () => {
  if (targetRows.value.length === 0) {
    ElMessage.warning('未选择需要批量发货的商品行')
    return
  }
  if (!String(sharedForm.deliveryMethod || '').trim()) {
    ElMessage.warning('请先选择配送方式')
    return
  }
  if (sharedForm.deliveryMethod === '商家联系物流' && !String(sharedForm.trackingNumber || '').trim()) {
    ElMessage.warning('请填写母单物流单号')
    return
  }
  if (sharedForm.deliveryMethod === '自主车辆配送' && !String(sharedForm.vehiclePlate || '').trim()) {
    ElMessage.warning('请填写送货车牌号')
    return
  }
  for (const row of targetRows.value) {
    const snCodes = parseSnCodes(serialDrafts.value[String(row.id)] || '')
    const quantity = Number(row?.quantity || 0)
    if (quantity > 0 && snCodes.length > quantity) {
      ElMessage.warning(`${row?.model || row?.omsOrderNo || row?.id} 的 SN 数量超出订单数量`)
      return
    }
  }

  submitting.value = true
  try {
    await request.post('/order-shipments/batch', {
      deliveryNoteUrl: sharedForm.deliveryNoteUrl,
      needReceiptReturn: sharedForm.needReceiptReturn,
      printQuantity: sharedForm.printQuantity,
      forbiddenCouriers: sharedForm.forbiddenCouriers,
      printBoxLabel: sharedForm.printBoxLabel,
      printBarcode128: sharedForm.printBarcode128,
      boxLabelUrls: sharedForm.boxLabelUrls,
      deliveryMethod: sharedForm.deliveryMethod,
      logisticsCompany: sharedForm.deliveryMethod === '商家联系物流' ? sharedForm.logisticsCompany : '',
      trackingNumber: sharedForm.deliveryMethod === '商家联系物流' ? sharedForm.trackingNumber : '',
      returnReceiptTrackingNumber: sharedForm.deliveryMethod === '商家联系物流' && sharedForm.needReceiptReturn ? sharedForm.returnReceiptTrackingNumber : '',
      returnReceiptReceiverPhone: sharedForm.deliveryMethod === '商家联系物流' && sharedForm.needReceiptReturn ? sharedForm.returnReceiptReceiverPhone : '',
      vehiclePlate: sharedForm.deliveryMethod === '自主车辆配送' ? sharedForm.vehiclePlate : '',
      logisticsContact: sharedForm.deliveryMethod === '自主车辆配送' ? sharedForm.logisticsContact : '',
      logisticsPhone: sharedForm.deliveryMethod === '自主车辆配送' ? sharedForm.logisticsPhone : '',
      shippingAddress: sharedForm.shippingAddress,
      shippingContact: sharedForm.shippingContact,
      shippingPhone: sharedForm.shippingPhone,
      items: targetRows.value.map(row => ({
        salesOrderId: row.id,
        snCodes: parseSnCodes(serialDrafts.value[String(row.id)] || '')
      }))
    })
    ElMessage.success(`已完成 ${targetRows.value.length} 条商品行的批量发货保存`)
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.mt-16 {
  margin-top: 16px;
}

.batch-group-summary {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 13px;
  color: #606266;
  margin-top: 12px;
}

.section-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  background: #fff;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.upload-row {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.upload-stack {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.file-chip-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.file-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.line-section {
  margin-top: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  background: #fff;
}

.line-section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.line-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.line-section-tip {
  font-size: 12px;
  color: #909399;
}
</style>
