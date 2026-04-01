<template>
  <div class="invoice-list mobile-list-layout">
    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm">
        <el-form-item label="对账日期">
          <el-date-picker
            v-model="filterForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="对账单号">
          <el-input v-model="filterForm.billNo" placeholder="请输入对账单号" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchInvoices">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button type="success" @click="handleExport">导出 Excel</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-tabs v-model="activeStatus" class="status-tabs" @tab-click="handleSearch">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="待提交" name="draft" />
      <el-tab-pane label="待对账" name="pending" />
      <el-tab-pane label="已对账" name="confirmed" />
      <el-tab-pane label="已结清" name="settled" />
    </el-tabs>

    <el-card>
      <div class="table-ops">
        <el-button type="primary" @click="openCreateInvoiceDialog">发起对账</el-button>
        <el-button type="success" plain>批量导出</el-button>
        <el-button type="warning" plain>批量打印</el-button>
        <el-popover placement="bottom" width="500" trigger="click">
          <template #reference>
            <el-button>编辑表头</el-button>
          </template>
          <div class="column-settings">
            <div class="column-header">
              <span>列设置</span>
              <el-button size="small" @click="moveToTop">置顶选中</el-button>
            </div>
            <el-scrollbar height="300px">
              <draggable
                v-model="orderedColumns"
                item-key="label"
                handle=".drag-handle"
                @end="onDragEnd"
                ghost-class="drag-ghost"
                chosen-class="drag-chosen"
              >
                <template #item="{ element }">
                  <div class="column-item" :class="{ 'hidden-column': !element.visible }">
                    <div class="drag-handle">≡</div>
                    <el-checkbox
                      v-model="element.visible"
                      :label="element.label"
                      @change="onColumnVisibilityChange(element.label, $event)"
                    >
                      {{ element.title }}
                    </el-checkbox>
                    <el-button
                      link
                      size="small"
                      @click="moveToTopByLabel(element.label)"
                      title="置顶"
                    >
                      ↑
                    </el-button>
                  </div>
                </template>
              </draggable>
            </el-scrollbar>
            <div class="settings-actions">
              <el-button size="small" @click="resetColumns">恢复默认</el-button>
              <el-button type="primary" size="small" @click="saveColumns">保存</el-button>
            </div>
          </div>
        </el-popover>
      </div>
      <div class="table-wrapper">
      <el-table :data="tableData" style="width: 100%" border stripe size="small">
        <el-table-column type="selection" width="55" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="showDetail(scope.row)">详情</el-button>
            <el-button 
              link 
              type="success" 
              v-if="scope.row.status === 'INVOICED'" 
              @click="handleGenerateSettlement(scope.row)"
            >生成结算单</el-button>
          </template>
        </el-table-column>
        <template v-for="column in orderedColumns" :key="column.label">
          <el-table-column
            v-if="column.visible && column.label !== '操作'"
            :prop="column.label"
            :label="column.title"
            :width="column.width"
            :min-width="column.minWidth"
            :fixed="column.fixed"
            show-overflow-tooltip
          >
            <template #default="scope" v-if="column.label === 'status'">
              <el-tag :type="getStatusType(scope.row[column.label])" size="small">{{ scope.row[column.label] }}</el-tag>
            </template>
            <template #default="scope" v-else-if="column.label === 'amount'">
              ¥{{ scope.row[column.label] }}
            </template>
            <template #default="scope" v-else>
              {{ scope.row[column.label] }}
            </template>
          </el-table-column>
        </template>
      </el-table>
      </div>
      <div class="pagination">
        <el-pagination
          background
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="detailVisible"
      title="对账单详情"
      width="85%"
      top="3vh"
      class="bill-detail-dialog"
    >
      <div v-if="currentBill" class="detail-content">
        <div class="steps-container">
          <el-steps :active="currentBill.activeStep" align-center>
            <el-step title="发起时间" :description="currentBill.stepInfo.step1" />
            <el-step title="确认时间" :description="currentBill.stepInfo.step2" />
            <el-step title="申请时间" :description="currentBill.stepInfo.step3" />
            <el-step title="开票时间" :description="currentBill.stepInfo.step4" />
            <el-step title="回执时间" :description="currentBill.stepInfo.step5" />
          </el-steps>
        </div>

        <div class="summary-bar">
          <div class="bill-info">
            <span class="label">甲方申请号：</span>
            <span class="value">{{ currentBill.platformApplyNo || '-' }}</span>
            <el-tag :type="getStatusType(currentBill.status)" size="small" class="status-tag">{{ currentBill.status }}</el-tag>
            <span class="label ml-20">项目名称：</span>
            <span class="value">{{ currentBill.projectName || '-' }}</span>
          </div>
          <div class="money-info">
            <span class="item">开票总额：<span class="price">¥{{ currentBill.amount }}</span></span>
            <template v-if="currentBill.status !== 'INVOICED' && currentBill.status !== 'SETTLED'">
              <el-select v-model="detailStatusNext" placeholder="推进状态" size="small" class="ml-20" style="width: 120px">
                <el-option label="待对账 PENDING" value="PENDING" />
                <el-option label="已对账 CONFIRMED" value="CONFIRMED" />
                <el-option label="待开票 APPLIED" value="APPLIED" />
                <el-option label="已开票 INVOICED" value="INVOICED" />
              </el-select>
              <el-button type="primary" size="small" class="ml-8" :loading="detailStatusUpdating" @click="updateInvoiceStatusInDetail">更新状态</el-button>
            </template>
          </div>
        </div>

        <el-row :gutter="20" class="info-grid">
          <el-col :span="6">
            <div class="info-section">
              <div class="section-title">开票信息</div>
              <div class="info-item"><span class="label">发票抬头：</span>{{ currentBill.buyerName }}</div>
              <div class="info-item"><span class="label">企业税号：</span>{{ currentBill.buyerTaxNo || '-' }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="info-section">
              <div class="section-title">销方信息</div>
              <div class="info-item"><span class="label">开户银行：</span>{{ currentBill.sellerBank || '-' }}</div>
              <div class="info-item"><span class="label">开票人：</span>{{ currentBill.creator || '系统' }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="info-section">
              <div class="section-title">收票信息</div>
              <div class="info-item"><span class="label">电子邮箱：</span>-</div>
              <div class="info-item"><span class="label">收票人员：</span>-</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="info-section">
              <div class="section-title">其他信息</div>
              <div class="info-item"><span class="label">页面备注：</span>{{ currentBill.remark || '-' }}</div>
            </div>
          </el-col>
        </el-row>

        <el-tabs v-model="detailActiveTab" class="detail-tabs">
          <el-tab-pane label="商品明细" name="products">
            <el-table :data="currentBill.products" border stripe size="small">
              <el-table-column prop="code" label="商品编码" width="100" />
              <el-table-column prop="status" label="对账状态" width="100" />
              <el-table-column prop="name" label="商品名称" min-width="150" />
              <el-table-column prop="taxRate" label="税率" width="80" />
              <el-table-column prop="price" label="单价" width="100" />
              <el-table-column prop="quantity" label="开票数量" width="100" />
              <el-table-column prop="total" label="金额" width="120" />
              <el-table-column prop="orderNo" label="网订订单号" width="150" />
              <el-table-column prop="deliveryEntity" label="交付主体" width="150" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="操作日志" name="logs">
            <el-table :data="operationLogs" border stripe size="small">
              <el-table-column prop="createTime" label="时间" width="160" />
              <el-table-column prop="operatorName" label="操作人" width="120" />
              <el-table-column prop="action" label="动作" width="120" />
              <el-table-column prop="details" label="备注" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>

    <!-- 发起对账：选择销售订单生成对账单 -->
    <el-dialog v-model="createInvoiceDialogVisible" title="发起对账" width="800px" @closed="resetCreateInvoiceForm">
      <el-form :model="createInvoiceForm" label-width="100px">
        <el-form-item label="项目名称">
          <el-input v-model="createInvoiceForm.projectName" placeholder="选填，如项目/客户名称" clearable />
        </el-form-item>
        <el-form-item label="选择订单">
          <div class="mb-8">仅展示状态为「已发货」「已到货」的订单，可多选后生成对账单。</div>
          <el-table
            ref="createInvoiceOrderTableRef"
            :data="createInvoiceOrderList"
            border
            max-height="320"
            @selection-change="onCreateInvoiceOrderSelectionChange"
          >
            <el-table-column type="selection" width="55" />
            <el-table-column prop="omsOrderNo" label="OMS订单号" width="160" />
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column prop="platformName" label="甲方" min-width="140" show-overflow-tooltip />
            <el-table-column prop="taxIncludedTotal" label="含税总价" width="110" align="right">
              <template #default="{ row }">¥{{ (row.taxIncludedTotal ?? 0).toFixed(2) }}</template>
            </el-table-column>
          </el-table>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="createInvoiceDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="createInvoiceSubmitting" @click="submitCreateInvoice">生成对账单</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import draggable from 'vuedraggable'
import request from '@/utils/request'

interface FilterForm {
  dateRange: string[]
  billNo: string
  projectName: string
}

interface ColumnConfig {
  label: string
  title: string
  width?: number
  minWidth?: number
  fixed?: 'left' | 'right'
  visible: boolean
}

const filterForm = ref<FilterForm>({
  dateRange: [],
  billNo: '',
  projectName: ''
})

const activeStatus = ref('all')
const detailVisible = ref(false)
const detailActiveTab = ref('products')

// 定义所有列配置
const allColumns = ref<ColumnConfig[]>([
  { label: 'billNo', title: '单据编号', width: 180, visible: true },
  { label: 'status', title: '状态', width: 100, visible: true },
  { label: 'invoiceNo', title: '开票单号', width: 180, visible: true },
  { label: 'projectName', title: '项目名称', minWidth: 180, visible: true },
  { label: 'sellerName', title: '销方名称', minWidth: 180, visible: true },
  { label: 'amount', title: '开票总额', width: 120, visible: true },
  { label: 'createTime', title: '创建时间', width: 160, visible: true },
  { label: 'confirmTime', title: '确认时间', width: 160, visible: true }
])

// 有序的列配置
const orderedColumns = ref<ColumnConfig[]>([...allColumns.value])

// 可见列数组（保持兼容性）
const visibleColumns = computed<string[]>({
  get: () => orderedColumns.value.filter(col => col.visible).map(col => col.label),
  set: (value) => {
    allColumns.value.forEach(col => {
      col.visible = value.includes(col.label)
    })
    // 保持原有顺序
    orderedColumns.value = allColumns.value.filter(col => value.includes(col.label))
      .concat(allColumns.value.filter(col => !value.includes(col.label)))
  }
})

const currentBill = ref<any>(null)
const tableData = ref<any[]>([])
const operationLogs = ref<any[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 发起对账弹窗
const createInvoiceDialogVisible = ref(false)
const createInvoiceOrderList = ref<any[]>([])
const createInvoiceForm = ref({ projectName: '', orderNos: [] as string[] })
const createInvoiceSubmitting = ref(false)
const createInvoiceOrderTableRef = ref()
const detailStatusNext = ref('')
const detailStatusUpdating = ref(false)

const loadSettings = () => {
  const saved = localStorage.getItem('invoiceListColumns')
  if (saved) {
    try {
      const settings = JSON.parse(saved)
      if (settings.columnsOrder) {
        // 按照保存的顺序重新排列
        const savedOrder = settings.columnsOrder
        const newOrderedColumns = [...allColumns.value].sort((a, b) => {
          const aIndex = savedOrder.indexOf(a.label)
          const bIndex = savedOrder.indexOf(b.label)
          if (aIndex === -1 && bIndex === -1) return 0
          if (aIndex === -1) return 1
          if (bIndex === -1) return -1
          return aIndex - bIndex
        })
        orderedColumns.value = newOrderedColumns
      }
      if (settings.visible) {
        const visibleLabels = settings.visible
        orderedColumns.value.forEach(col => {
          col.visible = visibleLabels.includes(col.label)
        })
      }
    } catch {
      orderedColumns.value = [...allColumns.value]
    }
  }
}

const saveColumns = () => {
  const visible = visibleColumns.value
  const columnsOrder = orderedColumns.value.map(col => col.label)
  
  localStorage.setItem('invoiceListColumns', JSON.stringify({
    visible,
    columnsOrder
  }))
  ElMessage.success('表头设置已保存')
}

const resetColumns = () => {
  allColumns.value.forEach((col, index) => {
    col.visible = true
    col.label = allColumns.value[index].label
  })
  orderedColumns.value = [...allColumns.value]
}

const onColumnVisibilityChange = (label: string, checked: boolean) => {
  const column = orderedColumns.value.find(col => col.label === label)
  if (column) {
    column.visible = checked
  }
}

const moveToTop = () => {
  // 将所有选中的列移到最前面
  const checkedColumns = orderedColumns.value.filter(col => col.visible)
  const uncheckedColumns = orderedColumns.value.filter(col => !col.visible)
  orderedColumns.value = [...checkedColumns, ...uncheckedColumns]
}

const moveToTopByLabel = (label: string) => {
  const index = orderedColumns.value.findIndex(col => col.label === label)
  if (index > -1) {
    const [column] = orderedColumns.value.splice(index, 1)
    orderedColumns.value.unshift(column)
  }
}

const onDragEnd = () => {
  // 拖拽结束后可以做些事情，比如保存顺序
  console.log('列顺序已更新')
}

const fetchInvoices = async () => {
  try {
    const statusParam = activeStatus.value === 'all' ? undefined : { draft: 'DRAFT', pending: 'PENDING', confirmed: 'CONFIRMED', settled: 'SETTLED' }[activeStatus.value]
    const res: any = await request.get('/invoices', {
      params: {
        billNo: filterForm.value.billNo || undefined,
        status: statusParam,
        projectName: filterForm.value.projectName || undefined,
        page: currentPage.value - 1,
        size: pageSize.value
      }
    })
    const content = res?.content ?? (Array.isArray(res) ? res : [])
    tableData.value = content.map((item: any) => ({
      ...item,
      createTime: item.createTime ? (typeof item.createTime === 'string' ? item.createTime : item.createTime.replace('T', ' ')) : '',
      confirmTime: item.confirmTime ? (typeof item.confirmTime === 'string' ? item.confirmTime : item.confirmTime.replace('T', ' ')) : ''
    }))
    total.value = res?.totalElements ?? content.length
  } catch (e) {
    console.error('获取对账单列表失败', e)
    ElMessage.error('获取对账单列表失败')
    tableData.value = []
  }
}

onMounted(() => {
  fetchInvoices()
  loadSettings()
})

const handleExport = () => {
  console.log('导出Excel')
}

const resetSearch = () => {
  filterForm.value = { dateRange: [], billNo: '', projectName: '' }
  activeStatus.value = 'all'
  currentPage.value = 1
  fetchInvoices()
}

const handleSearch = () => {
  currentPage.value = 1
  fetchInvoices()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
}

const getStatusType = (status: string) => {
  switch (status) {
    case 'SETTLED':
    case '已结清':
    case '已付款': return 'success'
    case 'PENDING':
    case '待对账': return 'warning'
    case 'CONFIRMED':
    case '已对账': return 'primary'
    default: return 'info'
  }
}

const getStep = (status: string) => {
  switch (status) {
    case 'DRAFT': return 1
    case 'PENDING': return 1
    case 'CONFIRMED': return 2
    case 'APPLIED': return 3
    case 'INVOICED': return 4
    case 'SETTLED': return 5
    default: return 1
  }
}

const handleGenerateSettlement = async (row: any) => {
  try {
    await ElMessageBox.confirm(
      `确定要为对账单 ${row.billNo} 生成结算单吗？此操作将完结该对账单。`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await request.post(`/settlements/generate?invoiceBillNo=${encodeURIComponent(row.billNo)}`)
    ElMessage.success('结算单生成成功，可到「结算单」页查看')
    fetchInvoices()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || error?.message || '生成结算单失败')
    }
  }
}

// 发起对账：打开弹窗并拉取可对账订单（已发货/已到货）
const openCreateInvoiceDialog = async () => {
  createInvoiceDialogVisible.value = true
  createInvoiceForm.value = { projectName: '', orderNos: [] }
  try {
    const [resShipped, resReceived]: any[] = await Promise.all([
      request.get('/sales-orders', { params: { status: '已发货', page: 0, size: 200 } }),
      request.get('/sales-orders', { params: { status: '已到货', page: 0, size: 200 } })
    ])
    const list1 = resShipped?.content ?? (Array.isArray(resShipped) ? resShipped : [])
    const list2 = resReceived?.content ?? (Array.isArray(resReceived) ? resReceived : [])
    const map = new Map()
    ;[...list1, ...list2].forEach((o: any) => {
      if (o.omsOrderNo && o.status && ['已发货', '已到货'].includes(o.status)) map.set(o.id, o)
    })
    createInvoiceOrderList.value = Array.from(map.values())
    if (createInvoiceOrderList.value.length === 0) {
      ElMessage.info('当前没有可对账的订单（需为已发货或已到货）')
    }
    await nextTick()
    createInvoiceOrderTableRef.value?.clearSelection?.()
  } catch (e) {
    console.error(e)
    ElMessage.error('获取订单列表失败')
    createInvoiceOrderList.value = []
  }
}

const onCreateInvoiceOrderSelectionChange = (rows: any[]) => {
  createInvoiceForm.value.orderNos = (rows || []).map((r: any) => r.omsOrderNo).filter(Boolean)
}

const resetCreateInvoiceForm = () => {
  createInvoiceForm.value = { projectName: '', orderNos: [] }
  createInvoiceOrderList.value = []
}

const submitCreateInvoice = async () => {
  const orderNos = createInvoiceForm.value.orderNos
  if (!orderNos || orderNos.length === 0) {
    ElMessage.warning('请至少选择一条销售订单')
    return
  }
  createInvoiceSubmitting.value = true
  try {
    await request.post('/invoices/generate', {
      orderNos,
      projectName: createInvoiceForm.value.projectName || undefined
    })
    ElMessage.success('对账单生成成功')
    createInvoiceDialogVisible.value = false
    fetchInvoices()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '生成对账单失败')
  } finally {
    createInvoiceSubmitting.value = false
  }
}

const showDetail = async (row: any) => {
  currentBill.value = {
    ...row,
    activeStep: getStep(row.status),
    stepInfo: {
      step1: row.createTime,
      step2: row.confirmTime || '-',
      step3: row.applyTime || '-',
      step4: row.invoiceTime || '-',
      step5: row.receiptTime || '-'
    },
    products: row.products || []
  }
  detailStatusNext.value = ''
  detailVisible.value = true
  detailActiveTab.value = 'products'

  try {
    const res: any = await request.get(`/logs/INVOICE/${row.billNo}`)
    operationLogs.value = Array.isArray(res) ? res : []
  } catch {
    operationLogs.value = []
  }
}

const updateInvoiceStatusInDetail = async () => {
  const billNo = currentBill.value?.billNo
  const next = detailStatusNext.value
  if (!billNo || !next) {
    ElMessage.warning('请选择要推进的状态')
    return
  }
  detailStatusUpdating.value = true
  try {
    await request.post(`/invoices/${billNo}/status?status=${encodeURIComponent(next)}`)
    currentBill.value = { ...currentBill.value, status: next, activeStep: getStep(next) }
    ElMessage.success('状态已更新')
    fetchInvoices()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '更新状态失败')
  } finally {
    detailStatusUpdating.value = false
  }
}
</script>

<style scoped>
.filter-card {
  margin-bottom: 15px;
}
.status-tabs {
  margin-bottom: 10px;
}
.table-ops {
  margin-bottom: 15px;
  display: flex;
  gap: 10px;
}
.mb-8 {
  margin-bottom: 8px;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
.column-settings {
  max-height: 450px;
  overflow-y: hidden;
}
.column-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid #eee;
}
.column-item {
  display: flex;
  align-items: center;
  padding: 5px 0;
  cursor: move;
  transition: background-color 0.2s;
}
.column-item:hover {
  background-color: #f5f5f5;
}
.column-item.hidden-column {
  opacity: 0.6;
}
.drag-handle {
  cursor: move;
  padding: 4px 8px;
  margin-right: 8px;
  color: #999;
  font-size: 14px;
  user-select: none;
}
.drag-handle:hover {
  color: #666;
}
.drag-ghost {
  opacity: 0.5;
  background: #c8ebfb;
}
.drag-chosen {
  background: #e6f7ff;
  border: 1px dashed #1890ff;
}
.settings-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.steps-container {
  padding: 20px 0 40px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 20px;
}
.summary-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px;
  background: #f8f9fb;
  border-radius: 4px;
  margin-bottom: 20px;
}
.bill-info {
  display: flex;
  align-items: center;
  font-weight: bold;
}
.status-tag {
  margin-left: 10px;
}
.money-info {
  display: flex;
  gap: 30px;
  font-size: 14px;
}
.money-info .price {
  color: #f56c6c;
  font-weight: bold;
  font-size: 16px;
}
.info-grid {
  margin-bottom: 25px;
}
.info-section {
  background: #fff;
  border: 1px solid #ebeef5;
  padding: 15px;
  border-radius: 4px;
  height: 100%;
}
.section-title {
  font-weight: bold;
  margin-bottom: 15px;
  padding-left: 10px;
  border-left: 4px solid #409EFF;
}
.info-item {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}
.info-item .label {
  color: #909399;
  width: 100px;
  display: inline-block;
}
.detail-tabs {
  margin-top: 20px;
}
.ml-20 {
  margin-left: 20px;
}
</style>