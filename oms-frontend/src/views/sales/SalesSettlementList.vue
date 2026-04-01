<template>
  <div class="doc-page mobile-list-layout">
    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="结算单号">
          <el-input v-model="filters.billNo" placeholder="请输入结算单号" clearable />
        </el-form-item>
        <el-form-item label="甲方抬头">
          <el-input v-model="filters.platformName" placeholder="请输入甲方抬头" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 160px">
            <el-option label="待回款/部分回款" value="待回款,部分回款" />
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
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column label="操作" width="140" fixed="left">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button link type="success" @click="openRefundDialog(row)">更新回款</el-button>
            <el-button v-if="row.status !== '已作废'" link type="danger" @click="voidDoc(row)">作废</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="billNo" label="销售结算单号" min-width="220" />
        <el-table-column prop="platformName" label="甲方抬头" min-width="180" />
        <el-table-column prop="settlementDate" label="结算日期" width="120" />
        <el-table-column prop="refundStatus" label="回款状态" width="96" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getRefundTag(row.refundStatus)">{{ row.refundStatus || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="单据状态" width="96" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getStatusTag(row.status)">{{ row.status || '-' }}</el-tag>
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

    <el-dialog v-model="detailVisible" title="销售结算单详情" width="860px">
      <div v-if="detailData">
        <div class="detail-summary">
          <div>单号：{{ detailData.billNo }}</div>
          <div>甲方：{{ detailData.platformName || '-' }}</div>
          <div>回款状态：{{ detailData.refundStatus || '-' }}</div>
          <div>金额：{{ formatMoney(detailData.totalAmount) }}</div>
        </div>
        <el-table :data="detailData.items || []" border stripe max-height="420">
          <el-table-column prop="invoiceBillNo" label="销项发票单号" min-width="220" />
          <el-table-column prop="reconciliationBillNo" label="销售对账单号" min-width="220" />
          <el-table-column prop="lineAmount" label="金额" width="140" align="right">
            <template #default="{ row }">{{ formatMoney(row.lineAmount) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <el-dialog v-model="refundVisible" title="更新回款状态" width="520px">
      <el-form :model="refundForm" label-width="100px">
        <el-form-item label="回款状态">
          <el-select v-model="refundForm.refundStatus" style="width: 100%">
            <el-option label="未回款" value="未回款" />
            <el-option label="部分回款" value="部分回款" />
            <el-option label="已回款" value="已回款" />
          </el-select>
        </el-form-item>
        <el-form-item label="回款日期">
          <el-date-picker v-model="refundForm.refundDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="附件地址">
          <el-input v-model="refundForm.attachmentUrl" placeholder="可选" clearable />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="refundForm.remark" type="textarea" :rows="3" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitRefund">保存</el-button>
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
const refundVisible = ref(false)
const tableData = ref<any[]>([])
const detailData = ref<any>(null)
const currentRow = ref<any>(null)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({
  billNo: '',
  platformName: '',
  status: ''
})

const refundForm = reactive({
  refundStatus: '未回款',
  refundDate: '',
  attachmentUrl: '',
  remark: ''
})

const fetchList = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/sales-settlements', {
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

function getRefundTag(status: string) {
  const value = String(status || '').trim()
  if (value === '已回款') return 'success'
  if (value === '部分回款') return 'warning'
  if (value === '已作废') return 'info'
  return 'warning'
}

function getStatusTag(status: string) {
  const value = String(status || '').trim()
  if (value === '已结算') return 'success'
  if (value === '已作废') return 'info'
  if (value === '部分回款') return 'warning'
  return 'warning'
}

const resetSearch = () => {
  filters.billNo = ''
  filters.platformName = ''
  filters.status = ''
  handleSearch()
}

const showDetail = async (row: any) => {
  detailData.value = await request.get(`/sales-settlements/${row.id}`)
  detailVisible.value = true
}

const openRefundDialog = (row: any) => {
  currentRow.value = row
  refundForm.refundStatus = row.refundStatus || '未回款'
  refundForm.refundDate = row.refundDate || ''
  refundForm.attachmentUrl = row.attachmentUrl || ''
  refundForm.remark = row.remark || ''
  refundVisible.value = true
}

const submitRefund = async () => {
  if (!currentRow.value?.id) return
  try {
    submitting.value = true
    await request.post(`/sales-settlements/${currentRow.value.id}/refund-status`, { ...refundForm })
    ElMessage.success('回款状态更新成功')
    refundVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

const voidDoc = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确认作废销售结算单 ${row.billNo} 吗？`, '作废确认', { type: 'warning' })
    await request.post(`/sales-settlements/${row.id}/void`)
    ElMessage.success('销售结算单已作废')
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
