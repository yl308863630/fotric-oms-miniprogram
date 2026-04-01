<template>
  <div class="doc-page mobile-list-layout">
    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="结算单号">
          <el-input v-model="filters.billNo" placeholder="请输入结算单号" clearable />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="filters.supplier" placeholder="请输入供应商" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 160px">
            <el-option label="待付款/部分付款" value="待付款,部分付款" />
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
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column label="操作" width="140" fixed="left">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button link type="success" @click="openPaymentDialog(row)">更新付款</el-button>
            <el-button v-if="row.status !== '已作废'" link type="danger" @click="voidDoc(row)">作废</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="billNo" label="采购结算单号" min-width="220" />
        <el-table-column prop="supplier" label="供应商" min-width="180" />
        <el-table-column prop="settlementDate" label="结算日期" width="120" />
        <el-table-column prop="paymentStatus" label="付款状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag>{{ row.paymentStatus || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="单据状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag>{{ row.status || '-' }}</el-tag>
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

    <el-dialog v-model="detailVisible" title="采购结算单详情" width="860px">
      <div v-if="detailData">
        <div class="detail-summary">
          <div>单号：{{ detailData.billNo }}</div>
          <div>供应商：{{ detailData.supplier || '-' }}</div>
          <div>付款状态：{{ detailData.paymentStatus || '-' }}</div>
          <div>金额：{{ formatMoney(detailData.totalAmount) }}</div>
        </div>
        <el-table :data="detailData.items || []" border stripe max-height="420">
          <el-table-column prop="invoiceBillNo" label="进项发票单号" min-width="220" />
          <el-table-column prop="reconciliationBillNo" label="采购对账单号" min-width="220" />
          <el-table-column prop="lineAmount" label="金额" width="140" align="right">
            <template #default="{ row }">{{ formatMoney(row.lineAmount) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <el-dialog v-model="paymentVisible" title="更新付款状态" width="520px">
      <el-form :model="paymentForm" label-width="100px">
        <el-form-item label="付款状态">
          <el-select v-model="paymentForm.paymentStatus" style="width: 100%">
            <el-option label="待付款" value="待付款" />
            <el-option label="部分付款" value="部分付款" />
            <el-option label="已付款" value="已付款" />
          </el-select>
        </el-form-item>
        <el-form-item label="付款日期">
          <el-date-picker v-model="paymentForm.paymentDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="附件地址">
          <el-input v-model="paymentForm.attachmentUrl" placeholder="可选" clearable />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="paymentForm.remark" type="textarea" :rows="3" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="paymentVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPayment">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import request from '../../utils/request'

const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const detailVisible = ref(false)
const paymentVisible = ref(false)
const tableData = ref<any[]>([])
const detailData = ref<any>(null)
const currentRow = ref<any>(null)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({
  billNo: '',
  supplier: '',
  status: ''
})

const paymentForm = reactive({
  paymentStatus: '待付款',
  paymentDate: '',
  attachmentUrl: '',
  remark: ''
})

const fetchList = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/purchase-settlements', {
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
  detailData.value = await request.get(`/purchase-settlements/${row.id}`)
  detailVisible.value = true
}

const openPaymentDialog = (row: any) => {
  currentRow.value = row
  paymentForm.paymentStatus = row.paymentStatus || '待付款'
  paymentForm.paymentDate = row.paymentDate || ''
  paymentForm.attachmentUrl = row.attachmentUrl || ''
  paymentForm.remark = row.remark || ''
  paymentVisible.value = true
}

const submitPayment = async () => {
  if (!currentRow.value?.id) return
  try {
    submitting.value = true
    await request.post(`/purchase-settlements/${currentRow.value.id}/payment-status`, { ...paymentForm })
    ElMessage.success('付款状态更新成功')
    paymentVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

const voidDoc = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确认作废采购结算单 ${row.billNo} 吗？`, '作废确认', { type: 'warning' })
    await request.post(`/purchase-settlements/${row.id}/void`)
    ElMessage.success('采购结算单已作废')
    await fetchList()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || error?.message || '作废失败')
    }
  }
}

const formatMoney = (value: any) => `¥${Number(value || 0).toFixed(2)}`

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
</style>
