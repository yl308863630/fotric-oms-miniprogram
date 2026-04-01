<template>
  <div class="doc-page mobile-list-layout">
    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="对账单号">
          <el-input v-model="filters.billNo" placeholder="请输入对账单号" clearable />
        </el-form-item>
        <el-form-item label="OMS订单号">
          <el-input v-model="filters.omsOrderNo" placeholder="请输入OMS订单号" clearable />
        </el-form-item>
        <el-form-item label="甲方抬头">
          <el-input v-model="filters.platformName" placeholder="请输入甲方抬头" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 160px">
            <el-option label="已对账" value="已对账" />
            <el-option label="已开票" value="已开票" />
            <el-option label="待回款" value="待回款" />
            <el-option label="部分回款" value="部分回款" />
            <el-option label="已结算" value="已结算" />
            <el-option label="已作废" value="已作废" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <div class="table-ops">
        <el-button type="primary" :disabled="selectedRows.length === 0" @click="openGenerateInvoiceDialog">
          生成销项发票
        </el-button>
      </div>
      <el-table v-loading="loading" :data="tableData" border stripe @selection-change="selectedRows = $event">
        <el-table-column type="selection" width="55" />
        <el-table-column label="操作" width="90" fixed="left">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button v-if="row.status !== '已作废'" link type="danger" @click="voidDoc(row)">作废</el-button>
          </template>
        </el-table-column>
        <el-table-column label="对账单号" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ getDisplayedReconciliationNo(row) }}
          </template>
        </el-table-column>
        <el-table-column label="OMS/甲方订单号" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.orderNoSummary || row.omsOrderNoSummary || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="platformName" label="甲方抬头" min-width="180" />
        <el-table-column prop="partyATitle" label="真实甲方抬头" min-width="180" />
        <el-table-column prop="reconciliationDate" label="对账日期" width="120" />
        <el-table-column prop="status" label="状态" width="98" align="center">
          <template #default="{ row }">
            <div v-if="getStatusLayers(row.status).length > 1" class="status-layer-stack">
              <el-tag
                v-for="layer in getStatusLayers(row.status)"
                :key="`${row.id}-${layer.label}`"
                :type="layer.type"
                size="small"
                effect="plain"
              >
                {{ layer.label }}
              </el-tag>
            </div>
            <el-tag v-else :type="getStatusTag(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="总金额" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="对账附件" width="110" align="center">
          <template #default="{ row }">
            <el-button v-if="row.attachmentUrl" link type="primary" @click="previewFile(row.attachmentUrl)">查看附件</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
      </el-table>
      <div class="pagination">
        <el-pagination
          background
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="销售对账单详情" width="980px">
      <div v-if="detailData">
        <div class="detail-summary">
          <div>单号：{{ getDisplayedReconciliationNo(detailData) }}</div>
          <div>系统单号：{{ detailData.billNo }}</div>
          <div>甲方：{{ detailData.platformName || '-' }}</div>
          <div>金额：{{ formatMoney(detailData.totalAmount) }}</div>
          <div>状态：{{ detailData.status || '-' }}</div>
        </div>
        <el-form :model="reconciliationForm" label-width="110px" class="detail-form">
          <el-form-item label="甲方对账单号">
            <el-input
              v-model="reconciliationForm.platformReconciliationNo"
              placeholder="生成时默认带入；如需补录或修正可在此填写"
              :disabled="detailData.status === '已作废'"
              clearable
            />
          </el-form-item>
          <el-form-item label="对账附件">
            <div class="attachment-edit-row">
              <el-upload
                action="/api/upload"
                :show-file-list="false"
                :before-upload="beforeUpload"
                :on-success="handleAttachmentUploadSuccess"
                :disabled="detailData.status === '已作废'"
              >
                <el-button type="primary" :disabled="detailData.status === '已作废'">上传文件</el-button>
              </el-upload>
              <el-button v-if="reconciliationForm.attachmentUrl" link type="primary" @click="previewFile(reconciliationForm.attachmentUrl)">
                查看附件
              </el-button>
              <el-button
                v-if="reconciliationForm.attachmentUrl && detailData.status !== '已作废'"
                link
                type="danger"
                @click="clearAttachment"
              >
                删除
              </el-button>
              <span class="upload-tip">生成时未上传的附件，可在此补录或替换；支持图片、PDF、Word、Excel</span>
            </div>
          </el-form-item>
          <el-form-item label="备注">
            <el-input
              v-model="reconciliationForm.remark"
              type="textarea"
              :rows="2"
              clearable
              :disabled="detailData.status === '已作废'"
            />
          </el-form-item>
        </el-form>
        <el-table :data="detailData.items || []" border stripe max-height="420">
          <el-table-column label="OMS/甲方订单号" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              {{ [row.omsOrderNo, row.platformOrderNo].filter(Boolean).join(' / ') || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="productName" label="商品名称" min-width="160" />
          <el-table-column prop="model" label="型号" min-width="140" />
          <el-table-column prop="quantity" label="数量" width="90" />
          <el-table-column prop="orderStatus" label="订单状态" width="120" />
          <el-table-column prop="lineAmount" label="金额" width="130" align="right">
            <template #default="{ row }">{{ formatMoney(row.lineAmount) }}</template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="!detailData || detailData.status === '已作废'"
          @click="saveReconciliationMeta"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="generateInvoiceVisible" title="生成销项发票" width="520px">
      <el-form :model="generateInvoiceForm" label-width="100px">
        <el-form-item label="开票日期">
          <el-date-picker v-model="generateInvoiceForm.invoiceDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="发票号码">
          <el-input v-model="generateInvoiceForm.invoiceNumber" placeholder="可选，不填则系统生成" clearable />
        </el-form-item>
        <el-form-item label="发票上传">
          <div class="attachment-edit-row">
            <el-upload
              action="/api/upload"
              :show-file-list="false"
              :before-upload="beforeUpload"
              :on-success="handleInvoiceUploadSuccess"
            >
              <el-button type="primary">上传文件</el-button>
            </el-upload>
            <el-button v-if="generateInvoiceForm.attachmentUrl" link type="primary" @click="previewFile(generateInvoiceForm.attachmentUrl)">
              查看附件
            </el-button>
            <el-button v-if="generateInvoiceForm.attachmentUrl" link type="danger" @click="clearInvoiceAttachment">
              删除
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="generateInvoiceForm.remark" type="textarea" :rows="3" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateInvoiceVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitGenerateInvoice">确定生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import request from '../../utils/request'
import { apiBase } from '@/utils/apiBase'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const detailVisible = ref(false)
const generateInvoiceVisible = ref(false)
const tableData = ref<any[]>([])
const selectedRows = ref<any[]>([])
const detailData = ref<any>(null)
const currentRow = ref<any>(null)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({
  billNo: '',
  omsOrderNo: '',
  platformName: '',
  status: ''
})

const generateInvoiceForm = reactive({
  invoiceDate: new Date().toISOString().slice(0, 10),
  invoiceNumber: '',
  attachmentUrl: '',
  remark: ''
})

const reconciliationForm = reactive({
  platformReconciliationNo: '',
  attachmentUrl: '',
  remark: ''
})

const fetchList = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/sales-reconciliations', {
      params: {
        billNo: filters.billNo,
        omsOrderNo: filters.omsOrderNo,
        platformName: filters.platformName,
        status: filters.status,
        page: currentPage.value - 1,
        size: pageSize.value
      }
    })
    tableData.value = res?.content || []
    total.value = res?.totalElements || 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchList()
}

function getStatusLayers(status: string) {
  const value = String(status || '').trim()
  if (value === '已开票') {
    return [
      { label: '已对账', type: 'success' },
      { label: '已开票', type: 'warning' }
    ]
  }
  if (value === '待回款' || value === '部分回款') {
    return [
      { label: '已开票', type: 'success' },
      { label: value, type: 'warning' }
    ]
  }
  if (value === '已结算') {
    return [
      { label: '已开票', type: 'success' },
      { label: '已结算', type: 'success' }
    ]
  }
  return []
}

function getStatusTag(status: string) {
  const value = String(status || '').trim()
  if (value === '已对账') return 'success'
  if (value === '已作废') return 'info'
  if (value === '待回款' || value === '部分回款') return 'warning'
  return 'success'
}

const resetSearch = () => {
  filters.billNo = ''
  filters.omsOrderNo = ''
  filters.platformName = ''
  filters.status = ''
  handleSearch()
}

const showDetail = async (row: any) => {
  currentRow.value = row
  detailData.value = await request.get(`/sales-reconciliations/${row.id}`)
  reconciliationForm.platformReconciliationNo = detailData.value?.platformReconciliationNo || ''
  reconciliationForm.attachmentUrl = detailData.value?.attachmentUrl || ''
  reconciliationForm.remark = detailData.value?.remark || ''
  detailVisible.value = true
}

const openGenerateInvoiceDialog = () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择销售对账单')
    return
  }
  const invalidRows = selectedRows.value.filter(row => String(row?.status || '').trim() !== '已对账')
  if (invalidRows.length > 0) {
    ElMessage.warning('仅“已对账”状态的销售对账单可以生成销项发票')
    return
  }
  generateInvoiceForm.invoiceDate = new Date().toISOString().slice(0, 10)
  generateInvoiceForm.invoiceNumber = ''
  generateInvoiceForm.attachmentUrl = ''
  generateInvoiceForm.remark = ''
  generateInvoiceVisible.value = true
}

const submitGenerateInvoice = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择销售对账单')
    return
  }
  const invalidRows = selectedRows.value.filter(row => String(row?.status || '').trim() !== '已对账')
  if (invalidRows.length > 0) {
    ElMessage.warning('仅“已对账”状态的销售对账单可以生成销项发票')
    return
  }
  try {
    submitting.value = true
    await request.post('/sales-output-invoices/generate', {
      reconciliationIds: selectedRows.value.map(row => row.id),
      invoiceDate: generateInvoiceForm.invoiceDate,
      invoiceNumber: generateInvoiceForm.invoiceNumber,
      attachmentUrl: generateInvoiceForm.attachmentUrl,
      remark: generateInvoiceForm.remark
    })
    ElMessage.success('销项发票生成成功')
    generateInvoiceVisible.value = false
    selectedRows.value = []
    generateInvoiceForm.invoiceNumber = ''
    generateInvoiceForm.attachmentUrl = ''
    generateInvoiceForm.remark = ''
    await fetchList()
    router.push('/invoice/output')
  } finally {
    submitting.value = false
  }
}

const handleInvoiceUploadSuccess = (response: any, file: File) => {
  if (response.success || response.url) {
    generateInvoiceForm.attachmentUrl = response.url || file.name
    ElMessage.success('发票附件上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const clearInvoiceAttachment = () => {
  generateInvoiceForm.attachmentUrl = ''
}

const beforeUpload = (file: File) => {
  const isImageOrDoc = file.type.includes('image') || file.type.includes('pdf') || file.type.includes('word') || file.name.endsWith('.doc') || file.name.endsWith('.docx') || file.type.includes('excel') || file.name.endsWith('.xls') || file.name.endsWith('.xlsx')
  if (!isImageOrDoc) {
    ElMessage.error('只能上传图片、PDF、Word或Excel文档!')
    return false
  }
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    ElMessage.error('文件大小不能超过10MB!')
    return false
  }
  return true
}

const handleAttachmentUploadSuccess = (response: any, file: File) => {
  if (response.success || response.url) {
    reconciliationForm.attachmentUrl = response.url || file.name
    ElMessage.success('对账附件上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const clearAttachment = () => {
  reconciliationForm.attachmentUrl = ''
}

const previewFile = (fileUrl: string) => {
  if (!fileUrl) return
  const fullUrl = fileUrl.startsWith('http') ? fileUrl : `${apiBase()}${fileUrl}`
  window.open(fullUrl, '_blank')
}

const saveReconciliationMeta = async () => {
  if (!currentRow.value?.id || !detailData.value) return
  try {
    submitting.value = true
    detailData.value = await request.patch(`/sales-reconciliations/${currentRow.value.id}`, {
      platformReconciliationNo: reconciliationForm.platformReconciliationNo,
      attachmentUrl: reconciliationForm.attachmentUrl,
      remark: reconciliationForm.remark
    })
    ElMessage.success('销售对账单更新成功')
    await fetchList()
  } finally {
    submitting.value = false
  }
}

const voidDoc = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确认作废销售对账单 ${row.billNo} 吗？`, '作废确认', { type: 'warning' })
    await request.post(`/sales-reconciliations/${row.id}/void`)
    ElMessage.success('销售对账单已作废')
    await fetchList()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || error?.message || '作废失败')
    }
  }
}

const formatMoney = (value: any) => `¥${Number(value || 0).toFixed(2)}`

const getDisplayedReconciliationNo = (row: any) => {
  return String(row?.platformReconciliationNo || row?.billNo || '-').trim() || '-'
}

const applyRouteQuery = () => {
  const q = route.query as Record<string, string>
  filters.billNo = String(q.billNo || '')
  filters.omsOrderNo = String(q.omsOrderNo || '')
  filters.platformName = String(q.platformName || '')
  filters.status = String(q.status || '')
  currentPage.value = 1
}

onMounted(() => {
  applyRouteQuery()
  fetchList()
})

watch(() => route.query, () => {
  applyRouteQuery()
  fetchList()
})
</script>

<style scoped>
.table-ops {
  margin-bottom: 12px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.detail-form {
  margin-bottom: 16px;
}

.attachment-edit-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

.upload-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.status-layer-stack {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.status-layer-stack .el-tag {
  min-width: 58px;
  justify-content: center;
}
</style>
