<template>
  <div class="purchase-list">
    <el-tabs v-model="mainTab" class="main-tabs">
      <el-tab-pane label="采购订单" name="purchase">
    <!-- 搜索筛选区 -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm">
        <el-form-item label="关键词搜索">
          <el-input v-model="filterForm.keyword" placeholder="采购单号/销售单号" />
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="filterForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 状态切换 -->
    <el-tabs v-model="activeStatus" class="status-tabs" @tab-click="handleSearch">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="待确认" name="pending" />
      <el-tab-pane label="已确认" name="confirmed" />
      <el-tab-pane label="已发货" name="shipped" />
      <el-tab-pane label="已到货" name="received" />
    </el-tabs>

    <!-- 表格区 -->
    <el-card>
      <div class="table-ops">
        <el-button type="primary">导出数据</el-button>
        <el-button type="success">批量确认</el-button>
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
      <el-table :data="tableData" style="width: 100%" border stripe>
        <el-table-column type="selection" width="55" />
        <el-table-column label="操作" width="100" fixed>
          <template #default="scope">
            <el-dropdown trigger="click">
              <el-button type="primary" size="small" :icon="MoreFilled">
                操作
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="showDetail(scope.row)">
                    <el-icon><Document /></el-icon>
                    <span>详情</span>
                  </el-dropdown-item>
                  <el-dropdown-item @click="handleUpdatePaymentStatus(scope.row)">
                    <el-icon><Money /></el-icon>
                    <span>更新付款状态</span>
                  </el-dropdown-item>
                  <el-dropdown-item @click="handleUpdateReconciliationStatus(scope.row)">
                    <el-icon><List /></el-icon>
                    <span>更新对账状态</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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
            <template #default="scope" v-else-if="column.label === 'paymentStatus'">
              <el-tag :type="getPaymentStatusType(scope.row[column.label])" size="small">{{ scope.row[column.label] || '未付款' }}</el-tag>
            </template>
            <template #default="scope" v-else-if="column.label === 'reconciliationStatus'">
              <el-tag :type="getReconciliationStatusType(scope.row[column.label])" size="small">{{ scope.row[column.label] || '未对账' }}</el-tag>
            </template>
            <template #default="scope" v-else-if="column.label === 'taxIncludedPurchasePrice'">
              ¥{{ scope.row[column.label] || '0.00' }}
            </template>
            <template #default="scope" v-else-if="column.label === 'taxIncludedPurchaseTotal'">
              ¥{{ scope.row[column.label] || '0.00' }}
            </template>
            <template #default="scope" v-else-if="['purchaseOrderNo', 'trackingNumber', 'omsOrderNo', 'supplier', 'invoiceNumber'].includes(column.label)">
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

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="采购单详情"
      width="80%"
      top="5vh"
      class="purchase-detail-dialog"
    >
      <div v-if="currentOrder" class="detail-content">
        <!-- 头部状态栏 -->
        <div class="detail-header">
          <div class="order-info">
            <span class="label">采购订单号：</span>
            <span class="value">{{ currentOrder.purchaseOrderNo }}</span>
            <el-tag size="small" type="success" class="status-tag">{{ currentOrder.status }}</el-tag>
          </div>
          <div class="actions">
            <el-button type="primary" @click="handlePrint">打印签收单</el-button>
          </div>
        </div>

        <!-- 信息网格：与采购单实体字段一致 -->
        <el-row :gutter="20" class="info-grid">
          <el-col :span="8">
            <div class="info-section">
              <div class="section-title">订单信息</div>
              <div class="info-item"><span class="label">采购类型：</span>{{ currentOrder.purchaseType || '-' }}</div>
              <div class="info-item"><span class="label">工业电商销售订单号：</span>{{ currentOrder.omsOrderNo || '-' }}</div>
              <div class="info-item"><span class="label">快递/发货单号：</span>{{ currentOrder.trackingNumber || '-' }}</div>
              <div class="info-item"><span class="label">业务员：</span>{{ currentOrder.salesPerson || '-' }}</div>
              <div class="info-item"><span class="label">创建日期：</span>{{ currentOrder.createTime || '-' }}</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="info-section">
              <div class="section-title">品项与金额</div>
              <div class="info-item"><span class="label">型号：</span>{{ currentOrder.model || '-' }}</div>
              <div class="info-item"><span class="label">数量：</span>{{ currentOrder.quantity ?? '-' }}</div>
              <div class="info-item"><span class="label">含税采购单价：</span>¥{{ currentOrder.taxIncludedPurchasePrice ?? '0.00' }}</div>
              <div class="info-item"><span class="label">含税采购总额：</span>¥{{ currentOrder.taxIncludedPurchaseTotal ?? '0.00' }}</div>
              <div class="info-item"><span class="label">交货日期：</span>{{ currentOrder.deliveryDate || '-' }}</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="info-section">
              <div class="section-title">结算与主体</div>
              <div class="info-item"><span class="label">付款状态：</span>{{ currentOrder.paymentStatus || '未付款' }}</div>
              <div class="info-item"><span class="label">对账状态：</span>{{ currentOrder.reconciliationStatus || '未对账' }}</div>
              <div class="info-item"><span class="label">发票号：</span>{{ currentOrder.invoiceNumber || '-' }}</div>
              <div class="info-item"><span class="label">供应商：</span>{{ currentOrder.supplier || '-' }}</div>
              <div class="info-item"><span class="label">制单人：</span>{{ currentOrder.creator || '-' }}</div>
            </div>
          </el-col>
        </el-row>

        <!-- 详情 Tabs -->
        <el-tabs v-model="detailActiveTab" class="detail-tabs">
          <el-tab-pane label="详情" name="detail">
            <el-table :data="detailTableRows" border stripe>
              <el-table-column prop="model" label="型号" width="140" />
              <el-table-column prop="quantity" label="数量" width="80" />
              <el-table-column prop="taxIncludedPurchasePrice" label="含税采购单价" width="140" align="right">
                <template #default="scope">¥{{ scope.row.taxIncludedPurchasePrice ?? '0.00' }}</template>
              </el-table-column>
              <el-table-column prop="taxIncludedPurchaseTotal" label="含税采购总额" width="140" align="right">
                <template #default="scope">¥{{ scope.row.taxIncludedPurchaseTotal ?? '0.00' }}</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="操作日志" name="logs">
            <el-table :data="purchaseOperationLogs" style="width: 100%" border stripe size="small">
              <el-table-column prop="operationType" label="操作类型" width="120" />
              <el-table-column prop="description" label="操作描述" />
              <el-table-column prop="operator" label="操作人" width="100" />
              <el-table-column prop="operationTime" label="操作时间" width="180" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>

    <!-- 更新付款状态对话框 -->
    <el-dialog
      v-model="updatePaymentStatusDialogVisible"
      title="更新付款状态"
      width="500px"
    >
      <el-form :model="updatePaymentStatusForm" label-width="100px">
        <el-form-item label="当前状态">
          <el-tag :type="getPaymentStatusType(currentPaymentStatus)">{{ currentPaymentStatus || '未付款' }}</el-tag>
        </el-form-item>
        <el-form-item label="新状态" required>
          <el-select v-model="updatePaymentStatusForm.status" placeholder="请选择付款状态" style="width: 100%">
            <el-option label="未付款" value="未付款" />
            <el-option label="部分付款" value="部分付款" />
            <el-option label="已付款" value="已付款" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="updatePaymentStatusForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="updatePaymentStatusDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitUpdatePaymentStatus">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 更新对账状态对话框 -->
    <el-dialog
      v-model="updateReconciliationStatusDialogVisible"
      title="更新对账状态"
      width="500px"
    >
      <el-form :model="updateReconciliationStatusForm" label-width="100px">
        <el-form-item label="当前状态">
          <el-tag :type="getReconciliationStatusType(currentReconciliationStatus)">{{ currentReconciliationStatus || '未对账' }}</el-tag>
        </el-form-item>
        <el-form-item label="新状态" required>
          <el-select v-model="updateReconciliationStatusForm.status" placeholder="请选择对账状态" style="width: 100%">
            <el-option label="未对账" value="未对账" />
            <el-option label="部分对账" value="部分对账" />
            <el-option label="已对账" value="已对账" />
          </el-select>
        </el-form-item>
        <el-form-item label="发票号">
          <el-input v-model="updateReconciliationStatusForm.invoiceNumber" placeholder="请输入发票号" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="updateReconciliationStatusForm.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="updateReconciliationStatusDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitUpdateReconciliationStatus">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 销售订单指派对话框 -->
    <el-dialog
      v-model="assignDialogVisible"
      title="订单指派"
      width="800px"
      append-to-body
      destroy-on-close
    >
      <el-form :model="assignForm" label-width="140px" label-position="right">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="交付方（乙方）">
              <el-select
                v-model="assignForm.deliveryParty"
                :placeholder="isReassignFlow ? '请选择新交付方（转派）' : '请选择交付方（来自用户信息维护）'"
                filterable
                allow-create
                default-first-option
                style="width: 100%"
                @change="onAssignDeliveryPartyChange"
                @blur="onAssignDeliveryPartyBlur"
              >
                <el-option v-for="t in assignDeliveryPartyOptions" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出货方">
              <el-select
                v-model="assignForm.shippingParty"
                placeholder="请选择出货方（来自用户信息维护）"
                filterable
                allow-create
                default-first-option
                style="width: 100%"
              >
                <el-option v-for="t in assignShippingPartyOptions" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="扣点(%)">
              <el-input v-model.number="assignForm.deductionRate" placeholder="如2" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="交付方采购价">
              <el-input v-model="assignForm.deliveryPartyPurchasePrice" placeholder="A的采购价=B的销售价" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品型号">
              <el-input v-model="assignForm.model" disabled />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="甲方抬头">
              <el-input v-model="assignForm.platformName" placeholder="请输入甲方抬头" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="支付方式">
              <el-select v-model="assignForm.paymentMethod" placeholder="请选择支付方式" style="width: 100%" filterable allow-create>
                <el-option label="30天月结" value="30天月结" />
                <el-option label="60天月结" value="60天月结" />
                <el-option label="3个月月结" value="3个月月结" />
                <el-option label="30%预付、70%尾款到发货" value="30%预付、70%尾款到发货" />
                <el-option label="100%款到发货" value="100%款到发货" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="合同模板">
              <el-select
                v-model="assignForm.templateUrl"
                placeholder="请选择或输入合同模板"
                style="width: 100%"
                filterable
                allow-create
                default-first-option
                @blur="onAssignTemplateBlur"
              >
                <el-option
                  v-for="template in contractTemplateList"
                  :key="template.id"
                  :label="template.templateName"
                  :value="template.templateUrl"
                >
                  <div style="display: flex; justify-content: space-between;">
                    <span>{{ template.templateName }}</span>
                    <span style="color: #999; font-size: 12px;">{{ template.description || '无描述' }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="乙方业务员">
              <el-select
                v-model="assignForm.partyBRepresentative"
                placeholder="请选择或输入乙方业务员"
                style="width: 100%"
                filterable
                allow-create
                default-first-option
                @blur="onPartyBRepresentativeBlur"
              >
                <el-option
                  v-for="user in partyBUsers"
                  :key="user.id"
                  :label="user.realName"
                  :value="user.realName"
                >
                  <div style="display: flex; justify-content: space-between;">
                    <span>{{ user.realName }}</span>
                    <span style="color: #999; font-size: 12px;">{{ user.username }}</span>
                  </div>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="assignDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="generateContract" :loading="isGeneratingContract" :disabled="isGeneratingContract">生成合同</el-button>
        </span>
      </template>
    </el-dialog>
      </el-tab-pane>

      <el-tab-pane label="销售订单指派" name="assign">
        <el-card class="filter-card">
          <el-form :inline="true" :model="assignFilterForm">
            <el-form-item label="平台订单号">
              <el-input v-model="assignFilterForm.platformOrderNo" placeholder="平台订单号" clearable />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="assignFilterForm.status" placeholder="全部" clearable style="width: 120px">
                <el-option label="待指派" value="待指派" />
                <el-option label="已退回" value="已退回" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="fetchSalesOrdersForAssign">查询</el-button>
              <el-button @click="assignFilterForm.platformOrderNo = ''; assignFilterForm.status = ''; fetchSalesOrdersForAssign()">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-card>
          <el-table :data="assignTableData" border stripe v-loading="assignLoading">
            <el-table-column prop="platformOrderNo" label="平台订单号" width="140" show-overflow-tooltip />
            <el-table-column prop="omsOrderNo" label="OMS订单号" width="140" show-overflow-tooltip />
            <el-table-column prop="platformName" label="平台" width="100" />
            <el-table-column prop="taxIncludedPrice" label="含税单价" width="110" align="right" />
            <el-table-column prop="taxIncludedTotal" label="含税总价" width="110" align="right" />
            <el-table-column prop="status" label="状态" width="90" align="center">
              <template #default="scope">
                <el-tag :type="scope.row.status === '已退回' ? 'danger' : 'warning'" size="small">{{ scope.row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="ecommerceSalesName" label="业务员" width="100" />
            <el-table-column prop="createTime" label="订单日期" width="160" show-overflow-tooltip />
            <el-table-column prop="deliveryParty" label="交付方" width="120" show-overflow-tooltip />
            <el-table-column prop="shippingParty" label="出货方" width="120" show-overflow-tooltip />
            <el-table-column prop="deductionRate" label="扣点" width="80" />
            <el-table-column prop="deliveryPartyPurchasePrice" label="交付方采购价" width="120" align="right" />
            <el-table-column prop="assignTime" label="指派时间" width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="scope">
                <el-button v-if="canAssign(scope.row)" type="primary" size="small" @click="handleAssignSalesOrder(scope.row)">指派</el-button>
                <el-button v-if="canReassign(scope.row)" type="success" size="small" @click="handleAssignSalesOrder(scope.row)">转派</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination">
            <el-pagination
              background
              v-model:current-page="assignPage"
              v-model:page-size="assignPageSize"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              :total="assignTotal"
              @size-change="() => fetchSalesOrdersForAssign()"
              @current-change="() => fetchSalesOrdersForAssign()"
            />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, reactive, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { CopyDocument, MoreFilled, Money, Document, List } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import draggable from 'vuedraggable'
import request from '../../utils/request'
import { apiBase } from '@/utils/apiBase'

const route = useRoute()

interface ColumnConfig {
  label: string
  title: string
  width?: number
  minWidth?: number
  visible: boolean
}

interface FilterForm {
  keyword: string
  dateRange: any[]
}

const filterForm = ref<FilterForm>({
  keyword: '',
  dateRange: []
})

const mainTab = ref('purchase')
const activeStatus = ref('all')
const detailVisible = ref(false)
const detailActiveTab = ref('detail')
const currentOrder = ref<any>(null)
const purchaseOperationLogs = ref<any[]>([])
const loading = ref(false)
// 详情 Tab 表格数据：采购单为单行，用单元素数组便于表格展示
const detailTableRows = computed(() => (currentOrder.value ? [currentOrder.value] : []))

// 销售订单指派 Tab
const assignFilterForm = reactive({ platformOrderNo: '', status: '' })
const assignTableData = ref<any[]>([])
const assignLoading = ref(false)
const assignPage = ref(1)
const assignPageSize = ref(10)
const assignTotal = ref(0)
const assignDialogVisible = ref(false)
const currentAssignOrder = ref<any>(null)
const isReassignFlow = ref(false)
const assignDeliveryPartyOptions = ref<string[]>([])
const assignShippingPartyOptions = ref<string[]>([])
const contractTemplateList = ref<any[]>([])
const partyBUsers = ref<any[]>([])
const isGeneratingContract = ref(false)
const currentUsername = ref(localStorage.getItem('username') || '')
const assignForm = reactive({
  deliveryParty: '',
  shippingParty: '',
  model: '',
  deductionRate: '' as any,
  deliveryPartyPurchasePrice: '',
  templateUrl: '',
  partyBRepresentative: '',
  platformName: '',
  paymentMethod: ''
})

// 更新付款状态相关
const updatePaymentStatusDialogVisible = ref(false)
const currentPaymentStatus = ref('')
const updatePaymentStatusForm = reactive({
  status: '',
  remark: ''
})

// 更新对账状态相关
const updateReconciliationStatusDialogVisible = ref(false)
const currentReconciliationStatus = ref('')
const updateReconciliationStatusForm = reactive({
  status: '',
  invoiceNumber: '',
  remark: ''
})

let currentPurchaseOrder: any = null

// 定义所有列配置
const allColumns = ref<ColumnConfig[]>([
  { label: 'purchaseOrderNo', title: '采购订单号', width: 150, visible: true },
  { label: 'status', title: '状态', width: 100, visible: true },
  { label: 'purchaseType', title: '采购类型', width: 120, visible: true },
  { label: 'omsOrderNo', title: '工业电商销售订单号', width: 180, visible: true },
  { label: 'trackingNumber', title: '快递/发货单号', width: 150, visible: true },
  { label: 'salesPerson', title: '业务员', width: 100, visible: true },
  { label: 'model', title: '型号', width: 120, visible: true },
  { label: 'quantity', title: '数量', width: 80, visible: true },
  { label: 'taxIncludedPurchasePrice', title: '含税采购单价', width: 130, visible: true },
  { label: 'taxIncludedPurchaseTotal', title: '含税采购总额', width: 130, visible: true },
  { label: 'deliveryDate', title: '交货日期', width: 120, visible: true },
  { label: 'paymentStatus', title: '付款状态', width: 100, visible: true },
  { label: 'reconciliationStatus', title: '对账状态', width: 100, visible: true },
  { label: 'invoiceNumber', title: '发票号', width: 150, visible: true },
  { label: 'supplier', title: '供应商', width: 200, visible: true },
  { label: 'creator', title: '制单人', width: 120, visible: true },
  { label: 'createTime', title: '创建日期', width: 180, visible: true }
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
  const saved = localStorage.getItem('purchaseListColumns')
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
  
  localStorage.setItem('purchaseListColumns', JSON.stringify({
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

const tableData = ref<any[]>([
  {
    purchaseOrderNo: '11763114473692',
    status: '已发货',
    purchaseType: '特采采购订单',
    omsOrderNo: '251023161550564',
    trackingNumber: 'SF1234567890',
    salesPerson: '张三',
    model: 'FOTRIC 348L-L12-L25',
    quantity: 2,
    taxIncludedPurchasePrice: '113739.06',
    taxIncludedPurchaseTotal: '227478.12',
    deliveryDate: '2025-11-14',
    paymentStatus: '未付款',
    reconciliationStatus: '未对账',
    invoiceNumber: '',
    supplier: '飞础科智慧',
    creator: 'OFFICEMATE',
    createTime: '2025-11-12 10:00:00',
    receiver: '曾*世',
    phone: '131****1052',
    address: '甘肃省******************光伏电站',
    remark: '客户备注:自提门店:251023161550564;',
    items: [
      {
        skuCode: '7845184',
        skuName: 'FOTRIC 热像仪 348L-L12-L25 -20-650℃ 640*480分辨率 配双镜头',
        unit: '台',
        spec: '1*1',
        qty: 2,
        price: '113739.06',
        amount: '227478.12',
        taxRate: '13%',
        status: '已发货'
      }
    ]
  }
])

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(1)

const handleSearch = () => {
  currentPage.value = 1
  fetchPurchaseOrders()
}

const resetSearch = () => {
  filterForm.value = { keyword: '', dateRange: [] }
  currentPage.value = 1
  fetchPurchaseOrders()
}

const fetchPurchaseOrders = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/purchase-orders', {
      params: {
        keyword: filterForm.value.keyword,
        status: activeStatus.value === 'all' ? '' : activeStatus.value,
        page: currentPage.value - 1,
        size: pageSize.value
      }
    })
    const content = res?.content || (Array.isArray(res) ? res : [])
    tableData.value = content
    total.value = res?.totalElements || content.length || 0
  } catch (error) {
    console.error('Fetch purchase orders error:', error)
    ElMessage.error('获取采购订单列表失败')
  } finally {
    loading.value = false
  }
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchPurchaseOrders()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  fetchPurchaseOrders()
}

const getStatusType = (status: string) => {
  switch (status) {
    case '已发货': return 'success'
    case '待确认': return 'warning'
    default: return 'info'
  }
}

const getPaymentStatusType = (status: string) => {
  switch (status) {
    case '已付款': return 'success'
    case '部分付款': return 'warning'
    case '未付款': return 'danger'
    default: return 'info'
  }
}

const getReconciliationStatusType = (status: string) => {
  switch (status) {
    case '已对账': return 'success'
    case '部分对账': return 'warning'
    case '未对账': return 'danger'
    default: return 'info'
  }
}

const showDetail = (row: any) => {
  currentOrder.value = row
  detailVisible.value = true
  purchaseOperationLogs.value = []
}

// 获取采购订单操作日志（后端返回 operationType/description/operator/operationTime）
const fetchPurchaseOperationLogs = async (orderId: number) => {
  try {
    const res: any = await request.get(`/purchase-orders/${orderId}/operation-logs`)
    const raw = Array.isArray(res) ? res : (res?.content || [])
    purchaseOperationLogs.value = raw.map((item: any) => {
      if (item.operationType != null) return item
      const fmt = (t: string) => (t && t.length >= 19 ? t.slice(0, 19) : t)
      return {
        operationType: item.action ?? '',
        description: item.details ?? '',
        operator: item.operatorName ?? '',
        operationTime: item.createTime ? fmt(String(item.createTime)) : ''
      }
    })
  } catch (error) {
    console.error('Fetch purchase operation logs error:', error)
    purchaseOperationLogs.value = []
  }
}

// 更新付款状态
const handleUpdatePaymentStatus = (row: any) => {
  currentPurchaseOrder = row
  currentPaymentStatus.value = row.paymentStatus || '未付款'
  updatePaymentStatusForm.status = ''
  updatePaymentStatusForm.remark = ''
  updatePaymentStatusDialogVisible.value = true
}

const submitUpdatePaymentStatus = async () => {
  if (!updatePaymentStatusForm.status) {
    ElMessage.warning('请选择付款状态')
    return
  }
  
  try {
    await request.patch(`/purchase-orders/${currentPurchaseOrder.id}/payment-status`, {
      status: updatePaymentStatusForm.status
    })
    if (currentPurchaseOrder) {
      currentPurchaseOrder.paymentStatus = updatePaymentStatusForm.status
    }
    ElMessage.success('付款状态更新成功')
    updatePaymentStatusDialogVisible.value = false
  } catch (error) {
    ElMessage.error('更新付款状态失败')
  }
}

// 更新对账状态
const handleUpdateReconciliationStatus = (row: any) => {
  currentPurchaseOrder = row
  currentReconciliationStatus.value = row.reconciliationStatus || '未对账'
  updateReconciliationStatusForm.status = ''
  updateReconciliationStatusForm.invoiceNumber = row.invoiceNumber || ''
  updateReconciliationStatusForm.remark = ''
  updateReconciliationStatusDialogVisible.value = true
}

const submitUpdateReconciliationStatus = async () => {
  if (!updateReconciliationStatusForm.status) {
    ElMessage.warning('请选择对账状态')
    return
  }
  
  try {
    await request.patch(`/purchase-orders/${currentPurchaseOrder.id}/reconciliation-status`, {
      status: updateReconciliationStatusForm.status,
      invoiceNumber: updateReconciliationStatusForm.invoiceNumber
    })
    if (currentPurchaseOrder) {
      currentPurchaseOrder.reconciliationStatus = updateReconciliationStatusForm.status
      if (updateReconciliationStatusForm.invoiceNumber) {
        currentPurchaseOrder.invoiceNumber = updateReconciliationStatusForm.invoiceNumber
      }
    }
    ElMessage.success('对账状态更新成功')
    updateReconciliationStatusDialogVisible.value = false
  } catch (error) {
    ElMessage.error('更新对账状态失败')
  }
}

// 销售订单指派：获取待指派/已退回列表（不传 view=sales，拿完整数据）
const fetchSalesOrdersForAssign = async () => {
  assignLoading.value = true
  try {
    const params: Record<string, any> = {
      page: assignPage.value - 1,
      size: assignPageSize.value,
      sort: 'createTime,desc'
    }
    if (assignFilterForm.platformOrderNo) params.platformOrderNo = assignFilterForm.platformOrderNo
    if (assignFilterForm.status) params.status = assignFilterForm.status
    else params.status = '待指派,待合同盖章,待发货,已退回' // 含已退回，便于指派方（如 sonmin）对退回订单发起第二次指派
    const res: any = await request.get('/sales-orders', { params })
    const content = res?.content ?? []
    assignTableData.value = content
    assignTotal.value = res?.totalElements ?? content.length
  } catch (e) {
    console.error(e)
    ElMessage.error('获取销售订单列表失败')
  } finally {
    assignLoading.value = false
  }
}

const canAssign = (row: any) => {
  return row.status === '待指派' || row.status === '已退回'
}
const canReassign = (row: any) => {
  return row.assignedUsername === currentUsername.value && row.status !== '已退回'
}

const fetchContractTemplates = async () => {
  try {
    const res: any = await request.get('/contract-templates', { params: { page: 0, size: 100 } })
    const templates = res?.content ?? []
    templates.forEach((t: any) => {
      if (t.templateUrl && !t.templateUrl.startsWith('http')) t.templateUrl = apiBase() + t.templateUrl
    })
    contractTemplateList.value = templates
  } catch {
    contractTemplateList.value = []
  }
}

const handleAssignSalesOrder = async (row: any) => {
  currentAssignOrder.value = row
  isReassignFlow.value = row.assignedUsername === currentUsername.value
  assignForm.deliveryParty = row.deliveryParty || ''
  assignForm.shippingParty = row.shippingParty || ''
  assignForm.model = row.model || ''
  assignForm.deductionRate = row.deductionRate ?? ''
  assignForm.deliveryPartyPurchasePrice = row.deliveryPartyPurchasePrice ?? ''
  // 甲方抬头：优先使用当前登录账号的抬头主体（如 sonmin），无需指派方再手动修改
  const myCompanyTitle = localStorage.getItem('companyTitle') || ''
  assignForm.platformName = myCompanyTitle.trim() || row.platformName || ''
  // 转派为正常加价买货，默认账期；指派沿用原单或用户选择
  assignForm.paymentMethod = canReassign(row) ? (row.paymentMethod || '账期') : (row.paymentMethod || '')
  assignForm.templateUrl = ''
  assignForm.partyBRepresentative = ''
  partyBUsers.value = []
  // 先打开弹窗，再异步加载下拉数据，避免点击后无反馈/看起来没弹窗
  assignDialogVisible.value = true
  await nextTick()
  await fetchContractTemplates()
  if (row.deliveryParty) {
    try {
      const userRes: any = await request.get('/users/by-company-title', { params: { companyTitle: row.deliveryParty } })
      partyBUsers.value = userRes || []
      if (partyBUsers.value.length === 1) assignForm.partyBRepresentative = partyBUsers.value[0].realName
    } catch {
      partyBUsers.value = []
    }
  }
  if (contractTemplateList.value.length === 1) assignForm.templateUrl = contractTemplateList.value[0].templateUrl
  try {
    const res: any = await request.get('/partner-info', { params: { page: 0, size: 1000 } })
    const allPartnerInfo = res?.content || (Array.isArray(res) ? res : [])
    const hasIdentity = (p: any, id: string) => {
      const ids = (p.identities || '').split(',').map((s: string) => s.trim())
      return ids.includes(id)
    }
    const titlesFor = (id: string) => allPartnerInfo.filter((p: any) => hasIdentity(p, id)).map((p: any) => (p.title || '').trim()).filter((t: string) => t)
    const namesFor = (id: string) => allPartnerInfo.filter((p: any) => hasIdentity(p, id)).map((p: any) => (p.name || '').trim()).filter((n: string) => n)
    // 交付方选项：含「交付方」「工厂方」「出货方」，均可作为乙方
    const deliveryTitlesA = [...titlesFor('交付方'), ...titlesFor('工厂方'), ...titlesFor('出货方')]
    const deliveryNamesA = [...namesFor('交付方'), ...namesFor('工厂方'), ...namesFor('出货方')]
    assignDeliveryPartyOptions.value = [...new Set([...deliveryTitlesA, ...deliveryNamesA])].filter(Boolean)
    assignShippingPartyOptions.value = titlesFor('出货方')
    const deliveryTitle = (assignForm.deliveryParty || '').trim()
    const deliveryValid = !deliveryTitle || assignDeliveryPartyOptions.value.some((t: string) => t === deliveryTitle || (t && deliveryTitle && (t.includes(deliveryTitle) || deliveryTitle.includes(t))))
    if (!deliveryValid) {
      ElMessage.warning('当前交付方在合作管理-用户信息维护中未找到，请先添加该抬头或从下拉选择')
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('获取合作方信息失败')
  }
}

const onAssignDeliveryPartyChange = async () => {
  const companyTitle = (assignForm.deliveryParty || '').trim()
  if (!companyTitle) {
    partyBUsers.value = []
    assignForm.partyBRepresentative = ''
    return
  }
  try {
    const userRes: any = await request.get('/users/by-company-title', { params: { companyTitle } })
    partyBUsers.value = userRes || []
    assignForm.partyBRepresentative = partyBUsers.value.length === 1 ? partyBUsers.value[0].realName : ''
  } catch {
    partyBUsers.value = []
    assignForm.partyBRepresentative = ''
  }
}

const onAssignDeliveryPartyBlur = (e: FocusEvent) => {
  const text = (e.target as HTMLInputElement)?.value?.trim()
  if (text) {
    assignForm.deliveryParty = text
    onAssignDeliveryPartyChange()
  }
}

// 乙方业务员支持直接输入：失焦时写回表单值
const onPartyBRepresentativeBlur = (e: FocusEvent) => {
  const text = (e.target as HTMLInputElement)?.value?.trim()
  if (text) assignForm.partyBRepresentative = text
}

// 合同模板支持直接输入模板名，失焦时写回，后续在生成合同前自动解析为 templateUrl
const onAssignTemplateBlur = (e: FocusEvent) => {
  const text = (e.target as HTMLInputElement)?.value?.trim()
  if (text) assignForm.templateUrl = text
}

const resolveTemplateUrl = (input: string) => {
  const raw = (input || '').trim()
  if (!raw) return ''
  if (/^https?:\/\//i.test(raw) || raw.startsWith('/')) return raw
  const exact = contractTemplateList.value.find((t: any) => (t.templateName || '').trim() === raw)
  if (exact?.templateUrl) return exact.templateUrl
  const fuzzy = contractTemplateList.value.find((t: any) => (t.templateName || '').includes(raw))
  if (fuzzy?.templateUrl) return fuzzy.templateUrl
  return raw
}

const generateContract = async () => {
  if (isGeneratingContract.value) return
  const order = currentAssignOrder.value
  if (!order?.id) return
  const templateUrl = resolveTemplateUrl(assignForm.templateUrl)
  if (!templateUrl) {
    ElMessage.error('请先选择合同模板')
    return
  }
  try {
    isGeneratingContract.value = true
    const orderRes: any = await request.get(`/sales-orders/${order.id}`)
    const deliveryParty = assignForm.deliveryParty?.trim() ?? orderRes.deliveryParty
    const shippingParty = assignForm.shippingParty ?? orderRes.shippingParty
    const platformName = (assignForm.platformName || '').trim() || orderRes.platformName
    const updateBody = {
      ...orderRes,
      deliveryParty,
      shippingParty,
      platformName: platformName || orderRes.platformName,
      operationEntityTitle: platformName || orderRes.operationEntityTitle,
      deductionRate: assignForm.deductionRate !== '' && assignForm.deductionRate != null ? assignForm.deductionRate : orderRes.deductionRate,
      deliveryPartyPurchasePrice: assignForm.deliveryPartyPurchasePrice ?? orderRes.deliveryPartyPurchasePrice,
      paymentMethod: assignForm.paymentMethod || orderRes.paymentMethod
    }
    if (Array.isArray(updateBody.logistics) && updateBody.logistics.length > 0 && updateBody.logistics[0] && typeof updateBody.logistics[0] === 'object') {
      if (deliveryParty !== undefined) updateBody.logistics[0].deliveryParty = deliveryParty
      if (shippingParty !== undefined) updateBody.logistics[0].shippingParty = shippingParty
    }
    await request.put(`/sales-orders/${order.id}`, updateBody)
    await request.post(`/contracts/generate-from-order/${order.id}`, {
      templateUrl,
      partyBRepresentative: assignForm.partyBRepresentative,
      platformName: assignForm.platformName,
      paymentMethod: assignForm.paymentMethod,
      deliveryParty: deliveryParty || orderRes.deliveryParty || ''
    })
    await request.patch(`/sales-orders/${order.id}/status`, { status: '待确认订单' })
    ElMessage.success('合同生成成功')
    assignDialogVisible.value = false
    fetchSalesOrdersForAssign()
    fetchPurchaseOrders()
  } catch (e: any) {
    console.error(e)
    const msg = e?.response?.data?.message || e?.message || '合同生成失败'
    ElMessage.error(msg)
  } finally {
    isGeneratingContract.value = false
  }
}

watch(mainTab, (tab) => {
  if (tab === 'assign') fetchSalesOrdersForAssign()
})

watch(detailActiveTab, (tab) => {
  if (tab === 'logs' && currentOrder.value?.id) {
    fetchPurchaseOperationLogs(currentOrder.value.id)
  }
})

onMounted(() => {
  const q = route.query as Record<string, string>
  if (q.tab === 'assign') {
    mainTab.value = 'assign'
    if (q.status) assignFilterForm.status = String(q.status)
  } else if (q.tab === 'purchase' && q.status) {
    const s = String(q.status)
    if (['all', 'pending', 'confirmed', 'shipped', 'received'].includes(s)) activeStatus.value = s
  }
  loadSettings()
  if (mainTab.value === 'assign') {
    fetchSalesOrdersForAssign()
  } else {
    fetchPurchaseOrders()
  }
})

const handlePrint = () => {
  if (!currentOrder.value) return
  
  // 创建一个隐藏的打印区域
  const printContent = document.createElement('div')
  printContent.id = 'print-area'
  printContent.style.display = 'none'
  
  const o = currentOrder.value
  const rowHtml = `
    <tr>
      <td>${o.model || '-'}</td>
      <td>${o.quantity ?? '-'}</td>
      <td>¥${o.taxIncludedPurchasePrice ?? '0.00'}</td>
      <td>¥${o.taxIncludedPurchaseTotal ?? '0.00'}</td>
    </tr>
  `

  printContent.innerHTML = `
    <div style="padding: 40px; font-family: sans-serif;">
      <h1 style="text-align: center; border-bottom: 2px solid #333; padding-bottom: 10px;">采购签收单</h1>
      
      <div style="display: flex; justify-content: space-between; margin-top: 20px;">
        <div>
          <p><strong>采购订单号：</strong>${o.purchaseOrderNo || '-'}</p>
          <p><strong>工业电商销售订单号：</strong>${o.omsOrderNo || '-'}</p>
          <p><strong>供应商：</strong>${o.supplier || '-'}</p>
        </div>
        <div style="text-align: right;">
          <p><strong>打印日期：</strong>${new Date().toLocaleDateString()}</p>
          <p><strong>交货日期：</strong>${o.deliveryDate || '-'}</p>
        </div>
      </div>

      <table style="width: 100%; border-collapse: collapse; margin-top: 20px;">
        <thead>
          <tr style="background: #eee;">
            <th style="border: 1px solid #ddd; padding: 8px;">型号</th>
            <th style="border: 1px solid #ddd; padding: 8px;">数量</th>
            <th style="border: 1px solid #ddd; padding: 8px;">含税采购单价</th>
            <th style="border: 1px solid #ddd; padding: 8px;">含税采购总额</th>
          </tr>
        </thead>
        <tbody>${rowHtml}</tbody>
        <tfoot>
          <tr style="font-weight: bold;">
            <td colspan="3" style="border: 1px solid #ddd; padding: 8px; text-align: right;">总金额：</td>
            <td style="border: 1px solid #ddd; padding: 8px;">¥${o.taxIncludedPurchaseTotal ?? '0.00'}</td>
          </tr>
        </tfoot>
      </table>

      <div style="margin-top: 50px; display: flex; justify-content: space-between;">
        <p>收货方签字：____________________</p>
        <p>送货方签字：____________________</p>
      </div>
    </div>
  `

  // 打印逻辑
  const originalBody = document.body.innerHTML
  const printWindow = window.open('', '_blank')
  if (printWindow) {
    printWindow.document.write(`
            <html>
              <head>
                <title>打印签收单 - ${o.purchaseOrderNo || '-'}</title>
                <style>
                  @page { size: A4; margin: 0; }
                  body { margin: 0; }
                  table { border-collapse: collapse; width: 100%; }
                  th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                </style>
              </head>
              <body>
                ${printContent.innerHTML}
                <script>
                  window.onload = function() {
                    window.print();
                    window.close();
                  };
                </${'script'}>
              </body>
            </html>
          `)
    printWindow.document.close()
  } else {
    ElMessage.error('弹出窗口被阻止，请允许弹出窗口后重试')
  }
}
</script>

<style scoped>
.main-tabs {
  margin-bottom: 10px;
}
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
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 20px;
}
.order-info {
  font-size: 18px;
  font-weight: bold;
}
.status-tag {
  margin-left: 10px;
}
.info-grid {
  margin-bottom: 30px;
}
.info-section {
  background: #fafafa;
  padding: 15px;
  border-radius: 4px;
  height: 100%;
}
.section-title {
  font-weight: bold;
  margin-bottom: 15px;
  color: #333;
}
.info-item {
  margin-bottom: 10px;
  font-size: 14px;
  color: #666;
}
.info-item .label {
  color: #999;
  width: 80px;
  display: inline-block;
}
.remark {
  color: #f56c6c;
}
.copy-icon {
  cursor: pointer;
  margin-left: 5px;
  color: #409EFF;
}
.detail-tabs {
  margin-top: 20px;
}
.table-footer {
  margin-top: 15px;
  padding: 10px;
  background: #fdf6ec;
  border-radius: 4px;
  font-size: 14px;
}
.highlight {
  color: #f56c6c;
  font-weight: bold;
}
</style>
