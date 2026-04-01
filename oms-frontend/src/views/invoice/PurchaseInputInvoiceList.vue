<template>
  <div class="doc-page mobile-list-layout">
    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="发票单号">
          <el-input v-model="filters.billNo" placeholder="请输入发票单号" clearable />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="filters.supplier" placeholder="请输入供应商" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 160px">
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
      <el-alert
        v-if="dashboardEntryHint"
        class="dashboard-entry-hint"
        title="Dashboard 入口提示"
        :description="dashboardEntryHint"
        type="warning"
        :closable="false"
        show-icon
      />
    </el-card>

    <el-card>
      <div class="table-ops">
        <el-button type="primary" :disabled="selectedRows.length === 0" @click="openGenerateSettlementDialog">
          生成采购结算单
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
        <el-table-column prop="billNo" label="进项发票单号" min-width="220" />
        <el-table-column prop="invoiceNumber" label="发票号码" min-width="180" />
        <el-table-column prop="supplier" label="供应商" min-width="180" />
        <el-table-column prop="invoiceDate" label="收票日期" width="120" />
        <el-table-column label="发票附件" width="110" align="center">
          <template #default="{ row }">
            <el-button v-if="row.attachmentUrl" link type="primary" @click="previewFile(row.attachmentUrl)">查看附件</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag>{{ row.status }}</el-tag>
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

    <el-dialog v-model="detailVisible" title="进项发票详情" width="860px">
      <div v-if="detailData">
        <div class="detail-summary">
          <div>单号：{{ detailData.billNo }}</div>
          <div>发票号：{{ detailData.invoiceNumber || '-' }}</div>
          <div>供应商：{{ detailData.supplier || '-' }}</div>
          <div>金额：{{ formatMoney(detailData.totalAmount) }}</div>
          <div>
            发票附件：
            <el-button v-if="detailData.attachmentUrl" link type="primary" @click="previewFile(detailData.attachmentUrl)">查看附件</el-button>
            <span v-else>-</span>
          </div>
        </div>
        <el-table :data="detailData.items || []" border stripe max-height="420">
          <el-table-column prop="reconciliationBillNo" label="采购对账单号" min-width="220" />
          <el-table-column prop="supplier" label="供应商" min-width="180" />
          <el-table-column prop="lineAmount" label="金额" width="140" align="right">
            <template #default="{ row }">{{ formatMoney(row.lineAmount) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <el-dialog v-model="generateSettlementVisible" title="生成采购结算单" width="520px">
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
const generateSettlementVisible = ref(false)
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
const dashboardEntryHint = computed(() => {
  const entry = String(route.query.dashboardCard || '').trim()
  if (entry === 'pendingInboundReturn') {
    return '当前从 Dashboard 的“待处理进项退票”进入。该卡片尚未接入专属退票维度，现临时落到进项发票列表供人工排查。'
  }
  return ''
})

const generateSettlementForm = reactive({
  settlementDate: new Date().toISOString().slice(0, 10),
  remark: ''
})

const fetchList = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/purchase-input-invoices', {
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
  detailData.value = await request.get(`/purchase-input-invoices/${row.id}`)
  detailVisible.value = true
}

const openGenerateSettlementDialog = () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择进项发票')
    return
  }
  generateSettlementVisible.value = true
}

const submitGenerateSettlement = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择进项发票')
    return
  }
  try {
    submitting.value = true
    await request.post('/purchase-settlements/generate', {
      invoiceIds: selectedRows.value.map(row => row.id),
      settlementDate: generateSettlementForm.settlementDate,
      remark: generateSettlementForm.remark
    })
    ElMessage.success('采购结算单生成成功')
    generateSettlementVisible.value = false
    selectedRows.value = []
    await fetchList()
    router.push('/purchase/settlement')
  } finally {
    submitting.value = false
  }
}

const voidDoc = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确认作废进项发票 ${row.billNo} 吗？`, '作废确认', { type: 'warning' })
    await request.post(`/purchase-input-invoices/${row.id}/void`)
    ElMessage.success('进项发票已作废')
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

.dashboard-entry-hint {
  margin-top: 8px;
}
</style>
