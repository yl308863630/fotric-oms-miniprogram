<template>
  <div class="purchase-list mobile-list-layout">
    <el-tabs v-model="mainTab" class="main-tabs">
      <el-tab-pane label="采购订单" name="purchase">
    <!-- 搜索筛选区 -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm">
        <el-form-item label="关键词搜索">
          <el-input v-model="filterForm.keyword" placeholder="合同编号/销售单号" />
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
        <el-form-item label="商务ERP录单">
          <el-select v-model="filterForm.erpEntryStatus" placeholder="全部" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="待系统录单" value="待系统录单" />
            <el-option label="已录单" value="已录单" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterForm.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="待确认" value="待确认" />
            <el-option label="已确认" value="已确认" />
            <el-option label="已发货" value="已发货" />
            <el-option label="已到货" value="已到货" />
          </el-select>
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
        <el-button type="success">批量确认</el-button>
        <el-button
          type="warning"
          plain
          :disabled="selectedPurchaseRows.length === 0"
          @click="openBatchErpEntryDialog"
        >
          批量商务ERP录单
        </el-button>
        <el-button type="warning" plain :disabled="selectedPurchaseRows.length === 0" @click="handleBatchGeneratePurchaseReconciliation">
          生成采购对账单
        </el-button>
        <el-popover placement="bottom" width="500" trigger="click">
          <template #reference>
            <el-button>表头设置</el-button>
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
      <el-table :data="tableData" style="width: 100%" border stripe @selection-change="handlePurchaseSelectionChange" :row-class-name="getPurchaseTableRowClassName">
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
                  <el-dropdown-item @click="openStructureInspect(scope.row)">
                    <el-icon><Document /></el-icon>
                    <span>链路观察</span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="scope.row.trackingNumber" @click="showLogisticsInfo(scope.row)">
                    <el-icon><Van /></el-icon>
                    <span>物流轨迹</span>
                  </el-dropdown-item>
                  <el-dropdown-item @click="handleUpdatePaymentStatus(scope.row)">
                    <el-icon><Money /></el-icon>
                    <span>更新付款状态</span>
                  </el-dropdown-item>
                  <el-dropdown-item @click="handleUpdateReconciliationStatus(scope.row)">
                    <el-icon><List /></el-icon>
                    <span>更新对账状态</span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="scope.row.erpEntryCanEdit" @click="openErpEntryDialog(scope.row)">
                    <el-icon><CopyDocument /></el-icon>
                    <span>商务ERP录单</span>
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="handleCreatePaymentRequest(scope.row)" :disabled="!canApplyPaymentRequest">
                    <el-icon><Money /></el-icon>
                    <span>发起付款申请</span>
                  </el-dropdown-item>
                  <el-dropdown-item @click="handleOpenPaymentRequest(scope.row)" :disabled="!canViewPaymentRequest">
                    <el-icon><Document /></el-icon>
                    <span>付款申请详情</span>
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="handleDeletePurchaseOrder(scope.row)">
                    <el-icon><Delete /></el-icon>
                    <span>删除</span>
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
            <template #default="scope" v-else-if="column.label === 'paymentRequestStatus'">
              <el-tag :type="getPaymentRequestStatusType(scope.row[column.label])" size="small">
                {{ scope.row[column.label] || '未发起' }}
              </el-tag>
            </template>
            <template #default="scope" v-else-if="column.label === 'erpEntryStatus'">
              <div class="erp-entry-cell">
                <el-tag :type="getErpEntryStatusType(scope.row[column.label])" size="small">
                  {{ scope.row[column.label] || '-' }}
                </el-tag>
                <span v-if="scope.row.erpEntryOperator" class="erp-entry-meta">录单人：{{ scope.row.erpEntryOperator }}</span>
                <span v-if="scope.row.erpEntryTime" class="erp-entry-meta">录单时间：{{ formatErpEntryTime(scope.row.erpEntryTime) }}</span>
                <div class="erp-entry-actions">
                  <el-button
                    v-if="scope.row.erpEntryCanEdit"
                    link
                    type="primary"
                    size="small"
                    @click="openErpEntryDialog(scope.row)"
                  >
                    {{ scope.row.erpEntryStatus === '已录单' ? '修改' : '录单' }}
                  </el-button>
                  <el-button
                    v-if="scope.row.erpEntryCanPreviewScreenshot && scope.row.erpEntryScreenshotUrl"
                    link
                    type="primary"
                    size="small"
                    @click="openErpEntryScreenshot(scope.row)"
                  >
                    查看截图
                  </el-button>
                </div>
              </div>
            </template>
            <template #default="scope" v-else-if="column.label === 'taxIncludedPurchasePrice'">
              ¥{{ scope.row[column.label] || '0.00' }}
            </template>
            <template #default="scope" v-else-if="column.label === 'taxIncludedPurchaseTotal'">
              ¥{{ scope.row[column.label] || '0.00' }}
            </template>
            <template #default="scope" v-else-if="column.label === 'trackingNumber'">
              <template v-if="scope.row.trackingNumber">
                <el-button link type="primary" size="small" @click="showLogisticsInfo(scope.row)">
                  {{ scope.row.trackingNumber }}
                </el-button>
              </template>
              <span v-else>-</span>
            </template>
            <template #default="scope" v-else-if="column.label === 'contractNo'">
              <div class="purchase-contract-cell">
                <div class="purchase-contract-main">
                  <span
                    v-if="isFirstPurchaseGroupRow(scope.$index)"
                    :class="['master-group-chip', `master-group-chip-${getPurchaseGroupTone(scope.row)}`]"
                  >
                    {{ scope.row.contractNo || '未生成合同' }}
                  </span>
                  <span v-else class="purchase-group-continued">同合同</span>
                </div>
                <span
                  v-if="getPurchaseGroupSize(scope.row) > 1 && isFirstPurchaseGroupRow(scope.$index)"
                  class="purchase-contract-sub"
                >
                  共 {{ getPurchaseGroupSize(scope.row) }} 行商品
                </span>
              </div>
            </template>
            <template #default="scope" v-else-if="['purchaseOrderNo', 'omsOrderNo', 'supplier', 'invoiceNumber'].includes(column.label)">
              <el-tooltip :content="scope.row[column.label]" placement="top">
                <span>{{ scope.row[column.label] || '-' }}</span>
              </el-tooltip>
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

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="采购单详情"
      width="80%"
      top="5vh"
      class="purchase-detail-dialog"
    >
      <div v-if="currentOrder" class="detail-content">
        <div class="detail-header">
          <div class="detail-header-main">
          <div class="order-info">
              <span class="label">合同编号：</span>
              <span class="value">{{ currentOrder.contractNo || '-' }}</span>
            <el-tag size="small" type="success" class="status-tag">{{ currentOrder.status }}</el-tag>
            </div>
            <div class="detail-header-sub">
              <span>采购订单号：{{ currentOrder.purchaseOrderNo || '-' }}</span>
              <span>共 {{ detailTableRows.length }} 行商品</span>
              <span v-if="detailFocusLabel">当前定位：{{ detailFocusLabel }}</span>
            </div>
          </div>
          <div class="actions">
            <el-button type="primary" @click="handlePrint">打印签收单</el-button>
          </div>
        </div>

        <el-row :gutter="20" class="info-grid">
          <el-col :span="9">
            <div class="info-section">
              <div class="section-title">单据信息</div>
              <div class="info-item"><span class="label">采购类型：</span>{{ currentOrder.purchaseType || '-' }}</div>
              <div class="info-item"><span class="label">OMS 单号：</span>{{ currentOrder.omsOrderNo || '-' }}</div>
              <div class="info-item"><span class="label">供应商：</span>{{ currentOrder.supplier || '-' }}</div>
              <div class="info-item"><span class="label">业务员：</span>{{ currentOrder.salesPerson || '-' }}</div>
              <div class="info-item"><span class="label">制单人：</span>{{ currentOrder.creator || '-' }}</div>
              <div class="info-item"><span class="label">创建日期：</span>{{ currentOrder.createTime || '-' }}</div>
            </div>
          </el-col>
          <el-col :span="9">
            <div class="info-section">
              <div class="section-title">财务进度</div>
              <div class="info-item"><span class="label">付款状态：</span>{{ currentOrder.paymentStatus || '未付款' }}</div>
              <div class="info-item"><span class="label">付款申请单号：</span>{{ currentOrder.paymentRequestNo || '-' }}</div>
              <div class="info-item"><span class="label">付款申请状态：</span>{{ currentOrder.paymentRequestStatus || '未发起' }}</div>
              <div class="info-item"><span class="label">对账状态：</span>{{ currentOrder.reconciliationStatus || '未对账' }}</div>
              <div class="info-item"><span class="label">发票号：</span>{{ currentOrder.invoiceNumber || '-' }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="info-section">
              <div class="section-title">交付与物流</div>
              <div class="info-item">
                <span class="label">母单物流单号：</span>
                <el-button
                  v-if="currentOrder.trackingNumber"
                  link
                  type="primary"
                  size="small"
                  style="padding: 0;"
                  @click="showLogisticsInfo(currentOrder)"
                >
                  {{ currentOrder.trackingNumber }}
                </el-button>
                <span v-else>-</span>
              </div>
              <div class="info-item"><span class="label">交货日期：</span>{{ currentOrder.deliveryDate || '-' }}</div>
            </div>
          </el-col>
        </el-row>

        <div class="detail-line-section">
          <div class="section-head">
            <div>
              <div class="section-title">商品明细</div>
              <div class="section-subtitle">金额与数量统一按商品行展示，当前定位行会高亮。</div>
            </div>
            <div class="detail-line-summary">
              <span>合计数量：{{ detailTotalQuantity }}</span>
              <span>合计金额：¥{{ detailTotalAmount.toFixed(2) }}</span>
            </div>
          </div>
          <el-table
            :data="detailTableRows"
            border
            stripe
            :row-class-name="getDetailTableRowClassName"
          >
            <el-table-column label="当前行" width="88" align="center">
              <template #default="scope">
                <el-tag v-if="isFocusedDetailRow(scope.row)" size="small" type="warning">当前</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column type="index" label="序号" width="70" align="center" />
            <el-table-column prop="model" label="型号" min-width="160" />
            <el-table-column prop="quantity" label="数量" width="90" align="center" />
            <el-table-column prop="taxIncludedPurchasePrice" label="含税采购单价" min-width="150" align="right">
                <template #default="scope">¥{{ scope.row.taxIncludedPurchasePrice ?? '0.00' }}</template>
              </el-table-column>
            <el-table-column prop="taxIncludedPurchaseTotal" label="含税采购总额" min-width="150" align="right">
                <template #default="scope">¥{{ scope.row.taxIncludedPurchaseTotal ?? '0.00' }}</template>
              </el-table-column>
            <el-table-column label="交货日期" min-width="120">
              <template #default="scope">{{ scope.row.deliveryDate || currentOrder.deliveryDate || '-' }}</template>
            </el-table-column>
            </el-table>
        </div>

        <div class="detail-log-section">
          <div class="section-head">
            <div>
              <div class="section-title">操作日志</div>
              <div class="section-subtitle">日志仅作为辅助查看，不再与商品明细并列争夺主视角。</div>
            </div>
          </div>
          <el-table :data="purchaseOperationLogs" style="width: 100%" border stripe size="small" empty-text="暂无操作日志">
              <el-table-column prop="operationType" label="操作类型" width="120" />
              <el-table-column prop="description" label="操作描述" />
              <el-table-column prop="operator" label="操作人" width="100" />
              <el-table-column prop="operationTime" label="操作时间" width="180" />
            </el-table>
        </div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="logisticsDialogVisible"
      title="采购物流轨迹"
      width="900px"
    >
      <div v-loading="logisticsLoading">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
          <div style="font-size: 16px; font-weight: bold; color: #1890ff;">
            <span style="margin-right: 8px;">|</span>
            物流信息
          </div>
          <el-button type="primary" :icon="Refresh" @click="refreshLogistics" :loading="logisticsLoading">
            刷新
          </el-button>
        </div>

        <el-table :data="logisticsInfo ? [logisticsInfo] : []" style="width: 100%; margin-bottom: 20px;">
          <el-table-column label="序号" width="80" align="center">
            <template #default>
              1
            </template>
          </el-table-column>
          <el-table-column prop="company" label="快递公司" width="200" />
          <el-table-column prop="trackingNumber" label="母单物流单号" />
        </el-table>

        <div v-if="logisticsInfo" class="trace-list">
          <div v-for="(trace, index) in logisticsInfo.traces || []" :key="index" class="trace-item">
            <div class="trace-dot" :class="{ active: index === 0 }">
              <el-icon v-if="index === 0"><CircleCheck /></el-icon>
            </div>
            <div class="trace-content">
              <div class="trace-status" :class="{ latest: index === 0 }">{{ trace.status }}</div>
              <div class="trace-desc" :class="{ latest: index === 0 }">{{ trace.desc }}</div>
              <div class="trace-time">{{ trace.time }}</div>
            </div>
            <div v-if="index < (logisticsInfo.traces || []).length - 1" class="trace-line"></div>
          </div>
        </div>
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

    <!-- 发起付款申请对话框（采购域） -->
    <el-dialog
      v-model="createPaymentRequestDialogVisible"
      title="发起付款申请"
      width="520px"
    >
      <el-form :model="createPaymentRequestForm" label-width="110px">
        <el-form-item label="采购订单号">
          <el-input :model-value="currentPurchaseOrder?.purchaseOrderNo || '-'" disabled />
        </el-form-item>
        <el-form-item label="工业电商单号">
          <el-input :model-value="currentPurchaseOrder?.omsOrderNo || '-'" disabled />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input :model-value="currentPurchaseOrder?.supplier || '-'" disabled />
        </el-form-item>
        <el-form-item label="申请金额">
          <el-input :model-value="String(currentPurchaseOrder?.taxIncludedPurchaseTotal ?? '')" disabled />
        </el-form-item>
        <el-form-item label="申请备注">
          <el-input v-model="createPaymentRequestForm.applyRemark" type="textarea" :rows="3" placeholder="可填写付款说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="createPaymentRequestDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="createPaymentRequestSubmitting" @click="submitCreatePaymentRequest">确定发起</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 付款申请详情（采购域） -->
    <el-dialog
      v-model="paymentRequestDetailVisible"
      title="付款申请详情"
      width="900px"
      top="4vh"
    >
      <div v-if="currentPaymentRequest" class="detail-content">
        <div class="summary-bar">
          <div class="bill-info">
            <span class="label">申请单号：</span>
            <span class="value">{{ currentPaymentRequest.requestNo || '-' }}</span>
            <el-tag :type="getPaymentRequestStatusType(currentPaymentRequest.status)" size="small" class="status-tag ml-20">
              {{ currentPaymentRequest.status || '未发起' }}
            </el-tag>
          </div>
          <div class="money-info">
            <span class="item">申请金额：<span class="price">¥{{ currentPaymentRequest.requestedAmount || '0.00' }}</span></span>
          </div>
        </div>

        <el-tabs v-model="paymentRequestDetailTab">
          <el-tab-pane label="申请信息" name="base">
            <el-form :model="paymentRequestStatusForm" label-width="120px">
              <el-row :gutter="20">
                <el-col :span="12"><el-form-item label="采购订单号"><el-input :model-value="currentPaymentRequest.purchaseOrderNo || '-'" disabled /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="工业电商单号"><el-input :model-value="currentPaymentRequest.omsOrderNo || '-'" disabled /></el-form-item></el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="12"><el-form-item label="收款方"><el-input :model-value="currentPaymentRequest.payeeCompany || '-'" disabled /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="已打款金额"><el-input :model-value="String(currentPaymentRequest.paidAmount || '')" disabled /></el-form-item></el-col>
              </el-row>
              <el-form-item label="状态推进">
                <el-select v-model="paymentRequestStatusForm.status" placeholder="请选择状态" style="width: 220px" :disabled="!canTrackInboundInvoice && !canFinancePayment">
                  <el-option label="PENDING_REVIEW" value="PENDING_REVIEW" />
                  <el-option label="INVOICE_TRACKING" value="INVOICE_TRACKING" />
                  <el-option label="READY_TO_PAY" value="READY_TO_PAY" />
                  <el-option label="PAID" value="PAID" />
                  <el-option label="REJECTED" value="REJECTED" />
                  <el-option label="CLOSED" value="CLOSED" />
                </el-select>
              </el-form-item>
              <el-form-item label="备注">
                <el-input v-model="paymentRequestStatusForm.remark" type="textarea" :rows="2" :disabled="!canTrackInboundInvoice && !canFinancePayment" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="submitPaymentRequestStatus" :disabled="!canTrackInboundInvoice && !canFinancePayment">更新状态</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="进项发票跟进" name="invoice">
            <el-table :data="paymentRequestInvoices" border stripe size="small">
              <el-table-column prop="invoiceNumber" label="发票号" width="180" />
              <el-table-column prop="invoiceAmount" label="金额" width="120" />
              <el-table-column prop="status" label="状态" width="120" />
              <el-table-column prop="receivedDate" label="收票日期" width="140" />
              <el-table-column prop="invoiceUrl" label="附件" min-width="180">
                <template #default="{ row }">
                  <el-button v-if="row.invoiceUrl" link type="primary" @click="openExternalLink(row.invoiceUrl)">查看附件</el-button>
                  <span v-else>-</span>
                </template>
              </el-table-column>
            </el-table>
            <el-divider />
            <el-form :model="newInboundInvoiceForm" label-width="100px">
              <el-row :gutter="20">
                <el-col :span="8"><el-form-item label="发票号"><el-input v-model="newInboundInvoiceForm.invoiceNumber" :disabled="!canTrackInboundInvoice && !canFinancePayment" /></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="金额"><el-input v-model="newInboundInvoiceForm.invoiceAmount" :disabled="!canTrackInboundInvoice && !canFinancePayment" /></el-form-item></el-col>
                <el-col :span="8">
                  <el-form-item label="状态">
                    <el-select v-model="newInboundInvoiceForm.status" style="width:100%" :disabled="!canTrackInboundInvoice && !canFinancePayment">
                      <el-option label="待开票" value="待开票" />
                      <el-option label="已开票" value="已开票" />
                      <el-option label="已收票" value="已收票" />
                      <el-option label="退票重开" value="退票重开" />
                      <el-option label="异常" value="异常" />
                    </el-select>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="20">
                <el-col :span="16"><el-form-item label="备注"><el-input v-model="newInboundInvoiceForm.remark" :disabled="!canTrackInboundInvoice && !canFinancePayment" /></el-form-item></el-col>
                <el-col :span="8">
                  <el-form-item label="附件上传">
                    <el-upload action="/api/upload" :show-file-list="false" :before-upload="beforeUpload" :on-success="handleInboundInvoiceUploadSuccess" :disabled="!canTrackInboundInvoice && !canFinancePayment">
                      <el-button type="primary" :disabled="!canTrackInboundInvoice && !canFinancePayment">上传</el-button>
                    </el-upload>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-form-item>
                <el-button type="primary" @click="submitInboundInvoice" :disabled="!canTrackInboundInvoice && !canFinancePayment">新增进项发票跟进</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="打款记录/凭证" name="voucher">
            <el-form :model="paymentVoucherForm" label-width="110px">
              <el-form-item label="打款金额">
                <el-input v-model="paymentVoucherForm.paidAmount" placeholder="默认全款" :disabled="!canFinancePayment" />
              </el-form-item>
              <el-form-item label="银行流水号">
                <el-input v-model="paymentVoucherForm.bankFlowNo" :disabled="!canFinancePayment" />
              </el-form-item>
              <el-form-item label="凭证上传">
                <el-upload action="/api/upload" :show-file-list="false" :before-upload="beforeUpload" :on-success="handlePaymentVoucherUploadSuccess" :disabled="!canFinancePayment">
                  <el-button type="primary" :disabled="!canFinancePayment">上传打款凭证</el-button>
                </el-upload>
                <el-button v-if="paymentVoucherForm.voucherUrl" link type="primary" @click="openExternalLink(paymentVoucherForm.voucherUrl)">预览</el-button>
              </el-form-item>
              <el-form-item>
                <el-button type="success" @click="submitPaymentVoucher" :disabled="!canFinancePayment">确认打款并回写采购付款状态</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>

    <!-- 销售订单指派对话框 -->
    <el-dialog
      v-model="assignDialogVisible"
      :title="assignDialogTitle"
      width="800px"
      append-to-body
      destroy-on-close
    >
      <el-form :model="assignForm" label-width="140px" label-position="right" class="assign-contract-form">
        <div class="assign-party-summary">
          <div class="assign-party-summary-row">
            <span class="summary-label">原订单甲方：</span>
            <span>{{ assignOriginalPartyATitle }}</span>
          </div>
          <div class="assign-party-summary-row">
            <span class="summary-label">当前操作方所属抬头：</span>
            <span>{{ assignOperatorCompanyTitle || '未识别' }}</span>
          </div>
          <div class="assign-party-summary-row">
            <span class="summary-label">本次交付方（乙方）：</span>
            <span>{{ assignCurrentDeliveryParty }}</span>
          </div>
          <div v-if="isMergeAssignMode" class="assign-party-summary-row">
            <span class="summary-label">本次合并范围：</span>
            <span>{{ mergeAssignSummary }}</span>
          </div>
          <div class="assign-party-summary-tip">
            说明：当前弹窗用于确认本次交付/采购关系；你当前是在选择乙方，不会覆盖原订单甲方信息。
          </div>
        </div>
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
            <el-form-item label="乙方业务员">
              <el-select
                v-if="(assignPartnerInfoList || []).length > 1"
                v-model="assignForm.partyBRepresentative"
                placeholder="请选择该交付方下的乙方业务员（用户名）"
                clearable
                filterable
                style="width: 100%"
                @change="onPartyBRepresentativeSelect"
              >
                <el-option
                  v-for="p in (assignPartnerInfoList || [])"
                  :key="(p.username || '') + (p.id || '')"
                  :label="(p.username || '') + (p.contactPerson ? ' - ' + p.contactPerson : '')"
                  :value="(p.username || '').trim()"
                />
              </el-select>
              <el-input
                v-else
                v-model="assignForm.partyBRepresentative"
                placeholder="仅从「合作管理-用户信息维护」该抬头的用户名带出"
                readonly
                style="width: 100%"
              />
              <div v-if="showAssignPartnerHint" class="assign-partner-hint">
                <span class="hint-text">PS：如果交付方不在列表，或交付方尚未维护乙方业务员（用户名），请先</span>
                <router-link to="/cooperation/partner-info" class="hint-link" @click="assignDialogVisible = false">点击跳转到用户信息维护</router-link>
                <span class="hint-text">，维护交付方信息和用户名后再指派。指派仅使用用户信息维护中的用户名，不会从用户管理-用户列表获取。</span>
              </div>
              <div v-if="showAssignOfflineSalesBlock" :class="['assign-offline-sales-card', { 'is-filled': hasAssignOfflineSalesValue }]">
                <div class="assign-offline-sales-title">线下销售</div>
                <el-select
                  v-model="assignForm.offlineSales"
                  filterable
                  allow-create
                  default-first-option
                  clearable
                  placeholder="请选择或输入线下销售姓名"
                  style="width: 100%"
                  @change="onAssignOfflineSalesChange"
                  @blur="onAssignOfflineSalesBlur"
                >
                  <el-option
                    v-for="name in assignOfflineSalesOptions"
                    :key="name"
                    :label="name"
                    :value="name"
                  />
                </el-select>
                <div class="assign-offline-sales-tip">
                  第三方订单由上海热像科技股份有限公司承接交付时，请填写对应线下销售姓名；该值会保存到订单列表并支持下次快速选择。
                </div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="扣点(%)">
              <el-input v-model.number="assignForm.deductionRate" placeholder="如2" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row v-if="!isMergeAssignMode" :gutter="20">
          <el-col :span="12">
            <el-form-item label="交付方采购价" :class="['assign-emphasis-item', { 'is-filled': hasAssignDeliveryPriceValue }]">
              <el-input
                v-model="assignForm.deliveryPartyPurchasePrice"
                placeholder="A的采购价=B的销售价"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品型号">
              <el-input
                v-model="assignForm.model"
                disabled
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="!isMergeAssignMode" label="金额核对" class="assign-single-preview-item">
          <div class="assign-single-preview">
            <div class="assign-single-preview-title">
              当前商品在指派前的金额核对
            </div>
            <div class="assign-single-preview-summary">
              <span class="summary-chip">型号：{{ singleAssignPreview.model }}</span>
              <span class="summary-chip">数量：{{ singleAssignPreview.quantity }}</span>
              <span class="summary-chip">所属抬头含税单价：{{ singleAssignPreview.taxIncludedPriceText }}</span>
              <span class="summary-chip">所属抬头含税总价：{{ singleAssignPreview.taxIncludedTotalText }}</span>
              <span class="summary-chip">当前采购单价：{{ singleAssignPreview.purchasePriceText }}</span>
              <span class="summary-chip">当前采购总额：{{ singleAssignPreview.purchaseTotalText }}</span>
            </div>
            <div class="assign-single-preview-tip">
              这里用于确认当前这行商品的数量、销售金额和采购金额，避免单独指派时选错价格。
            </div>
          </div>
        </el-form-item>
        <el-form-item v-else label="商品金额明细" class="assign-merge-preview-item">
          <div class="assign-merge-preview">
            <div class="assign-merge-preview-summary">
              <span class="summary-chip">商品行数：{{ mergeAssignPreviewRows.length }}</span>
              <span class="summary-chip">合计数量：{{ mergeAssignPreviewTotalQty }}</span>
              <span class="summary-chip">含税销售总额：{{ formatCurrencyDisplay(mergeAssignPreviewSalesTotal) }}</span>
              <span class="summary-chip">含税采购总额：{{ formatCurrencyDisplay(mergeAssignPreviewPurchaseTotal) }}</span>
            </div>
            <div class="assign-merge-preview-tip">
              按采购订单列表的核对方式展示本次合并商品，提交前可逐行确认型号、数量、单价和总价。
            </div>
            <el-table
              :data="mergeAssignPreviewRows"
              border
              stripe
              size="small"
              max-height="260"
              class="assign-merge-preview-table"
            >
              <el-table-column type="index" label="#" width="56" align="center" />
              <el-table-column prop="model" label="型号" min-width="160" show-overflow-tooltip />
              <el-table-column prop="quantityText" label="数量" width="90" align="center" />
              <el-table-column prop="taxIncludedPriceText" label="含税单价" width="120" align="right" />
              <el-table-column prop="taxIncludedTotalText" label="含税总价" width="120" align="right" />
              <el-table-column label="采购单价" width="150" align="right">
                <template #default="scope">
                  <el-input
                    v-model="mergeAssignPurchasePriceEdits[String(scope.row.sourceOrderId)]"
                    size="small"
                    class="assign-merge-price-input"
                    inputmode="decimal"
                    placeholder="可手动修改"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="purchaseTotalText" label="采购总额" width="120" align="right" />
            </el-table>
          </div>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="甲方抬头">
              <el-input v-model="assignForm.platformName" placeholder="本次指派不覆盖原订单甲方" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="账期" :class="['assign-emphasis-item', { 'is-filled': hasAssignPaymentMethodValue }]">
              <el-select v-model="assignForm.paymentMethod" placeholder="请选择账期" style="width: 100%" filterable allow-create>
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
            <el-form-item label="合同编号" :class="['assign-emphasis-item', { 'is-filled': hasAssignContractNoValue }]">
              <el-input
                v-model="assignForm.contractNo"
                placeholder="默认按系统规则带出，可手动修改"
                @input="assignNumberDirty.contractNo = true"
              />
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
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="assignDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="generateContract" :loading="isGeneratingContract" :disabled="isGeneratingContract">{{ assignSubmitLabel }}</el-button>
        </span>
      </template>
    </el-dialog>
      </el-tab-pane>

      <el-tab-pane label="销售订单指派" name="assign">
        <el-card class="filter-card">
          <el-form :inline="true" :model="assignFilterForm">
            <el-form-item label="甲方订单号">
              <el-input v-model="assignFilterForm.platformOrderNo" placeholder="甲方订单号" clearable />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="assignFilterForm.status" placeholder="全部" clearable style="width: 140px">
                <el-option label="待指派" value="待指派" />
                <el-option label="待合同盖章" value="待合同盖章" />
                <el-option label="待发货" value="待发货" />
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
          <el-alert
            v-if="mergeAssignableRowsWithoutMasterCount > 0"
            class="assign-master-anchor-alert"
            type="warning"
            :closable="false"
            show-icon
            :title="`当前列表有 ${mergeAssignableRowsWithoutMasterCount} 行历史订单未挂接主单`"
            description="这些行暂不支持合并指派；只有已挂接 masterId 的商品行，才能按主单维度勾选合并。"
          />
          <div class="assign-toolbar">
            <div class="assign-toolbar-left">
              <el-button type="primary" plain :disabled="!canOpenMergeAssign" @click="openMergeAssignDialog">{{ mergeAssignButtonLabel }}</el-button>
              <el-button type="danger" plain :disabled="!canBatchReturnSelected" @click="handleBatchReturnSelected">同主单整单退回</el-button>
              <span class="assign-toolbar-tip">{{ mergeAssignToolbarTip }}</span>
            </div>
            <div v-if="selectedAssignRows.length > 0" class="assign-toolbar-right">{{ mergeAssignSelectionHint }}</div>
          </div>
          <el-table :data="assignTableData" border stripe v-loading="assignLoading" @selection-change="handleAssignSelectionChange" :row-class-name="getAssignTableRowClassName">
            <el-table-column type="selection" width="48" :selectable="isAssignRowSelectableForMerge" />
            <el-table-column prop="masterId" label="主单ID" width="104">
              <template #default="scope">
                <span v-if="scope.row.masterId" :class="['master-group-chip', `master-group-chip-${getMasterGroupTone(scope.row)}`]">
                  主单 {{ scope.row.masterId }}
                </span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="platformOrderNo" label="甲方订单号" width="140" show-overflow-tooltip />
            <el-table-column prop="omsOrderNo" label="OMS订单号" width="140" show-overflow-tooltip />
            <el-table-column prop="platformName" label="甲方" width="100" />
            <el-table-column prop="model" label="型号" width="140" show-overflow-tooltip />
            <el-table-column prop="quantity" label="数量" width="80" align="center" />
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
            <el-table-column prop="deductionRate" label="扣点" width="80" />
            <el-table-column prop="deliveryPartyPurchasePrice" label="交付方采购价" width="120" align="right" />
            <el-table-column prop="assignTime" label="指派时间" width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="scope">
                <el-button v-if="canAssign(scope.row)" type="primary" size="small" @click="handleAssignSalesOrder(scope.row)">指派</el-button>
                <el-button v-if="canReassign(scope.row)" type="success" size="small" @click="handleAssignSalesOrder(scope.row)">转派</el-button>
                <el-button v-if="canReassign(scope.row)" type="danger" link size="small" @click="handleSingleReturn(scope.row)">退回</el-button>
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

    <ErpEntryDialog
      v-model="erpEntryDialogVisible"
      target-type="purchase"
      :row="currentErpEntryRow"
      :rows="currentErpEntryRows"
      @saved="handleErpEntrySaved"
    />

    <el-dialog
      v-model="structureInspectVisible"
      title="采购单链路观察"
      width="1100px"
      destroy-on-close
    >
      <div v-loading="structureInspectLoading" class="structure-inspect-wrap">
        <el-alert
          v-if="structureInspectError"
          :title="structureInspectError"
          type="warning"
          :closable="false"
          show-icon
        />

        <div v-if="currentStructurePurchaseOrder" class="structure-inspect-head">
          <div>采购单号：{{ currentStructurePurchaseOrder.purchaseOrderNo || '-' }}</div>
          <div>OMS订单号：{{ currentStructurePurchaseOrder.omsOrderNo || '-' }}</div>
          <div>来源销售单ID：{{ currentStructurePurchaseOrder.sourceSalesOrderId || '-' }}</div>
          <div>主单ID：{{ currentStructurePurchaseOrder.masterId || '-' }}</div>
          <div>分配ID：{{ currentStructurePurchaseOrder.allocationId || '-' }}</div>
        </div>

        <div v-if="!structureInspectError && structureInspectMaster" class="structure-section">
          <div class="structure-section-title">主单摘要</div>
          <el-descriptions :column="4" border size="small">
            <el-descriptions-item label="主单号">{{ structureInspectMaster.masterNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="主单状态">{{ structureInspectMaster.masterStatus || '-' }}</el-descriptions-item>
            <el-descriptions-item label="指派状态">{{ structureInspectMaster.assignStatus || '-' }}</el-descriptions-item>
            <el-descriptions-item label="财务状态">{{ structureInspectMaster.financeStatus || '-' }}</el-descriptions-item>
            <el-descriptions-item label="根OMS订单号">{{ structureInspectMaster.rootOmsOrderNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="甲方抬头">{{ structureInspectMaster.partyATitle || '-' }}</el-descriptions-item>
            <el-descriptions-item label="商品行数">{{ structureInspectMasterSummary?.orderCount ?? structureInspectMaster.totalLineCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="分配数">{{ structureInspectMasterSummary?.allocationCount ?? 0 }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div v-if="!structureInspectError && structureInspectAllocation" class="structure-section">
          <div class="structure-section-title">当前分配摘要</div>
          <el-descriptions :column="4" border size="small">
            <el-descriptions-item label="分配单号">{{ structureInspectAllocation.allocationNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="分配状态">{{ structureInspectAllocation.allocationStatus || '-' }}</el-descriptions-item>
            <el-descriptions-item label="分配数量">{{ structureInspectAllocationSummary?.allocatedQty ?? structureInspectAllocation.allocatedQty ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="链尾">{{ structureInspectAllocation.isChainTail ? '是' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="被分配方">{{ structureInspectAllocation.assignedCompanyTitle || '-' }}</el-descriptions-item>
            <el-descriptions-item label="被分配用户">{{ structureInspectAllocation.assignedUsername || '-' }}</el-descriptions-item>
            <el-descriptions-item label="子分配数">{{ structureInspectAllocationSummary?.childCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="SN数">{{ structureInspectAllocationSummary?.serialCount ?? 0 }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div v-if="!structureInspectError" class="structure-section">
          <div class="structure-section-title">主单订单行</div>
          <el-table :data="structureInspectOrders" border stripe size="small" max-height="220">
            <el-table-column prop="id" label="订单ID" width="90" />
            <el-table-column prop="lineNo" label="行号" width="80" />
            <el-table-column prop="platformOrderNo" label="甲方订单号" min-width="180" show-overflow-tooltip />
            <el-table-column prop="omsOrderNo" label="OMS订单号" min-width="170" show-overflow-tooltip />
            <el-table-column prop="model" label="型号" min-width="140" show-overflow-tooltip />
            <el-table-column prop="quantity" label="数量" width="90" />
            <el-table-column prop="status" label="状态" width="120" />
            <el-table-column prop="masterId" label="主单ID" width="90" />
            <el-table-column prop="allocationId" label="分配ID" width="90" />
          </el-table>
        </div>

        <div v-if="!structureInspectError" class="structure-section">
          <div class="structure-section-title">分配分支</div>
          <el-table :data="structureInspectAllocations" border stripe size="small" max-height="220">
            <el-table-column prop="id" label="分配ID" width="90" />
            <el-table-column prop="allocationNo" label="分配单号" min-width="180" show-overflow-tooltip />
            <el-table-column prop="allocatedQty" label="数量" width="90" />
            <el-table-column prop="allocationStatus" label="状态" width="120" />
            <el-table-column prop="assignedCompanyTitle" label="被分配方" min-width="180" show-overflow-tooltip />
            <el-table-column prop="assignedUsername" label="被分配用户" width="120" />
            <el-table-column prop="hopNo" label="跳数" width="80" />
            <el-table-column label="链尾" width="80">
              <template #default="{ row }">{{ row.isChainTail ? '是' : '否' }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="!structureInspectError" class="structure-section">
          <div class="structure-section-title">SN明细</div>
          <el-table :data="structureInspectSerialItems" border stripe size="small" max-height="220">
            <el-table-column prop="id" label="SN ID" width="90" />
            <el-table-column prop="snCode" label="SN编码" min-width="220" show-overflow-tooltip />
            <el-table-column prop="productModel" label="型号" min-width="160" show-overflow-tooltip />
            <el-table-column prop="serialStatus" label="状态" width="120" />
            <el-table-column prop="salesOrderId" label="订单ID" width="90" />
            <el-table-column prop="allocationId" label="分配ID" width="90" />
            <el-table-column prop="batchId" label="批次ID" width="90" />
          </el-table>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button
            v-if="currentStructurePurchaseOrder && (!currentStructurePurchaseOrder.masterId || !currentStructurePurchaseOrder.allocationId || !currentStructurePurchaseOrder.sourceSalesOrderId)"
            :loading="structureBackfillLoading"
            @click="backfillCurrentPurchaseOrderStructure"
          >
            补挂当前采购单
          </el-button>
          <el-button @click="structureInspectVisible = false">关闭</el-button>
          <el-button
            type="primary"
            :loading="structureInspectLoading"
            @click="refreshStructureInspect"
            :disabled="!currentStructurePurchaseOrder"
          >
            刷新
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, reactive, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CopyDocument, MoreFilled, Money, Document, List, Delete, Van, Refresh, CircleCheck } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import draggable from 'vuedraggable'
import ErpEntryDialog from '@/components/ErpEntryDialog.vue'
import request from '../../utils/request'
import { apiBase } from '@/utils/apiBase'

const route = useRoute()
const router = useRouter()

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
  erpEntryStatus: string
  status: string
}

interface LogisticsTrace {
  time: string
  status: string
  desc: string
}

const filterForm = ref<FilterForm>({
  keyword: '',
  dateRange: [],
  erpEntryStatus: '',
  status: ''
})

const mainTab = ref('purchase')
const detailVisible = ref(false)
const currentOrder = ref<any>(null)
const structureInspectVisible = ref(false)
const structureInspectLoading = ref(false)
const structureBackfillLoading = ref(false)
const currentStructurePurchaseOrder = ref<any>(null)
const structureInspectMaster = ref<any>(null)
const structureInspectMasterSummary = ref<any>(null)
const structureInspectAllocation = ref<any>(null)
const structureInspectAllocationSummary = ref<any>(null)
const structureInspectOrders = ref<any[]>([])
const structureInspectAllocations = ref<any[]>([])
const structureInspectSerialItems = ref<any[]>([])
const structureInspectError = ref('')
const logisticsDialogVisible = ref(false)
const currentLogisticsOrder = ref<any>(null)
const logisticsInfo = ref<{ company?: string; trackingNumber?: string; traces?: LogisticsTrace[] } | null>(null)
const logisticsLoading = ref(false)
const erpEntryDialogVisible = ref(false)
const currentErpEntryRow = ref<any>(null)
const currentErpEntryRows = ref<any[]>([])
const purchaseOperationLogs = ref<any[]>([])
const loading = ref(false)
// 详情 Tab 表格数据：聚合采购单按逐商品行展示，普通采购单退化为单行
const detailTableRows = computed(() => {
  if (!currentOrder.value) return []
  const detailRows = currentOrder.value?.detailRows
  return Array.isArray(detailRows) && detailRows.length > 0 ? detailRows : [currentOrder.value]
})
const detailFocusSourceSalesOrderId = computed(() => currentOrder.value?.sourceSalesOrderId ?? null)
const detailFocusLabel = computed(() => {
  if (!currentOrder.value) return ''
  const model = String(currentOrder.value?.model || '').trim()
  const quantity = currentOrder.value?.quantity
  if (!model && quantity == null) return ''
  return `${model || '-'}${quantity != null ? ` x${quantity}` : ''}`
})
const detailTotalQuantity = computed(() => detailTableRows.value.reduce((sum, row) => sum + Number(row?.quantity || 0), 0))
const detailTotalAmount = computed(() => detailTableRows.value.reduce((sum, row) => sum + Number(row?.taxIncludedPurchaseTotal || 0), 0))

// 销售订单指派 Tab
const assignFilterForm = reactive({ platformOrderNo: '', status: '' })
const assignTableData = ref<any[]>([])
const assignLoading = ref(false)
const assignPage = ref(1)
const assignPageSize = ref(10)
const assignTotal = ref(0)
const selectedAssignRows = ref<any[]>([])
const assignDialogVisible = ref(false)
/** URL 传入的 orderId 仅用于高亮该行，不自动打开弹窗 */
const assignHighlightOrderId = ref<string | null>(null)
const currentAssignOrder = ref<any>(null)
const mergeAssignRows = ref<any[]>([])
const mergeAssignPurchasePriceEdits = ref<Record<string, string>>({})
const assignDialogMode = ref<'single' | 'merge'>('single')
const isReassignFlow = ref(false)
const assignDeliveryPartyOptions = ref<string[]>([])
const contractTemplateList = ref<any[]>([])
const partyBUsers = ref<any[]>([])
/** 当前所选交付方在「用户信息维护」中的记录，用于带出乙方业务员（用户名） */
const assignPartnerInfo = ref<{ username?: string; name?: string; title?: string; contactPerson?: string } | null>(null)
/** 该抬头下全部 PartnerInfo（同一公司多用户时用于乙方业务员下拉选唯一用户名） */
const assignPartnerInfoList = ref<any[]>([])
const isGeneratingContract = ref(false)
const currentUsername = ref(localStorage.getItem('username') || '')
const currentUserId = ref<number | null>(null)
const assignOperatorCompanyTitle = ref((localStorage.getItem('companyTitle') || '').trim())
const assignOfflineSalesOptions = ref<string[]>([])
const permissionSet = computed(() => {
  const raw = localStorage.getItem('permissions') || ''
  return new Set(raw.split(',').map((s: string) => s.trim()).filter(Boolean))
})
const isAdminUser = computed(() => (localStorage.getItem('username') || '').trim() === 'admin')
const hasPermission = (perm: string) => isAdminUser.value || permissionSet.value.has(perm)
const canApplyPaymentRequest = computed(() => hasPermission('purchase_payment_apply'))
const canTrackInboundInvoice = computed(() => hasPermission('purchase_invoice_track'))
const canFinancePayment = computed(() => hasPermission('purchase_payment_finance'))
const canViewPaymentRequest = computed(() =>
  canApplyPaymentRequest.value || canTrackInboundInvoice.value || canFinancePayment.value || hasPermission('purchase_payment_view_all')
)
const isCurrentFeichukeOperator = computed(() => {
  const username = (currentUsername.value || '').trim().toLowerCase()
  const company = (localStorage.getItem('companyTitle') || '').trim()
  return username === 'sonmin' || username === 'songmin' || company.includes('飞础科智慧科技（上海）有限公司')
})
const showAssignPartnerHint = computed(() => {
  const dp = (assignForm.deliveryParty || '').trim()
  if (!dp) return false
  const list = assignPartnerInfoList.value || []
  if (list.length === 0) return true
  const hasAnyUsername = list.some((p: any) => (p?.username || '').trim())
  return !hasAnyUsername
})
const assignOriginalPartyATitle = computed(() => {
  const row = currentAssignOrder.value || {}
  return String(row.platformName || row.operationEntityTitle || row.partyATitle || '').trim() || '未识别'
})
const assignCurrentDeliveryParty = computed(() => String(assignForm.deliveryParty || '').trim() || '未选择')
const getMasterGroupTone = (row: any) => {
  const masterId = Number(row?.masterId)
  if (!Number.isFinite(masterId) || masterId <= 0) return 0
  return Math.abs(masterId) % 4 + 1
}
const getAssignTableRowClassName = ({ row }: { row: any }) => {
  const classes: string[] = []
  if (assignHighlightOrderId.value && String(row?.id) === assignHighlightOrderId.value) {
    classes.push('assign-row-highlight')
  }
  const tone = getMasterGroupTone(row)
  if (tone > 0) {
    classes.push(`master-group-row-${tone}`)
  }
  return classes.join(' ')
}
const getPurchaseGroupKey = (row: any) => {
  return String(row?.contractNo || row?.purchaseOrderNo || row?.mergeSelectionKey || row?.id || '').trim()
}
const getPurchaseGroupTone = (row: any) => {
  const key = getPurchaseGroupKey(row)
  if (!key) return 0
  let seed = 0
  for (let i = 0; i < key.length; i += 1) {
    seed += key.charCodeAt(i)
  }
  return Math.abs(seed) % 4 + 1
}
const getPurchaseGroupSize = (row: any) => {
  const key = getPurchaseGroupKey(row)
  if (!key) return 1
  return tableData.value.filter((item: any) => getPurchaseGroupKey(item) === key).length || 1
}
const isFirstPurchaseGroupRow = (index: number) => {
  if (index <= 0) return true
  const current = tableData.value[index]
  const previous = tableData.value[index - 1]
  return getPurchaseGroupKey(current) !== getPurchaseGroupKey(previous)
}
const getPurchaseTableRowClassName = ({ row }: { row: any }) => {
  const tone = getPurchaseGroupTone(row)
  return tone > 0 ? `master-group-row-${tone}` : ''
}
const isMergeAssignMode = computed(() => assignDialogMode.value === 'merge')
const isRowMasterAnchored = (row: any) => row?.masterId !== null && row?.masterId !== undefined && row?.masterId !== ''
const isRowOperableForAssign = (row: any) => canAssign(row) || canReassign(row)
const isAssignRowSelectableForMerge = (row: any) => isRowMasterAnchored(row) && isRowOperableForAssign(row)
const mergeAssignableRowsWithoutMasterCount = computed(() => {
  return assignTableData.value.filter((row: any) => !isRowMasterAnchored(row)).length
})
const selectedAssignMasterIds = computed(() => {
  const ids = selectedAssignRows.value
    .map((row: any) => row?.masterId)
    .filter((id: any) => id !== null && id !== undefined && id !== '')
    .map((id: any) => String(id))
  return Array.from(new Set(ids))
})
const mergeAssignActionMode = computed<'assign' | 'reassign' | 'invalid'>(() => {
  if (selectedAssignRows.value.length === 0) return 'invalid'
  const allAssignable = selectedAssignRows.value.every((row: any) => canAssign(row))
  const allReassignable = selectedAssignRows.value.every((row: any) => canReassign(row))
  if (allAssignable) return 'assign'
  if (allReassignable) return 'reassign'
  return 'invalid'
})
const mergeAssignSummary = computed(() => {
  if (!isMergeAssignMode.value) return ''
  const rows = mergeAssignRows.value || []
  if (rows.length === 0) return '未选择商品行'
  const qty = rows.reduce((sum: number, row: any) => sum + Number(row?.quantity || 0), 0)
  return `主单 ${rows[0]?.masterId ?? '-'} 下 ${rows.length} 行，合计数量 ${qty}`
})
const roundMoneyValue = (value: number) => Math.round(value * 100) / 100
const normalizeNumericString = (value: unknown) => {
  if (value == null) return ''
  return String(value).replace(/[^\d.-]/g, '').trim()
}
const parseEditableMoney = (value: unknown) => {
  const text = normalizeNumericString(value)
  if (!text) return null
  const amount = Number(text)
  return Number.isFinite(amount) ? roundMoneyValue(amount) : null
}
const parseMergeOrderDetails = (row: any) => {
  const raw = row?.orderDetails
  if (!raw) return null
  if (typeof raw === 'object') return raw
  if (typeof raw !== 'string') return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}
const firstPositiveMoney = (...values: unknown[]) => {
  for (const value of values) {
    const amount = parseEditableMoney(value)
    if (amount != null && amount > 0) return amount
  }
  return null
}
const resolveDetailPurchasePrice = (row: any) => {
  const details = parseMergeOrderDetails(row)
  const reconciliationList = Array.isArray(details?.reconciliations) ? details.reconciliations : []
  const productList = Array.isArray(details?.products) ? details.products : []
  for (const reconciliation of reconciliationList) {
    const amount = firstPositiveMoney(
      reconciliation?.deliveryPartyPurchasePrice,
      reconciliation?.purchasePrice,
      reconciliation?.taxIncludedPurchasePrice
    )
    if (amount != null) return amount
  }
  for (const product of productList) {
    const amount = firstPositiveMoney(
      product?.deliveryPartyPurchasePrice,
      product?.purchasePrice,
      product?.taxIncludedPurchasePrice
    )
    if (amount != null) return amount
  }
  return null
}
const resolveDeductionCalculatedPurchasePrice = (row: any) => {
  const unitPrice = parseEditableMoney(row?.taxIncludedPrice)
  if (unitPrice == null || unitPrice <= 0) return null
  const rate = parseEditableMoney(assignForm.deductionRate)
  if (rate == null || rate < 0) return null
  return roundMoneyValue(unitPrice * (1 - rate / 100))
}
const resolveMergeRowDefaultPurchasePrice = (row: any) => {
  const detailPrice = resolveDetailPurchasePrice(row)
  if (detailPrice != null && detailPrice > 0) return detailPrice
  const deductionPrice = resolveDeductionCalculatedPurchasePrice(row)
  if (deductionPrice != null && deductionPrice > 0) return deductionPrice
  const topLevelPrice = parseEditableMoney(row?.deliveryPartyPurchasePrice)
  if (topLevelPrice != null && topLevelPrice > 0) return topLevelPrice
  return null
}
const buildMergeAssignPurchasePriceEdits = (rows: any[]) => {
  const next: Record<string, string> = {}
  ;(rows || []).forEach((row: any) => {
    const key = String(row?.id ?? '')
    if (!key) return
    const resolved = resolveMergeRowDefaultPurchasePrice(row)
    next[key] = resolved != null ? resolved.toFixed(2) : ''
  })
  mergeAssignPurchasePriceEdits.value = next
}
const formatCurrencyDisplay = (value: unknown) => {
  const amount = Number(value)
  if (!Number.isFinite(amount)) return '-'
  return `¥${amount.toFixed(2)}`
}
const mergeAssignPreviewRows = computed(() => {
  if (!isMergeAssignMode.value) return []
  return (mergeAssignRows.value || []).map((row: any) => {
    const quantity = Number(row?.quantity || 0)
    const unitPrice = Number(row?.taxIncludedPrice || 0)
    const totalPrice = Number(row?.taxIncludedTotal || 0)
    const editedPurchaseUnitPrice = parseEditableMoney(mergeAssignPurchasePriceEdits.value[String(row?.id ?? '')])
    const purchaseUnitPrice = editedPurchaseUnitPrice != null ? editedPurchaseUnitPrice : (resolveMergeRowDefaultPurchasePrice(row) ?? 0)
    const effectiveQty = quantity > 0 ? quantity : 1
    const purchaseTotal = Number.isFinite(purchaseUnitPrice) && purchaseUnitPrice > 0
      ? purchaseUnitPrice * effectiveQty
      : NaN
    return {
      sourceOrderId: row?.id,
      id: row?.id ?? `${row?.masterId || 'master'}-${row?.model || 'row'}`,
      model: String(row?.model || '').trim() || '-',
      quantityText: String(quantity > 0 ? quantity : 1),
      taxIncludedPriceText: formatCurrencyDisplay(unitPrice),
      taxIncludedTotalText: formatCurrencyDisplay(totalPrice),
      purchasePriceText: formatCurrencyDisplay(purchaseUnitPrice),
      purchaseTotalText: formatCurrencyDisplay(purchaseTotal),
      quantityValue: effectiveQty,
      taxIncludedTotalValue: Number.isFinite(totalPrice) ? totalPrice : 0,
      purchaseTotalValue: Number.isFinite(purchaseTotal) ? purchaseTotal : 0
    }
  })
})
const mergeAssignPreviewTotalQty = computed(() => {
  return mergeAssignPreviewRows.value.reduce((sum: number, row: any) => sum + Number(row?.quantityValue || 0), 0)
})
const mergeAssignPreviewSalesTotal = computed(() => {
  return mergeAssignPreviewRows.value.reduce((sum: number, row: any) => sum + Number(row?.taxIncludedTotalValue || 0), 0)
})
const mergeAssignPreviewPurchaseTotal = computed(() => {
  return mergeAssignPreviewRows.value.reduce((sum: number, row: any) => sum + Number(row?.purchaseTotalValue || 0), 0)
})
const singleAssignPreview = computed(() => {
  const row = currentAssignOrder.value || {}
  const quantity = Number(row?.quantity || 0)
  const effectiveQty = quantity > 0 ? quantity : 1
  const taxIncludedPrice = parseEditableMoney(row?.taxIncludedPrice) ?? 0
  const taxIncludedTotal = parseEditableMoney(row?.taxIncludedTotal) ?? roundMoneyValue(taxIncludedPrice * effectiveQty)
  const purchasePrice = parseEditableMoney(assignForm.deliveryPartyPurchasePrice)
    ?? parseEditableMoney(row?.deliveryPartyPurchasePrice)
    ?? resolveMergeRowDefaultPurchasePrice(row)
    ?? 0
  const purchaseTotal = roundMoneyValue(purchasePrice * effectiveQty)
  return {
    model: String(row?.model || '').trim() || '-',
    quantity: effectiveQty,
    taxIncludedPriceText: formatCurrencyDisplay(taxIncludedPrice),
    taxIncludedTotalText: formatCurrencyDisplay(taxIncludedTotal),
    purchasePriceText: formatCurrencyDisplay(purchasePrice),
    purchaseTotalText: formatCurrencyDisplay(purchaseTotal)
  }
})
const mergeAssignSelectionHint = computed(() => {
  if (selectedAssignRows.value.length === 0) return ''
  if (selectedAssignRows.value.length < 2) return '至少勾选 2 行才能合并指派'
  if (selectedAssignRows.value.some((row: any) => !isRowMasterAnchored(row))) return '当前勾选里含未挂接主单的历史订单，请先补挂主单锚点'
  if (selectedAssignMasterIds.value.length !== 1) return '当前勾选包含多个主单，请缩小到同一主单'
  const blocked = selectedAssignRows.value.filter((row: any) => !isRowOperableForAssign(row))
  if (blocked.length > 0) return '当前勾选里含不可指派/转派的订单'
  if (mergeAssignActionMode.value === 'invalid') return '当前勾选同时包含待指派与已指派订单，请分别处理'
  return `当前主单：${selectedAssignMasterIds.value[0]}，可发起${mergeAssignActionMode.value === 'reassign' ? '合并转派' : '合并指派'}`
})
const canOpenMergeAssign = computed(() => {
  return selectedAssignRows.value.length >= 2
    && selectedAssignRows.value.every((row: any) => isRowMasterAnchored(row))
    && selectedAssignMasterIds.value.length === 1
    && selectedAssignRows.value.every((row: any) => isRowOperableForAssign(row))
    && mergeAssignActionMode.value !== 'invalid'
})
const canBatchReturnSelected = computed(() => {
  return selectedAssignRows.value.length >= 1
    && selectedAssignRows.value.every((row: any) => isRowMasterAnchored(row))
    && selectedAssignMasterIds.value.length === 1
    && selectedAssignRows.value.every((row: any) => canReassign(row))
})
const mergeAssignButtonLabel = computed(() => mergeAssignActionMode.value === 'reassign' ? '同主单合并转派' : '同主单合并指派')
const mergeAssignToolbarTip = computed(() => {
  if (mergeAssignActionMode.value === 'reassign') {
    return `已选 ${selectedAssignRows.value.length} 行。支持同一主单下多行一次转派，统一生成 1 份聚合采购单和 1 份执行合同。`
  }
  return `已选 ${selectedAssignRows.value.length} 行。仅支持同一主单下多行一次生成 1 份采购单和 1 份执行合同。`
})
const assignDialogTitle = computed(() => {
  if (isMergeAssignMode.value) return isReassignFlow.value ? '同主单合并转派' : '同主单合并指派'
  return canReassign(currentAssignOrder.value) ? '订单转派' : '订单指派'
})
const assignSubmitLabel = computed(() => {
  if (isMergeAssignMode.value) return isReassignFlow.value ? '生成聚合采购单/合同并转派' : '生成聚合采购单/合同'
  return canReassign(currentAssignOrder.value) ? '生成合同并转派' : '生成合同'
})
const showAssignOfflineSalesBlock = computed(() => {
  const orderType = String(currentAssignOrder.value?.orderType || '').trim()
  return assignCurrentDeliveryParty.value === '上海热像科技股份有限公司' && orderType.includes('第三方')
})
const assignForm = reactive({
  deliveryParty: '',
  model: '',
  deductionRate: '' as any,
  deliveryPartyPurchasePrice: '',
  templateUrl: '',
  partyBRepresentative: '',
  platformName: '',
  paymentMethod: '',
  offlineSales: '',
  contractNo: '',
  purchaseOrderNo: ''
})
const hasAssignOfflineSalesValue = computed(() => String(assignForm.offlineSales || '').trim().length > 0)
const hasAssignDeliveryPriceValue = computed(() => String(assignForm.deliveryPartyPurchasePrice ?? '').trim() !== '')
const hasAssignPaymentMethodValue = computed(() => String(assignForm.paymentMethod || '').trim().length > 0)
const hasAssignContractNoValue = computed(() => String(assignForm.contractNo || '').trim().length > 0)
const assignNumberDirty = reactive({
  contractNo: false,
  purchaseOrderNo: false
})
const resetAssignNumberDraftState = () => {
  assignNumberDirty.contractNo = false
  assignNumberDirty.purchaseOrderNo = false
  assignForm.contractNo = ''
  assignForm.purchaseOrderNo = ''
}

const ASSIGN_OFFLINE_SALES_STORAGE_KEY = 'oms_assign_offline_sales_options'

const loadAssignOfflineSalesOptions = () => {
  try {
    const saved = localStorage.getItem(ASSIGN_OFFLINE_SALES_STORAGE_KEY)
    if (!saved) return
    const parsed = JSON.parse(saved)
    assignOfflineSalesOptions.value = Array.isArray(parsed)
      ? parsed.map((item: any) => String(item || '').trim()).filter(Boolean)
      : []
  } catch {
    assignOfflineSalesOptions.value = []
  }
}

const rememberAssignOfflineSales = (value: string) => {
  const text = String(value || '').trim()
  if (!text) return
  if (!assignOfflineSalesOptions.value.includes(text)) {
    assignOfflineSalesOptions.value = [...assignOfflineSalesOptions.value, text]
    localStorage.setItem(ASSIGN_OFFLINE_SALES_STORAGE_KEY, JSON.stringify(assignOfflineSalesOptions.value))
  }
}

const onAssignOfflineSalesChange = (value: string) => {
  const text = String(value || '').trim()
  assignForm.offlineSales = text
  rememberAssignOfflineSales(text)
}

const onAssignOfflineSalesBlur = (e: FocusEvent) => {
  const text = (e.target as HTMLInputElement)?.value?.trim()
  if (!text) return
  assignForm.offlineSales = text
  rememberAssignOfflineSales(text)
}

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

// 付款申请（采购域）
const createPaymentRequestDialogVisible = ref(false)
const createPaymentRequestSubmitting = ref(false)
const createPaymentRequestForm = reactive({
  applyRemark: ''
})
const paymentRequestDetailVisible = ref(false)
const paymentRequestDetailTab = ref('base')
const currentPaymentRequest = ref<any>(null)
const paymentRequestInvoices = ref<any[]>([])
const paymentRequestStatusForm = reactive({
  status: '',
  remark: ''
})
const newInboundInvoiceForm = reactive({
  invoiceNumber: '',
  invoiceAmount: '',
  status: '待开票',
  remark: '',
  invoiceUrl: ''
})
const paymentVoucherForm = reactive({
  paidAmount: '',
  bankFlowNo: '',
  voucherUrl: ''
})

// 定义所有列配置
const allColumns = ref<ColumnConfig[]>([
  { label: 'contractNo', title: '合同编号', width: 160, visible: true },
  { label: 'status', title: '状态', width: 100, visible: true },
  { label: 'erpEntryStatus', title: '商务ERP录单', width: 220, visible: true },
  { label: 'purchaseType', title: '采购类型', width: 120, visible: true },
  { label: 'omsOrderNo', title: '工业电商销售订单号', width: 180, visible: true },
  { label: 'trackingNumber', title: '母单物流单号', width: 160, visible: true },
  { label: 'salesPerson', title: '业务员', width: 100, visible: true },
  { label: 'model', title: '型号', width: 120, visible: true },
  { label: 'quantity', title: '数量', width: 80, visible: true },
  { label: 'taxIncludedPurchasePrice', title: '含税采购单价', width: 130, visible: true },
  { label: 'taxIncludedPurchaseTotal', title: '含税采购总额', width: 130, visible: true },
  { label: 'deliveryDate', title: '交货日期', width: 120, visible: true },
  { label: 'paymentStatus', title: '付款状态', width: 100, visible: true },
  { label: 'paymentRequestStatus', title: '付款申请状态', width: 130, visible: true },
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
      const savedOrder = Array.isArray(settings.columnsOrder) ? settings.columnsOrder : []
      if (settings.columnsOrder) {
        // 按照保存的顺序重新排列
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
        const savedLabelSet = new Set<string>([...savedOrder, ...visibleLabels])
        orderedColumns.value.forEach(col => {
          if (savedLabelSet.has(col.label)) {
          col.visible = visibleLabels.includes(col.label)
            return
          }
          col.visible = allColumns.value.find(base => base.label === col.label)?.visible ?? true
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
const selectedPurchaseRows = ref<any[]>([])

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(1)

const handleSearch = () => {
  currentPage.value = 1
  fetchPurchaseOrders()
}

const resetSearch = () => {
  filterForm.value = { keyword: '', dateRange: [], erpEntryStatus: '', status: '' }
  currentPage.value = 1
  fetchPurchaseOrders()
}

const fetchPurchaseOrders = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/purchase-orders', {
      params: {
        keyword: filterForm.value.keyword,
        status: filterForm.value.status,
        erpEntryStatus: filterForm.value.erpEntryStatus,
        page: currentPage.value - 1,
        size: pageSize.value
      }
    })
    const content = res?.content || (Array.isArray(res) ? res : [])
    tableData.value = content
    await enrichPaymentRequestSummary(tableData.value)
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

const inferPurchaseLogisticsCompany = (trackingNumber: string | undefined | null) => {
  const no = String(trackingNumber || '').trim().toUpperCase()
  if (no.startsWith('JD')) return '京东物流'
  if (no.startsWith('YT')) return '圆通速递'
  if (no.startsWith('ZT')) return '中通快递'
  if (no.startsWith('ST')) return '申通快递'
  if (no.startsWith('YD')) return '韵达快递'
  if (no.startsWith('DB')) return '德邦快递'
  if (no.startsWith('EMS')) return '邮政EMS'
  return '顺丰速运'
}

const last4Phone = (phone: string | null | undefined): string | undefined => {
  if (!phone || typeof phone !== 'string') return undefined
  const digits = phone.replace(/\D/g, '')
  return digits.length >= 4 ? digits.slice(-4) : (digits || undefined)
}

const fetchLogisticsInfo = async (company: string, trackingNumber: string, receiverPhone?: string) => {
  logisticsLoading.value = true
  try {
    const params: Record<string, string> = { company, trackingNumber }
    const phone4 = last4Phone(receiverPhone)
    if (phone4) params.checkPhoneNo = phone4
    const res: any = await request.get('/logistics/query', { params })
    if (res?.isSuccess) {
      logisticsInfo.value = res
      return
    }
    ElMessage.error(res?.message || '获取物流信息失败')
  } catch (error) {
    console.error('Fetch purchase logistics info error:', error)
    ElMessage.error('获取物流信息失败')
  } finally {
    logisticsLoading.value = false
  }
}

const showLogisticsInfo = async (row: any) => {
  const tracking = String(row?.trackingNumber || '').trim()
  if (!tracking) {
    ElMessage.warning('暂无母单物流单号')
    return
  }
  currentLogisticsOrder.value = row
  logisticsInfo.value = null
  await fetchLogisticsInfo(
    inferPurchaseLogisticsCompany(tracking),
    tracking,
    row?.receiverPhone
  )
  logisticsDialogVisible.value = true
}

const refreshLogistics = () => {
  const row = currentLogisticsOrder.value
  if (!row?.trackingNumber) return
  fetchLogisticsInfo(
    inferPurchaseLogisticsCompany(row.trackingNumber),
    String(row.trackingNumber).trim(),
    row?.receiverPhone
  )
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

const getPaymentRequestStatusType = (status: string) => {
  switch (status) {
    case 'PAID': return 'success'
    case 'READY_TO_PAY': return 'warning'
    case 'INVOICE_TRACKING': return 'warning'
    case 'PENDING_REVIEW': return 'info'
    case 'REJECTED': return 'danger'
    case 'CLOSED': return 'info'
    default: return 'info'
  }
}

const getErpEntryStatusType = (status: string) => {
  switch (status) {
    case '已录单': return 'success'
    case '待系统录单': return 'warning'
    default: return 'info'
  }
}

const formatErpEntryTime = (value?: string) => {
  const text = String(value || '').trim()
  return text ? text.replace('T', ' ') : '-'
}

const openErpEntryDialog = (row: any) => {
  currentErpEntryRow.value = row
  currentErpEntryRows.value = row ? [row] : []
  erpEntryDialogVisible.value = true
}

const openBatchErpEntryDialog = () => {
  if (selectedPurchaseRows.value.length === 0) {
    ElMessage.warning('请先选择采购订单')
    return
  }
  const invalidRows = selectedPurchaseRows.value.filter((row: any) => !row?.erpEntryCanEdit)
  if (invalidRows.length > 0) {
    ElMessage.warning('所选采购订单中包含无权限维护商务ERP录单的条目，请调整后再试')
    return
  }
  currentErpEntryRows.value = [...selectedPurchaseRows.value]
  currentErpEntryRow.value = currentErpEntryRows.value[0] || null
  erpEntryDialogVisible.value = true
}

const openErpEntryScreenshot = (row: any) => {
  const rawUrl = String(row?.erpEntryScreenshotUrl || '').trim()
  if (!rawUrl) {
    ElMessage.warning('未找到已保存的截图')
    return
  }
  const url = rawUrl.startsWith('/') ? rawUrl : `/${rawUrl}`
  window.open(url, '_blank')
}

const handleErpEntrySaved = async () => {
  await fetchPurchaseOrders()
}

const enrichPaymentRequestSummary = async (rows: any[]) => {
  if (!Array.isArray(rows) || rows.length === 0) return
  await Promise.all(rows.map(async (row) => {
    if (!row?.id) return
    try {
      const list: any = await request.get(`/purchase-payment-requests/by-purchase-order/${row.id}`)
      const latest = Array.isArray(list) && list.length > 0 ? list[0] : null
      row.paymentRequestStatus = latest?.status || ''
      row.paymentRequestNo = latest?.requestNo || ''
      row.paymentRequestId = latest?.id || null
    } catch {
      row.paymentRequestStatus = ''
      row.paymentRequestNo = ''
      row.paymentRequestId = null
    }
  }))
}

const showDetail = (row: any) => {
  currentOrder.value = row
  detailVisible.value = true
  purchaseOperationLogs.value = []
  if (row?.id) {
    fetchPurchaseOperationLogs(row.id)
  }
}

const isFocusedDetailRow = (row: any) => {
  if (!row) return false
  return detailFocusSourceSalesOrderId.value != null
    && String(row.sourceSalesOrderId ?? '') === String(detailFocusSourceSalesOrderId.value)
}

const getDetailTableRowClassName = ({ row }: { row: any }) => {
  return isFocusedDetailRow(row) ? 'purchase-detail-row-highlight' : ''
}

const resetStructureInspectState = () => {
  structureInspectMaster.value = null
  structureInspectMasterSummary.value = null
  structureInspectAllocation.value = null
  structureInspectAllocationSummary.value = null
  structureInspectOrders.value = []
  structureInspectAllocations.value = []
  structureInspectSerialItems.value = []
  structureInspectError.value = ''
}

const loadStructureInspect = async (row: any) => {
  if (!row?.id) return
  structureInspectLoading.value = true
  resetStructureInspectState()
  try {
    const latestPurchaseOrder: any = await request.get(`/purchase-orders/${row.id}`)
    currentStructurePurchaseOrder.value = latestPurchaseOrder

    const requests: Promise<any>[] = []
    if (latestPurchaseOrder.masterId) {
      requests.push(request.get(`/sales-order-masters/${latestPurchaseOrder.masterId}`))
    } else {
      requests.push(Promise.resolve(null))
    }
    if (latestPurchaseOrder.allocationId) {
      requests.push(request.get(`/sales-order-allocations/${latestPurchaseOrder.allocationId}/detail`))
    } else {
      requests.push(Promise.resolve(null))
    }

    const [masterDetail, allocationDetail] = await Promise.all(requests)

    if (masterDetail) {
      structureInspectMaster.value = masterDetail.master || null
      structureInspectMasterSummary.value = masterDetail.summary || null
      structureInspectOrders.value = masterDetail.orders || []
      structureInspectAllocations.value = masterDetail.allocations || []
      structureInspectSerialItems.value = masterDetail.serialItems || []
    }

    if (allocationDetail) {
      structureInspectAllocation.value = allocationDetail.allocation || null
      structureInspectAllocationSummary.value = allocationDetail.summary || null
      if (!structureInspectMaster.value && allocationDetail.master) {
        structureInspectMaster.value = allocationDetail.master
      }
      if (structureInspectAllocations.value.length === 0 && allocationDetail.allocation) {
        structureInspectAllocations.value = [allocationDetail.allocation, ...(allocationDetail.children || [])]
      }
      if (structureInspectSerialItems.value.length === 0) {
        structureInspectSerialItems.value = allocationDetail.serialItems || []
      }
    }

    if (!latestPurchaseOrder.masterId && !latestPurchaseOrder.allocationId) {
      structureInspectError.value = '当前采购订单尚未挂接主单/分配锚点，现阶段请先从对应销售订单侧检查或补挂锚点。'
    }
  } catch (error: any) {
    console.error('Load purchase structure inspect error:', error)
    structureInspectError.value = error?.response?.data?.message || error?.message || '采购链路观察数据加载失败'
  } finally {
    structureInspectLoading.value = false
  }
}

const openStructureInspect = async (row: any) => {
  currentStructurePurchaseOrder.value = row
  structureInspectVisible.value = true
  await loadStructureInspect(row)
}

const refreshStructureInspect = async () => {
  if (!currentStructurePurchaseOrder.value) return
  await loadStructureInspect(currentStructurePurchaseOrder.value)
}

const backfillCurrentPurchaseOrderStructure = async () => {
  if (!currentStructurePurchaseOrder.value?.id) return
  try {
    structureBackfillLoading.value = true
    await request.post('/purchase-orders/backfill-anchors', {
      purchaseOrderIds: [currentStructurePurchaseOrder.value.id],
      onlyMissing: true,
      limit: 1
    })
    await fetchPurchaseOrders()
    await loadStructureInspect(currentStructurePurchaseOrder.value)
    ElMessage.success('当前采购单锚点补挂成功')
  } catch (error: any) {
    console.error('Backfill purchase structure error:', error)
    ElMessage.error(error?.response?.data?.message || error?.message || '采购单补挂失败')
  } finally {
    structureBackfillLoading.value = false
  }
}

const handleDeletePurchaseOrder = async (row: any) => {
  const id = row?.id
  const no = row?.purchaseOrderNo || '该条'
  if (id == null) {
    ElMessage.warning('无法获取订单 id')
    return
  }
  try {
    await ElMessageBox.confirm(`确定要删除采购订单「${no}」吗？删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await request.delete(`/purchase-orders/${id}`)
    ElMessage.success('已删除')
    fetchPurchaseOrders()
    if (detailVisible.value && currentOrder.value?.id === id) {
      detailVisible.value = false
      currentOrder.value = null
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '删除失败')
  }
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

// 采购域：付款申请
const handleCreatePaymentRequest = (row: any) => {
  if (!canApplyPaymentRequest.value) {
    ElMessage.warning('无权限发起付款申请')
    return
  }
  currentPurchaseOrder = row
  createPaymentRequestForm.applyRemark = ''
  createPaymentRequestDialogVisible.value = true
}

const submitCreatePaymentRequest = async () => {
  if (!currentPurchaseOrder?.id) return
  createPaymentRequestSubmitting.value = true
  try {
    const created: any = await request.post('/purchase-payment-requests', {
      purchaseOrderId: currentPurchaseOrder.id,
      applyRemark: createPaymentRequestForm.applyRemark
    })
    ElMessage.success('付款申请已发起')
    createPaymentRequestDialogVisible.value = false
    if (currentPurchaseOrder) {
      currentPurchaseOrder.paymentRequestId = created?.id || null
      currentPurchaseOrder.paymentRequestNo = created?.requestNo || ''
      currentPurchaseOrder.paymentRequestStatus = created?.status || ''
    }
    fetchPurchaseOrders()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '发起付款申请失败')
  } finally {
    createPaymentRequestSubmitting.value = false
  }
}

const handleOpenPaymentRequest = async (row: any) => {
  if (!canViewPaymentRequest.value) {
    ElMessage.warning('无权限查看付款申请')
    return
  }
  currentPurchaseOrder = row
  try {
    let requestId = row.paymentRequestId
    if (!requestId) {
      const list: any = await request.get(`/purchase-payment-requests/by-purchase-order/${row.id}`)
      requestId = Array.isArray(list) && list.length > 0 ? list[0].id : null
    }
    if (!requestId) {
      ElMessage.warning('该采购单尚未发起付款申请')
      return
    }
    const detail: any = await request.get(`/purchase-payment-requests/${requestId}`)
    currentPaymentRequest.value = detail
    paymentRequestStatusForm.status = detail?.status || ''
    paymentRequestStatusForm.remark = ''
    paymentVoucherForm.paidAmount = detail?.requestedAmount ?? ''
    paymentVoucherForm.bankFlowNo = detail?.bankFlowNo || ''
    paymentVoucherForm.voucherUrl = detail?.voucherUrl || ''
    paymentRequestDetailTab.value = 'base'
    await fetchPaymentRequestInvoices(requestId)
    paymentRequestDetailVisible.value = true
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '获取付款申请详情失败')
  }
}

const fetchPaymentRequestInvoices = async (requestId: number) => {
  try {
    const res: any = await request.get(`/purchase-payment-requests/${requestId}/inbound-invoices`)
    paymentRequestInvoices.value = Array.isArray(res) ? res : (res?.content || [])
  } catch {
    paymentRequestInvoices.value = []
  }
}

const submitPaymentRequestStatus = async () => {
  if (!canTrackInboundInvoice.value && !canFinancePayment.value) {
    ElMessage.warning('无权限更新申请状态')
    return
  }
  if (!currentPaymentRequest.value?.id || !paymentRequestStatusForm.status) {
    ElMessage.warning('请选择状态')
    return
  }
  try {
    const saved: any = await request.post(`/purchase-payment-requests/${currentPaymentRequest.value.id}/status`, {
      status: paymentRequestStatusForm.status,
      remark: paymentRequestStatusForm.remark
    })
    currentPaymentRequest.value = saved
    ElMessage.success('状态更新成功')
    fetchPurchaseOrders()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '状态更新失败')
  }
}

const handleInboundInvoiceUploadSuccess = (response: any, file: File) => {
  if (!canTrackInboundInvoice.value && !canFinancePayment.value) {
    ElMessage.warning('无权限上传进项发票附件')
    return
  }
  if (response?.url) {
    newInboundInvoiceForm.invoiceUrl = response.url
    ElMessage.success('进项发票附件上传成功')
  } else {
    ElMessage.error(response?.message || '上传失败')
  }
}

const beforeUpload = () => {
  return true
}

const openExternalLink = (url?: string) => {
  if (!url) return
  window.open(url, '_blank')
}

const submitInboundInvoice = async () => {
  if (!canTrackInboundInvoice.value && !canFinancePayment.value) {
    ElMessage.warning('无权限维护进项发票')
    return
  }
  if (!currentPaymentRequest.value?.id) return
  if (!newInboundInvoiceForm.status) {
    ElMessage.warning('请选择进项发票状态')
    return
  }
  try {
    await request.post(`/purchase-payment-requests/${currentPaymentRequest.value.id}/inbound-invoices`, {
      invoiceNumber: newInboundInvoiceForm.invoiceNumber,
      invoiceAmount: newInboundInvoiceForm.invoiceAmount,
      status: newInboundInvoiceForm.status,
      remark: newInboundInvoiceForm.remark,
      invoiceUrl: newInboundInvoiceForm.invoiceUrl
    })
    ElMessage.success('进项发票跟进已新增')
    newInboundInvoiceForm.invoiceNumber = ''
    newInboundInvoiceForm.invoiceAmount = ''
    newInboundInvoiceForm.status = '待开票'
    newInboundInvoiceForm.remark = ''
    newInboundInvoiceForm.invoiceUrl = ''
    await fetchPaymentRequestInvoices(currentPaymentRequest.value.id)
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '新增失败')
  }
}

const handlePaymentVoucherUploadSuccess = (response: any, file: File) => {
  if (!canFinancePayment.value) {
    ElMessage.warning('无权限上传打款凭证')
    return
  }
  if (response?.url) {
    paymentVoucherForm.voucherUrl = response.url
    ElMessage.success('打款凭证上传成功')
  } else {
    ElMessage.error(response?.message || '上传失败')
  }
}

const submitPaymentVoucher = async () => {
  if (!canFinancePayment.value) {
    ElMessage.warning('无权限确认打款')
    return
  }
  if (!currentPaymentRequest.value?.id) return
  if (!paymentVoucherForm.voucherUrl) {
    ElMessage.warning('请先上传打款凭证')
    return
  }
  try {
    const saved: any = await request.post(`/purchase-payment-requests/${currentPaymentRequest.value.id}/voucher`, {
      voucherUrl: paymentVoucherForm.voucherUrl,
      bankFlowNo: paymentVoucherForm.bankFlowNo,
      paidAmount: paymentVoucherForm.paidAmount
    })
    currentPaymentRequest.value = saved
    ElMessage.success('打款完成，已回写采购付款状态')
    fetchPurchaseOrders()
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '提交失败')
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
    const routeOrderId = typeof route.query.orderId === 'string' ? route.query.orderId.trim() : ''
    if (assignFilterForm.platformOrderNo) params.platformOrderNo = assignFilterForm.platformOrderNo
    if (assignFilterForm.status) params.status = assignFilterForm.status
    // 从销售列表点「转派/指派」跳进来时会带 orderId；此时不能再套默认状态，
    // 否则像「待确认订单 / 已盖章」这类可转派链式单会被前端默认筛掉，表现成“没有生成待指派订单”。
    else if (!routeOrderId) params.status = '待指派,待合同盖章,待发货,已退回' // 含已退回，便于指派方（如 sonmin）对退回订单发起第二次指派
    params.excludeChainOrders = true // 同一指派只显示一条，不展示下一层链式订单
    const res: any = await request.get('/sales-orders', { params })
    const content = res?.content ?? []
    assignTableData.value = content
    assignTotal.value = res?.totalElements ?? content.length
    selectedAssignRows.value = []
  } catch (e) {
    console.error(e)
    ElMessage.error('获取销售订单列表失败')
  } finally {
    assignLoading.value = false
  }
}

const canAssign = (row: any) => {
  if (!row) return false
  if (row.status === '待指派') return true
  if (row.status === '已退回') {
    const createdBy = row.createdBy != null ? Number(row.createdBy) : null
    const uid = currentUserId.value != null ? Number(currentUserId.value) : null
    return uid != null && createdBy != null && uid === createdBy
  }
  return false
}
const canReassign = (row: any) => {
  if (!row) return false
  return row.assignedUsername === currentUsername.value && row.status !== '已退回'
}

const handleAssignSelectionChange = (rows: any[]) => {
  selectedAssignRows.value = Array.isArray(rows) ? rows : []
}

const openAssignDialog = async (rows: any[], mode: 'single' | 'merge') => {
  const firstRow = rows?.[0]
  if (!firstRow) return
  assignDialogMode.value = mode
  mergeAssignRows.value = rows
  mergeAssignPurchasePriceEdits.value = {}
  currentAssignOrder.value = firstRow
  isReassignFlow.value = rows.every((row: any) => row.assignedUsername === currentUsername.value)
  assignForm.deliveryParty = firstRow.deliveryParty || ''
  assignForm.model = mode === 'merge'
    ? rows.map((row: any) => String(row?.model || '').trim()).filter(Boolean).join(' / ')
    : (firstRow.model || '')
  assignForm.deductionRate = firstRow.deductionRate ?? ''
  assignForm.deliveryPartyPurchasePrice = mode === 'merge' ? '' : (firstRow.deliveryPartyPurchasePrice ?? '')
  let myCompanyTitle = ''
  try {
    const meRes: any = await request.get('/users/me')
    if (meRes?.companyTitle) myCompanyTitle = String(meRes.companyTitle).trim()
    if (myCompanyTitle) localStorage.setItem('companyTitle', myCompanyTitle)
  } catch {
    myCompanyTitle = (localStorage.getItem('companyTitle') || '').trim()
  }
  const normalizedUsername = (currentUsername.value || '').trim().toLowerCase()
  if (!myCompanyTitle && (normalizedUsername === 'sonmin' || normalizedUsername === 'songmin')) {
    myCompanyTitle = '飞础科智慧科技（上海）有限公司'
  }
  assignOperatorCompanyTitle.value = myCompanyTitle
  const lastPaymentMethod = (localStorage.getItem('oms_assign_last_paymentMethod') || '').trim()
  assignForm.platformName = ''
  const isFeichuke = myCompanyTitle && (myCompanyTitle.includes('飞础科') || myCompanyTitle.includes('飞础科智慧科技'))
  if (isFeichuke && !lastPaymentMethod) {
    assignForm.paymentMethod = '背靠背'
  } else if (lastPaymentMethod) {
    assignForm.paymentMethod = lastPaymentMethod
  } else {
    assignForm.paymentMethod = canReassign(firstRow) ? (firstRow.paymentMethod || '账期') : (firstRow.paymentMethod || '')
  }
  assignForm.templateUrl = ''
  assignForm.partyBRepresentative = ''
  assignForm.offlineSales = String(firstRow.offlineSales || '').trim()
  resetAssignNumberDraftState()
  rememberAssignOfflineSales(assignForm.offlineSales)
  assignPartnerInfo.value = null
  assignPartnerInfoList.value = []
  assignDialogVisible.value = true
  if (mode === 'merge') {
    buildMergeAssignPurchasePriceEdits(rows)
  }
  await nextTick()
  await fetchContractTemplates()
  if ((firstRow.deliveryParty || '').trim()) {
  try {
    const res = await request.get('/partner-info/by-title/list', {
        params: { title: (firstRow.deliveryParty || '').trim() },
      skipErrorMsg: true
    } as any)
    const list = normalizePartnerInfoList(res)
      assignPartnerInfoList.value = list || []
      if (list.length === 1 && list[0]?.username) {
        assignPartnerInfo.value = list[0]
        assignForm.partyBRepresentative = (list[0].username || '').trim()
      } else if (list.length > 1) {
        assignPartnerInfo.value = null
        const current = (firstRow.assignedUsername || '').trim()
        const match = list.find((p: any) => (p?.username || '').trim() === current)
        assignForm.partyBRepresentative = match ? (match.username || '').trim() : ''
        if (assignForm.partyBRepresentative) assignPartnerInfo.value = match
      } else {
        assignPartnerInfo.value = null
        assignForm.partyBRepresentative = ''
      }
    } catch {
      assignPartnerInfoList.value = []
      assignPartnerInfo.value = null
      assignForm.partyBRepresentative = ''
    }
  }
  if (contractTemplateList.value.length === 1) assignForm.templateUrl = contractTemplateList.value[0].templateUrl
  try {
    const res: any = await request.get('/partner-info', { params: { page: 0, size: 1000 } })
    const allPartnerInfo = normalizePartnerInfoList(res)
    const hasIdentity = (p: any, id: string) => {
      const ids = (p.identities || '').split(',').map((s: string) => s.trim())
      return ids.includes(id)
    }
    const titlesFor = (id: string) => allPartnerInfo.filter((p: any) => hasIdentity(p, id)).map((p: any) => (p.title || '').trim()).filter((t: string) => t)
    const namesFor = (id: string) => allPartnerInfo.filter((p: any) => hasIdentity(p, id)).map((p: any) => (p.name || '').trim()).filter((n: string) => n)
    const deliveryTitlesA = [...titlesFor('交付方'), ...titlesFor('工厂方'), ...titlesFor('出货方')]
    const deliveryNamesA = [...namesFor('交付方'), ...namesFor('工厂方'), ...namesFor('出货方')]
    assignDeliveryPartyOptions.value = [...new Set([...deliveryTitlesA, ...deliveryNamesA])].filter(Boolean)
    const deliveryTitle = (assignForm.deliveryParty || '').trim()
    const deliveryValid = !deliveryTitle || assignDeliveryPartyOptions.value.some((t: string) => t === deliveryTitle || (t && deliveryTitle && (t.includes(deliveryTitle) || deliveryTitle.includes(t))))
    if (!deliveryValid) {
      ElMessage.warning('当前交付方在合作管理-用户信息维护中未找到，请先添加该抬头或从下拉选择')
    }
  } catch (e) {
    console.error(e)
    ElMessage.error('获取合作方信息失败')
  }
  await fetchAssignNumberPreview()
}

// 根据订单 ID 在指派列表中高亮该行（不自动打开弹窗，用户需点击「指派」才打开）
const highlightAssignOrderId = (orderId: string) => {
  if (!orderId) return
  assignHighlightOrderId.value = orderId
  setTimeout(() => { assignHighlightOrderId.value = null }, 5000)
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

const normalizeApiArray = (res: any): any[] => {
  if (Array.isArray(res)) return res
  if (Array.isArray(res?.content)) return res.content
  if (Array.isArray(res?.data)) return res.data
  if (Array.isArray(res?.data?.content)) return res.data.content
  if (Array.isArray(res?.items)) return res.items
  return []
}

const normalizePartnerInfoList = (res: any): any[] => {
  return normalizeApiArray(res)
    .filter((p: any) => p && typeof p === 'object')
    .map((p: any) => ({
      ...p,
      username: (p.username || '').toString().trim(),
      title: (p.title || '').toString().trim(),
      name: (p.name || '').toString().trim(),
      contactPerson: (p.contactPerson || '').toString().trim()
    }))
}

const handleAssignSalesOrder = async (row: any) => {
  await openAssignDialog([row], 'single')
}

const openMergeAssignDialog = async () => {
  if (!canOpenMergeAssign.value) {
    ElMessage.warning(mergeAssignSelectionHint.value || '请先勾选同一主单下至少 2 行可指派商品')
    return
  }
  await openAssignDialog(selectedAssignRows.value, 'merge')
}

const executeReturnOrders = async (rows: any[], returnReason: string) => {
  const reason = String(returnReason || '').trim()
  if (!rows.length) return
  const ids = rows
    .map(row => row?.id)
    .filter((id): id is number => Number.isFinite(Number(id)))
    .map(id => Number(id))
  if (!ids.length) return
  if (ids.length === 1) {
    await request.patch(`/sales-orders/${ids[0]}/return`, {
      returnReason: reason
    })
    return
  }
  await request.patch('/sales-orders/batch-return', {
    ids,
    returnReason: reason
  })
}

const handleSingleReturn = async (row: any) => {
  if (!row?.id) return
  try {
    const promptResult: any = await ElMessageBox.prompt('请输入退回原因', '订单退回', {
      confirmButtonText: '确认退回',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：价格不对、交期不对、型号不对',
      inputValidator: (v: string) => String(v || '').trim() ? true : '请先填写退回原因'
    })
    await executeReturnOrders([row], promptResult?.value)
    ElMessage.success('退回成功')
    fetchSalesOrdersForAssign()
    fetchPurchaseOrders()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.response?.data?.message || error?.message || '退回失败')
  }
}

const handleBatchReturnSelected = async () => {
  if (!canBatchReturnSelected.value) {
    ElMessage.warning('请先勾选同一主单下可退回的商品行')
    return
  }
  try {
    const promptResult: any = await ElMessageBox.prompt(
      `将整单退回主单 ${selectedAssignMasterIds.value[0]} 下已勾选的 ${selectedAssignRows.value.length} 行，请填写统一退回原因`,
      '同主单整单退回',
      {
        confirmButtonText: '确认整单退回',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：采购价不对、交期不一致、需退回上一主体重选交付方',
        inputValidator: (v: string) => String(v || '').trim() ? true : '请先填写退回原因'
      }
    )
    await executeReturnOrders(selectedAssignRows.value, promptResult?.value)
    ElMessage.success('整单退回成功')
    selectedAssignRows.value = []
    fetchSalesOrdersForAssign()
    fetchPurchaseOrders()
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error?.response?.data?.message || error?.message || '整单退回失败')
  }
}

const onAssignDeliveryPartyChange = async () => {
  const companyTitle = (assignForm.deliveryParty || '').trim()
  if (!companyTitle) {
    assignPartnerInfoList.value = []
    assignPartnerInfo.value = null
    assignForm.partyBRepresentative = ''
    if (!assignNumberDirty.contractNo) assignForm.contractNo = ''
    if (!assignNumberDirty.purchaseOrderNo) assignForm.purchaseOrderNo = ''
    return
  }
  try {
    const res = await request.get('/partner-info/by-title/list', {
      params: { title: companyTitle },
      skipErrorMsg: true
    } as any)
    const list = normalizePartnerInfoList(res)
    assignPartnerInfoList.value = list || []
    if (list.length === 1 && list[0]?.username) {
      assignPartnerInfo.value = list[0]
      assignForm.partyBRepresentative = (list[0].username || '').trim()
    } else if (list.length > 1) {
      assignPartnerInfo.value = null
      const prev = (assignForm.partyBRepresentative || '').trim()
      const match = list.find((p: any) => (p?.username || '').trim() === prev)
      assignForm.partyBRepresentative = match ? (match.username || '').trim() : ''
      if (assignForm.partyBRepresentative) assignPartnerInfo.value = match
    } else {
      assignPartnerInfo.value = null
      assignForm.partyBRepresentative = ''
    }
  } catch {
    assignPartnerInfoList.value = []
    assignPartnerInfo.value = null
    assignForm.partyBRepresentative = ''
  }
  await fetchAssignNumberPreview()
}

const onPartyBRepresentativeSelect = (username: string) => {
  const list = assignPartnerInfoList.value || []
  const p = list.find((x: any) => (x?.username || '').trim() === (username || '').trim())
  assignPartnerInfo.value = p || null
  fetchAssignNumberPreview()
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

const fetchAssignNumberPreview = async () => {
  const deliveryParty = String(assignForm.deliveryParty || '').trim()
  if (!deliveryParty) return
  try {
    const payload: Record<string, any> = {
      deliveryParty,
      partyBRepresentative: String(assignForm.partyBRepresentative || '').trim() || undefined
    }
    if (isMergeAssignMode.value) {
      const ids = (mergeAssignRows.value || [])
        .map((row: any) => Number(row?.id))
        .filter((id: number) => Number.isFinite(id))
      if (!ids.length) return
      payload.salesOrderIds = ids
    } else {
      const salesOrderId = Number(currentAssignOrder.value?.id)
      if (!Number.isFinite(salesOrderId)) return
      payload.salesOrderId = salesOrderId
    }
    const res: any = await request.post('/contracts/number-preview', payload, { skipErrorMsg: true } as any)
    const contractNo = String(res?.contractNo || '').trim()
    const purchaseOrderNo = String(res?.purchaseOrderNo || '').trim()
    if (!assignNumberDirty.contractNo) {
      assignForm.contractNo = contractNo
    }
    if (!assignNumberDirty.purchaseOrderNo) {
      assignForm.purchaseOrderNo = purchaseOrderNo
    }
  } catch {
    // 仅用于默认值预览，不阻断指派流程
  }
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
  const templateUrl = resolveTemplateUrl(assignForm.templateUrl)
  if (!templateUrl) {
    ElMessage.error('请先选择合同模板')
    return
  }
  try {
    isGeneratingContract.value = true
    if (isMergeAssignMode.value) {
      const rows = mergeAssignRows.value || []
      if (rows.length < 2) {
        ElMessage.error('合并指派至少需要 2 行商品')
        return
      }
      if (rows.some((row: any) => !isRowMasterAnchored(row))) {
        ElMessage.error('当前勾选商品行尚未挂接主单，请先补挂主单锚点后再合并指派')
        return
      }
      if (selectedAssignMasterIds.value.length !== 1) {
        ElMessage.error('合并指派只允许选择同一主单下的商品行')
        return
      }
      if (!(assignForm.deliveryParty || '').trim()) {
        ElMessage.error('请先选择交付方后再合并指派')
        return
      }
      if ((assignPartnerInfoList.value || []).length > 1 && !String(assignForm.partyBRepresentative || '').trim()) {
        ElMessage.error('当前交付方存在多个乙方业务员，请先明确选择一位后再提交')
        return
      }
      if (showAssignPartnerHint.value) {
        ElMessage.error('当前交付方尚未在用户信息维护中配置可用用户名，请先维护后再合并指派')
        return
      }
      const offlineSales = String(assignForm.offlineSales || '').trim()
      if (showAssignOfflineSalesBlock.value && !offlineSales) {
        ElMessage.error('当前为第三方订单且乙方为上海热像科技股份有限公司，请先填写线下销售姓名')
        return
      }
      await request.post('/contracts/generate-from-master-selection', {
        salesOrderIds: rows.map((row: any) => row.id),
        templateUrl,
        partyBRepresentative: assignForm.partyBRepresentative,
        contractNo: String(assignForm.contractNo || '').trim() || undefined,
        purchaseOrderNo: String(assignForm.purchaseOrderNo || '').trim() || undefined,
        platformName: '',
        paymentMethod: assignForm.paymentMethod,
        deliveryParty: (assignForm.deliveryParty || '').trim(),
        deliveryPartyPurchasePriceByOrderId: Object.fromEntries(
          rows.map((row: any) => {
            const rowId = Number(row?.id)
            const edited = parseEditableMoney(mergeAssignPurchasePriceEdits.value[String(rowId)])
            return [String(rowId), edited != null ? edited : null]
          })
        ),
        deductionRate: assignForm.deductionRate !== '' && assignForm.deductionRate != null ? Number(assignForm.deductionRate) : undefined,
        offlineSales: offlineSales || undefined
      })
      rememberAssignOfflineSales(offlineSales)
      if ((assignForm.paymentMethod || '').trim()) localStorage.setItem('oms_assign_last_paymentMethod', (assignForm.paymentMethod || '').trim())
      ElMessage.success('合并指派成功，已生成聚合采购单和合同')
      assignDialogVisible.value = false
      selectedAssignRows.value = []
      mergeAssignRows.value = []
      mergeAssignPurchasePriceEdits.value = {}
      resetAssignNumberDraftState()
      assignDialogMode.value = 'single'
      mainTab.value = 'purchase'
      await nextTick()
      await fetchPurchaseOrders()
      fetchSalesOrdersForAssign()
      return
    }
    const order = currentAssignOrder.value
    if (!order?.id) return
    const orderRes: any = await request.get(`/sales-orders/${order.id}`)
    const deliveryParty = assignForm.deliveryParty?.trim() ?? orderRes.deliveryParty
    const offlineSales = String(assignForm.offlineSales || '').trim()
    if (showAssignOfflineSalesBlock.value && !offlineSales) {
      ElMessage.error('当前为第三方订单且乙方为上海热像科技股份有限公司，请先填写线下销售姓名')
      return
    }
    const updateBody = {
      ...orderRes,
      deliveryParty,
      deductionRate: assignForm.deductionRate !== '' && assignForm.deductionRate != null ? assignForm.deductionRate : orderRes.deductionRate,
      deliveryPartyPurchasePrice: assignForm.deliveryPartyPurchasePrice ?? orderRes.deliveryPartyPurchasePrice,
      paymentMethod: assignForm.paymentMethod || orderRes.paymentMethod,
      offlineSales: offlineSales || orderRes.offlineSales || ''
    }
    if (Array.isArray(updateBody.logistics) && updateBody.logistics.length > 0 && updateBody.logistics[0] && typeof updateBody.logistics[0] === 'object') {
      if (deliveryParty !== undefined) updateBody.logistics[0].deliveryParty = deliveryParty
    }
    await request.put(`/sales-orders/${order.id}`, updateBody)
    await request.post(`/contracts/generate-from-order/${order.id}`, {
      templateUrl,
      partyBRepresentative: assignForm.partyBRepresentative,
      contractNo: String(assignForm.contractNo || '').trim() || undefined,
      purchaseOrderNo: String(assignForm.purchaseOrderNo || '').trim() || undefined,
      platformName: '',
      paymentMethod: assignForm.paymentMethod,
      deliveryParty: deliveryParty || orderRes.deliveryParty || '',
      deliveryPartyPurchasePrice: assignForm.deliveryPartyPurchasePrice != null && assignForm.deliveryPartyPurchasePrice !== '' ? Number(assignForm.deliveryPartyPurchasePrice) : undefined,
      offlineSales: offlineSales || undefined
    })
    // 状态已由后端在指派时设为「待合同盖章」，无需再 PATCH，避免指派后 assignedUsername 已为新乙方导致当前用户无权限 500
    rememberAssignOfflineSales(offlineSales)
    if ((assignForm.paymentMethod || '').trim()) localStorage.setItem('oms_assign_last_paymentMethod', (assignForm.paymentMethod || '').trim())
    ElMessage.success('合同生成成功')
    assignDialogVisible.value = false
    assignDialogMode.value = 'single'
    mergeAssignRows.value = []
    mergeAssignPurchasePriceEdits.value = {}
    resetAssignNumberDraftState()
    mainTab.value = 'purchase'
    await nextTick()
    await fetchPurchaseOrders()
    fetchSalesOrdersForAssign()
  } catch (e: any) {
    console.error(e)
    const msg = e?.response?.data?.message || e?.message || '合同生成失败'
    ElMessage.error(msg)
  } finally {
    isGeneratingContract.value = false
  }
}

watch(mainTab, (tab) => {
  if (tab === 'assign') {
    fetchSalesOrdersForAssign()
  } else if (tab === 'purchase') {
    // 清除指派 Tab 的筛选条件，避免从 Dashboard 带入「仅待指派」后切走再回来仍窄筛选导致列表像「消失」
    assignFilterForm.status = ''
    assignFilterForm.platformOrderNo = ''
    // 切换到采购订单时清除 URL 中的 tab/orderId，避免刷新时再次弹出指派弹窗
    const q = { ...route.query } as Record<string, string>
    if (q.tab !== undefined || q.orderId !== undefined) {
      delete q.tab
      delete q.orderId
      delete q.status
      router.replace({ path: route.path, query: q })
    }
  }
})

onMounted(async () => {
  try {
    const meRes: any = await request.get('/users/me')
    if (meRes?.id != null) currentUserId.value = Number(meRes.id)
    if (meRes?.companyTitle) {
      assignOperatorCompanyTitle.value = String(meRes.companyTitle).trim()
      localStorage.setItem('companyTitle', assignOperatorCompanyTitle.value)
    }
  } catch {
    // 忽略
  }
  loadAssignOfflineSalesOptions()
  const q = route.query as Record<string, string>
  if (q.tab === 'assign') {
    mainTab.value = 'assign'
    const st = q.status != null ? String(q.status).trim() : ''
    assignFilterForm.status = st
  } else if (q.tab === 'purchase' && q.status) {
    filterForm.value.status = normalizePurchaseStatusQuery(q.status)
  }
  if (q.erpEntryStatus !== undefined) {
    filterForm.value.erpEntryStatus = String(q.erpEntryStatus || '').trim()
  }
  loadSettings()
  if (mainTab.value === 'assign') {
    fetchSalesOrdersForAssign().then(() => {
      // orderId 仅用于在列表中高亮/定位该订单，不自动打开指派弹窗，避免刷新时反复弹窗
      if (q.orderId) highlightAssignOrderId(q.orderId)
    })
  } else {
    fetchPurchaseOrders()
  }
})

watch(() => route.query, (query) => {
  const q = (query || {}) as Record<string, string>
  if (q.tab === 'assign') {
    mainTab.value = 'assign'
    const st = q.status != null ? String(q.status).trim() : ''
    assignFilterForm.status = st
    fetchSalesOrdersForAssign().then(() => {
      if (q.orderId) highlightAssignOrderId(q.orderId)
    })
    return
  }
  if (route.path === '/purchase') {
    mainTab.value = 'purchase'
    filterForm.value.status = normalizePurchaseStatusQuery(q.status)
    filterForm.value.erpEntryStatus = q.erpEntryStatus != null ? String(q.erpEntryStatus).trim() : ''
    currentPage.value = 1
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

const handlePurchaseSelectionChange = (rows: any[]) => {
  if (!Array.isArray(rows)) {
    selectedPurchaseRows.value = []
    return
  }
  const uniqueRows = new Map<string, any>()
  rows.forEach((row: any) => {
    const key = String(row?.id ?? '')
    if (!key) return
    if (!uniqueRows.has(key)) {
      uniqueRows.set(key, row)
    }
  })
  selectedPurchaseRows.value = Array.from(uniqueRows.values())
}

const normalizePurchaseStatusQuery = (value?: string) => {
  const status = String(value || '').trim()
  if (!status || status === 'all') return ''
  if (status === 'pending') return '待确认'
  if (status === 'confirmed') return '已确认'
  if (status === 'shipped') return '已发货'
  if (status === 'received') return '已到货'
  return status
}

const handleBatchGeneratePurchaseReconciliation = async () => {
  if (selectedPurchaseRows.value.length === 0) {
    ElMessage.warning('请先选择采购订单')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认将已选择的 ${selectedPurchaseRows.value.length} 条采购订单生成一张采购对账单吗？`,
      '生成采购对账单',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await request.post('/purchase-reconciliations/generate', {
      purchaseOrderIds: selectedPurchaseRows.value.map((row: any) => row.id),
      reconciliationDate: new Date().toISOString().slice(0, 10)
    })
    ElMessage.success('采购对账单生成成功')
    selectedPurchaseRows.value = []
    await fetchPurchaseOrders()
    router.push('/purchase/reconciliation')
  } catch (error: any) {
    if (error !== 'cancel') {
      const msg = error?.response?.data?.message || error?.message || '采购对账单生成失败'
      ElMessage.error(msg)
    }
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
.structure-inspect-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 160px;
}
.structure-inspect-head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  font-size: 13px;
  color: #606266;
}
.structure-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.structure-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 20px;
}
.detail-header-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.detail-header-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  font-size: 13px;
  color: #606266;
}
.order-info {
  font-size: 18px;
  font-weight: bold;
}
.status-tag {
  margin-left: 10px;
}
.info-grid {
  margin-bottom: 22px;
}
.info-section {
  background: #fafafa;
  padding: 15px;
  border-radius: 4px;
  height: 100%;
  border: 1px solid #f0f0f0;
}
.section-title {
  font-weight: bold;
  margin-bottom: 15px;
  color: #333;
}
.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}
.section-subtitle {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
.info-item {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  margin-bottom: 10px;
  font-size: 14px;
  color: #666;
  line-height: 1.5;
  word-break: break-all;
}
.info-item .label {
  flex: 0 0 104px;
  color: #999;
  max-width: 104px;
  display: inline-flex;
}
.remark {
  color: #f56c6c;
}
.copy-icon {
  cursor: pointer;
  margin-left: 5px;
  color: #409EFF;
}
.detail-line-section,
.detail-log-section {
  margin-top: 20px;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
}
.detail-line-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  font-size: 13px;
  color: #606266;
}
:deep(.purchase-detail-row-highlight) {
  --el-table-tr-bg-color: #fff7e6;
}
:deep(.purchase-detail-row-highlight td) {
  background: #fff7e6 !important;
}
.table-footer {
  margin-top: 15px;
  padding: 10px;
  background: #fdf6ec;
  border-radius: 4px;
  font-size: 14px;
}
.purchase-contract-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.purchase-contract-main {
  display: flex;
  align-items: center;
  min-height: 24px;
}
.purchase-contract-sub {
  font-size: 12px;
  color: #909399;
}
.purchase-group-continued {
  font-size: 12px;
  color: #909399;
  font-weight: 600;
}
.assign-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.assign-master-anchor-alert {
  margin-bottom: 12px;
}
.assign-toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.assign-toolbar-right {
  font-size: 12px;
  color: #606266;
}
.assign-toolbar-tip {
  font-size: 12px;
  color: #909399;
}
.highlight {
  color: #f56c6c;
  font-weight: bold;
}
.assign-party-summary {
  margin-bottom: 16px;
  padding: 12px 14px;
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}
.assign-party-summary-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 6px;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}
.assign-party-summary-row:last-child {
  margin-bottom: 0;
}
.assign-party-summary .summary-label {
  min-width: 126px;
  color: #303133;
  font-weight: 500;
}
.assign-party-summary-tip {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
.assign-partner-hint {
  margin-top: 8px;
  padding: 8px 12px;
  background: #fdf6ec;
  border-radius: 4px;
  font-size: 13px;
  color: #e6a23c;
}
.assign-partner-hint .hint-link {
  color: #409eff;
  font-weight: 500;
  margin: 0 2px;
}
.assign-partner-hint .hint-text {
  color: #606266;
}
.assign-offline-sales-card {
  margin-top: 10px;
  padding: 10px 12px;
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
  border-radius: 4px;
}
.assign-offline-sales-card.is-filled {
  background: linear-gradient(180deg, #fff7e6 0%, #fff3d6 100%);
  border-color: #f3d19e;
  box-shadow: 0 0 0 1px rgba(230, 162, 60, 0.08);
}
.assign-offline-sales-title {
  margin-bottom: 8px;
  color: #303133;
  font-size: 13px;
  font-weight: 600;
}
.assign-offline-sales-tip {
  margin-top: 8px;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
}
.assign-contract-form :deep(.el-form-item) {
  margin-bottom: 18px;
}
.assign-emphasis-item {
  transition: all 0.2s ease;
}
.assign-emphasis-item.is-filled :deep(.el-input__wrapper),
.assign-emphasis-item.is-filled :deep(.el-select__wrapper) {
  background: #fff8e8;
  box-shadow: 0 0 0 1px #f3d19e inset;
}
.assign-emphasis-item.is-filled :deep(.el-input__inner),
.assign-emphasis-item.is-filled :deep(.el-select__selected-item) {
  color: #8a5a00;
  font-weight: 600;
}
.assign-emphasis-item.is-filled :deep(.el-form-item__label) {
  color: #8a5a00;
  font-weight: 600;
}
.assign-single-preview-item :deep(.el-form-item__content) {
  display: block;
  min-width: 0;
}
.assign-single-preview {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  padding: 12px;
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow-x: auto;
}
.assign-single-preview-title {
  margin-bottom: 10px;
  color: #303133;
  font-size: 13px;
  font-weight: 600;
}
.assign-single-preview-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
.assign-single-preview-summary .summary-chip {
  padding: 4px 10px;
  background: #eef6ff;
  border: 1px solid #d9ecff;
  border-radius: 999px;
  color: #606266;
  font-size: 12px;
  line-height: 20px;
}
.assign-single-preview-tip {
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
.assign-merge-preview-item :deep(.el-form-item__content) {
  display: block;
  min-width: 0;
}
.assign-merge-preview {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  padding: 12px;
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow-x: auto;
}
.assign-merge-preview-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}
.assign-merge-preview-summary .summary-chip {
  padding: 4px 10px;
  background: #f0f9eb;
  border: 1px solid #d9ecff;
  border-radius: 999px;
  color: #606266;
  font-size: 12px;
  line-height: 20px;
}
.assign-merge-preview-tip {
  margin-bottom: 10px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
.assign-merge-preview-table {
  width: 100%;
}
.assign-merge-preview-table :deep(.el-table__inner-wrapper),
.assign-merge-preview-table :deep(.el-scrollbar__view),
.assign-merge-preview-table :deep(table) {
  width: 100% !important;
}
.assign-merge-price-input :deep(.el-input__wrapper) {
  padding-left: 8px;
  padding-right: 8px;
}
.master-group-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 20px;
}
.master-group-chip-1 {
  background: #edf5ff;
  color: #3a7afe;
}
.master-group-chip-2 {
  background: #eefaf2;
  color: #28a35c;
}
.master-group-chip-3 {
  background: #fff6ec;
  color: #d97706;
}
.master-group-chip-4 {
  background: #f5efff;
  color: #8b5cf6;
}
.assign-row-highlight {
  background-color: #ecf5ff !important;
}
:deep(.master-group-row-1 > td) {
  background: #f7fbff !important;
}
:deep(.master-group-row-2 > td) {
  background: #f7fcf8 !important;
}
:deep(.master-group-row-3 > td) {
  background: #fffaf5 !important;
}
:deep(.master-group-row-4 > td) {
  background: #faf7ff !important;
}
.erp-entry-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.erp-entry-meta {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
.erp-entry-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}
.trace-list {
  position: relative;
}
.trace-item {
  position: relative;
  padding-left: 32px;
  padding-bottom: 20px;
}
.trace-item:last-child {
  padding-bottom: 0;
}
.trace-dot {
  position: absolute;
  left: 0;
  top: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: center;
}
.trace-dot.active {
  background-color: #1890ff;
}
.trace-dot .el-icon {
  color: #fff;
}
.trace-line {
  position: absolute;
  left: 9px;
  top: 24px;
  bottom: 0;
  width: 2px;
  background: #e4e7ed;
}
.trace-content {
  padding-left: 10px;
}
.trace-status {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.trace-status.latest {
  color: #1890ff;
}
.trace-desc {
  margin-top: 4px;
  font-size: 14px;
  line-height: 1.6;
  color: #606266;
}
.trace-desc.latest {
  color: #1890ff;
}
.trace-time {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}
</style>
