<template>
  <div class="doc-page mobile-list-layout">
    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="发票单号">
          <el-input v-model="filters.billNo" placeholder="请输入发票单号" clearable />
        </el-form-item>
        <el-form-item label="甲方抬头">
          <el-input v-model="filters.platformName" placeholder="请输入甲方抬头" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 160px">
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
        <el-button type="primary" :disabled="selectedRows.length === 0" @click="openGenerateSettlementDialog">
          生成销售结算单
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
        <el-table-column prop="billNo" label="销项发票单号" min-width="220" />
        <el-table-column prop="invoiceNumber" label="发票号码" min-width="180" />
        <el-table-column prop="platformName" label="甲方抬头" min-width="180" />
        <el-table-column prop="invoiceDate" label="开票日期" width="120" />
        <el-table-column label="发票附件" width="110" align="center">
          <template #default="{ row }">
            <el-button v-if="row.attachmentUrl" link type="primary" @click="previewFile(row.attachmentUrl)">查看附件</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
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

    <el-dialog v-model="detailVisible" title="销项发票详情" width="860px">
      <div v-if="detailData">
        <div class="detail-summary">
          <div>单号：{{ detailData.billNo }}</div>
          <div>发票号：{{ detailData.invoiceNumber || '-' }}</div>
          <div>甲方：{{ detailData.platformName || '-' }}</div>
          <div>金额：{{ formatMoney(detailData.totalAmount) }}</div>
          <div>
            发票附件：
            <el-button v-if="detailData.attachmentUrl" link type="primary" @click="previewFile(detailData.attachmentUrl)">查看附件</el-button>
            <span v-else>-</span>
          </div>
        </div>
        <el-table :data="detailData.items || []" border stripe max-height="420">
          <el-table-column prop="reconciliationBillNo" label="销售对账单号" min-width="220" />
          <el-table-column prop="platformName" label="甲方抬头" min-width="180" />
          <el-table-column prop="lineAmount" label="金额" width="140" align="right">
            <template #default="{ row }">{{ formatMoney(row.lineAmount) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <el-dialog v-model="generateSettlementVisible" title="生成销售结算单" width="520px">
      <el-form :model="generateSettlementForm" label-width="100px">
        <el-form-item label="结算日期">
          <el-date-picker v-model="generateSettlementForm.settlementDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="generateSettlementForm.remark" type="textarea" :rows="3" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateSettlementVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitGenerateSettlement">确定生成</el-button>
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
const generateSettlementVisible = ref(false)
const tableData = ref<any[]>([])
const selectedRows = ref<any[]>([])
const detailData = ref<any>(null)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({
  billNo: '',
  platformName: '',
  status: ''
})

const generateSettlementForm = reactive({
  settlementDate: new Date().toISOString().slice(0, 10),
  remark: ''
})

const fetchList = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/sales-output-invoices', {
      params: {
        billNo: filters.billNo,
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
  if (value === '已作废') return 'info'
  if (value === '待回款' || value === '部分回款') return 'warning'
  return 'success'
}

const resetSearch = () => {
  filters.billNo = ''
  filters.platformName = ''
  filters.status = ''
  handleSearch()
}

const showDetail = async (row: any) => {
  detailData.value = await request.get(`/sales-output-invoices/${row.id}`)
  detailVisible.value = true
}

const openGenerateSettlementDialog = () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择销项发票')
    return
  }
  generateSettlementVisible.value = true
}

const submitGenerateSettlement = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择销项发票')
    return
  }
  try {
    submitting.value = true
    await request.post('/sales-settlements/generate', {
      invoiceIds: selectedRows.value.map(row => row.id),
      settlementDate: generateSettlementForm.settlementDate,
      remark: generateSettlementForm.remark
    })
    ElMessage.success('销售结算单生成成功')
    generateSettlementVisible.value = false
    selectedRows.value = []
    await fetchList()
    router.push('/sales/settlement')
  } finally {
    submitting.value = false
  }
}

const voidDoc = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确认作废销项发票 ${row.billNo} 吗？`, '作废确认', { type: 'warning' })
    await request.post(`/sales-output-invoices/${row.id}/void`)
    ElMessage.success('销项发票已作废')
    await fetchList()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || error?.message || '作废失败')
    }
  }
}

const formatMoney = (value: any) => `¥${Number(value || 0).toFixed(2)}`

const previewFile = (fileUrl: string) => {
  if (!fileUrl) return
  const fullUrl = fileUrl.startsWith('http') ? fileUrl : `${apiBase()}${fileUrl}`
  window.open(fullUrl, '_blank')
}

const applyRouteQuery = () => {
  const q = route.query as Record<string, string>
  filters.billNo = String(q.billNo || '')
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
