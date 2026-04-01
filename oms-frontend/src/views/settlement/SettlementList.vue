<template>
  <div class="settlement-list mobile-list-layout">
    <!-- 搜索筛选区 -->
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
        <el-form-item label="结算单号">
          <el-input v-model="filterForm.billNo" placeholder="请输入结算单号" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card>
      <div class="table-ops">
        <el-button type="primary">发起结算</el-button>
        <el-button type="success" plain>批量导出</el-button>
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
      <el-table :data="tableData" style="width: 100%" border stripe>
        <el-table-column type="selection" width="55" />
        <el-table-column label="操作" width="150" fixed>
          <template #default="scope">
            <el-button link type="primary" @click="showDetail(scope.row)">详情</el-button>
            <el-button link type="success" @click="handlePrint(scope.row.billNo)">打印</el-button>
          </template>
        </el-table-column>
        <template v-for="column in orderedColumns" :key="column.label">
          <el-table-column
            v-if="column.visible"
            :prop="column.label"
            :label="column.title"
            :width="column.width"
            :min-width="column.minWidth"
          >
            <template #default="scope" v-if="column.label === 'status'">
              <el-tag :type="getStatusType(scope.row[column.label])" size="small">{{ scope.row[column.label] }}</el-tag>
            </template>
            <template #default="scope" v-else-if="column.label === 'amount'">
              ¥{{ scope.row[column.label] }}
            </template>
            <template #default="scope" v-else-if="['billNo', 'projectName'].includes(column.label)">
              <el-tooltip :content="scope.row[column.label]" placement="top">
                <span>{{ scope.row[column.label] }}</span>
              </el-tooltip>
            </template>
            <template #default="scope" v-else>
              {{ scope.row[column.label] }}
            </template>
          </el-table-column>
        </template>
      </el-table>
      </div>

      <div class="pagination-container">
        <el-pagination
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

    <!-- 结算单详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="结算单详情"
      width="85%"
      top="3vh"
    >
      <div v-if="currentBill" class="detail-content">
        <!-- 步骤条区 -->
        <div class="steps-container">
          <el-steps :active="currentBill.activeStep" align-center>
            <el-step title="创建时间" :description="currentBill.stepInfo.step1" />
            <el-step title="申请时间" :description="currentBill.stepInfo.step2" />
            <el-step title="结算时间" :description="currentBill.stepInfo.step3" />
          </el-steps>
        </div>

        <!-- 汇总栏 -->
        <div class="summary-bar">
          <div class="bill-info">
            <span class="label">结算单号：</span>
            <span class="value">{{ currentBill.billNo }}</span>
            <el-tag :type="getStatusType(currentBill.status)" size="small" class="status-tag ml-20">{{ currentBill.status }}</el-tag>
            <span class="label ml-20">项目名称：</span>
            <span class="value">{{ currentBill.projectName || '-' }}</span>
          </div>
          <div class="money-info">
            <span class="item">结算总额：<span class="price">¥{{ currentBill.amount }}</span></span>
            <div class="detail-actions ml-20">
              <el-button 
                v-if="currentBill.status === 'DRAFT'" 
                type="primary" 
                size="small" 
                @click="handleApply(currentBill.billNo)"
              >提交结算</el-button>
              <el-button 
                v-if="currentBill.status === 'PENDING'" 
                type="success" 
                size="small" 
                @click="handleSettle(currentBill.billNo)"
              >确认收票</el-button>
              <el-button type="primary" size="small" icon="Printer" @click="handlePrint(currentBill.billNo)">打印 PDF</el-button>
            </div>
          </div>
        </div>

        <!-- 信息网格 -->
        <div class="info-grid-simple">
          <el-row :gutter="20">
            <el-col :span="6">
              <div class="info-item"><span class="label">不含税金额：</span>¥{{ currentBill.preTaxAmount || '0.00' }}</div>
            </el-col>
            <el-col :span="6">
              <div class="info-item"><span class="label">利息：</span>-</div>
            </el-col>
            <el-col :span="6">
              <div class="info-item"><span class="label">税额：</span>¥{{ currentBill.taxAmount || '0.00' }}</div>
            </el-col>
            <el-col :span="6">
              <div class="info-item"><span class="label">备注：</span>-</div>
            </el-col>
          </el-row>
        </div>

        <!-- 详情页签 -->
        <el-tabs v-model="detailActiveTab" class="detail-tabs">
          <el-tab-pane label="商品明细" name="products">
            <el-table :data="currentBill.products" border stripe size="small">
              <el-table-column type="selection" width="40" />
              <el-table-column prop="code" label="商品编码" width="100" />
              <el-table-column prop="status" label="商品状态" width="100" />
              <el-table-column prop="name" label="商品全称" min-width="150" />
              <el-table-column prop="taxRate" label="税率" width="80" />
              <el-table-column prop="price" label="单价" width="100" />
              <el-table-column prop="quantity" label="数量" width="80" />
              <el-table-column prop="buyQty" label="购买数量" width="80" />
              <el-table-column prop="total" label="金额" width="100" />
              <el-table-column prop="specs" label="规格" width="120" />
              <el-table-column prop="unit" label="单位" width="60" />
              <el-table-column prop="orderNo" label="网订订单号" width="120" />
              <el-table-column prop="deliveryEntity" label="交付主体" width="120" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="结算详情" name="settlement">暂无数据</el-tab-pane>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { apiBase } from '@/utils/apiBase'
import draggable from 'vuedraggable'

interface ColumnConfig {
  label: string
  title: string
  width?: number
  minWidth?: number
  visible: boolean
}

interface FilterForm {
  dateRange: string[]
  billNo: string
  projectName: string
}

const filterForm = ref<FilterForm>({
  dateRange: [],
  billNo: '',
  projectName: ''
})

const detailVisible = ref(false)
const detailActiveTab = ref('products')
const currentBill = ref<any>(null)
const tableData = ref<any[]>([])
const operationLogs = ref<any[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 定义所有列配置
const allColumns = ref<ColumnConfig[]>([
  { label: 'billNo', title: '结算单号', width: 180, visible: true },
  { label: 'status', title: '状态', width: 100, visible: true },
  { label: 'amount', title: '结算金额', width: 120, visible: true },
  { label: 'projectName', title: '项目名称', minWidth: 200, visible: true },
  { label: 'createTime', title: '创建时间', width: 180, visible: true }
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

const loadSettings = () => {
  const saved = localStorage.getItem('settlementListColumns')
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
  
  localStorage.setItem('settlementListColumns', JSON.stringify({
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

const fetchSettlements = async () => {
  try {
    const res: any = await request.get('/settlements', {
      params: {
        billNo: filterForm.value.billNo,
        projectName: filterForm.value.projectName,
        page: currentPage.value - 1,
        size: pageSize.value
      }
    })
    const content = res?.content || (Array.isArray(res) ? res : [])
    tableData.value = content.map((item: any) => ({
      ...item,
      preTaxAmount: item.preTaxAmount || '0.00',
      taxAmount: item.taxAmount || '0.00',
      products: item.products || []
    }))
    total.value = res?.totalElements || content.length || 0
  } catch (error) {
    console.error('Fetch settlements error:', error)
  }
}

onMounted(() => {
  fetchSettlements()
  loadSettings()
})

const handleSearch = () => {
  currentPage.value = 1
  fetchSettlements()
}

const resetSearch = () => {
  filterForm.value = { dateRange: [], billNo: '', projectName: '' }
  currentPage.value = 1
  fetchSettlements()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchSettlements()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  fetchSettlements()
}

const getStatusType = (status: string) => {
  switch (status) {
    case 'SETTLED':
    case '已结清': return 'success'
    case 'DRAFT': return 'info'
    default: return 'warning'
  }
}

const handleApply = async (billNo: string) => {
  try {
    await request.post(`/settlements/${billNo}/apply`)
    ElMessage.success('申请成功')
    fetchSettlements()
    if (currentBill.value) {
      currentBill.value.status = 'PENDING'
      currentBill.value.activeStep = 2
    }
  } catch (error) {
    console.error('Apply settlement error:', error)
    ElMessage.error('申请失败')
  }
}

const handleSettle = async (billNo: string) => {
  try {
    await request.post(`/settlements/${billNo}/settle`)
    ElMessage.success('结算成功')
    fetchSettlements()
    if (currentBill.value) {
      currentBill.value.status = 'SETTLED'
      currentBill.value.activeStep = 3
    }
  } catch (error) {
    console.error('Settle error:', error)
    ElMessage.error('结算失败')
  }
}

const showDetail = async (row: any) => {
  currentBill.value = {
    ...row,
    activeStep: row.status === 'SETTLED' ? 3 : (row.status === 'PENDING' ? 2 : 1),
    stepInfo: {
      step1: row.createTime,
      step2: row.applyTime || '-',
      step3: row.settleTime || '-'
    }
  }
  detailVisible.value = true
  detailActiveTab.value = 'products'

  // 获取操作日志
  try {
    const res: any = await request.get(`/logs/SETTLEMENT/${row.billNo}`)
    operationLogs.value = res
  } catch (error) {
    console.error('Fetch logs error:', error)
  }
}

const handlePrint = (billNo: string) => {
  const token = localStorage.getItem('token')
  window.open(`${apiBase()}/api/settlements/${billNo}/pdf?access_token=${token}`)
}
</script>

<style scoped>
.filter-card {
  margin-bottom: 15px;
}
.table-ops {
  margin-bottom: 15px;
  display: flex;
  gap: 10px;
}
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
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
.money-info .price {
  color: #f56c6c;
  font-weight: bold;
  font-size: 16px;
}
.info-grid-simple {
  padding: 0 15px;
  margin-bottom: 20px;
  font-size: 14px;
}
.info-item .label {
  color: #909399;
}
.detail-tabs {
  margin-top: 20px;
}
.ml-20 {
  margin-left: 20px;
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
</style>
