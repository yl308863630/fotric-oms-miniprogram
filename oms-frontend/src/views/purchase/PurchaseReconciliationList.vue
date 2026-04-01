<template>
  <div class="doc-page mobile-list-layout">
    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="对账单号">
          <el-input v-model="filters.billNo" placeholder="请输入对账单号" clearable />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="filters.supplier" placeholder="请输入供应商" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 160px">
            <el-option label="已对账" value="已对账" />
            <el-option label="已收票" value="已收票" />
            <el-option label="待付款" value="待付款" />
            <el-option label="部分付款" value="部分付款" />
            <el-option label="已付款" value="已付款" />
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
          生成进项发票
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
        <el-table-column prop="billNo" label="采购对账单号" min-width="220" />
        <el-table-column prop="supplier" label="供应商" min-width="180" />
        <el-table-column prop="reconciliationDate" label="对账日期" width="120" />
        <el-table-column prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag>{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="总金额" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
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

    <el-dialog v-model="detailVisible" title="采购对账单详情" width="1080px">
      <div v-if="detailData">
        <div class="detail-summary">
          <div>单号：{{ detailData.billNo }}</div>
          <div>供应商：{{ detailData.supplier || '-' }}</div>
          <div>金额：{{ formatMoney(detailData.totalAmount) }}</div>
          <div>状态：{{ detailData.status || '-' }}</div>
        </div>
        <div class="detail-section">
          <div class="detail-section-head">
            <span class="detail-section-title">商品明细</span>
            <div class="detail-section-summary">
              <span>共 {{ detailItems.length }} 行商品</span>
              <span>合计数量：{{ detailTotalQuantity }}</span>
              <span>合计金额：{{ formatMoney(detailData.totalAmount) }}</span>
            </div>
          </div>
          <el-table :data="detailItems" border stripe max-height="420">
            <el-table-column type="index" label="#" width="60" align="center" />
            <el-table-column prop="purchaseOrderNo" label="采购单号" min-width="180" show-overflow-tooltip />
            <el-table-column prop="omsOrderNo" label="OMS订单号" min-width="180" show-overflow-tooltip />
            <el-table-column prop="model" label="型号" min-width="220" show-overflow-tooltip />
            <el-table-column prop="quantity" label="数量" width="90" align="center" />
            <el-table-column label="含税采购单价" width="140" align="right">
              <template #default="{ row }">{{ formatMoney(resolveUnitPrice(row)) }}</template>
            </el-table-column>
            <el-table-column prop="lineAmount" label="含税采购总额" width="150" align="right">
              <template #default="{ row }">{{ formatMoney(row.lineAmount) }}</template>
            </el-table-column>
            <el-table-column prop="orderStatus" label="采购状态" width="120" align="center" />
          </el-table>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="generateInvoiceVisible" title="生成进项发票" width="520px">
      <el-form :model="generateInvoiceForm" label-width="100px">
        <el-form-item label="收票日期">
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
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
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({
  billNo: '',
  supplier: '',
  status: ''
})

const generateInvoiceForm = reactive({
  invoiceDate: new Date().toISOString().slice(0, 10),
  invoiceNumber: '',
  attachmentUrl: '',
  remark: ''
})

const detailItems = computed(() => Array.isArray(detailData.value?.items) ? detailData.value.items : [])
const detailTotalQuantity = computed(() => detailItems.value.reduce((sum: number, item: any) => sum + Number(item?.quantity || 0), 0))

const fetchList = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/purchase-reconciliations', {
      params: {
        billNo: filters.billNo,
        supplier: filters.supplier,
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

const resetSearch = () => {
  filters.billNo = ''
  filters.supplier = ''
  filters.status = ''
  handleSearch()
}

const showDetail = async (row: any) => {
  detailData.value = await request.get(`/purchase-reconciliations/${row.id}`)
  detailVisible.value = true
}

const openGenerateInvoiceDialog = () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择采购对账单')
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
    ElMessage.warning('请先选择采购对账单')
    return
  }
  try {
    submitting.value = true
    await request.post('/purchase-input-invoices/generate', {
      reconciliationIds: selectedRows.value.map(row => row.id),
      invoiceDate: generateInvoiceForm.invoiceDate,
      invoiceNumber: generateInvoiceForm.invoiceNumber,
      attachmentUrl: generateInvoiceForm.attachmentUrl,
      remark: generateInvoiceForm.remark
    })
    ElMessage.success('进项发票生成成功')
    generateInvoiceVisible.value = false
    selectedRows.value = []
    await fetchList()
    router.push('/invoice/input')
  } finally {
    submitting.value = false
  }
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

const handleInvoiceUploadSuccess = (response: any, file: File) => {
  if (response.success || response.url) {
    generateInvoiceForm.attachmentUrl = response.url || file.name
    ElMessage.success('进项发票附件上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const clearInvoiceAttachment = () => {
  generateInvoiceForm.attachmentUrl = ''
}

const previewFile = (fileUrl: string) => {
  if (!fileUrl) return
  const fullUrl = fileUrl.startsWith('http') ? fileUrl : `${apiBase()}${fileUrl}`
  window.open(fullUrl, '_blank')
}

const voidDoc = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确认作废采购对账单 ${row.billNo} 吗？`, '作废确认', { type: 'warning' })
    await request.post(`/purchase-reconciliations/${row.id}/void`)
    ElMessage.success('采购对账单已作废')
    await fetchList()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || error?.message || '作废失败')
    }
  }
}

const formatMoney = (value: any) => `¥${Number(value || 0).toFixed(2)}`
const resolveUnitPrice = (row: any) => {
  const quantity = Number(row?.quantity || 0)
  if (!quantity) return 0
  return Number(row?.lineAmount || 0) / quantity
}

const applyRouteQuery = () => {
  const q = route.query as Record<string, string>
  filters.billNo = String(q.billNo || '')
  filters.supplier = String(q.supplier || '')
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

.detail-section {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 16px;
  background: #fff;
}

.detail-section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.detail-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.detail-section-summary {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  color: #606266;
  font-size: 13px;
}

.attachment-edit-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}
</style>
