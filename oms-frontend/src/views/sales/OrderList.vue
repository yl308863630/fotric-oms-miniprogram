<template>
  <div class="order-list-container mobile-list-layout">
    <!-- 搜索筛选区 -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm" class="demo-form-inline">
        <el-form-item label="甲方订单号">
          <el-input v-model="filterForm.platformOrderNo" placeholder="请输入甲方订单号" clearable />
        </el-form-item>
        <el-form-item label="OMS订单号">
          <el-input v-model="filterForm.omsOrderNo" placeholder="请输入OMS订单号" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterForm.status" placeholder="全部" clearable style="width: 120px" @change="handleStatusFilterChange">
            <el-option label="全部" value="" />
            <el-option label="待指派" value="待指派" />
            <el-option label="待确认订单" value="待确认订单" />
            <el-option label="待合同盖章" value="待合同盖章" />
            <el-option label="待发货" value="待发货" />
            <el-option label="已发货" value="已发货" />
            <el-option label="已对账未开票" value="已对账未开票" />
            <el-option label="已开票待结算" value="已开票待结算" />
            <el-option label="已结算" value="已结算" />
            <el-option label="已退回" value="已退回" />
          </el-select>
        </el-form-item>
        <el-form-item label="商务ERP录单">
          <el-select v-model="filterForm.erpEntryStatus" placeholder="全部" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="待系统录单" value="待系统录单" />
            <el-option label="已录单" value="已录单" />
          </el-select>
        </el-form-item>
        <el-form-item label="甲方回款状态">
          <el-select v-model="filterForm.platformRefundStatus" placeholder="全部" clearable style="width: 120px">
            <el-option label="全部" value="" />
            <el-option label="未回款/部分回款" value="未回款,部分回款" />
            <el-option label="未回款" value="未回款" />
            <el-option label="部分回款" value="部分回款" />
            <el-option label="已回款" value="已回款" />
          </el-select>
        </el-form-item>
        <el-form-item label="预计回款">
          <el-select v-model="filterForm.expectedRefundOverdue" placeholder="全部" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="已超期" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务员">
          <el-select
            v-model="filterForm.ecommerceSalesName"
            placeholder="全部"
            clearable
            filterable
            style="width: 130px"
          >
            <el-option label="全部" value="" />
            <el-option v-for="n in salespersonFilterOptions" :key="n" :label="n" :value="n" />
          </el-select>
        </el-form-item>
        <el-form-item label="线下销售">
          <el-select v-model="filterForm.offlineSales" placeholder="全部" clearable style="width: 120px">
            <el-option label="全部" value="" />
            <el-option label="是" value="1" />
            <el-option label="否" value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="签收单状态">
          <el-select
            v-model="filterForm.receiptFilter"
            placeholder="全部"
            clearable
            style="width: 220px"
            @change="handleReceiptFilterChange"
          >
            <el-option label="全部" value="" />
            <el-option label="需签收单（待上传）" value="pending_upload" />
            <el-option label="已发货等待签收" value="waiting_sign" />
            <el-option label="自主车辆待签收单" value="self_vehicle_pending_receipt" />
            <el-option label="妥投结束" value="completed" />
            <el-option label="已发货待签收" value="waiting_mother" />
            <el-option label="已到货待回单" value="mother_delivered" />
            <el-option label="已回单待上传签收单" value="return_delivered" />
            <el-option label="全量（不筛签收/妥投）" value="all" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
          <el-button link type="primary" @click="showMoreFilters = !showMoreFilters">
            {{ showMoreFilters ? '收起筛选' : '更多筛选' }}
          </el-button>
        </el-form-item>
      </el-form>
      <el-form v-if="showMoreFilters" :inline="true" :model="filterForm" class="demo-form-inline more-filters-form">
        <el-form-item label="甲方抬头">
          <el-input v-model="filterForm.partyATitleKeyword" placeholder="请输入甲方抬头" clearable />
        </el-form-item>
        <el-form-item label="交付方">
          <el-input v-model="filterForm.deliveryPartyKeyword" placeholder="请输入交付方" clearable />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="dashboardEntryHint"
        class="filter-inline-hint"
        title="Dashboard 入口提示"
        :description="dashboardEntryHint"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-alert
        v-if="statusFilterHint"
        class="filter-inline-hint"
        title="筛选提示"
        :description="statusFilterHint"
        type="info"
        :closable="false"
        show-icon
      />
    </el-card>

    <!-- 数据列表 -->
    <el-card class="table-card">
      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新建销售订单</el-button>
        <el-button
          type="warning"
          plain
          :disabled="selectedSalesRows.length === 0"
          @click="openBatchErpEntryDialog"
        >
          批量商务ERP录单
        </el-button>
        <el-button
          type="primary"
          plain
          :disabled="selectedSalesRows.length === 0"
          @click="openBatchShipmentDialog"
        >
          整单批量发货
        </el-button>
        <el-button
          type="success"
          plain
          :disabled="selectedSalesRows.length === 0"
          @click="handleBatchGenerateSalesReconciliation"
        >
          生成销售对账单
        </el-button>
        <el-button plain @click="openMasterViewDrawer">主单观察</el-button>
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
      <el-table :key="listRefreshKey" :data="tableData" border style="width: 100%" stripe size="small" v-loading="loading" @selection-change="handleTableSelectionChange" :row-class-name="getOrderTableRowClassName">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="操作" width="100" fixed="left" align="center">
          <template #default="scope">
            <el-dropdown trigger="click">
              <el-button type="primary" size="small" :icon="MoreFilled">
                操作
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="!isWarehouseRole" @click="goToContract(scope.row)">
                    <el-icon><Document /></el-icon>
                    <span>合同</span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="!isWarehouseRole" @click="handleEdit(scope.row)">
                    <el-icon><Edit /></el-icon>
                    <span>编辑</span>
                  </el-dropdown-item>
                  <el-dropdown-item @click="openStructureInspect(scope.row)">
                    <el-icon><Document /></el-icon>
                    <span>链路观察</span>
                  </el-dropdown-item>
                  <el-dropdown-item @click="handleShipment(scope.row)">
                    <el-icon><Van /></el-icon>
                    <span>发货</span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="scope.row.erpEntryCanEdit" @click="openErpEntryDialog(scope.row)">
                    <el-icon><Picture /></el-icon>
                    <span>商务ERP录单</span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="!isWarehouseRole && !isAssignedUserForOrder(scope.row)" @click="handleSettlement(scope.row)">
                    <el-icon><Money /></el-icon>
                    <span>结算</span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="!isWarehouseRole && canReassignFromSales(scope.row)" @click="goToReassign(scope.row)">
                    <el-icon><Share /></el-icon>
                    <span>转派</span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="!isWarehouseRole && canReturnOrder(scope.row) && scope.row.status !== '已退回'" @click="handleReturn(scope.row)">
                    <el-icon><Refresh /></el-icon>
                    <span>{{ isAssignedUserForOrder(scope.row) ? '指派退回' : '退回' }}</span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="!isWarehouseRole" @click="handleDelete(scope.row)" divided>
                    <el-icon><Delete /></el-icon>
                    <span>删除</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('status') && orderedColumns.find(c => c.label === 'status')?.visible" prop="status" label="状态" width="118" align="center">
          <template #default="scope">
            <template v-if="scope.row.status === '待指派'">
              <el-tooltip content="点击跳转到「采购管理-销售订单指派」并打开该订单指派" placement="top">
                <el-button link type="primary" class="status-pending-assign-btn" @click="goToAssignOrder(scope.row)">
                  <el-tag :type="getStatusTag(scope.row.status)">待指派</el-tag>
                </el-button>
              </el-tooltip>
              <span class="status-extra-hint">点击指派</span>
            </template>
            <template v-else-if="scope.row.status === '已退回'">
              <template v-if="isAssignerForReturnedOrder(scope.row)">
                <el-tooltip :content="(scope.row.returnReason ? scope.row.returnReason + '；' : '') + '点击跳转到「采购管理-销售订单指派」重新指派'" placement="top">
                  <el-button link type="primary" class="status-pending-assign-btn" @click="goToAssignOrder(scope.row)">
                    <el-tag :type="getStatusTag(scope.row.status)">已退回</el-tag>
                  </el-button>
                </el-tooltip>
                <span class="status-extra-hint">点击指派</span>
              </template>
              <template v-else>
                <el-tooltip v-if="scope.row.returnReason" :content="scope.row.returnReason" placement="top">
                  <el-tag :type="getStatusTag(scope.row.status)">已退回</el-tag>
                </el-tooltip>
                <el-tag v-else :type="getStatusTag(scope.row.status)">已退回</el-tag>
              </template>
            </template>
            <template v-else>
              <el-tooltip v-if="scope.row.returnReason" :content="scope.row.returnReason" placement="top">
                <div class="status-cell">
                  <div v-if="getCompactStatusLayers(scope.row).length > 1" class="status-layer-stack">
                    <el-tag
                      v-for="layer in getCompactStatusLayers(scope.row)"
                      :key="`${scope.row.id || scope.row.platformOrderNo || 'row'}-${layer.label}`"
                      :type="layer.type"
                      size="small"
                      effect="plain"
                    >
                      {{ layer.label }}
                    </el-tag>
                  </div>
                  <el-tag v-else :type="getDisplayedStatusTag(scope.row)">{{ getDisplayedStatus(scope.row) }}</el-tag>
                  <span v-if="getStatusProcessHint(scope.row)" class="status-extra-hint">
                    {{ getStatusProcessHint(scope.row) }}
                  </span>
                </div>
              </el-tooltip>
              <div v-else class="status-cell">
                <div v-if="getCompactStatusLayers(scope.row).length > 1" class="status-layer-stack">
                  <el-tag
                    v-for="layer in getCompactStatusLayers(scope.row)"
                    :key="`${scope.row.id || scope.row.platformOrderNo || 'row'}-${layer.label}`"
                    :type="layer.type"
                    size="small"
                    effect="plain"
                  >
                    {{ layer.label }}
                  </el-tag>
                </div>
                <el-tag v-else :type="getDisplayedStatusTag(scope.row)">{{ getDisplayedStatus(scope.row) }}</el-tag>
                <span v-if="getStatusProcessHint(scope.row)" class="status-extra-hint">
                  {{ getStatusProcessHint(scope.row) }}
                </span>
              </div>
            </template>
          </template>
        </el-table-column>
        <el-table-column
          v-if="shouldShowColumn('erpEntryStatus') && orderedColumns.find(c => c.label === 'erpEntryStatus')?.visible"
          label="商务ERP录单"
          width="172"
          align="center"
        >
          <template #default="scope">
            <div class="erp-entry-cell">
              <el-tag :type="getErpEntryStatusTag(scope.row.erpEntryStatus)">
                {{ scope.row.erpEntryStatus || '-' }}
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
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('receiptSlip') && orderedColumns.find(c => c.label === 'receiptSlip')?.visible" label="签收单状态" width="190" align="center">
          <template #default="scope">
            <div style="display: flex; flex-direction: column; align-items: center; gap: 4px;">
              <el-tag
                v-if="needReceiptSlipTruthy(scope.row)"
                type="warning"
                size="small"
                effect="plain"
                style="cursor: pointer;"
                @click.stop="openReceiptUpload(scope.row)"
              >需签收单</el-tag>
              <el-tag
                v-else-if="isSelfVehicleOrder(scope.row)"
                type="info"
                size="small"
                effect="plain"
                style="cursor: pointer;"
                @click.stop="openReceiptUpload(scope.row)"
              >自主车辆</el-tag>
              <span :style="{ color: getReceiptFlowLabel(scope.row) ? '#606266' : '#c0c4cc', fontWeight: scope.row.receiptUrl ? 500 : 400 }">
                {{ getReceiptFlowLabel(scope.row) || '-' }}
              </span>
              <div
                v-if="scope.row.receiptTime || scope.row.receiptUrl || shouldAllowReceiptUpload(scope.row)"
                style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap; justify-content: center;"
              >
                <span v-if="scope.row.receiptTime" style="font-size: 12px; color: #909399;">
                  签收时间：{{ formatReceiptTime(scope.row.receiptTime) }}
                </span>
                <el-button
                  v-if="scope.row.receiptUrl"
                  link
                  type="primary"
                  size="small"
                  @click="previewFile(scope.row.receiptUrl)"
                >
                  查看
                </el-button>
                <el-button
                  v-else-if="shouldAllowReceiptUpload(scope.row)"
                  link
                  type="primary"
                  size="small"
                  @click="openReceiptUpload(scope.row)"
                >
                  上传
                </el-button>
                <el-button
                  v-if="scope.row.receiptUrl || shouldAllowReceiptUpload(scope.row)"
                  link
                  type="primary"
                  size="small"
                  @click="openReceiptUpload(scope.row)"
                >
                  修改
                </el-button>
              </div>
            </div>
            <!-- 回单物流单号：同母单一样可查轨迹 -->
            <div v-if="(scope.row.returnReceiptTrackingNumber || '').trim()" style="margin-top: 6px; font-size: 12px;">
              <div style="color: #909399;">回单物流</div>
              <el-button link type="primary" size="small" @click.stop="showLogisticsInfo(scope.row, 'return')">
                {{ scope.row.returnReceiptTrackingNumber }}
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('boxMark') && orderedColumns.find(c => c.label === 'boxMark')?.visible" label="发货要求" min-width="168" align="left" show-overflow-tooltip>
          <template #default="scope">
            <div class="shipping-requirements-inline">
              <div v-if="scope.row.deliveryNoteUrl" class="req-line">
                送货单：
                <el-button link type="primary" size="small" @click.stop="previewDeliveryNote(scope.row)">
                  <el-icon><Document /></el-icon>
                  查看
                </el-button>
              </div>
              <div v-if="scope.row.deliveryNotePrintQuantity != null" class="req-line">送货单打印数量：{{ scope.row.deliveryNotePrintQuantity }}</div>
              <div v-if="scope.row.forbiddenCouriers" class="req-line">快递不让用：{{ scope.row.forbiddenCouriers }}</div>
              <div v-if="scope.row.printBoxLabel && scope.row.boxLabelUrls" class="req-line">
                打印箱唛：
                <template v-for="(url, index) in (scope.row.boxLabelUrls || '').split(',').filter(Boolean)" :key="index">
                  <el-button link type="primary" size="small" @click.stop="previewFile(url)">箱唛{{ index + 1 }}预览</el-button>
                  <span v-if="index < (scope.row.boxLabelUrls || '').split(',').filter(Boolean).length - 1">、</span>
                </template>
              </div>
              <div v-if="scope.row.printBarcode128 && scope.row.platformSku" class="req-line">
                128条码：
                <el-button link type="primary" size="small" @click.stop="downloadBarcode128(scope.row.platformSku)">网页预览/下载</el-button>
              </div>
              <div v-if="!scope.row.deliveryNoteUrl && scope.row.deliveryNotePrintQuantity == null && !scope.row.forbiddenCouriers && !scope.row.printBoxLabel && !scope.row.printBarcode128 && !scope.row.boxLabelUrls" style="color: #909399;">暂无</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('deliveryNotePrintQuantity') && orderedColumns.find(c => c.label === 'deliveryNotePrintQuantity')?.visible" prop="deliveryNotePrintQuantity" label="送货单打印数量" width="120" align="center" />
        <el-table-column v-if="shouldShowColumn('forbiddenCouriers') && orderedColumns.find(c => c.label === 'forbiddenCouriers')?.visible" prop="forbiddenCouriers" label="快递不让用" width="140" show-overflow-tooltip />
        <el-table-column v-if="shouldShowColumn('printBoxLabel') && orderedColumns.find(c => c.label === 'printBoxLabel')?.visible" label="打印箱唛" width="100" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.printBoxLabel === true || scope.row.printBoxLabel === 'true'" type="success" size="small">是</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('printBarcode128') && orderedColumns.find(c => c.label === 'printBarcode128')?.visible" label="打印128条码" width="110" align="center">
          <template #default="scope">
            <el-tag v-if="scope.row.printBarcode128 === true || scope.row.printBarcode128 === 'true'" type="success" size="small">是</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('snCode') && orderedColumns.find(c => c.label === 'snCode')?.visible" label="SN编码" prop="snCode" width="132">
          <template #default="scope">
            {{ scope.row.snCode || '-' }}
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('trackingNumber') && orderedColumns.find(c => c.label === 'trackingNumber')?.visible" label="母单物流单号" prop="trackingNumber" width="136">
          <template #default="scope">
            <template v-if="scope.row.trackingNumber">
              <el-button link type="primary" size="small" @click="showLogisticsInfo(scope.row, 'main')">
                {{ scope.row.trackingNumber }}
              </el-button>
            </template>
            <span v-else style="color: #c0c4cc;">-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('platformName') && orderedColumns.find(c => c.label === 'platformName')?.visible" prop="platformName" label="甲方抬头" min-width="200" show-overflow-tooltip />
        <el-table-column v-if="shouldShowColumn('platformOrderNo') && orderedColumns.find(c => c.label === 'platformOrderNo')?.visible" label="甲方订单号" width="180" show-overflow-tooltip>
          <template #default="scope">
            <div style="display: flex; flex-direction: column;">
              <span style="font-weight: 500;">{{ scope.row.platformOrderNo }}</span>
              <span v-if="scope.row.masterId" :class="['master-group-chip', `master-group-chip-${getMasterGroupTone(scope.row)}`]">
                主单 {{ scope.row.masterId }}
              </span>
              <span v-if="getPlatformOrderIndex(scope.row) > 0" style="font-size: 12px; color: #909399;">
                第 {{ getPlatformOrderIndex(scope.row) + 1 }} / {{ getPlatformOrderCount(scope.row) }} 个商品
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('omsOrderNo') && orderedColumns.find(c => c.label === 'omsOrderNo')?.visible" label="工业电商销售订单号" width="180">
          <template #default="scope">
            <div style="display: flex; flex-direction: column;">
              <span>{{ scope.row.omsOrderNo }}</span>
              <span v-if="scope.row.masterId" class="master-group-tip">同色行表示同一主单</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('createTime') && orderedColumns.find(c => c.label === 'createTime')?.visible" prop="createTime" label="创建时间" width="160" show-overflow-tooltip />
        <el-table-column v-if="shouldShowColumn('orderDate') && orderedColumns.find(c => c.label === 'orderDate')?.visible" prop="orderDate" label="订单时间" width="110" />
        <el-table-column v-if="shouldShowColumn('platformSku') && orderedColumns.find(c => c.label === 'platformSku')?.visible" label="商品信息" width="260">
          <template #default="scope">
            <div style="display: flex; flex-direction: column;">
              <div style="display: flex; align-items: center; gap: 8px;">
                <span style="font-weight: 500; color: #303133;">{{ scope.row.platformSku || '-' }}</span>
                <el-tag v-if="scope.row.quantity > 1" type="warning" size="small" effect="light">×{{ scope.row.quantity }}</el-tag>
              </div>
              <span v-if="scope.row.materialNo" style="font-size: 12px; color: #606266; margin-top: 2px;">{{ scope.row.materialNo }}</span>
              <span style="font-size: 12px; color: #909399; margin-top: 2px;">{{ scope.row.model || '' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('quantity') && orderedColumns.find(c => c.label === 'quantity')?.visible" prop="quantity" label="数量" width="80" align="center" />
        <el-table-column v-if="shouldShowColumn('taxIncludedPrice') && orderedColumns.find(c => c.label === 'taxIncludedPrice')?.visible" label="含税单价" width="110" align="right">
          <template #default="scope">
            <span>{{ scope.row.taxIncludedPrice }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('taxIncludedTotal') && orderedColumns.find(c => c.label === 'taxIncludedTotal')?.visible" label="含税总价" width="110" align="right">
          <template #default="scope">
            <span>{{ scope.row.taxIncludedTotal }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('ecommerceSalesName') && orderedColumns.find(c => c.label === 'ecommerceSalesName')?.visible" prop="ecommerceSalesName" label="业务员" width="100" />
        <el-table-column v-if="shouldShowColumn('paymentMethod') && orderedColumns.find(c => c.label === 'paymentMethod')?.visible" prop="paymentMethod" label="账期" width="100" />
        <el-table-column v-if="shouldShowColumn('platformRefundStatus') && orderedColumns.find(c => c.label === 'platformRefundStatus')?.visible" label="结算单号/甲方回款状态" width="190">
          <template #default="scope">
            <div style="display: flex; flex-direction: column; align-items: flex-start; line-height: 1.5;">
              <el-button
                v-if="scope.row.settlementNo && scope.row.settlementUrl"
                link
                type="primary"
                size="small"
                style="padding: 0;"
                @click.stop="previewInvoice(scope.row.settlementUrl)"
              >
                {{ scope.row.settlementNo }}
              </el-button>
              <span v-else>{{ scope.row.settlementNo || '-' }}</span>
              <el-button
                v-if="scope.row.platformRefundStatus && scope.row.platformRefundUrl"
                link
                type="primary"
                size="small"
                style="padding: 0; font-size: 12px;"
                @click.stop="previewInvoice(scope.row.platformRefundUrl)"
              >
                {{ scope.row.platformRefundStatus }}
              </el-button>
              <span v-else-if="scope.row.platformRefundStatus" style="font-size: 12px; color: #606266;">
                {{ scope.row.platformRefundStatus }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          v-if="shouldShowColumn('expectedRefundDate') && orderedColumns.find(c => c.label === 'expectedRefundDate')?.visible"
          label="预计回款时间"
          width="180"
        >
          <template #default="scope">
            <div class="expected-refund-cell">
              <span
                :class="{ 'expected-refund-overdue': isExpectedRefundOverdue(scope.row) }"
              >
                {{ getExpectedRefundDisplayText(scope.row) }}
              </span>
              <span v-if="isExpectedRefundOverdue(scope.row)" class="expected-refund-overdue-hint">
                {{ getExpectedRefundOverdueText(scope.row) }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('orderType') && orderedColumns.find(c => c.label === 'orderType')?.visible" prop="orderType" label="订单类型" width="100" />
        <el-table-column v-if="shouldShowColumn('receiverName') && orderedColumns.find(c => c.label === 'receiverName')?.visible" label="物流信息" width="220">
          <template #default="scope">
            <div v-if="hasReceiverInfo(scope.row)" style="display: flex; align-items: flex-start; gap: 6px;">
              <div style="display: flex; flex-direction: column; flex: 1; min-width: 0;">
                <span style="font-weight: 500; color: #303133;">{{ maskName(scope.row.receiverName) }}</span>
                <span style="font-size: 12px; color: #909399; margin-top: 2px;">{{ maskPhone(scope.row.receiverPhone) }}</span>
                <span style="font-size: 11px; color: #c0c4cc; margin-top: 1px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">{{ maskAddress(scope.row.receiverAddress) }}</span>
              </div>
              <el-button link type="primary" size="small" @click.stop="copyReceiverInfo(scope.row)" title="一键复制">
                <el-icon><CopyDocument /></el-icon>
              </el-button>
            </div>
            <span v-else style="color: #c0c4cc;">-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('settlementNo') && orderedColumns.find(c => c.label === 'settlementNo')?.visible" label="对账单号/发票" width="190">
          <template #default="scope">
            <div style="display: flex; flex-direction: column; align-items: flex-start; line-height: 1.5;">
              <el-button
                v-if="scope.row.platformReconciliationNo && scope.row.platformReconciliationUrl"
                link
                type="primary"
                size="small"
                style="padding: 0;"
                @click.stop="previewInvoice(scope.row.platformReconciliationUrl)"
              >
                {{ scope.row.platformReconciliationNo }}
              </el-button>
              <span v-else>{{ displayReconciliationBillNo(scope.row) }}</span>
              <el-button
                v-if="scope.row.invoiceNumber && scope.row.invoiceUrl"
                link
                type="primary"
                size="small"
                style="padding: 0;"
                @click.stop="previewInvoice(scope.row.invoiceUrl)"
              >
                {{ scope.row.invoiceNumber }}
              </el-button>
              <span v-else-if="scope.row.invoiceNumber" style="font-size: 12px; color: #606266;">
                {{ scope.row.invoiceNumber }}
              </span>
              <span
                v-if="scope.row.invoiceIssuedDate"
                style="font-size: 12px; color: #909399;"
              >
                开票日期：{{ formatExpectedRefundDate(scope.row.invoiceIssuedDate) }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('offlineSales') && orderedColumns.find(c => c.label === 'offlineSales')?.visible" label="线下销售/合同号/出货价" width="220">
          <template #default="scope">
            <div
              v-if="scope.row.offlineSales || scope.row.offlineContractNo || scope.row.offlineShippingPrice"
              style="display: flex; flex-direction: column; align-items: flex-start; line-height: 1.5;"
            >
              <span v-if="scope.row.offlineSales" style="font-weight: 500; color: #303133;">{{ scope.row.offlineSales }}</span>
              <span v-if="scope.row.offlineContractNo" style="font-size: 12px; color: #606266;">{{ scope.row.offlineContractNo }}</span>
              <span v-if="scope.row.offlineShippingPrice" style="font-size: 12px; color: #909399;">{{ scope.row.offlineShippingPrice }}</span>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('deliveryDate') && orderedColumns.find(c => c.label === 'deliveryDate')?.visible" label="交货日期" width="120" align="center">
          <template #default="scope">
            <el-button 
              v-if="scope.row.deliveryDate" 
              link 
              type="primary"
              @click.stop="editDeliveryDate(scope.row)"
            >
              {{ scope.row.deliveryDate }}
            </el-button>
            <el-button 
              v-else 
              link 
              type="primary"
              @click.stop="editDeliveryDate(scope.row)"
            >
              未设置
            </el-button>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('returnInfo') && orderedColumns.find(c => c.label === 'returnInfo')?.visible" label="退回信息" width="220" show-overflow-tooltip>
          <template #default="scope">
            <div v-if="scope.row.returnReason || scope.row.returnTime || scope.row.returnedBy" style="display: flex; flex-direction: column;">
              <span style="font-weight: 500; color: #303133;">{{ scope.row.returnReason || '-' }}</span>
              <span style="font-size: 12px; color: #909399; margin-top: 2px;">{{ scope.row.returnTime || '' }}</span>
              <span style="font-size: 11px; color: #c0c4cc; margin-top: 1px;">{{ scope.row.returnedBy ? '退回人: ' + scope.row.returnedBy : '' }}</span>
            </div>
            <span v-else style="color: #c0c4cc;">-</span>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-drawer
      v-model="masterViewDrawerVisible"
      title="主单观察"
      size="72%"
      destroy-on-close
    >
      <div class="structure-inspect-wrap">
        <el-alert
          v-if="masterViewHint"
          title="当前仅做结构观察"
          :description="masterViewHint"
          type="info"
          :closable="false"
          show-icon
        />
        <el-table
          :data="masterTableData"
          border
          style="width: 100%"
          stripe
          size="small"
          v-loading="masterLoading"
          @expand-change="handleMasterExpandChange"
        >
          <el-table-column type="expand" width="46">
            <template #default="{ row }">
              <div class="master-expand-panel">
                <div class="master-expand-header">
                  <span>主单号：{{ row.masterNo || '-' }}</span>
                  <el-button link type="primary" @click="openMasterChainObserver(row)">打开链路观察页</el-button>
                </div>
                <div v-if="masterDetailLoadingMap[String(row.id)]" class="master-expand-loading">正在加载主单详情...</div>
                <template v-else-if="masterDetailMap[String(row.id)]">
                  <div class="structure-section">
                    <div class="structure-section-title">商品行</div>
                    <el-table :data="masterDetailMap[String(row.id)].orders || []" border stripe size="small">
                      <el-table-column prop="lineNo" label="行号" width="80" />
                      <el-table-column prop="model" label="型号" min-width="150" show-overflow-tooltip />
                      <el-table-column prop="quantity" label="数量" width="90" />
                      <el-table-column prop="status" label="状态" width="120" />
                      <el-table-column prop="deliveryParty" label="交付方" min-width="180" show-overflow-tooltip />
                      <el-table-column prop="assignedUsername" label="被指派用户" width="120" />
                      <el-table-column prop="omsOrderNo" label="OMS订单号" min-width="170" show-overflow-tooltip />
                    </el-table>
                  </div>
                  <div class="structure-section">
                    <div class="structure-section-title">分配分支</div>
                    <el-table :data="masterDetailMap[String(row.id)].allocations || []" border stripe size="small">
                      <el-table-column prop="allocationNo" label="分配单号" min-width="180" show-overflow-tooltip />
                      <el-table-column prop="allocatedQty" label="数量" width="90" />
                      <el-table-column prop="allocationStatus" label="状态" width="120" />
                      <el-table-column prop="assignedCompanyTitle" label="被分配方" min-width="180" show-overflow-tooltip />
                      <el-table-column prop="assignedUsername" label="被分配用户" width="120" />
                      <el-table-column prop="hopNo" label="跳数" width="80" />
                      <el-table-column label="链尾" width="80">
                        <template #default="{ row: allocationRow }">{{ allocationRow.isChainTail ? '是' : '否' }}</template>
                      </el-table-column>
                    </el-table>
                  </div>
                </template>
                <div v-else class="master-expand-empty">展开后可查看该主单下的商品行和分配分支。</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="96" fixed="left" align="center">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openMasterChainObserver(row)">链路</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="masterNo" label="主单号" min-width="170" show-overflow-tooltip />
          <el-table-column prop="platformOrderNo" label="甲方订单号" min-width="170" show-overflow-tooltip />
          <el-table-column prop="rootOmsOrderNo" label="OMS订单号" min-width="160" show-overflow-tooltip />
          <el-table-column prop="partyATitle" label="甲方抬头" min-width="200" show-overflow-tooltip />
          <el-table-column prop="masterStatus" label="主单状态" width="104" align="center">
            <template #default="{ row }">
              <el-tag type="info" effect="plain">{{ row.masterStatus || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="assignStatus" label="指派" width="88" align="center">
            <template #default="{ row }">
              <el-tag :type="row.assignStatus === '已指派' ? 'success' : 'warning'" effect="plain">{{ row.assignStatus || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="financeStatus" label="财务" width="88" align="center" />
          <el-table-column prop="totalLineCount" label="行数" width="72" align="center" />
          <el-table-column prop="totalQuantity" label="数量" width="72" align="center" />
          <el-table-column prop="creator" label="创建人" width="96" />
          <el-table-column prop="createTime" label="创建时间" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ formatErpEntryTime(row.createTime) }}</template>
          </el-table-column>
        </el-table>
        <div class="pagination-container">
          <el-pagination
            v-model:current-page="masterCurrentPage"
            v-model:page-size="masterPageSize"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="masterTotal"
            @size-change="handleMasterSizeChange"
            @current-change="handleMasterCurrentChange"
          />
        </div>
      </div>
    </el-drawer>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="1000px"
      @close="resetForm"
    >
      <!-- 订单概览信息 -->
          <div class="order-overview" style="background: #f5f7fa; padding: 20px; margin-bottom: 20px; border-radius: 4px;">
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <div>
                <span v-if="orderForm.platformOrderNo" style="font-size: 16px; font-weight: bold; margin-right: 20px;">甲方订单号: {{ orderForm.platformOrderNo }}</span>
                <el-tag :type="getStatusTag(orderForm.status)" size="small" effect="dark">{{ orderForm.status || '待指派' }}</el-tag>
              </div>
              <div style="font-size: 18px; font-weight: bold; color: #f56c6c;">
                订单总额: ¥{{ calculateOrderTotal() }}
              </div>
            </div>
          </div>

      <!-- 标签页导航 -->
      <el-tabs v-model="orderActiveTab" type="card" style="margin-bottom: 20px;">
        <el-tab-pane label="商品明细" name="product">
          <!-- 订单基本信息 -->
          <el-form :model="orderForm" :rules="rules" ref="orderFormRef" label-width="140px" label-position="right">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="甲方抬头" prop="platformName">
                  <el-select
                    ref="platformNameSelectRef"
                    v-model="orderForm.platformName"
                    filterable
                    allow-create
                    placeholder="请选择或输入（来自用户信息维护，甲方排前）"
                    style="width: 100%"
                    @blur="onPlatformNameBlur"
                    @change="onPlatformNameChange"
                  >
                    <el-option v-for="item in platformOptions" :key="item" :label="item" :value="item" />
                  </el-select>
                  <div v-if="showPlatformNameHint" class="platform-name-hint" style="margin-top: 6px; font-size: 12px; color: var(--el-color-warning);">
                    <span>如果抬头不在列表，请先</span>
                    <router-link to="/cooperation/partner-info" class="hint-link" @click="dialogVisible = false">点击跳转到用户信息维护</router-link>
                    <span>，创建并维护抬头信息（可勾选用户身份「甲方」以排在前列）后再选择。</span>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="甲方订单号" prop="platformOrderNo">
                  <el-input v-model="orderForm.platformOrderNo" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="业务员" prop="ecommerceSalesId">
                  <el-select v-model="orderForm.ecommerceSalesId" @change="handleSalesChange" style="width: 100%">
                    <el-option v-for="user in salesUsersForSelect" :key="user.id" :label="user.realName || user.username || String(user.id)" :value="user.id" />
                  </el-select>
                  <div v-if="salesPersonContact" class="sales-person-contact">联系人：{{ salesPersonContact }}</div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="订单时间" prop="orderDate">
                  <el-date-picker v-model="orderForm.orderDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" @change="handleOrderDateChange" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-row :gutter="20">
              <el-col :span="24">
                <el-form-item label="合同上传">
                  <el-upload
                    class="upload-demo"
                    action="/api/upload"
                    :on-success="handleNewOrderContractUploadSuccess"
                    :before-upload="beforeContractUpload"
                    :file-list="newOrderContractFileList"
                    :limit="1"
                  >
                    <el-button type="primary" size="small">上传合同</el-button>
                  </el-upload>
                  <div v-if="orderForm.contractUrl && canViewContract" style="margin-top: 10px;">
                    <el-button link type="primary" size="small" @click="previewNewOrderContract">预览合同</el-button>
                  </div>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="24">
                <el-form-item label="从合同文件解析">
                  <el-upload
                    ref="newOrderImportUploadRef"
                    :auto-upload="false"
                    :limit="1"
                    :on-change="onNewOrderImportFileChange"
                    accept=".pdf,.doc,.docx,image/*"
                    show-file-list
                  >
                    <el-button type="success" plain size="small" :loading="orderImportLoading">上传 PDF/Word/图片解析</el-button>
                  </el-upload>
                  <div class="form-item-tip" style="margin-top: 4px; color: #909399; font-size: 12px;">上传震坤行等平台订单 PDF、Word 或图片，自动解析并带出甲方抬头、甲方订单号、型号、数量、金额等（Tesseract 免费开源，图片需服务器已安装）</div>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>

          <!-- 商品表格 -->
          <el-divider content-position="left">商品明细</el-divider>
          <div style="margin-bottom: 10px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
            <el-button type="primary" @click="addProductRow" size="small">
              <el-icon><Plus /></el-icon>
              添加商品行
            </el-button>
            <el-button plain @click="addSplitProduct" size="small">
              <el-icon><Plus /></el-icon>
              拆单添加商品
            </el-button>
            <span style="font-size: 12px; color: #909399;">默认按同一 OMS 订单号追加商品；只有明确拆单时才生成新的 OMS 订单号。</span>
          </div>
          <el-table :data="productList" border style="width: 100%;">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column label="操作" width="80" align="center">
              <template #default="scope">
                <el-button link type="danger" size="small" @click="removeProduct(scope.$index)">删除</el-button>
              </template>
            </el-table-column>
            <el-table-column prop="orderType" label="订单类型" width="120">
              <template #default="scope">
                <el-select v-model="scope.row.orderType" size="small" style="width: 100%;" @change="handleProductOrderTypeChange(scope.row, scope.$index)">
                  <el-option label="第三方订单" value="第三方订单" />
                  <el-option label="自营" value="自营" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="omsOrderNo" label="OMS订单号" min-width="180">
              <template #default="scope">
                <el-input v-model="scope.row.omsOrderNo" size="small" placeholder="自动生成" />
              </template>
            </el-table-column>
            <el-table-column prop="platformSku" label="甲方SKU" min-width="120">
              <template #default="scope">
                <el-input v-model="scope.row.platformSku" size="small" placeholder="请输入" />
              </template>
            </el-table-column>
            <el-table-column prop="model" label="型号" min-width="120">
              <template #default="scope">
                <el-select
                  v-model="scope.row.model"
                  filterable
                  allow-create
                  default-first-option
                  size="small"
                  placeholder="请选择或输入"
                  style="width: 100%;"
                  @change="handleProductModelChange(scope.row)"
                  @blur="(e: FocusEvent) => onModelSelectBlur(e, scope.row)"
                >
                  <el-option v-for="product in products" :key="product.id" :label="product.model" :value="product.model" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="productName" label="商品名称" min-width="160">
              <template #default="scope">
                <el-input v-model="scope.row.productName" size="small" placeholder="请输入商品名称" />
              </template>
            </el-table-column>
            <el-table-column prop="productConfig" label="产品配置" min-width="150">
              <template #default="scope">
                <el-input v-model="scope.row.productConfig" size="small" placeholder="请输入" />
              </template>
            </el-table-column>
            <el-table-column prop="warrantyPeriod" label="保修期" min-width="100">
              <template #default="scope">
                <el-input v-model="scope.row.warrantyPeriod" size="small" placeholder="例如：1年" />
              </template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="100">
              <template #default="scope">
                <el-input-number v-model="scope.row.quantity" :min="1" size="small" style="width: 100%;" @change="calculateProductTotal(scope.row)" />
              </template>
            </el-table-column>
            <el-table-column prop="taxIncludedPrice" label="含税单价" width="120">
              <template #default="scope">
                <el-input v-model="scope.row.taxIncludedPrice" size="small" placeholder="请输入" @input="calculateProductTotal(scope.row)" />
              </template>
            </el-table-column>
            <el-table-column prop="taxIncludedTotal" label="含税总价" width="120">
              <template #default="scope">
                <el-input v-model="scope.row.taxIncludedTotal" :disabled="true" size="small" />
              </template>
            </el-table-column>
          </el-table>
          <div style="text-align: right; margin-top: 10px; font-size: 16px; font-weight: bold;">
            订单总额: ¥{{ calculateOrderTotal() }}
          </div>
        </el-tab-pane>

        <el-tab-pane label="物流信息" name="logistics">
          <!-- 物流信息表格 -->
          <div style="margin-bottom: 10px;">
            <el-button type="primary" @click="addLogistics" size="small" :disabled="!canAddMoreLogistics">
              <el-icon><Plus /></el-icon>
              添加物流
            </el-button>
            <el-button type="success" plain @click="copyLogisticsBySameOms" size="small" style="margin-left: 8px;">
              按同OMS一键复制物流
            </el-button>
          </div>
          <el-table :data="logisticsList" border style="width: 100%;">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column label="操作" width="100" align="center">
              <template #default="scope">
                <el-button link type="primary" size="small" @click="copyLogisticsRow(scope.row)" title="一键复制收货人/电话/地址">
                  <el-icon><CopyDocument /></el-icon> 复制
                </el-button>
                <el-button link type="danger" size="small" @click="removeLogistics(scope.$index)">删除</el-button>
              </template>
            </el-table-column>
            <el-table-column label="商品信息" min-width="200">
              <template #default="scope">
                <el-select v-model="scope.row.productIndex" filterable size="small" style="width: 100%;" placeholder="请选择商品" @change="onLogisticsProductChange(scope.row)">
                  <el-option 
                    v-for="(product, index) in productList" 
                    :key="index"
                    :label="`${product.platformSku || ''} ${product.model || ''}`"
                    :value="index"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="productQuantity" label="发货数量" width="100">
              <template #default="scope">
                <el-input-number 
                  v-model="scope.row.productQuantity" 
                  :min="1" 
                  :max="maxLogisticsQuantityForRow(scope.row)" 
                  size="small" 
                  style="width: 100%;"
                  @change="onLogisticsQuantityChange(scope.row)"
                />
              </template>
            </el-table-column>
            <el-table-column prop="receiverName" label="收货人" min-width="120">
              <template #default="scope">
                <el-select v-model="scope.row.receiverName" filterable allow-create default-first-option size="small" style="width: 100%;" @blur="(e: FocusEvent) => onFilterableSelectBlur(e, (v) => { scope.row.receiverName = v }, receiverNameOptions, saveMemoryOptions)">
                  <el-option v-for="item in receiverNameOptions" :key="item" :label="item" :value="item" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="receiverPhone" label="电话" min-width="120">
              <template #default="scope">
                <el-select v-model="scope.row.receiverPhone" filterable allow-create default-first-option size="small" style="width: 100%;" @blur="(e: FocusEvent) => onFilterableSelectBlur(e, (v) => { scope.row.receiverPhone = v }, receiverPhoneOptions, saveMemoryOptions)">
                  <el-option v-for="item in receiverPhoneOptions" :key="item" :label="item" :value="item" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="receiverAddress" label="收货地址" min-width="200">
              <template #default="scope">
                <el-input v-model="scope.row.receiverAddress" type="textarea" :rows="2" size="small" placeholder="请输入" />
              </template>
            </el-table-column>
            <el-table-column prop="deliveryDate" label="交货日期" width="120">
              <template #default="scope">
                <el-date-picker v-model="scope.row.deliveryDate" type="date" value-format="YYYY-MM-DD" placeholder="请选择" style="width: 100%;" size="small" />
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="发票信息" name="invoice">
          <!-- 发票信息表格 -->
          <div style="margin-bottom: 10px;">
            <el-button type="primary" @click="addInvoice" size="small" :disabled="!canEditSettlementByFinance || !canAddMoreInvoice">
              <el-icon><Plus /></el-icon>
              添加发票
            </el-button>
          </div>
          <el-table :data="invoiceList" border style="width: 100%;">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column label="操作" width="80" align="center">
              <template #default="scope">
                <el-button link type="danger" size="small" @click="removeInvoice(scope.$index)" :disabled="!canEditSettlementByFinance">删除</el-button>
              </template>
            </el-table-column>
            <el-table-column label="商品信息" min-width="200">
              <template #default="scope">
                <el-select v-model="scope.row.productIndex" filterable size="small" style="width: 100%;" placeholder="请选择商品">
                  <el-option 
                    v-for="(product, index) in productList" 
                    :key="index"
                    :label="`${product.platformSku || ''} ${product.model || ''}`"
                    :value="index"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="finalCustomerTitle" label="最终客户抬头" min-width="180">
              <template #default="scope">
                <el-select
                  v-model="scope.row.finalCustomerTitle"
                  filterable
                  allow-create
                  default-first-option
                  size="small"
                  style="width: 100%;"
                  placeholder="请选择或输入"
                  @blur="(e: FocusEvent) => onFilterableSelectBlur(e, (v) => { scope.row.finalCustomerTitle = v }, finalCustomerTitleOptions, saveMemoryOptions)"
                  @change="onFinalCustomerTitleChange"
                >
                  <el-option v-for="item in finalCustomerTitleOptions" :key="item" :label="item" :value="item" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="paymentMethod" label="账期" min-width="100">
              <template #header>
                <span>账期</span>
                <span style="color: var(--el-color-danger); margin-left: 2px;">*</span>
              </template>
              <template #default="scope">
                <el-select
                  v-model="scope.row.paymentMethod"
                  filterable
                  allow-create
                  default-first-option
                  size="small"
                  style="width: 100%;"
                  placeholder="请选择或输入"
                  :disabled="!canEditSettlementByFinance || scope.$index !== 0"
                  @blur="(e: FocusEvent) => onInvoiceCreditTermBlur(e, scope.$index)"
                  @change="(val: string) => onInvoiceCreditTermChange(val, scope.$index)"
                >
                  <el-option v-for="item in paymentMethodOptions" :key="item" :label="item" :value="item" />
                </el-select>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="对账明细" name="reconciliation">
          <!-- 对账明细表格 -->
          <div style="margin-bottom: 10px;">
            <el-button type="primary" @click="addReconciliation" size="small" :disabled="!canEditSettlementBySales || !canAddMoreReconciliation">
              <el-icon><Plus /></el-icon>
              添加对账
            </el-button>
          </div>
          <el-table :data="reconciliationList" border style="width: 100%;">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column label="操作" width="80" align="center">
              <template #default="scope">
                <el-button link type="danger" size="small" @click="removeReconciliation(scope.$index)" :disabled="!canEditSettlementBySales">删除</el-button>
              </template>
            </el-table-column>
            <el-table-column label="关联商品" min-width="200">
              <template #default="scope">
                <el-select v-model="scope.row.productIndex" filterable size="small" style="width: 100%;" placeholder="请选择商品" @change="calculateReconciliationPrice(scope.$index)">
                  <el-option 
                    v-for="(product, index) in productList" 
                    :key="index"
                    :label="`${product.platformSku || ''} ${product.model || ''}`"
                    :value="index"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="关联物流" min-width="250">
              <template #default="scope">
                <el-select v-model="scope.row.logisticsIndex" filterable size="small" style="width: 100%;" placeholder="请选择物流" @change="calculateReconciliationPrice(scope.$index)">
                  <el-option 
                    v-for="(logistics, index) in logisticsList" 
                    :key="index"
                    :label="`${logistics.receiverName || ''} - ${logistics.deliveryParty || ''}`"
                    :value="index"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="offlineSales" min-width="150">
              <template #header>
                <span>线下销售</span>
                <span
                  v-if="showReconciliationOfflineSalesRequiredHint"
                  style="color: var(--el-color-danger); margin-left: 2px;"
                >*</span>
              </template>
              <template #default="scope">
                <el-select
                  v-model="scope.row.offlineSales"
                  filterable
                  allow-create
                  default-first-option
                  size="small"
                  style="width: 100%;"
                  :placeholder="isThirdPartyOrderProductIndex(scope.row.productIndex) ? '第三方订单必填' : '请选择或输入'"
                  @blur="(e: FocusEvent) => onFilterableSelectBlur(
                    e,
                    (v) => {
                      scope.row.offlineSales = v
                      onReconciliationOfflineSalesChange(v)
                    },
                    reconciliationOfflineSalesOptions,
                    saveMemoryOptions
                  )"
                  @change="onReconciliationOfflineSalesChange"
                >
                  <el-option v-for="item in reconciliationOfflineSalesOptions" :key="item" :label="item" :value="item" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="offlineContractNo" label="线下销售合同号" min-width="150">
              <template #default="scope">
                <el-input v-model="scope.row.offlineContractNo" size="small" placeholder="请输入" />
              </template>
            </el-table-column>
            <el-table-column prop="offlineShippingPrice" label="线下销售出货价" min-width="130">
              <template #default="scope">
                <el-input v-model="scope.row.offlineShippingPrice" size="small" placeholder="请输入" />
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="操作日志" name="logs">
          <!-- 操作日志内容 -->
          <el-table :data="orderOperationLogs" style="width: 100%" border>
            <el-table-column prop="operationType" label="操作类型" width="120" />
            <el-table-column prop="description" label="操作描述" />
            <el-table-column prop="operator" label="操作人" width="100" />
            <el-table-column prop="operationTime" label="操作时间" width="180" />
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 发货对话框 -->
    <el-dialog
      v-model="shipmentDialogVisible"
      title="订单发货"
      width="800px"
      @open-auto-focus="preventShipmentDialogAutoFocus"
    >
      <el-form :model="shipmentForm" label-width="140px" label-position="right">
        <!-- 发货要求：飞础科始终可见；交付方/出货方按「对交付方/出货方列显示」配置 -->
        <template v-if="canShowShipmentRequirementsInDialog">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="甲方送货单模板">
              <div class="delivery-note-upload-row">
                <el-upload
                  class="upload-demo"
                  action="/api/upload"
                  :on-success="handleDeliveryNoteUploadSuccess"
                  :before-upload="beforeUpload"
                  :limit="1"
                >
                  <el-button type="primary">上传文件</el-button>
                  <template #tip>
                    <div class="el-upload__tip">请上传甲方送货单模板；已上传后可点右侧预览</div>
                  </template>
                </el-upload>
                <el-button
                  v-if="shipmentForm.deliveryNoteUrl"
                  link
                  type="primary"
                  class="delivery-note-preview-btn"
                  @click="previewShipmentDeliveryNoteTemplate"
                >
                  <el-icon><Document /></el-icon>
                  预览
                </el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="需要签收单回传">
              <el-switch v-model="shipmentForm.needReceiptReturn" @change="onNeedReceiptReturnChange" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="送货单打印数量">
              <el-input-number v-model="shipmentForm.printQuantity" :min="1" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="哪些快递不能用">
              <el-input v-model="shipmentForm.forbiddenCouriers" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="打印箱唛">
              <el-switch v-model="shipmentForm.printBoxLabel" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="打印128条形码">
              <el-switch v-model="shipmentForm.printBarcode128" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20" v-if="shipmentForm.printBoxLabel">
          <el-col :span="24">
            <el-form-item label="上传箱唛文件">
              <el-upload
                class="upload-demo"
                action="/api/upload"
                :on-success="handleBoxLabelUploadSuccess"
                :on-remove="handleBoxLabelRemove"
                :file-list="boxLabelFileList"
                :limit="10"
                multiple
              >
                <el-button type="primary">上传文件</el-button>
                <template #tip>
                  <div class="el-upload__tip">支持上传多个箱唛文件</div>
                </template>
              </el-upload>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20" v-if="shipmentForm.printBarcode128 && shipmentForm.platformSku">
          <el-col :span="24" style="text-align: center; margin-bottom: 20px;">
            <div style="border: 1px solid #dcdfe6; padding: 20px; display: inline-block; background: #fff;">
              <img 
                :src="`/api/barcode/code128/with-text?text=${shipmentForm.platformSku}&width=400&height=130&labelText=${shipmentForm.platformSku}`" 
                alt="条形码预览"
                style="max-width: 100%;"
              />
              <div style="margin-top: 10px; color: #606266; font-size: 14px;">
                甲方SKU: {{ shipmentForm.platformSku }}
              </div>
              <div style="margin-top: 12px;">
                <el-button type="primary" plain size="small" @click="downloadBarcode128(shipmentForm.platformSku)">
                  下载128条码
                </el-button>
              </div>
            </div>
          </el-col>
        </el-row>
        </template>

        <!-- 物流信息（收货人/电话/地址）：与发货要求同权限控制，脱敏展示+一键复制 -->
        <template v-if="shouldShowShipmentLogisticsSection">
        <el-divider content-position="left">收货信息（物流）</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="收货人">
              <el-input v-model="shipmentForm.receiverName" placeholder="收货人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="收货电话">
              <el-input v-model="shipmentForm.receiverPhone" placeholder="收货电话" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="收货地址">
              <div style="display: flex; align-items: flex-start; gap: 8px;">
                <el-input v-model="shipmentForm.receiverAddress" type="textarea" :rows="2" placeholder="收货地址" style="flex: 1;" />
                <el-button v-if="currentShipmentOrder?.id" type="primary" plain size="small" @click="copyShipmentReceiverInfo" style="flex-shrink: 0;">
                  <el-icon><CopyDocument /></el-icon> 一键复制
                </el-button>
              </div>
              <div v-if="currentShipmentOrder?.id" style="margin-top: 4px; font-size: 12px; color: #909399;">点击「一键复制」可复制当前收货信息并记录</div>
            </el-form-item>
          </el-col>
        </el-row>
        </template>

        <el-divider content-position="left">发货信息</el-divider>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="发货地址">
              <el-input v-model="shipmentForm.shippingAddress" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="发货联系人">
              <el-input v-model="shipmentForm.shippingContact" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发货联系方式">
              <el-input v-model="shipmentForm.shippingPhone" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="配送方式">
              <el-select
                ref="deliveryMethodSelectRef"
                v-model="shipmentForm.deliveryMethod"
                filterable
                allow-create
                default-first-option
                placeholder="请选择"
                style="width: 100%"
                @change="handleDeliveryMethodChange"
                @blur="onShipmentDeliveryMethodBlur"
              >
                <el-option label="商家联系物流" value="商家联系物流" />
                <el-option label="自主车辆配送" value="自主车辆配送" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="SN编码">
              <el-input v-model="shipmentForm.snCode" placeholder="请输入SN编码" />
            </el-form-item>
          </el-col>
        </el-row>

        <template v-if="(shipmentForm.deliveryMethod || '').trim() === '商家联系物流'">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="物流公司">
                <el-select v-model="shipmentForm.logisticsCompany" filterable allow-create style="width: 100%" placeholder="请选择或输入物流公司" @blur="(e: FocusEvent) => { const t = (e.target as HTMLInputElement)?.value?.trim(); if (t) shipmentForm.logisticsCompany = t }">
                  <el-option
                    v-for="company in logisticsCompanies"
                    :key="company"
                    :label="company"
                    :value="company"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="母单物流单号">
                <el-input v-model="shipmentForm.trackingNumber" placeholder="发货地→目的地运单号" />
              </el-form-item>
            </el-col>
          </el-row>
          <!-- 回单：仅「商家联系物流」且开启「需要签收单回传」时展示；否则只需母单单号 -->
          <el-row v-if="shipmentForm.needReceiptReturn" :gutter="20">
            <el-col :span="12">
              <el-form-item label="回单物流单号">
                <el-input v-model="shipmentForm.returnReceiptTrackingNumber" placeholder="签收返单/回单单号；查轨迹默认顺丰" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="回单收件人手机">
                <el-input v-model="shipmentForm.returnReceiptReceiverPhone" placeholder="顺丰查轨迹需手机号后四位；与母单收货电话可不同" maxlength="20" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <template v-if="(shipmentForm.deliveryMethod || '').trim() === '自主车辆配送'">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="送货车牌号">
                <el-input v-model="shipmentForm.vehiclePlate" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="物流联系人">
                <el-input v-model="shipmentForm.logisticsContact" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="物流联系方式">
                <el-input v-model="shipmentForm.logisticsPhone" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="shipmentDialogVisible = false">取消</el-button>
          <el-tooltip
            v-if="canShowSyncButtonInShipment"
            content="将发货要求与物流联系人信息同步给被指派方；不点同步，对方可能暂时看不到这些信息。仓库账号点击同步时，仅同步母单物流号和SN编码。"
            placement="top"
          >
            <el-button type="success" @click="syncShipment">同步</el-button>
          </el-tooltip>
          <el-button type="primary" @click="submitShipment">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 签收单上传/编辑对话框（列表内「等待上传签收单」或「修改」打开） -->
    <el-dialog
      v-model="receiptUploadDialogVisible"
      title="签收单"
      width="520px"
      @close="currentReceiptOrder = null"
    >
      <el-form :model="receiptUploadForm" label-width="100px" label-position="right">
        <el-form-item label="签收时间" required>
          <el-date-picker
            v-model="receiptUploadForm.receiptTime"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择签收时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="签收状态">
          <el-select v-model="receiptUploadForm.receiptStatus" placeholder="请选择" style="width: 100%">
            <el-option label="等待签收" value="等待签收" />
            <el-option label="已上传" value="已上传" />
            <el-option label="妥投结束" value="妥投结束" />
          </el-select>
        </el-form-item>
        <el-form-item label="上传签收单">
          <el-upload
            class="upload-demo"
            action="/api/upload"
            :on-success="handleReceiptUploadSuccess"
            :before-upload="beforeUpload"
            :file-list="receiptFileList"
            :limit="1"
          >
            <el-button type="primary">上传文件</el-button>
            <template #tip>
              <div class="el-upload__tip">请上传签收单（图片/PDF/Word/Excel）</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item v-if="receiptUploadForm.receiptUrl" label="网页预览">
          <el-button link type="primary" @click="previewFile(receiptUploadForm.receiptUrl)">
            <el-icon><Document /></el-icon>
            在新窗口预览签收单
          </el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="receiptUploadDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitReceiptUpload">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 结算对话框 -->
    <el-dialog
      v-model="settlementDialogVisible"
      title="订单结算"
      width="800px"
    >
      <el-form :model="settlementForm" label-width="140px" label-position="right">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="预计回款时间">
              <span :class="{ 'expected-refund-overdue': !!settlementForm.expectedRefundOverdue }">
                {{ settlementForm.expectedRefundDate || settlementForm.expectedRefundPendingReason || '-' }}
              </span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规则说明">
              <span>{{ settlementForm.expectedRefundRuleDescription || '-' }}</span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="甲方对账单号" prop="platformReconciliationNo">
              <el-input v-model="settlementForm.platformReconciliationNo" :disabled="!canEditSettlementBySales" />
              <div class="settlement-field-hint">留空时系统会在生成销售对账单后自动带入甲方对账单号。</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发票号码" prop="invoiceNumber">
              <el-input v-model="settlementForm.invoiceNumber" placeholder="请输入发票号码" :disabled="!canEditSettlementByFinance" />
            </el-form-item>
            <el-form-item label="开票日期" prop="invoiceIssuedDate">
              <el-date-picker
                v-model="settlementForm.invoiceIssuedDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择开票日期"
                style="width: 100%"
                :disabled="!canEditSettlementByFinance"
              />
            </el-form-item>
            <el-form-item v-if="settlementInvoiceOcrWarnings.length" label=" ">
              <el-alert type="warning" :closable="false" show-icon>
                <template #title>
                  <div style="white-space: pre-wrap; font-size: 13px;">{{ settlementInvoiceOcrWarnings.join('\n') }}</div>
                </template>
              </el-alert>
            </el-form-item>
            <el-form-item v-if="settlementInvoiceOcrBuyerTitle || settlementInvoiceOcrSellerTitle" label=" ">
              <div class="settlement-invoice-ocr-result">
                <div class="settlement-invoice-ocr-result__title">
                  <span>发票识别结果</span>
                  <span v-if="settlementInvoiceBaiduVatUsed" class="settlement-invoice-ocr-result__source">结构化识别</span>
                </div>
                <div class="settlement-invoice-ocr-result__row">
                  <span class="settlement-invoice-ocr-result__label">购买方：</span>
                  <span class="settlement-invoice-ocr-result__value">{{ settlementInvoiceOcrBuyerTitle || '-' }}</span>
                  <span
                    v-if="settlementInvoiceOcrBuyerTitle"
                    :class="['settlement-invoice-ocr-result__tag', settlementInvoiceOcrBuyerMatched ? 'is-match' : 'is-mismatch']"
                  >
                    {{ settlementInvoiceOcrBuyerMatched ? '与订单一致' : '与订单不一致' }}
                  </span>
                </div>
                <div class="settlement-invoice-ocr-result__row">
                  <span class="settlement-invoice-ocr-result__label">销售方：</span>
                  <span class="settlement-invoice-ocr-result__value">{{ settlementInvoiceOcrSellerTitle || '-' }}</span>
                  <span
                    v-if="settlementInvoiceOcrSellerTitle"
                    :class="['settlement-invoice-ocr-result__tag', settlementInvoiceOcrSellerMatched ? 'is-match' : 'is-mismatch']"
                  >
                    {{ settlementInvoiceOcrSellerMatched ? '与订单一致' : '与订单不一致' }}
                  </span>
                </div>
              </div>
            </el-form-item>
            <el-form-item label="发票上传" prop="invoiceUrl" class="settlement-invoice-upload-item">
              <el-upload
                ref="settlementInvoiceUploadRef"
                class="upload-demo settlement-invoice-upload"
                action="/api/upload"
                :on-success="handleSettlementInvoiceUploadSuccess"
                :before-upload="beforeUpload"
                :on-remove="clearSettlementInvoiceFile"
                :file-list="settlementInvoiceFileList"
                :limit="1"
                :disabled="!canEditSettlementByFinance"
              >
                <el-button type="primary" :disabled="!canEditSettlementByFinance">上传文件</el-button>
                <template #tip>
                  <div class="el-upload__tip">支持上传图片、PDF、Word、Excel文件</div>
                </template>
              </el-upload>
              <div
                v-if="settlementInvoiceFileList.length && settlementInvoiceFileList[0]?.name"
                class="settlement-invoice-filename"
                :title="settlementInvoiceFileList[0].name"
              >
                {{ settlementInvoiceFileList[0].name }}
              </div>
              <el-button v-if="settlementForm.invoiceUrl" link type="primary" @click="previewInvoice(settlementForm.invoiceUrl)">预览发票</el-button>
              <el-button
                v-if="settlementForm.invoiceUrl && canEditSettlementByFinance"
                link
                type="danger"
                @click="clearSettlementInvoiceFile"
              >
                删除附件
              </el-button>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="对账单上传" prop="platformReconciliationUrl">
              <el-upload
                class="upload-demo"
                action="/api/upload"
                :on-success="handlePlatformReconciliationUploadSuccess"
                :before-upload="beforeUpload"
                :on-remove="clearSettlementReconciliationFile"
                :file-list="settlementReconciliationFileList"
                :limit="1"
                :disabled="!canEditSettlementBySales"
              >
                <el-button type="primary" :disabled="!canEditSettlementBySales">上传文件</el-button>
                <template #tip>
                  <div class="el-upload__tip">支持上传图片、PDF、Word、Excel文件</div>
                </template>
              </el-upload>
              <el-button
                v-if="settlementForm.platformReconciliationUrl"
                link
                type="primary"
                @click="previewInvoice(settlementForm.platformReconciliationUrl)"
              >
                预览对账单
              </el-button>
              <el-button
                v-if="settlementForm.platformReconciliationUrl && canEditSettlementBySales"
                link
                type="danger"
                @click="clearSettlementReconciliationFile"
              >
                删除附件
              </el-button>
            </el-form-item>
          </el-col>
          <el-col :span="12" />
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="结算单号" prop="settlementNo">
              <el-input v-model="settlementForm.settlementNo" :disabled="!canEditSettlementBySales" />
              <div class="settlement-field-hint">留空时系统会在后续结算流程中自动生成结算单号。</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="甲方回款状态" prop="platformRefundStatus">
              <el-select v-model="settlementForm.platformRefundStatus" style="width: 100%" :disabled="!canEditSettlementByFinance">
                <el-option label="未回款" value="未回款" />
                <el-option label="部分回款" value="部分回款" />
                <el-option label="已回款" value="已回款" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="结算单上传" prop="settlementUrl">
              <el-upload
                class="upload-demo"
                action="/api/upload"
                :on-success="handleSettlementNoUploadSuccess"
                :before-upload="beforeUpload"
                :on-remove="clearSettlementNoFile"
                :file-list="settlementNoFileList"
                :limit="1"
                :disabled="!canEditSettlementBySales"
              >
                <el-button type="primary" :disabled="!canEditSettlementBySales">上传文件</el-button>
                <template #tip>
                  <div class="el-upload__tip">支持上传图片、PDF、Word、Excel文件</div>
                </template>
              </el-upload>
              <el-button v-if="settlementForm.settlementUrl" link type="primary" @click="previewInvoice(settlementForm.settlementUrl)">预览结算单</el-button>
              <el-button
                v-if="settlementForm.settlementUrl && canEditSettlementBySales"
                link
                type="danger"
                @click="clearSettlementNoFile"
              >
                删除附件
              </el-button>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="回款附件上传" prop="platformRefundUrl">
              <el-upload
                class="upload-demo"
                action="/api/upload"
                :on-success="handlePlatformRefundUploadSuccess"
                :before-upload="beforeUpload"
                :on-remove="clearSettlementRefundFile"
                :file-list="settlementRefundFileList"
                :limit="1"
                :disabled="!canEditSettlementByFinance"
              >
                <el-button type="primary" :disabled="!canEditSettlementByFinance">上传文件</el-button>
                <template #tip>
                  <div class="el-upload__tip">支持上传图片、PDF、Word、Excel文件</div>
                </template>
              </el-upload>
              <el-button v-if="settlementForm.platformRefundUrl" link type="primary" @click="previewInvoice(settlementForm.platformRefundUrl)">预览回款附件</el-button>
              <el-button
                v-if="settlementForm.platformRefundUrl && canEditSettlementByFinance"
                link
                type="danger"
                @click="clearSettlementRefundFile"
              >
                删除附件
              </el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="settlementDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitSettlement">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 合同对话框 -->
    <el-dialog
      v-model="contractDialogVisible"
      title="合同管理"
      width="600px"
    >
      <el-form label-width="140px" label-position="right">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="合同上传">
              <el-upload
                class="upload-demo"
                action="/api/upload"
                :on-success="handleContractUploadSuccess"
                :before-upload="beforeContractUpload"
                :file-list="contractFileList"
                :limit="1"
              >
                <el-button type="primary">上传合同</el-button>
                <template #tip>
                  <div class="el-upload__tip">支持上传PNG、JPG、PDF文件</div>
                </template>
              </el-upload>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20" v-if="currentContractOrder?.contractUrl && canViewContract">
          <el-col :span="24">
            <el-form-item label="合同操作">
              <el-button type="primary" @click="previewContract">查看合同</el-button>
              <el-button type="success" @click="downloadContract" :loading="isDownloadingContract" :disabled="isDownloadingContract">下载合同</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeContractDialog">关闭</el-button>
          <el-button type="primary" @click="saveContract" :disabled="!contractFileList.length">保存合同</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 物流信息对话框 -->
    <el-dialog
      v-model="logisticsDialogVisible"
      :title="logisticsTraceKind === 'return' ? '回单物流轨迹' : '快递物流信息（母单）'"
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
          <el-table-column type="selection" width="55" />
          <el-table-column label="操作" width="80" align="center">
            <template #default="scope">
              <el-button :icon="Refresh" link type="primary" @click="refreshLogistics" />
            </template>
          </el-table-column>
          <el-table-column label="序号" width="80" align="center">
            <template #default="scope">
              1
            </template>
          </el-table-column>
          <el-table-column prop="company" label="快递公司" width="200" />
          <el-table-column prop="trackingNumber" label="快递单号" />
        </el-table>

        <div class="logistics-traces">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px;">
            <el-tabs v-model="activeTab" style="flex: 1;">
              <el-tab-pane label="物流轨迹" name="trace" />
            </el-tabs>
            <div style="display: flex; align-items: center;">
              <span style="margin-right: 10px;">地图模式</span>
              <el-switch v-model="mapMode" />
            </div>
          </div>

          <div v-if="logisticsInfo" class="trace-list">
            <div v-for="(trace, index) in logisticsInfo.traces" :key="index" class="trace-item" :class="{ 'latest': index === 0 }">
              <div class="trace-dot" :class="{ 'active': index === 0 }">
                <el-icon v-if="index === 0"><CircleCheck /></el-icon>
              </div>
              <div class="trace-content">
                <div class="trace-status" :class="{ 'latest': index === 0 }">
                  {{ trace.status }}
                </div>
                <div class="trace-desc" :class="{ 'latest': index === 0 }">
                  {{ trace.desc }}
                </div>
                <div class="trace-time">
                  {{ trace.time }}
                </div>
              </div>
              <div v-if="index < logisticsInfo.traces.length - 1" class="trace-line"></div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 确认订单对话框 -->
    <el-dialog
      v-model="auditDialogVisible"
      title="确认订单"
      width="500px"
    >
      <el-form :model="auditForm" label-width="120px" label-position="right">
        <el-form-item label="交货日期" required>
          <el-date-picker 
            v-model="auditDeliveryDate" 
            type="date" 
            value-format="YYYY-MM-DD" 
            placeholder="请选择交货日期"
            style="width: 100%" 
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="auditDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitAudit">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 编辑交货日期对话框 -->
    <el-dialog
      v-model="editDeliveryDateDialogVisible"
      title="编辑交货日期"
      width="500px"
    >
      <el-form :model="editDeliveryDateForm" label-width="120px" label-position="right">
        <el-form-item label="交货日期" required>
          <el-date-picker 
            v-model="editDeliveryDateForm.deliveryDate" 
            type="date" 
            value-format="YYYY-MM-DD" 
            placeholder="请选择交货日期"
            style="width: 100%" 
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editDeliveryDateDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitEditDeliveryDate">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 退回订单对话框 -->
    <el-dialog
      v-model="returnDialogVisible"
      title="退回订单"
      width="500px"
    >
      <el-form :model="returnForm" label-width="100px" label-position="right">
        <el-form-item label="退回原因" required>
          <el-input 
            v-model="returnForm.returnReason" 
            type="textarea" 
            :rows="4"
            placeholder="请输入退回原因，例如：价格不对、型号不对等" 
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="returnDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitReturn" :disabled="!returnForm.returnReason">确认退回</el-button>
        </span>
      </template>
    </el-dialog>

    <el-dialog
      v-model="structureInspectVisible"
      title="主单/分配链路观察"
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

        <div v-if="currentStructureOrder" class="structure-inspect-head">
          <div>当前订单：{{ currentStructureOrder.platformOrderNo || '-' }}</div>
          <div>OMS订单号：{{ currentStructureOrder.omsOrderNo || '-' }}</div>
          <div>主单ID：{{ currentStructureOrder.masterId || '-' }}</div>
          <div>分配ID：{{ currentStructureOrder.allocationId || '-' }}</div>
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
          <div class="structure-section-title">订单行</div>
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
            v-if="currentStructureOrder && (!currentStructureOrder.masterId || !currentStructureOrder.allocationId)"
            :loading="structureBackfillLoading"
            @click="backfillCurrentStructureOrder"
          >
            补挂当前订单
          </el-button>
          <el-button @click="structureInspectVisible = false">关闭</el-button>
          <el-button
            type="primary"
            :loading="structureInspectLoading"
            @click="refreshStructureInspect"
            :disabled="!currentStructureOrder"
          >
            刷新
          </el-button>
        </span>
      </template>
    </el-dialog>

    <ErpEntryDialog
      v-model="erpEntryDialogVisible"
      target-type="sales"
      :row="currentErpEntryRow"
      :rows="currentErpEntryRows"
      @saved="handleErpEntrySaved"
    />
    <SalesBatchShipmentDialog
      v-model="batchShipmentDialogVisible"
      :rows="currentBatchShipmentRows"
      @saved="handleBatchShipmentSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, CircleCheck, MoreFilled, Edit, Check, Van, UserFilled, Document, Money, Plus, Delete, Picture, Share, CopyDocument } from '@element-plus/icons-vue'
import { maskName, maskPhone, maskAddress, copyWithPrivacyLog, logPrivacyAccess } from '@/utils/privacy'
import ErpEntryDialog from '@/components/ErpEntryDialog.vue'
import SalesBatchShipmentDialog from '@/components/SalesBatchShipmentDialog.vue'
import request from '../../utils/request'
import { apiBase } from '@/utils/apiBase'
import draggable from 'vuedraggable'

const loading = ref(false)
// 使用 computed 保证每次判断时拿到最新登录名（避免 xckj 等被指派方无法退回）
const currentUsername = computed(() => (localStorage.getItem('username') || '').trim())
const currentUserId = ref<number | null>(null)
const route = useRoute()
const router = useRouter()
const showMoreFilters = ref(false)
const filterForm = reactive({
  receiptFilter: '' as string,
  platformOrderNo: '',
  omsOrderNo: '',
  partyATitleKeyword: '',
  deliveryPartyKeyword: '',
  status: '待指派',
  excludeStatuses: '' as string,
  erpEntryStatus: '' as string,
  platformRefundStatus: '' as string,
  expectedRefundOverdue: '' as string,
  ecommerceSalesName: '' as string,
  offlineSales: '' as string,
  needReceiptSlip: '' as string
})
const permissionSet = computed(() => {
  const raw = localStorage.getItem('permissions') || ''
  return new Set(raw.split(',').map((s: string) => s.trim()).filter(Boolean))
})
const isAdminUser = computed(() => localStorage.getItem('username') === 'admin')
const hasPermission = (perm: string) => isAdminUser.value || permissionSet.value.has(perm)
const canEditSettlementBySales = computed(() => hasPermission('settlement_sales'))
const canEditSettlementByFinance = computed(() =>
  hasPermission('settlement_finance') || hasPermission('platform_refund')
)
const dashboardEntryHint = computed(() => {
  const entry = String(route.query.dashboardCard || '').trim()
  if (entry === 'pendingWorkOrder') {
    return '当前从 Dashboard 的“待处理工单”进入。该卡片尚未接入专属工单维度，现临时落到销售列表供人工排查。'
  }
  if (entry === 'pendingClaim') {
    return '当前从 Dashboard 的“待处理索赔单”进入。该卡片尚未接入专属索赔维度，现临时落到销售列表供人工排查。'
  }
  if (entry === 'rejectedReceipt') {
    return '当前从 Dashboard 的“已拒绝签收单”进入。该卡片尚未接入专属拒签筛选，现临时落到销售列表供人工排查。'
  }
  return ''
})
const statusFilterHint = computed(() => {
  if (String(filterForm.status || '').trim() !== '已开票待结算') {
    return ''
  }
  const refundStatus = String(filterForm.platformRefundStatus || '').trim()
  const base = '当前“已开票待结算”会展示状态为“已开票待结算”的订单，以及历史“已开票”但甲方仍未回款/部分回款的订单；“已回款”不会计入。'
  if (!refundStatus) {
    return base
  }
  const refundLabel = refundStatus === '未回款,部分回款' ? '未回款/部分回款' : refundStatus
  return `${base} 当前还叠加了“甲方回款状态：${refundLabel}”筛选。`
})
const masterViewHint = computed(() => {
  const ignoredLabels: string[] = []
  if (String(filterForm.status || '').trim()) ignoredLabels.push('状态')
  if (String(filterForm.erpEntryStatus || '').trim()) ignoredLabels.push('商务ERP录单')
  if (String(filterForm.platformRefundStatus || '').trim()) ignoredLabels.push('甲方回款状态')
  if (String(filterForm.partyATitleKeyword || '').trim()) ignoredLabels.push('甲方抬头')
  if (String(filterForm.deliveryPartyKeyword || '').trim()) ignoredLabels.push('交付方')
  if (String(filterForm.expectedRefundOverdue || '').trim()) ignoredLabels.push('预计回款')
  if (String(filterForm.ecommerceSalesName || '').trim()) ignoredLabels.push('业务员')
  if (String(filterForm.offlineSales || '').trim()) ignoredLabels.push('线下销售')
  if (String(filterForm.receiptFilter || '').trim()) ignoredLabels.push('签收单状态')
  if (ignoredLabels.length === 0) {
    return '当前为主单视角第一版，优先支持按甲方订单号、OMS订单号观察整单结构；具体业务动作仍建议切回明细视角处理。'
  }
  return `当前为主单视角第一版，已设置的 ${ignoredLabels.join('、')} 暂不参与主单筛选；建议主要使用甲方订单号、OMS订单号定位整单。`
})

// 甲方合同：飞础科/热像抬头；rxkj-sw、rxkj-cw 全量热像+飞础科合同；权限 contract_full_rx_feichuke / sales_feichuke_contract
const CONTRACT_VIEW_COMPANIES = ['飞础科智慧科技（上海）有限公司', '上海热像科技股份有限公司']
const CONTRACT_FULL_PORTAL_USERNAMES = ['rxkj-sw', 'rxkj-cw']
const canViewContract = computed(() => {
  const u = (localStorage.getItem('username') || '').trim()
  if (CONTRACT_FULL_PORTAL_USERNAMES.includes(u)) return true
  if (hasPermission('contract_full_rx_feichuke') || hasPermission('sales_feichuke_contract')) return true
  const ct = (localStorage.getItem('companyTitle') || '').trim()
  if (!ct) return false
  if (CONTRACT_VIEW_COMPANIES.some(c => ct.includes(c) || c.includes(ct))) return true
  if (ct.includes('飞础科智慧科技（上海）') || ct.includes('飞础科智慧科技')) return true
  return false
})

// 仓库角色：仅保留发货权限，不显示编辑/退回/删除/合同/确认订单/结算
const isWarehouseRole = computed(() => {
  const role = (localStorage.getItem('role') || '').trim()
  if (role === 'ROLE_WAREHOUSE' || role === '仓库') return true
  if (role && role.toUpperCase().includes('WAREHOUSE')) return true
  const p = (localStorage.getItem('permissions') || '').trim()
  if (p && p.split(',').map((s: string) => s.trim()).some((s: string) => s.toLowerCase() === 'warehouse')) return true
  // 兼容：真实姓名或登录名包含「仓库」时视为仓库身份（如 热像科技-仓库）
  const realName = (localStorage.getItem('realName') || '').trim()
  if (realName && (realName === '仓库' || realName.endsWith('-仓库') || realName.includes('仓库'))) return true
  const loginName = (localStorage.getItem('username') || '').trim()
  if (loginName && (loginName === '仓库' || loginName.endsWith('-仓库') || loginName.includes('仓库'))) return true
  return false
})

const assignedUserVisibleColumns = [
  'status',
  'erpEntryStatus',
  'receiptSlip',
  'boxMark',
  'snCode',
  'trackingNumber',
  'platformName',
  'platformOrderNo',
  'omsOrderNo',
  'orderDate',
  'platformSku',
  'quantity',
  'taxIncludedPrice',
  'taxIncludedTotal',
  'paymentMethod',
  'expectedRefundDate',
  'orderType',
  'receiverName',
  'receiverPhone',
  'receiverAddress',
  'purchaseOrderNo',
  'offlineSales',
  'deliveryDate',
  'logisticsNo',
  'deliveryParty',
  'shippingParty',
  'deliveryPartyPurchasePrice',
  'settlementNo',
  'deductionRate',
  'assignTime',
  'returnReason',
  'returnTime',
  'returnedBy'
]

const alwaysVisibleColumns = ['操作', '发货要求']

const isAssignedUserForOrder = (row: any) => {
  const assigned = (row.assignedUsername || '').toString().trim()
  const current = currentUsername.value
  if (assigned && current && assigned === current) return true
  return false
}

// 是否可退回：当前用户为订单被指派方，或公司匹配交付方，或当前行是「被指派到我」的链式单（链式单 assignedUsername/deliveryParty 常为空，用 CHAIN_FROM 识别）
const canReturnOrder = (row: any) => {
  if (isAssignedUserForOrder(row)) return true
  const companyTitle = (localStorage.getItem('companyTitle') || '').trim()
  const deliveryParty = (row.deliveryParty || '').toString().trim()
  if (companyTitle && deliveryParty) {
    if (deliveryParty === companyTitle) return true
    if (deliveryParty.indexOf(companyTitle) >= 0 || companyTitle.indexOf(deliveryParty) >= 0) return true
  }
  const poNo = (row.purchaseOrderNo || '').toString()
  if (poNo.startsWith('CHAIN_FROM:')) return true
  return false
}

// 是否可转派：当前用户为被指派方且订单未退回；上海热像科技股份有限公司为工厂，不再转派
// 被指派方看到的是链式单（purchaseOrderNo 以 CHAIN_FROM: 开头），链式单 assignedUsername 为空，需单独判断
const canReassignFromSales = (row: any) => {
  const companyTitle = (localStorage.getItem('companyTitle') || '').trim()
  if (companyTitle && (companyTitle.includes('上海热像科技股份有限公司') || companyTitle.includes('上海热像科技'))) return false
  if (row.status === '已退回') return false
  const current = currentUsername.value
  if (!current) return false
  const assigned = (row.assignedUsername || '').toString().trim()
  if (assigned && assigned === current) return true
  const poNo = (row.purchaseOrderNo || '').toString()
  if (poNo.startsWith('CHAIN_FROM:')) return true
  return false
}

// 是否为「已退回」订单的指派方（仅指派方可见跳转，避免被指派方点击后暴露指派方列表）
const isAssignerForReturnedOrder = (row: any) => {
  if (row?.status !== '已退回') return false
  const createdBy = row.createdBy != null ? Number(row.createdBy) : null
  const uid = currentUserId.value != null ? Number(currentUserId.value) : null
  return uid != null && createdBy != null && uid === createdBy
}
// 从销售列表跳转到采购管理-销售订单指派并打开该订单的指派/转派弹窗（待指派防呆：创建人点击「待指派」即跳转去指派）
const goToAssignOrder = (row: any) => {
  if (!row?.id) return
  router.push({ path: '/purchase', query: { tab: 'assign', orderId: String(row.id) } })
}
const goToReassign = (row: any) => {
  goToAssignOrder(row)
}

// 当前是否为「被指派方」在编辑（用于禁止修改指派方推送的交付方采购价/扣点/含税价）
const isAssignedUserEditing = computed(() =>
  !!editingOrderRow.value && currentUsername.value === editingOrderRow.value.assignedUsername
)

// 当前列表是否全是「被指派方视角」订单（主单 assignedUsername=当前用户，或链式单 CHAIN_FROM:）
const isAssignedUserOnly = computed(() => {
  if (tableData.value.length === 0) return false
  return tableData.value.every((row: any) => {
    if (isAssignedUserForOrder(row)) return true
    const poNo = (row.purchaseOrderNo || '').toString()
    return poNo.startsWith('CHAIN_FROM:')
  })
})

// 是否为「交付方/出货方」视角（仅这类账号受「对交付方/出货方列显示」配置控制；飞础科、上海热像科技与主链同权，始终可见）
const isDeliveryOrShippingPartyViewer = computed(() => {
  if (isFeichukeCompany.value) return false
  const ct = (localStorage.getItem('companyTitle') || '').trim()
  if (ct && (ct.includes('上海热像科技股份有限公司') || ct.includes('上海热像科技'))) return false
  return !!ct && tableData.value.length > 0
})
const shouldShowColumn = (columnLabel: string) => {
  if (!isAssignedUserOnly.value) {
    if ((columnLabel === 'boxMark' || columnLabel === 'receiverName') && isDeliveryOrShippingPartyViewer.value) {
      const config = getDeliveryPartyColumnsConfig()
      if (columnLabel === 'boxMark' && !config.boxMark) return false
      if (columnLabel === 'receiverName' && !config.receiverName) return false
    }
    return true
  }
  if (!assignedUserVisibleColumns.includes(columnLabel)) return false
  if (columnLabel === 'boxMark' || columnLabel === 'receiverName') {
    const config = getDeliveryPartyColumnsConfig()
    if (columnLabel === 'boxMark' && !config.boxMark) return false
    if (columnLabel === 'receiverName' && !config.receiverName) return false
  }
  return true
}

// 发货弹窗：发货要求/物流信息对交付方是否开放（与列表「对交付方/出货方列显示」同源配置）
const DELIVERY_PARTY_COLUMNS_KEY = 'oms_columns_open_to_delivery_party'
const getDeliveryPartyColumnsConfig = (): { boxMark: boolean; receiverName: boolean } => {
  try {
    const raw = localStorage.getItem(DELIVERY_PARTY_COLUMNS_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      return { boxMark: parsed.boxMark !== false, receiverName: parsed.receiverName !== false }
    }
  } catch (_) {}
  return { boxMark: true, receiverName: true }
}
const isFeichukeCompany = computed(() => {
  const ct = (localStorage.getItem('companyTitle') || '').trim()
  return !!ct && (ct.includes('飞础科') || ct.includes('飞础科智慧科技'))
})
// 发货弹窗内「发货要求」区块：飞础科、上海热像科技始终显示；交付方/出货方按配置
const canShowShipmentRequirementsInDialog = computed(() => {
  if (isFeichukeCompany.value) return true
  const ct = (localStorage.getItem('companyTitle') || '').trim()
  if (ct && (ct.includes('上海热像科技股份有限公司') || ct.includes('上海热像科技'))) return true
  return getDeliveryPartyColumnsConfig().boxMark
})
// 发货弹窗内「收货信息/物流」区块：同上
const canShowLogisticsInDialog = computed(() => {
  if (isFeichukeCompany.value) return true
  const ct = (localStorage.getItem('companyTitle') || '').trim()
  if (ct && (ct.includes('上海热像科技股份有限公司') || ct.includes('上海热像科技'))) return true
  return getDeliveryPartyColumnsConfig().receiverName
})
const shouldShowShipmentLogisticsSection = computed(() => {
  if (!canShowLogisticsInDialog.value) return false
  if (isFeichukeCompany.value) return true
  const ct = (localStorage.getItem('companyTitle') || '').trim()
  if (ct && (ct.includes('上海热像科技股份有限公司') || ct.includes('上海热像科技'))) return true
  return hasReceiverInfo(shipmentForm)
})
// 仅飞础科、上海热像科技显示「同步」按钮；交付方/出货方不显示
const canShowSyncButtonInShipment = computed(() => {
  const ct = (localStorage.getItem('companyTitle') || '').trim()
  return !!ct && (ct.includes('飞础科') || ct.includes('上海热像科技股份有限公司'))
})

// 甲方抬头仅从合作管理-用户信息维护获取，甲方身份排前面，其余也可选
const platformOptions = ref<string[]>([])
const platformNameSelectRef = ref<any>(null)
const salesUsers = ref<any[]>([])
/** 当前所选业务员在用户信息维护中对应的联系人（如坚领→袁星辉） */
const salesPersonContact = ref('')
const partnerInfoList = ref<any[]>([])
const products = ref<any[]>([])
const tableData = ref<any[]>([])
const masterTableData = ref<any[]>([])
const selectedSalesRows = ref<any[]>([])
const total = ref(0)
const masterTotal = ref(0)
const listRefreshKey = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const masterCurrentPage = ref(1)
const masterPageSize = ref(10)
const masterLoading = ref(false)
const masterViewDrawerVisible = ref(false)
const masterDetailMap = ref<Record<string, any>>({})
const masterDetailLoadingMap = ref<Record<string, boolean>>({})

// 记忆功能选项
const invoiceTitleOptions = ref<string[]>([])
const finalCustomerTitleOptions = ref<string[]>([])
const receiverNameOptions = ref<string[]>([])
const receiverPhoneOptions = ref<string[]>([])
const paymentMethodOptions = ref<string[]>(['账期', '背靠背', '全款'])
/** 按甲方抬头（platformName）记忆上次填写的发票「账期」值（后端字段仍为 paymentMethod） */
const creditTermByPlatformName = ref<Record<string, string>>({})
const deliveryPartyOptions = ref<string[]>([])
const shippingPartyOptions = ref<string[]>([])
/** 对账明细「线下销售」记忆下拉（第三方订单常用） */
const reconciliationOfflineSalesOptions = ref<string[]>([])

// 合作方信息过滤计算属性
const platformPartnerList = computed(() => {
  return partnerInfoList.value.filter((p: any) => 
    p.identities && p.identities.includes('平台方')
  )
})

const deliveryPartnerList = computed(() => {
  return partnerInfoList.value.filter((p: any) => 
    p.identities && p.identities.includes('交付方')
  )
})

const shippingPartnerList = computed(() => {
  return partnerInfoList.value.filter((p: any) => 
    p.identities && p.identities.includes('出货方')
  )
})

// 甲方抬头不在用户信息维护列表中时显示提示（与指派界面交付方一致）
const showPlatformNameHint = computed(() => {
  const v = (orderForm.platformName || '').trim()
  return !!v && !platformOptions.value.includes(v)
})

/** 存在第三方订单商品时，对账明细「线下销售」表头显示必填提示 */
const showReconciliationOfflineSalesRequiredHint = computed(() =>
  productList.value.some((p: any) => (p?.orderType || '') === '第三方订单')
)

// 业务员只表示我方负责人，列表应独立于甲方抬头，不再被甲方映射筛选或自动覆盖。
const salesUsersForSelect = computed(() => {
  const base = salesUsers.value
  const sid = orderForm.ecommerceSalesId
  if (sid == null || sid === '') return base
  const idStr = String(sid)
  if (base.some((u: any) => String(u.id) === idStr)) return base
  const fromAll = salesUsers.value.find((u: any) => String(u.id) === idStr)
  if (fromAll) return [...base, fromAll]
  const creatorId = orderForm.createdBy
  if (creatorId != null && creatorId !== '' && String(creatorId) === idStr) {
    return [...base, {
      id: Number(creatorId),
      username: orderForm.creatorUsername || '',
      realName: (orderForm.creatorName || orderForm.ecommerceSalesName || '').trim() || `用户 #${idStr}`
    }]
  }
  const name = (orderForm.ecommerceSalesName || '').trim()
  return [...base, {
    id: Number(sid),
    username: '',
    realName: name || `用户 #${idStr}`
  }]
})

/** 销售列表筛选：业务员名下拉（去重展示名，与订单列 ecommerceSalesName 模糊匹配一致） */
const salespersonFilterOptions = computed(() => {
  const seen = new Set<string>()
  const out: string[] = []
  for (const u of salesUsers.value) {
    const n = String(u.realName || u.username || '').trim()
    if (n && !seen.has(n)) {
      seen.add(n)
      out.push(n)
    }
  }
  out.sort((a, b) => a.localeCompare(b, 'zh-CN'))
  return out
})

const omsOrderSeqSeedByPrefix = reactive<Record<string, number>>({})

// 加载记忆选项
const loadMemoryOptions = () => {
  const saved = localStorage.getItem('orderMemoryOptions')
  if (saved) {
    try {
      const options = JSON.parse(saved)
      invoiceTitleOptions.value = options.invoiceTitle || []
      finalCustomerTitleOptions.value = options.finalCustomerTitle || []
      receiverNameOptions.value = options.receiverName || []
      receiverPhoneOptions.value = options.receiverPhone || []
      paymentMethodOptions.value = options.paymentMethod || ['账期', '背靠背', '全款']
      creditTermByPlatformName.value =
        options.creditTermByPlatformName && typeof options.creditTermByPlatformName === 'object'
          ? options.creditTermByPlatformName
          : {}
      deliveryPartyOptions.value = options.deliveryParty || []
      shippingPartyOptions.value = options.shippingParty || []
      reconciliationOfflineSalesOptions.value = Array.isArray(options.reconciliationOfflineSales)
        ? options.reconciliationOfflineSales
        : []
    } catch (error) {
      console.error('Load memory options error:', error)
    }
  }
  
}

// 保存记忆选项
const saveMemoryOptions = () => {
  const options = {
    invoiceTitle: invoiceTitleOptions.value,
    finalCustomerTitle: finalCustomerTitleOptions.value,
    receiverName: receiverNameOptions.value,
    receiverPhone: receiverPhoneOptions.value,
    paymentMethod: paymentMethodOptions.value,
    creditTermByPlatformName: creditTermByPlatformName.value,
    deliveryParty: deliveryPartyOptions.value,
    shippingParty: shippingPartyOptions.value,
    reconciliationOfflineSales: reconciliationOfflineSalesOptions.value
  }
  localStorage.setItem('orderMemoryOptions', JSON.stringify(options))
}

// 添加记忆选项
const addMemoryOption = (key: string, value: string) => {
  if (!value) return
  let options: string[]
  switch (key) {
    case 'invoiceTitle':
      options = invoiceTitleOptions.value
      break
    case 'finalCustomerTitle':
      options = finalCustomerTitleOptions.value
      break
    case 'receiverName':
      options = receiverNameOptions.value
      break
    case 'receiverPhone':
      options = receiverPhoneOptions.value
      break
    case 'paymentMethod':
      options = paymentMethodOptions.value
      break
    case 'deliveryParty':
      options = deliveryPartyOptions.value
      break
    case 'shippingParty':
      options = shippingPartyOptions.value
      break
    case 'reconciliationOfflineSales':
      options = reconciliationOfflineSalesOptions.value
      break
    default:
      return
  }
  if (!options.includes(value)) {
    options.push(value)
    saveMemoryOptions()
  }
}

function isThirdPartyOrderProductIndex(productIndex: number | undefined | null) {
  if (productIndex === undefined || productIndex === null) return false
  const p = productList.value[Number(productIndex)]
  const orderType = String(p?.orderType || '').trim()
  return orderType.includes('第三方')
}

function onReconciliationOfflineSalesChange(val: string) {
  if (!val || typeof val !== 'string') return
  const v = val.trim()
  if (!v) return
  const opts = reconciliationOfflineSalesOptions.value
  if (!opts.includes(v)) {
    reconciliationOfflineSalesOptions.value = [...opts, v]
    saveMemoryOptions()
  }
}

function isNewOrderDraft() {
  const id = (orderForm as any).id
  return id == null || id === ''
}

/** 发票多行时账期与第一行一致，并写回订单主表字段供保存 */
function syncInvoicePaymentMethodFromFirst() {
  const first = String(invoiceList.value[0]?.paymentMethod ?? '').trim()
  for (let i = 1; i < invoiceList.value.length; i++) {
    invoiceList.value[i].paymentMethod = first
  }
  orderForm.paymentMethod = first
}

/** 新建订单：根据甲方抬头带出上次记忆的账期 */
function applyRememberedCreditTermForNewOrder() {
  if (!isNewOrderDraft()) return
  const title = (orderForm.platformName || '').trim()
  if (!title || invoiceList.value.length === 0) return
  const current = String(invoiceList.value[0]?.paymentMethod ?? '').trim()
  // 当前账期已有值时不覆盖，避免用户手动修改后切换甲方又被默认值顶掉
  if (current) {
    syncInvoicePaymentMethodFromFirst()
    return
  }
  const remembered = (creditTermByPlatformName.value[title] || '').trim()
  if (!remembered) return
  invoiceList.value[0].paymentMethod = remembered
  if (!paymentMethodOptions.value.includes(remembered)) {
    paymentMethodOptions.value = [...paymentMethodOptions.value, remembered]
  }
  syncInvoicePaymentMethodFromFirst()
}

// 甲方抬头：失焦时写回 v-model；若不在列表中不自动创建，由提示引导去用户信息维护
function onPlatformNameBlur() {
  const sel = platformNameSelectRef.value
  const input = sel?.$el?.querySelector?.('input')
  const text = input?.value?.trim()
  if (!text) return
  orderForm.platformName = text
  applyRememberedCreditTermForNewOrder()
}

// 甲方抬头变更时仅处理甲方自身相关逻辑，不再改写我方业务员。
async function onPlatformNameChange(platformName: string) {
  const title = (platformName || '').trim()
  if (!title) {
    await fetchSalesPersonContact()
    return
  }
  await fetchSalesPersonContact()
  applyRememberedCreditTermForNewOrder()
}

// 发票信息「账期」：第一行变更时同步所有行，并按甲方抬头写入本地记忆（字段名仍为 paymentMethod）
function onInvoiceCreditTermChange(val: string, rowIndex: number) {
  if (rowIndex !== 0) return
  const v = typeof val === 'string' ? val.trim() : ''
  if (v && !paymentMethodOptions.value.includes(v)) {
    paymentMethodOptions.value = [...paymentMethodOptions.value, v]
  }
  syncInvoicePaymentMethodFromFirst()
  const platform = (orderForm.platformName || '').trim()
  if (platform && v) {
    creditTermByPlatformName.value = { ...creditTermByPlatformName.value, [platform]: v }
  }
  saveMemoryOptions()
}

function onInvoiceCreditTermBlur(e: FocusEvent, rowIndex: number) {
  if (rowIndex !== 0) return
  onFilterableSelectBlur(
    e,
    (v) => { invoiceList.value[rowIndex].paymentMethod = v },
    paymentMethodOptions,
    saveMemoryOptions
  )
  const text = (e.target as HTMLInputElement)?.value?.trim()
  if (text) {
    onInvoiceCreditTermChange(text, rowIndex)
  }
}

// 通用：任意 filterable allow-create 下拉失焦时提交输入到 row 并加入记忆
function onFilterableSelectBlur(
  e: FocusEvent,
  setValue: (v: string) => void,
  optionsRef: { value: string[] } | string[],
  saveMemory?: () => void
) {
  const text = (e.target as HTMLInputElement)?.value?.trim()
  if (!text) return
  setValue(text)
  const opts = Array.isArray(optionsRef) ? optionsRef : optionsRef?.value
  if (!Array.isArray(opts)) return
  if (!opts.includes(text)) {
    if (Array.isArray(optionsRef)) {
      optionsRef.splice(0, optionsRef.length, ...opts, text)
    } else {
      optionsRef.value = [...opts, text]
    }
    saveMemory?.()
  }
}

// 最终客户抬头：change 时把自定义输入加入选项并持久化，与支付方式同一套
function onFinalCustomerTitleChange(val: string) {
  if (!val || typeof val !== 'string') return
  const opts = finalCustomerTitleOptions.value
  if (!Array.isArray(opts)) return
  if (!opts.includes(val)) {
    finalCustomerTitleOptions.value = [...opts, val]
    saveMemoryOptions()
  }
}

// 商品型号支持手输：失焦时把输入框内容写回当前行，避免输入后切换单元格被清空
function onModelSelectBlur(e: FocusEvent, row: any) {
  const text = (e.target as HTMLInputElement)?.value?.trim()
  if (text) {
    row.model = text
    handleProductModelChange(row)
  }
}

function mergeUsersById(primary: any[], extra: any[]) {
  const map = new Map<string, any>()
  for (const u of [...primary, ...extra]) {
    if (u == null || u.id == null) continue
    map.set(String(u.id), u)
  }
  return Array.from(map.values())
}

const fetchUsers = async () => {
  try {
    const userListParams = { page: 0, size: 1000 }
    const opt = { skipErrorMsg: true, skipErrorLog: true } as const
    const norm = (r: any) => r?.content || (Array.isArray(r) ? r : [])
    const [resEcom, resXiaoan] = await Promise.all([
      request.get('/users', { params: { ...userListParams, role: 'ROLE_ECOMMERCE' }, ...opt }),
      request.get('/users', { params: { ...userListParams, role: 'ROLE_XIAOAN' }, ...opt }).catch(() => ({ content: [] }))
    ])
    let content = mergeUsersById(norm(resEcom), norm(resXiaoan))
    // 同公司用户一并拉取（不限制角色），避免订单里业务员是交付方等非 ROLE_ECOMMERCE 时下拉无选项、界面只显示 id
    const ct = (localStorage.getItem('companyTitle') || '').trim()
    if (ct) {
      try {
        const byCompany: any = await request.get('/users/by-company-title', {
          params: { companyTitle: ct },
          skipErrorMsg: true,
          skipErrorLog: true
        } as any)
        const extra = Array.isArray(byCompany) ? byCompany : []
        content = mergeUsersById(content, extra)
      } catch {
        /* ignore */
      }
    }
    // 交付方等账号可能不是 ROLE_ECOMMERCE，把当前用户加入业务员列表以便选自己并带出联系人（如坚领→袁星辉）
    try {
      const meRes: any = await request.get('/users/me', {
        skipErrorMsg: true,
        skipErrorLog: true
      } as any)
      const meId = meRes?.id
      const meUsername = (meRes?.username || '').trim()
      const meRealName = (meRes?.realName || '').trim() || meUsername
      if (meId && !content.some((u: any) => String(u.id) === String(meId))) {
        salesUsers.value = [{ id: meId, username: meUsername, realName: meRealName }, ...content]
      } else {
        salesUsers.value = content
      }
    } catch {
      salesUsers.value = content
    }
    console.log('Fetched sales users:', salesUsers.value)
  } catch (error) {
    console.error('Fetch users error:', error)
  }
}

const fetchProducts = async () => {
  try {
    const res: any = await request.get('/products?page=0&size=1000', {
      skipErrorMsg: true,
      skipErrorLog: true
    } as any)
    // 处理分页响应
    const content = res?.content || (Array.isArray(res) ? res : [])
    // 过滤掉没有型号的商品
    products.value = content.filter((product: any) => product.model && product.model.trim() !== '')
    console.log('Fetched products:', products.value)
  } catch (error) {
    console.error('Fetch products error:', error)
  }
}

const fetchPartnerInfo = async () => {
  try {
    const res: any = await request.get('/partner-info?page=0&size=1000', {
      skipErrorMsg: true,
      skipErrorLog: true
    } as any)
    const content = res?.content || (Array.isArray(res) ? res : [])
    partnerInfoList.value = content
    const hasPlatform = (p: any) => (p.identities || '').split(',').map((s: string) => s.trim()).includes('平台方')
    const toTitle = (p: any) => (p.title || p.name || '').trim()
    const platformFirst = [...new Set((content || []).filter(hasPlatform).map(toTitle).filter(Boolean))] as string[]
    const rest = [...new Set((content || []).filter((p: any) => !hasPlatform(p)).map(toTitle).filter(Boolean))] as string[]
    const restOnly = rest.filter((t: string) => !platformFirst.includes(t))
    platformOptions.value = [...platformFirst, ...restOnly]
  } catch (error) {
    console.error('Fetch partner info error:', error)
  }
}

const fetchOrders = async (noCache = false) => {
  loading.value = true
  try {
    const params: Record<string, any> = {
      ...filterForm,
      view: 'sales',
      /** 隐藏「下一层」链式单（CHAIN_FROM:），仅保留本人创建的链式单；创建人列表不再出现被指派方那条链式单 */
      excludeChainOrders: true,
      page: currentPage.value - 1,
      size: pageSize.value,
      sort: 'createTime,desc'
    }
    if (!params.needReceiptSlip) delete params.needReceiptSlip
    if (!params.receiptFilter || params.receiptFilter === 'all') delete params.receiptFilter
    const trimOrEmpty = (v: unknown) => (typeof v === 'string' ? v.trim() : v == null ? '' : String(v).trim())
    if (!trimOrEmpty(params.platformOrderNo)) delete params.platformOrderNo
    if (!trimOrEmpty(params.omsOrderNo)) delete params.omsOrderNo
    if (!trimOrEmpty(params.partyATitleKeyword)) delete params.partyATitleKeyword
    if (!trimOrEmpty(params.deliveryPartyKeyword)) delete params.deliveryPartyKeyword
    if (!trimOrEmpty(params.status)) delete params.status
    if (!trimOrEmpty(params.excludeStatuses)) delete params.excludeStatuses
    if (!trimOrEmpty(params.erpEntryStatus)) delete params.erpEntryStatus
    if (!trimOrEmpty(params.platformRefundStatus)) delete params.platformRefundStatus
    if (!trimOrEmpty(params.expectedRefundOverdue)) delete params.expectedRefundOverdue
    else params.expectedRefundOverdue = params.expectedRefundOverdue === '1'
    if (!trimOrEmpty(params.offlineSales)) delete params.offlineSales
    if (!trimOrEmpty(params.ecommerceSalesName)) delete params.ecommerceSalesName
    if (noCache) params._t = Date.now()
    const res: any = await request.get('/sales-orders', { params })
    const content = res?.content ?? res?.data?.content ?? (Array.isArray(res) ? res : [])
    const totalElements = res?.totalElements ?? res?.data?.totalElements ?? (Array.isArray(content) ? content.length : 0)
    const list = Array.isArray(content) ? [...content] : []
    tableData.value = list
    total.value = Number(totalElements) || 0
  } catch (error) {
    console.error('Fetch orders error:', error)
    ElMessage.error('获取订单列表失败')
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const fetchMasterOrders = async (noCache = false) => {
  masterLoading.value = true
  try {
    const params: Record<string, any> = {
      page: masterCurrentPage.value - 1,
      size: masterPageSize.value
    }
    const platformOrderNo = String(filterForm.platformOrderNo || '').trim()
    const omsOrderNo = String(filterForm.omsOrderNo || '').trim()
    if (platformOrderNo) params.platformOrderNo = platformOrderNo
    if (omsOrderNo) params.rootOmsOrderNo = omsOrderNo
    if (noCache) params._t = Date.now()
    const res: any = await request.get('/sales-order-masters', { params })
    const content = res?.content ?? res?.data?.content ?? (Array.isArray(res) ? res : [])
    const totalElements = res?.totalElements ?? res?.data?.totalElements ?? (Array.isArray(content) ? content.length : 0)
    masterTableData.value = Array.isArray(content) ? [...content] : []
    masterTotal.value = Number(totalElements) || 0
  } catch (error) {
    console.error('Fetch master orders error:', error)
    ElMessage.error('获取主单列表失败')
    masterTableData.value = []
    masterTotal.value = 0
  } finally {
    masterLoading.value = false
  }
}

/** 列表「对账单号/发票」：仅展示甲方对账单号 */
const displayReconciliationBillNo = (row: any) => {
  const a = row?.platformReconciliationNo
  const s = a != null ? String(a).trim() : ''
  return s || '-'
}

const getErpEntryStatusTag = (status?: string) => {
  if (status === '已录单') return 'success'
  if (status === '待系统录单') return 'warning'
  return 'info'
}

const formatErpEntryTime = (value?: string) => {
  const text = String(value || '').trim()
  if (!text) return '-'
  return text.replace('T', ' ')
}

const formatExpectedRefundDate = (value?: string) => {
  const text = String(value || '').trim()
  if (!text) return ''
  return text.includes('T') ? text.slice(0, 10) : text
}

const formatTodayDate = () => {
  const now = new Date()
  const yyyy = now.getFullYear()
  const mm = String(now.getMonth() + 1).padStart(2, '0')
  const dd = String(now.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

const startOfToday = () => {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), now.getDate())
}

const isExpectedRefundOverdue = (row: any) => {
  if (row?.expectedRefundOverdue === true) return true
  const refundStatus = String(row?.platformRefundStatus || '').trim()
  const status = String(row?.status || '').trim()
  if (!row?.expectedRefundDate || refundStatus === '已回款' || status === '已结算' || status === '已取消' || status === '已退回') {
    return false
  }
  const date = new Date(String(row.expectedRefundDate).slice(0, 10))
  return !Number.isNaN(date.getTime()) && date < startOfToday()
}

const getExpectedRefundOverdueText = (row: any) => {
  if (!isExpectedRefundOverdue(row)) return ''
  const date = new Date(String(row.expectedRefundDate).slice(0, 10))
  if (Number.isNaN(date.getTime())) return '已超期'
  const diffMs = startOfToday().getTime() - date.getTime()
  const days = Math.max(1, Math.floor(diffMs / (24 * 60 * 60 * 1000)))
  return `已超期${days}天`
}

const getExpectedRefundDisplayText = (row: any) => {
  const dateText = formatExpectedRefundDate(row?.expectedRefundDate)
  if (dateText) return dateText
  const pending = String(row?.expectedRefundPendingReason || '').trim()
  return pending || '-'
}

const openErpEntryDialog = (row: any) => {
  currentErpEntryRow.value = row
  currentErpEntryRows.value = row ? [row] : []
  erpEntryDialogVisible.value = true
}

const openBatchErpEntryDialog = () => {
  if (selectedSalesRows.value.length === 0) {
    ElMessage.warning('请先选择销售订单')
    return
  }
  const invalidRows = selectedSalesRows.value.filter((row: any) => !row?.erpEntryCanEdit)
  if (invalidRows.length > 0) {
    ElMessage.warning('所选订单中包含无权限维护商务ERP录单的条目，请调整后再试')
    return
  }
  currentErpEntryRows.value = [...selectedSalesRows.value]
  currentErpEntryRow.value = currentErpEntryRows.value[0] || null
  erpEntryDialogVisible.value = true
}

const openBatchShipmentDialog = () => {
  if (selectedSalesRows.value.length === 0) {
    ElMessage.warning('请先选择销售订单')
    return
  }
  const groupKeys = Array.from(new Set(selectedSalesRows.value.map((row: any) => {
    if (row?.masterId) return `master:${row.masterId}`
    const oms = String(row?.omsOrderNo || '').trim()
    if (oms) return `oms:${oms}`
    return `id:${row?.id || ''}`
  }).filter(Boolean)))
  if (groupKeys.length > 1) {
    ElMessage.warning('整单批量发货仅支持同一主单或同一 OMS 订单号下的商品行')
    return
  }
  currentBatchShipmentRows.value = [...selectedSalesRows.value]
  batchShipmentDialogVisible.value = true
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
  await fetchOrders(true)
}

const handleBatchShipmentSaved = async () => {
  await fetchOrders(true)
}

const hasReceiverInfo = (row: any) => {
  const name = row?.receiverName != null ? String(row.receiverName).trim() : ''
  const phone = row?.receiverPhone != null ? String(row.receiverPhone).trim() : ''
  const address = row?.receiverAddress != null ? String(row.receiverAddress).trim() : ''
  return !!(name || phone || address)
}

const getPlatformOrderIndex = (row: any) => {
  if (!tableData.value || !row.platformOrderNo) return 0
  const samePlatformOrders = tableData.value.filter((o: any) => o.platformOrderNo === row.platformOrderNo)
  return samePlatformOrders.findIndex((o: any) => o.id === row.id)
}

const getPlatformOrderCount = (row: any) => {
  if (!tableData.value || !row.platformOrderNo) return 1
  return tableData.value.filter((o: any) => o.platformOrderNo === row.platformOrderNo).length
}
const getMasterGroupTone = (row: any) => {
  const masterId = Number(row?.masterId)
  if (!Number.isFinite(masterId) || masterId <= 0) return 0
  return Math.abs(masterId) % 4 + 1
}
const getOrderTableRowClassName = ({ row }: { row: any }) => {
  const tone = getMasterGroupTone(row)
  return tone > 0 ? `master-group-row-${tone}` : ''
}

const logisticsCompanies = [
  '顺丰速运',
  '中通快递',
  '圆通速递',
  '韵达快递',
  '申通快递',
  '京东物流',
  '邮政EMS',
  '德邦快递',
  '极兔速递',
  '丰巢',
  '中国邮政',
  '天天快递',
  '宅急送',
  '中铁快运',
  '安能物流',
  '百世快递',
  '速尔快递',
  '全峰快递',
  '国通快递',
  '优速快递',
  '物流168',
  '日日顺',
  '苏宁物流',
  '品骏快递',
  '芝麻开门',
  '增益速递',
  '全一快递',
  '捷特快递',
  '飞远快递',
  '城市100',
  '万象物流',
  '晟邦物流',
  '微特派',
  '成都立即送',
  '环球物流',
  '华宇物流',
  '中铁物流',
  '中铁快运',
  '中铁物流集团',
  '中铁快运股份有限公司',
  '中铁物流集团有限公司',
  '中铁快运有限公司',
  '中铁物流集团股份有限公司',
  '中铁快运股份有限公司',
  '中铁物流集团有限公司'
]

const activeTab = ref('trace')
const mapMode = ref(false)

const logisticsDialogVisible = ref(false)
/** 物流弹窗：母单 trackingNumber / 回单 returnReceiptTrackingNumber */
const logisticsTraceKind = ref<'main' | 'return'>('main')
const currentLogisticsOrder = ref<any>(null)
const logisticsInfo = ref<any>(null)
const logisticsLoading = ref(false)

// 订单详情标签页
const orderActiveTab = ref('product')
// 订单操作日志
const orderOperationLogs = ref<any[]>([])

interface LogisticsTrace {
  time: string
  status: string
  desc: string
}

function needReceiptSlipTruthy(row: any): boolean {
  const v = row?.needReceiptSlip
  return v === true || v === 'true' || v === 1 || v === '1'
}

function isSelfVehicleOrder(row: any): boolean {
  return (row?.deliveryMethod || '').trim() === '自主车辆配送'
}

function shouldAllowReceiptUpload(row: any): boolean {
  const stage = (row?.receiptFlowStage || '').trim()
  if (isSelfVehicleOrder(row) || needReceiptSlipTruthy(row)) return true
  if (isShippedLikeStatus(row?.status)) return true
  return stage === 'PENDING_MOTHER' || stage === 'MOTHER_DELIVERED' || stage === 'RETURN_DELIVERED'
}

function getReceiptFlowLabel(row: any): string {
  const stage = (row?.receiptFlowStage || '').trim()
  const receiptUrl = (row?.receiptUrl || '').trim()
  const receiptStatus = (row?.receiptStatus || '').trim()
  if (receiptUrl && (receiptStatus === '已上传' || receiptStatus === '妥投结束')) {
    if (isSelfVehicleOrder(row)) return '妥投结束'
    return receiptStatus === '妥投结束' ? '妥投结束' : '签收单已上传'
  }
  if (isSelfVehicleOrder(row)) {
    return receiptUrl ? '妥投结束' : '自主车辆待签收单'
  }
  if (stage === 'RETURN_DELIVERED') return '已回单待上传签收单'
  if (stage === 'MOTHER_DELIVERED') return '已到货待回单'
  if (stage === 'PENDING_MOTHER') return '已发货待签收'
  if (stage === 'FLOW_COMPLETED') return '妥投结束'
  if (needReceiptSlipTruthy(row) && isShippedLikeStatus(row?.status)) return '等待上传签收单'
  if (!needReceiptSlipTruthy(row) && isShippedLikeStatus(row?.status)) return '已发货等待签收'
  return ''
}

function getStatusProcessHint(row: any): string {
  const flowLabel = getReceiptFlowLabel(row)
  const status = (row?.status || '').trim()
  if (!flowLabel) return ''
  if (flowLabel === '妥投结束' && isShippedLikeStatus(status)) return ''
  if (flowLabel === status) return ''
  return flowLabel
}

function getDisplayedStatus(row: any): string {
  const status = (row?.status || '').trim()
  const flowLabel = getReceiptFlowLabel(row)
  if (flowLabel === '妥投结束' && isShippedLikeStatus(status)) {
    return '妥投结束'
  }
  return status
}

function getCompactStatusLayers(row: any): Array<{ label: string; type: string }> {
  const status = (row?.status || '').trim()
  if (status === '已开票待结算') {
    return [
      { label: '已开票', type: 'success' },
      { label: '待结算', type: 'warning' }
    ]
  }
  if (status === '已对账未开票') {
    return [
      { label: '已对账', type: 'success' },
      { label: '未开票', type: 'info' }
    ]
  }
  return []
}

function getDisplayedStatusTag(row: any) {
  const displayStatus = getDisplayedStatus(row)
  if (displayStatus === '妥投结束') return 'success'
  return getStatusTag((row?.status || '').trim())
}

function formatReceiptTime(value: string) {
  const text = (value || '').trim()
  if (!text) return ''
  return text.replace('T', ' ').replace(/\s+00:00:00$/, '').replace(/\s+00:00$/, '')
}

/** 与列表「签收单」展示一致：已发货、已出库等均视为已发出货 */
function isShippedLikeStatus(status: string | undefined | null): boolean {
  const s = (status || '').trim()
  return s === '已发货' || s === '已出库' || s === '运输中' || s === '派送中'
}

const mockLogisticsTraces = [
  { time: '2025-11-14 17:57:35', status: '已签收', desc: '您的包裹已签收，感谢您使用本物流，期待下一次为您服务！您专心工作，琐事我来做！' },
  { time: '2025-11-14 10:03:35', status: '派件中', desc: '您的订单已由本人签收。感谢您的支持，欢迎再次光临。' },
  { time: '2025-11-14 17:57:05', status: '派件中', desc: '您的包裹正在派送中，请保持电话畅通以便司机联系，收到货请清点无误后请完成签收(派件人: 宋敏 电话: 18725533183)' },
  { time: '2025-11-14 09:56:14', status: '在途中', desc: '您的订单已拣货打包完成，订单正在配送途中，请您耐心等待' },
  { time: '2025-11-10 21:10:47', status: '已揽收', desc: '您的包裹由【集团控股】网点完成揽收，等待物流运输。' },
  { time: '2025-11-10 17:57:02', status: '已揽收', desc: '您的包裹由【本物流】网点完成揽收，等待物流运输。' }
]

/** 查物流轨迹用的收件电话：回单优先回单收件人手机，否则订单收货电话 */
function phoneForLogisticsQuery(row: any, kind: 'main' | 'return'): string | undefined {
  if (!row) return undefined
  if (kind === 'return') {
    const rp = (row.returnReceiptReceiverPhone || '').trim()
    if (rp) return rp
  }
  const recv = row.receiverPhone
  return recv != null && String(recv).trim() ? String(recv).trim() : undefined
}

const showLogisticsInfo = async (row: any, kind: 'main' | 'return' = 'main') => {
  logisticsTraceKind.value = kind
  currentLogisticsOrder.value = row
  const tracking = kind === 'return'
    ? ((row.returnReceiptTrackingNumber || '').trim())
    : ((row.trackingNumber || '').trim())
  if (!tracking) {
    ElMessage.warning(kind === 'return' ? '暂无回单物流单号' : '暂无母单物流单号')
    return
  }
  const companyForQuery = kind === 'return'
    ? (((row.logisticsCompany || '').trim()) || '顺丰速运')
    : (row.logisticsCompany || '')
  let phone = phoneForLogisticsQuery(row, kind)
  const needSfPhone = companyForQuery === '顺丰' || companyForQuery === '顺丰速运' || kind === 'return'
  if (!phone && row.id && needSfPhone) {
    try {
      const orderRes: any = await request.get(`/sales-orders/${row.id}`)
      const merged = { ...row, ...orderRes,
        returnReceiptReceiverPhone: orderRes.returnReceiptReceiverPhone ?? row.returnReceiptReceiverPhone,
        receiverPhone: orderRes.receiverPhone ?? row.receiverPhone
      }
      currentLogisticsOrder.value = merged
      phone = phoneForLogisticsQuery(merged, kind)
      if (!phone) {
        phone = (orderRes.logistics && orderRes.logistics[0] && orderRes.logistics[0].receiverPhone)
          ? String(orderRes.logistics[0].receiverPhone).trim()
          : undefined
      }
    } catch (_) { /* 忽略，下面用无手机号请求 */ }
  }
  await fetchLogisticsInfo(companyForQuery, tracking, phone)
  logisticsDialogVisible.value = true
}

/** 取手机号后四位，顺丰路由查询必传 */
function last4Phone(phone: string | null | undefined): string | undefined {
  if (!phone || typeof phone !== 'string') return undefined
  const digits = phone.replace(/\D/g, '')
  return digits.length >= 4 ? digits.slice(-4) : (digits ? digits : undefined)
}

const fetchLogisticsInfo = async (company: string, trackingNumber: string, receiverPhone?: string) => {
  logisticsLoading.value = true
  try {
    const params: Record<string, string> = { company, trackingNumber }
    const phone4 = last4Phone(receiverPhone)
    if (phone4) params.checkPhoneNo = phone4
    const res: any = await request.get('/logistics/query', {
      params
    })
    if (res.isSuccess) {
      logisticsInfo.value = res
    } else {
      ElMessage.error(res.message || '获取物流信息失败')
    }
  } catch (error) {
    console.error('Fetch logistics info error:', error)
    ElMessage.error('获取物流信息失败')
  } finally {
    logisticsLoading.value = false
  }
}

const refreshLogistics = () => {
  if (currentLogisticsOrder.value) {
    const r = currentLogisticsOrder.value
    const kind = logisticsTraceKind.value
    const tracking = kind === 'return'
      ? ((r.returnReceiptTrackingNumber || '').trim())
      : ((r.trackingNumber || '').trim())
    const company = kind === 'return'
      ? (((r.logisticsCompany || '').trim()) || '顺丰速运')
      : (r.logisticsCompany || '')
    fetchLogisticsInfo(company, tracking, phoneForLogisticsQuery(r, kind))
  }
}

// 获取订单操作日志（后端返回 operationType/description/operator/operationTime，或通用接口 action/details/operatorName/createTime）
const fetchOrderOperationLogs = async (orderId: number) => {
  try {
    const res: any = await request.get(`/sales-orders/${orderId}/operation-logs`)
    const raw = Array.isArray(res) ? res : (res?.content || [])
    orderOperationLogs.value = raw.map((item: any) => {
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
    console.error('Fetch order operation logs error:', error)
    orderOperationLogs.value = []
  }
}

// 表头列显示控制
interface ColumnConfig {
  label: string
  title: string
  width?: number
  minWidth?: number
  visible: boolean
}

const allColumns = ref<ColumnConfig[]>([
  { label: 'status', title: '状态', width: 118, visible: true },
  { label: 'erpEntryStatus', title: '商务ERP录单', width: 172, visible: true },
  { label: 'receiptSlip', title: '签收单状态', width: 164, visible: true },
  { label: 'boxMark', title: '发货要求', minWidth: 168, visible: true },
  { label: 'deliveryNotePrintQuantity', title: '送货单打印数量', width: 120, visible: false },
  { label: 'forbiddenCouriers', title: '快递不让用', width: 140, visible: false },
  { label: 'printBoxLabel', title: '打印箱唛', width: 100, visible: false },
  { label: 'printBarcode128', title: '打印128条码', width: 110, visible: false },
  { label: 'snCode', title: 'SN编码', width: 132, visible: true },
  { label: 'trackingNumber', title: '母单物流单号', width: 136, visible: true },
  { label: 'platformName', title: '甲方抬头', width: 200, visible: true },
  { label: 'platformOrderNo', title: '甲方订单号', width: 150, visible: true },
  { label: 'omsOrderNo', title: '工业电商销售订单号', width: 180, visible: true },
  { label: 'createTime', title: '创建时间', width: 160, visible: true },
  { label: 'orderDate', title: '订单时间', width: 110, visible: true },
  { label: 'platformSku', title: '商品信息', width: 260, visible: true },
  { label: 'quantity', title: '数量', width: 80, visible: true },
  { label: 'taxIncludedPrice', title: '含税单价', width: 110, visible: true },
  { label: 'taxIncludedTotal', title: '含税总价', width: 110, visible: true },
  { label: 'ecommerceSalesName', title: '业务员', width: 100, visible: true },
  { label: 'paymentMethod', title: '账期', width: 100, visible: true },
  { label: 'platformRefundStatus', title: '结算单号/甲方回款状态', width: 190, visible: true },
  { label: 'expectedRefundDate', title: '预计回款时间', width: 180, visible: true },
  { label: 'orderType', title: '订单类型', width: 100, visible: true },
  { label: 'receiverName', title: '物流信息', width: 200, visible: true },
  { label: 'settlementNo', title: '对账单号/发票', width: 190, visible: true },
  { label: 'offlineSales', title: '线下销售/合同号/出货价', width: 220, visible: true },
  { label: 'deliveryDate', title: '交货日期', width: 120, visible: true },
  { label: 'returnInfo', title: '退回信息', width: 220, visible: true }
])

const SALES_ORDER_COLUMNS_STORAGE_KEY = 'orderListColumns'
const SALES_ORDER_COLUMNS_VERSION = 3
const LEGACY_REMOVED_COLUMN_LABELS = new Set([
  'paymentStatus',
  'materialNo',
  'offlineContractNo',
  'offlineShippingPrice'
])

const migrateColumnSettings = (rawSettings: any) => {
  const currentLabels = new Set(allColumns.value.map(col => col.label))
  const originalOrder = Array.isArray(rawSettings?.columnsOrder) ? rawSettings.columnsOrder : []
  const originalVisible = Array.isArray(rawSettings?.visible) ? rawSettings.visible : []

  const columnsOrder = originalOrder.filter((label: string) => currentLabels.has(label))
  const visible = originalVisible.filter((label: string) => currentLabels.has(label))

  const hasLegacyColumns =
    originalOrder.some((label: string) => LEGACY_REMOVED_COLUMN_LABELS.has(label)) ||
    originalVisible.some((label: string) => LEGACY_REMOVED_COLUMN_LABELS.has(label))

  const needsMigration =
    rawSettings?.version !== SALES_ORDER_COLUMNS_VERSION ||
    hasLegacyColumns ||
    columnsOrder.length !== originalOrder.length ||
    visible.length !== originalVisible.length

  return {
    settings: {
      version: SALES_ORDER_COLUMNS_VERSION,
      columnsOrder,
      visible
    },
    needsMigration
  }
}

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

// 加载本地存储的列设置
const loadColumnSettings = () => {
  const saved = localStorage.getItem(SALES_ORDER_COLUMNS_STORAGE_KEY)
  if (saved) {
    try {
      const parsedSettings = JSON.parse(saved)
      const { settings, needsMigration } = migrateColumnSettings(parsedSettings)
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
        const savedOrder = settings.columnsOrder || []
        
        orderedColumns.value.forEach(col => {
          // 如果列在可见列表中，则显示
          if (visibleLabels.includes(col.label)) {
            col.visible = true
          } else {
            // 如果不在可见列表中，检查是否为新添加的列
            // 新添加的列是指：不在保存的列顺序列表中的列
            const isNewColumn = !savedOrder.includes(col.label)
            
            if (isNewColumn) {
              // 如果是新列，使用默认可见性
              const defaultCol = allColumns.value.find(c => c.label === col.label)
              col.visible = defaultCol ? defaultCol.visible : true
            } else {
              // 如果不是新列，按照保存的设置隐藏
              col.visible = false
            }
          }
        })
      }
      if (needsMigration) {
        localStorage.setItem(SALES_ORDER_COLUMNS_STORAGE_KEY, JSON.stringify(settings))
      }
    } catch {
      orderedColumns.value = [...allColumns.value]
    }
  }
}

// 保存列设置到本地存储
const saveColumns = () => {
  const visible = visibleColumns.value
  const columnsOrder = orderedColumns.value.map(col => col.label)
  
  localStorage.setItem(SALES_ORDER_COLUMNS_STORAGE_KEY, JSON.stringify({
    version: SALES_ORDER_COLUMNS_VERSION,
    visible,
    columnsOrder
  }))
  ElMessage.success('表头设置已保存')
}

// 恢复默认列设置
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
  // 拖拽结束后顺序已更新，保存时一并写入 localStorage
}

/** URL 带签收/妥投筛选时，仅设置 receiptFilter，本身不再强塞「已发货」，避免 Dashboard 跳转后顶部状态误显示。 */
const RECEIPT_FILTER_VALUES = [
  'pending_upload',
  'waiting_mother',
  'mother_delivered',
  'return_delivered',
  'waiting_sign',
  'self_vehicle_pending_receipt',
  'completed'
]
function applyRouteQuery() {
  const q = route.query
  filterForm.platformOrderNo = q.platformOrderNo !== undefined ? String(q.platformOrderNo) : ''
  filterForm.omsOrderNo = q.omsOrderNo !== undefined ? String(q.omsOrderNo) : ''
  filterForm.partyATitleKeyword = q.partyATitleKeyword !== undefined ? String(q.partyATitleKeyword) : ''
  filterForm.deliveryPartyKeyword = q.deliveryPartyKeyword !== undefined ? String(q.deliveryPartyKeyword) : ''
  filterForm.platformRefundStatus = q.platformRefundStatus !== undefined ? String(q.platformRefundStatus) : ''
  filterForm.expectedRefundOverdue = q.expectedRefundOverdue !== undefined ? String(q.expectedRefundOverdue) : ''
  filterForm.excludeStatuses = q.excludeStatuses !== undefined ? String(q.excludeStatuses) : ''
  filterForm.offlineSales = q.offlineSales !== undefined ? String(q.offlineSales) : ''
  filterForm.ecommerceSalesName = q.ecommerceSalesName !== undefined ? String(q.ecommerceSalesName) : ''
  filterForm.status = q.status !== undefined ? String(q.status) : ''
  filterForm.erpEntryStatus = q.erpEntryStatus !== undefined ? String(q.erpEntryStatus) : ''
  filterForm.needReceiptSlip = q.needReceiptSlip !== undefined ? String(q.needReceiptSlip) : ''
  const hasReceiptFilter = q.filter === 'receipt' || (typeof q.receiptFilter === 'string' && RECEIPT_FILTER_VALUES.includes(q.receiptFilter.trim()))
  if (hasReceiptFilter) {
    filterForm.receiptFilter = (q.receiptFilter as string)?.trim() || 'pending_upload'
  } else {
    filterForm.receiptFilter = ''
    if (q.status === undefined) filterForm.status = ''
  }
  showMoreFilters.value = Boolean(
    String(filterForm.partyATitleKeyword || '').trim() ||
    String(filterForm.deliveryPartyKeyword || '').trim()
  )
}

// 从 Dashboard 等跳转到销售列表时，keep-alive 不会重新挂载，需监听 route 同步筛选并拉数
watch(
  () => ({ path: route.path, ...route.query }),
  () => {
    if (route.path !== '/sales') return
    applyRouteQuery()
    currentPage.value = 1
    fetchOrders()
  },
  { deep: true }
)

onMounted(async () => {
  applyRouteQuery()
  loadColumnSettings()
  try {
    const meRes: any = await request.get('/users/me', {
      skipErrorMsg: true,
      skipErrorLog: true
    } as any)
    if (meRes?.id != null) currentUserId.value = Number(meRes.id)
  } catch {
    // 忽略
  }
  fetchUsers()
  fetchOrders()
})

const handleSearch = () => {
  currentPage.value = 1
  const nextQuery = buildRouteQueryFromFilters()
  const currentQuery = JSON.stringify(route.query)
  const targetQuery = JSON.stringify(nextQuery)
  if (currentQuery !== targetQuery) {
    router.replace({ path: '/sales', query: nextQuery })
    return
  }
  fetchOrders()
}

const resetSearch = () => {
  Object.assign(filterForm, {
    platformOrderNo: '',
    omsOrderNo: '',
    partyATitleKeyword: '',
    deliveryPartyKeyword: '',
    status: '待指派',
    excludeStatuses: '',
    erpEntryStatus: '',
    platformRefundStatus: '',
    expectedRefundOverdue: '',
    ecommerceSalesName: '',
    offlineSales: '',
    needReceiptSlip: '',
    receiptFilter: ''
  })
  showMoreFilters.value = false
  handleSearch()
}

const handleStatusFilterChange = (value: string) => {
  if ((value || '').trim()) {
    return
  }
  if (filterForm.receiptFilter) {
    filterForm.receiptFilter = ''
  }
}

const handleReceiptFilterChange = (value: string) => {
  if (!(value || '').trim()) {
    return
  }
  if (filterForm.status) {
    filterForm.status = ''
  }
}

function buildRouteQueryFromFilters() {
  const query: Record<string, string> = {}
  const assignIfFilled = (key: string, value: unknown) => {
    const text = typeof value === 'string' ? value.trim() : value == null ? '' : String(value).trim()
    if (text) query[key] = text
  }
  assignIfFilled('platformOrderNo', filterForm.platformOrderNo)
  assignIfFilled('omsOrderNo', filterForm.omsOrderNo)
  assignIfFilled('partyATitleKeyword', filterForm.partyATitleKeyword)
  assignIfFilled('deliveryPartyKeyword', filterForm.deliveryPartyKeyword)
  assignIfFilled('status', filterForm.status)
  assignIfFilled('excludeStatuses', filterForm.excludeStatuses)
  assignIfFilled('erpEntryStatus', filterForm.erpEntryStatus)
  assignIfFilled('platformRefundStatus', filterForm.platformRefundStatus)
  assignIfFilled('expectedRefundOverdue', filterForm.expectedRefundOverdue)
  assignIfFilled('ecommerceSalesName', filterForm.ecommerceSalesName)
  assignIfFilled('offlineSales', filterForm.offlineSales)
  assignIfFilled('needReceiptSlip', filterForm.needReceiptSlip)
  assignIfFilled('receiptFilter', filterForm.receiptFilter)
  return query
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  fetchOrders()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  fetchOrders()
}

const dialogVisible = ref(false)
const dialogTitle = ref('')
const erpEntryDialogVisible = ref(false)
const currentErpEntryRow = ref<any>(null)
const currentErpEntryRows = ref<any[]>([])
const batchShipmentDialogVisible = ref(false)
const currentBatchShipmentRows = ref<any[]>([])

// 订单基本信息
const orderForm = reactive({
  id: undefined as number | undefined,
  status: '',
  platformName: '',
  platformOrderNo: '',
  omsOrderNo: '',
  orderDate: '',
  deliveryDate: '',
  platformSku: '',
  model: '',
  productName: '',
  productConfig: '',
  warrantyPeriod: '',
  quantity: 1,
  taxIncludedPrice: '',
  taxIncludedTotal: '',
  invoiceTitle: '',
  finalCustomerTitle: '',
  operationEntityTitle: '',
  ecommerceSalesId: '' as string | number,
  ecommerceSalesName: '',
  paymentMethod: '',
  receiverName: '',
  receiverPhone: '',
  receiverAddress: '',
  deliveryParty: '',
  shippingParty: '',
  orderType: '第三方订单',
  settlementNo: '',
  settlementUrl: '',
  platformReconciliationNo: '',
  platformReconciliationUrl: '',
  invoiceNumber: '',
  invoiceUrl: '',
  receiptTime: '',
  offlineSales: '',
  offlineContractNo: '',
  offlineShippingPrice: '',
  platformRefundStatus: '',
  deductionRate: '2%',
  deliveryPartyPurchasePrice: '',
  contractUrl: '',
  contractName: '',
  materialNo: '',
  createdBy: '' as string | number,
  creatorName: '',
  creatorUsername: ''
})

// 商品列表
const productList = ref<any[]>([])

// 物流列表
const logisticsList = ref<any[]>([])

// 发票列表
const invoiceList = ref<any[]>([])

// 对账列表
const reconciliationList = ref<any[]>([])
// 当前编辑的订单行快照（被指派方编辑时用于只读价格与提交时回填，避免覆盖指派方推送的价格）
const editingOrderRow = ref<any>(null)

// 切换到操作日志 Tab 时拉取日志（orderForm.id 在编辑时由 handleEdit 赋值）
watch(orderActiveTab, (tab) => {
  if (tab === 'logs' && (orderForm as any).id) {
    fetchOrderOperationLogs(Number((orderForm as any).id))
  }
})


const buildProductDraft = (overrides: Record<string, any> = {}) => ({
  orderType: '第三方订单',
  omsOrderNo: '',
  platformSku: '',
  model: '',
  productName: '',
  productConfig: '',
  warrantyPeriod: '',
  quantity: 1,
  taxIncludedPrice: '',
  taxIncludedTotal: '',
  ...overrides
})

const findSameOmsInvoiceTemplate = (sourceIndex: number, omsOrderNo: string) => {
  const bySource = invoiceList.value.find((row: any) => Number(row?.productIndex) === sourceIndex)
  if (bySource) return bySource
  return invoiceList.value.find((row: any) => {
    const idx = Number(row?.productIndex)
    return Number.isFinite(idx) && (productList.value[idx]?.omsOrderNo || '').trim() === omsOrderNo
  }) || null
}

const findSameOmsReconciliationTemplate = (sourceIndex: number, omsOrderNo: string) => {
  const bySource = reconciliationList.value.find((row: any) => Number(row?.productIndex) === sourceIndex)
  if (bySource) return bySource
  return reconciliationList.value.find((row: any) => {
    const idx = Number(row?.productIndex)
    return Number.isFinite(idx) && (productList.value[idx]?.omsOrderNo || '').trim() === omsOrderNo
  }) || null
}

const appendSameOmsCompanionRows = (newIndex: number, sourceIndex: number) => {
  const omsOrderNo = (productList.value[newIndex]?.omsOrderNo || '').trim()
  if (!omsOrderNo) return

  const logisticsTemplate = buildLogisticsTemplateByOmsOrderNo(newIndex)
  if (logisticsTemplate || logisticsList.value.some((row: any) => Number(row?.productIndex) === sourceIndex)) {
    const logisticsRow = buildLogisticsRow(newIndex)
    logisticsRow.productQuantity = productList.value[newIndex]?.quantity || 1
    logisticsList.value.push(logisticsRow)
  }

  const invoiceTemplate = findSameOmsInvoiceTemplate(sourceIndex, omsOrderNo)
  if (invoiceTemplate) {
    invoiceList.value.push({
      productIndex: newIndex,
      invoiceTitle: invoiceTemplate.invoiceTitle || '',
      finalCustomerTitle: invoiceTemplate.finalCustomerTitle || '',
      operationEntityTitle: invoiceTemplate.operationEntityTitle || '',
      paymentMethod: invoiceTemplate.paymentMethod || invoiceList.value[0]?.paymentMethod || ''
    })
  }

  const reconciliationTemplate = findSameOmsReconciliationTemplate(sourceIndex, omsOrderNo)
  if (reconciliationTemplate) {
    const logisticsIndex = logisticsList.value.findIndex((row: any) => Number(row?.productIndex) === newIndex)
    reconciliationList.value.push({
      productIndex: newIndex,
      logisticsIndex: logisticsIndex >= 0 ? logisticsIndex : undefined,
      deductionRate: reconciliationTemplate.deductionRate || ((productList.value[newIndex]?.orderType || '').includes('自营') ? '20%' : '2%'),
      deliveryPartyPurchasePrice: '',
      offlineSales: reconciliationTemplate.offlineSales || '',
      offlineContractNo: reconciliationTemplate.offlineContractNo || '',
      offlineShippingPrice: reconciliationTemplate.offlineShippingPrice || ''
    })
    calculateReconciliationPrice(reconciliationList.value.length - 1)
  }
}

// 添加商品：默认同一 OMS 追加商品行；拆单时才生成新号
const addProduct = async (options?: { split?: boolean }) => {
  const split = options?.split === true
  const sourceIndex = productList.value.length > 0 ? productList.value.length - 1 : 0
  const sourceProduct = productList.value[sourceIndex]
  const reusedOmsOrderNo = !split ? String(sourceProduct?.omsOrderNo || '').trim() : ''
  const newIndex = productList.value.length
  productList.value.push(buildProductDraft({
    orderType: sourceProduct?.orderType || '第三方订单',
    omsOrderNo: reusedOmsOrderNo
  }))

  if (split || !reusedOmsOrderNo) {
    await generateOmsOrderNo(newIndex)
    return
  }

  appendSameOmsCompanionRows(newIndex, sourceIndex)
}

const addProductRow = () => addProduct({ split: false })

const addSplitProduct = () => addProduct({ split: true })

// 处理商品订单类型变化（自营时交付方默认上海热像科技、扣点 20%，并同步到订单级别供采购指派列表展示）
const handleProductOrderTypeChange = (product: any, index: number) => {
  if (product.orderType === '自营') {
    product.deliveryParty = '上海热像科技股份有限公司'
    product.deductionRate = '20%'
    // 同步到订单级别，保存后在采购管理-销售订单指派列表中显示默认交付方与扣点
    ;(orderForm as any).deliveryParty = '上海热像科技股份有限公司'
    ;(orderForm as any).deductionRate = '20%'
    ;(orderForm as any).orderType = '自营'
  } else {
    product.deductionRate = '2%'
    // 第一个商品为第三方时，订单级别也同步，便于列表展示一致
    if (index === 0) {
      ;(orderForm as any).deliveryParty = ''
      ;(orderForm as any).deductionRate = '2%'
      ;(orderForm as any).orderType = '第三方订单'
    }
  }
  // 自动生成OMS订单号
  generateOmsOrderNo(index)
  // 更新对应的对账明细的扣点
  const reconciliationIndex = reconciliationList.value.findIndex(r => r.productIndex === index)
  if (reconciliationIndex >= 0) {
    const reconciliation = reconciliationList.value[reconciliationIndex]
    reconciliation.deductionRate = product.deductionRate
    calculateReconciliationPrice(reconciliationIndex)
  }
}

function normalizeProductModel(value: any): string {
  return String(value || '')
    .trim()
    .toUpperCase()
    .replace(/[\s\u00A0\-_/]/g, '')
}

function findCatalogProductByModel(model: any) {
  const normalizedModel = normalizeProductModel(model)
  if (!normalizedModel) return null
  return products.value.find((p: any) => {
    const candidate = normalizeProductModel(p?.model)
    return !!candidate && (candidate === normalizedModel || candidate.includes(normalizedModel) || normalizedModel.includes(candidate))
  }) || null
}

// 处理商品型号变化，自动从商品库带出信息
const handleProductModelChange = (product: any, options?: { preserveParsedValues?: boolean }) => {
  const preserveParsedValues = options?.preserveParsedValues === true
  const selectedProduct = findCatalogProductByModel(product?.model)
  if (!selectedProduct) return

  if (selectedProduct.name && (!preserveParsedValues || !product.productName)) {
    product.productName = selectedProduct.name
  }
  if (selectedProduct.materialNo && (!preserveParsedValues || !product.materialNo)) {
    product.materialNo = selectedProduct.materialNo
  }
  if (selectedProduct.productConfig && (!preserveParsedValues || !product.productConfig)) {
    product.productConfig = selectedProduct.productConfig
  }
  if (selectedProduct.warrantyPeriod && (!preserveParsedValues || !product.warrantyPeriod)) {
    product.warrantyPeriod = selectedProduct.warrantyPeriod
  }
  if (selectedProduct.price && (!preserveParsedValues || !product.taxIncludedPrice)) {
    product.taxIncludedPrice = selectedProduct.price.toString()
  }
  calculateProductTotal(product)
}

// 删除商品
const removeProduct = (index: number) => {
  productList.value.splice(index, 1)
}

// 计算商品总价
const calculateProductTotal = (product: any) => {
  if (product.quantity && product.taxIncludedPrice) {
    product.taxIncludedTotal = (toNumberSafe(product.quantity) * toNumberSafe(product.taxIncludedPrice)).toFixed(2)
  }
  
  // 更新对应的对账明细的交付方采购价
  const productIndex = productList.value.indexOf(product)
  if (productIndex >= 0) {
    const reconciliationIndex = reconciliationList.value.findIndex(r => r.productIndex === productIndex)
    if (reconciliationIndex >= 0) {
      calculateReconciliationPrice(reconciliationIndex)
    }
  }
}

// 计算订单总额
const calculateOrderTotal = () => {
  return productList.value.reduce((sum, product) => {
    return sum + toNumberSafe(product.taxIncludedTotal)
  }, 0).toFixed(2)
}

// 添加物流
const buildLogisticsTemplateByOmsOrderNo = (productIndex: number, excludeRow?: any) => {
  const currentProduct = productList.value[productIndex]
  const currentOmsOrderNo = (currentProduct?.omsOrderNo || '').trim()
  if (!currentOmsOrderNo) return null

  return logisticsList.value.find((logisticsRow: any) => {
    if (!logisticsRow || logisticsRow === excludeRow) return false
    const idx = Number(logisticsRow.productIndex)
    if (!Number.isFinite(idx) || idx < 0 || idx >= productList.value.length) return false
    const rowOmsOrderNo = (productList.value[idx]?.omsOrderNo || '').trim()
    return rowOmsOrderNo && rowOmsOrderNo === currentOmsOrderNo
  }) || null
}

/** 同一商品在物流行中已占用的发货数量（可排除某一行，用于计算该行允许的最大值） */
const logisticsAllocatedSumForProduct = (productIndex: number, excludeRow?: any): number => {
  return logisticsList.value
    .filter((l: any) => l !== excludeRow && Number(l.productIndex) === productIndex)
    .reduce((s, l: any) => s + toNumberSafe(l.productQuantity), 0)
}

/** 当前物流行在不超出该商品订单数量的前提下，允许填写的最大发货数量 */
const maxLogisticsQuantityForRow = (row: any): number => {
  if (!row) return 9999
  const pi = Number(row.productIndex)
  if (!Number.isFinite(pi) || pi < 0 || pi >= productList.value.length) return 9999
  const pq = toNumberSafe(productList.value[pi]?.quantity)
  const sumOthers = logisticsAllocatedSumForProduct(pi, row)
  return Math.max(1, pq - sumOthers)
}

/** 调整发货数量后，将本行压到允许上限内（避免改了他行 max 后本行仍超大） */
const onLogisticsQuantityChange = (row: any) => {
  if (!row) return
  const cap = maxLogisticsQuantityForRow(row)
  const v = toNumberSafe(row.productQuantity)
  if (v > cap) row.productQuantity = cap
  if (!row.productQuantity || row.productQuantity < 1) row.productQuantity = 1
}

const logisticsRowCountForProduct = (pi: number) =>
  logisticsList.value.filter(l => Number(l.productIndex) === pi).length

const invoiceRowCountForProduct = (pi: number) =>
  invoiceList.value.filter(inv => Number(inv.productIndex) === pi).length

const reconciliationRowCountForProduct = (pi: number) =>
  reconciliationList.value.filter(r => Number(r.productIndex) === pi).length

/** 某商品第 k 条物流（按表格顺序）在 logisticsList 中的全局下标，用于对账行关联物流 */
const getKthLogisticsGlobalIndex = (productIndex: number, k: number): number => {
  let seen = 0
  for (let i = 0; i < logisticsList.value.length; i++) {
    if (Number(logisticsList.value[i].productIndex) === productIndex) {
      if (seen === k) return i
      seen++
    }
  }
  return -1
}

/** 各商品物流发货数量均已占满订单数量时不可再添加物流行 */
const canAddMoreLogistics = computed(() => {
  if (productList.value.length === 0) return false
  for (let i = 0; i < productList.value.length; i++) {
    const pq = toNumberSafe(productList.value[i]?.quantity)
    if (logisticsAllocatedSumForProduct(i) < pq) return true
  }
  return false
})

/** 每个商品：发票行数小于物流行数时还可添加（与物流行一一对应） */
const canAddMoreInvoice = computed(() => {
  if (!canEditSettlementByFinance.value) return false
  for (let i = 0; i < productList.value.length; i++) {
    const lc = logisticsRowCountForProduct(i)
    const ic = invoiceRowCountForProduct(i)
    if (lc > 0 && ic < lc) return true
  }
  return false
})

/** 对账行须在已有发票行之下成对增加：对账行数小于发票行数且小于物流行数 */
const canAddMoreReconciliation = computed(() => {
  if (!canEditSettlementBySales.value) return false
  for (let i = 0; i < productList.value.length; i++) {
    const lc = logisticsRowCountForProduct(i)
    const ic = invoiceRowCountForProduct(i)
    const rc = reconciliationRowCountForProduct(i)
    if (lc > 0 && rc < lc && rc < ic) return true
  }
  return false
})

const buildLogisticsRow = (productIndex: number, excludeRow?: any) => {
  const product = productList.value[productIndex]
  const template = buildLogisticsTemplateByOmsOrderNo(productIndex, excludeRow)
  return {
    productIndex,
    productQuantity: product?.quantity || 1,
    receiverName: template?.receiverName || '',
    receiverPhone: template?.receiverPhone || '',
    receiverAddress: template?.receiverAddress || '',
    deliveryParty: template?.deliveryParty || '',
    shippingParty: template?.shippingParty || '',
    deliveryDate: template?.deliveryDate || ''
  }
}

const addLogistics = () => {
  const usedProductIndexes = logisticsList.value.map(l => l.productIndex)
  let productIndex = 0
  for (let i = 0; i < productList.value.length; i++) {
    if (!usedProductIndexes.includes(i)) {
      productIndex = i
      break
    }
  }

  const row = buildLogisticsRow(productIndex)
  const pq = toNumberSafe(productList.value[productIndex]?.quantity)
  const allocated = logisticsAllocatedSumForProduct(productIndex)
  const remaining = pq - allocated
  // 多行对应同一商品时，新行默认填「剩余件数」，避免每行都默认成整单数量导致合计远超订单数量
  if (remaining >= 1) {
    row.productQuantity = remaining
  } else {
    row.productQuantity = 1
    const sku = (productList.value[productIndex]?.platformSku || productList.value[productIndex]?.model || '该商品').toString()
    ElMessage.warning(
      `${sku} 在物流中的发货数量已占满订单数量，新行默认 1；请先减少其他物流行的数量，或删除多余行，保存时合计须等于订单数量`
    )
  }
  logisticsList.value.push(row)
}

const onLogisticsProductChange = (row: any) => {
  if (!row) return
  const productIndex = Number(row.productIndex)
  if (!Number.isFinite(productIndex) || productIndex < 0 || productIndex >= productList.value.length) return

  const product = productList.value[productIndex]
  const rem = toNumberSafe(product.quantity) - logisticsAllocatedSumForProduct(productIndex, row)
  if (rem >= 1) {
    row.productQuantity = rem
  } else {
    row.productQuantity = 1
    ElMessage.warning('该商品在其他物流行已占满数量，本行暂为 1，请调整各行发货数量使合计等于订单数量')
  }

  // 切换到同 OMS 订单号商品时，自动复用已有物流，减少重复录入
  const template = buildLogisticsTemplateByOmsOrderNo(productIndex, row)
  if (!template) return
  if (!row.receiverName) row.receiverName = template.receiverName || ''
  if (!row.receiverPhone) row.receiverPhone = template.receiverPhone || ''
  if (!row.receiverAddress) row.receiverAddress = template.receiverAddress || ''
  if (!row.deliveryParty) row.deliveryParty = template.deliveryParty || ''
  if (!row.shippingParty) row.shippingParty = template.shippingParty || ''
  if (!row.deliveryDate) row.deliveryDate = template.deliveryDate || ''
}

const copyLogisticsBySameOms = () => {
  if (!logisticsList.value.length) {
    ElMessage.warning('请先添加物流信息')
    return
  }

  const templateByOms: Record<string, any> = {}
  logisticsList.value.forEach((row: any) => {
    const idx = Number(row?.productIndex)
    if (!Number.isFinite(idx) || idx < 0 || idx >= productList.value.length) return
    const omsOrderNo = (productList.value[idx]?.omsOrderNo || '').trim()
    if (!omsOrderNo) return
    if (!templateByOms[omsOrderNo]) {
      templateByOms[omsOrderNo] = row
    }
  })

  const omsKeys = Object.keys(templateByOms)
  if (!omsKeys.length) {
    ElMessage.warning('未找到可用于复制的OMS订单号')
    return
  }

  let affected = 0
  logisticsList.value.forEach((row: any) => {
    const idx = Number(row?.productIndex)
    if (!Number.isFinite(idx) || idx < 0 || idx >= productList.value.length) return
    const omsOrderNo = (productList.value[idx]?.omsOrderNo || '').trim()
    if (!omsOrderNo) return
    const template = templateByOms[omsOrderNo]
    if (!template || template === row) return

    row.receiverName = template.receiverName || ''
    row.receiverPhone = template.receiverPhone || ''
    row.receiverAddress = template.receiverAddress || ''
    row.deliveryParty = template.deliveryParty || ''
    row.shippingParty = template.shippingParty || ''
    row.deliveryDate = template.deliveryDate || ''
    affected++
  })

  if (affected > 0) {
    ElMessage.success(`已按同OMS复制 ${affected} 条物流信息`)
  } else {
    ElMessage.info('没有可复制的物流行（可能已一致）')
  }
}

// 删除物流
const removeLogistics = (index: number) => {
  logisticsList.value.splice(index, 1)
}

// 添加发票
const addInvoice = () => {
  if (!canEditSettlementByFinance.value) {
    ElMessage.warning('无权限维护发票信息，请联系财务账号操作')
    return
  }
  let productIndex = -1
  for (let i = 0; i < productList.value.length; i++) {
    const lc = logisticsRowCountForProduct(i)
    const ic = invoiceRowCountForProduct(i)
    if (lc > 0 && ic < lc) {
      productIndex = i
      break
    }
  }
  if (productIndex < 0) {
    ElMessage.warning('每个商品的发票行数已与物流行数一致，或请先维护物流；需要更多发票请先「添加物流」拆行')
    return
  }

  const ordinal = invoiceRowCountForProduct(productIndex)
  invoiceList.value.push({
    productIndex: productIndex,
    invoiceTitle: '',
    finalCustomerTitle: '',
    operationEntityTitle: '',
    paymentMethod: invoiceList.value[0]?.paymentMethod ?? ''
  })

  addReconciliationFromInvoice(productIndex, ordinal)
}

// 从发票添加对账（logisticsOrdinal：该商品下第几条物流，与发票行顺序对齐）
const addReconciliationFromInvoice = (productIndex: number | undefined, logisticsOrdinal?: number) => {
  if (productIndex === undefined) return
  
  const product = productList.value[productIndex]
  if (!product) return
  
  const orderType = product.orderType || '第三方订单'
  const defaultDeductionRate = (orderType === '自营' || orderType === '自营订单') ? '20%' : '2%'

  let logisticsIndex: number | undefined
  if (logisticsOrdinal !== undefined && logisticsOrdinal >= 0) {
    const idx = getKthLogisticsGlobalIndex(productIndex, logisticsOrdinal)
    logisticsIndex = idx >= 0 ? idx : undefined
  }
  if (logisticsIndex === undefined || logisticsIndex < 0) {
    const fallback = logisticsList.value.findIndex(l => Number(l.productIndex) === productIndex)
    logisticsIndex = fallback >= 0 ? fallback : undefined
  }
  
  const newReconciliation = {
    productIndex: productIndex,
    logisticsIndex: logisticsIndex !== undefined && logisticsIndex >= 0 ? logisticsIndex : undefined,
    deductionRate: defaultDeductionRate,
    deliveryPartyPurchasePrice: '',
    offlineSales: '',
    offlineContractNo: '',
    offlineShippingPrice: ''
  }
  
  reconciliationList.value.push(newReconciliation)
  
  const newIndex = reconciliationList.value.length - 1
  calculateReconciliationPrice(newIndex)
}

// 删除发票
const removeInvoice = (index: number) => {
  if (!canEditSettlementByFinance.value) {
    ElMessage.warning('无权限删除发票信息')
    return
  }
  const invoice = invoiceList.value[index]
  const pi = Number(invoice.productIndex)
  let ordinal = 0
  for (let i = 0; i < index; i++) {
    if (Number(invoiceList.value[i].productIndex) === pi) ordinal++
  }
  invoiceList.value.splice(index, 1)
  syncInvoicePaymentMethodFromFirst()

  let o = 0
  for (let r = 0; r < reconciliationList.value.length; r++) {
    if (Number(reconciliationList.value[r].productIndex) === pi) {
      if (o === ordinal) {
        reconciliationList.value.splice(r, 1)
        break
      }
      o++
    }
  }
}

// 添加对账（与发票成对：须先存在对应发票行）
const addReconciliation = () => {
  if (!canEditSettlementBySales.value) {
    ElMessage.warning('无权限维护对账信息，请联系销售账号操作')
    return
  }
  let productIndex = -1
  for (let i = 0; i < productList.value.length; i++) {
    const lc = logisticsRowCountForProduct(i)
    const ic = invoiceRowCountForProduct(i)
    const rc = reconciliationRowCountForProduct(i)
    if (lc > 0 && rc < lc && rc < ic) {
      productIndex = i
      break
    }
  }
  if (productIndex < 0) {
    ElMessage.warning('请先由财务添加发票行，或对账行数已与物流一致；每个物流行对应一行发票与一行对账')
    return
  }
  const ordinal = reconciliationRowCountForProduct(productIndex)
  addReconciliationFromInvoice(productIndex, ordinal)
}

// 计算对账的交付方采购价
const calculateReconciliationPrice = (index: number) => {
  const reconciliation = reconciliationList.value[index]
  if (!reconciliation) return
  
  console.log('calculateReconciliationPrice called for index:', index)
  console.log('reconciliation:', reconciliation)
  
  const product = reconciliation.productIndex !== undefined ? productList.value[reconciliation.productIndex] : null
  console.log('product:', product)
  if (!product) {
    reconciliation.deliveryPartyPurchasePrice = ''
    return
  }
  
  const taxIncludedTotal = toNumberSafe(product.taxIncludedTotal)
  console.log('taxIncludedTotal:', taxIncludedTotal)
  const deductionRateStr = reconciliation.deductionRate?.toString().replace('%', '') || '2'
  console.log('deductionRateStr:', deductionRateStr)
  const deductionRate = toNumberSafe(deductionRateStr) / 100
  console.log('deductionRate:', deductionRate)
  
  const calculatedPrice = (taxIncludedTotal * (1 - deductionRate)).toFixed(2)
  console.log('calculatedPrice:', calculatedPrice)
  
  reconciliation.deliveryPartyPurchasePrice = calculatedPrice
}

// 删除对账
const removeReconciliation = (index: number) => {
  if (!canEditSettlementBySales.value) {
    ElMessage.warning('无权限删除对账信息')
    return
  }
  reconciliationList.value.splice(index, 1)
}

// 处理发票表格项上传成功
const handleInvoiceItemUploadSuccess = (response: any, file: any, index: number) => {
  if (!canEditSettlementByFinance.value) {
    ElMessage.warning('无权限上传发票文件')
    return
  }
  if (response.success || response.url) {
    ElMessage.success('发票上传成功')
    invoiceList.value[index].invoiceUrl = response.url || file.name
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

// 发票文件列表
const invoiceFileList = ref<any[]>([])

// 新订单合同文件列表
const newOrderContractFileList = ref<any[]>([])
const orderImportLoading = ref(false)
const newOrderImportUploadRef = ref<any>(null)

const rules = {
platformName: [{ required: true, message: '请选择甲方抬头', trigger: 'blur' }],
    platformOrderNo: [{ required: true, message: '请输入甲方订单号', trigger: 'blur' }],
  ecommerceSalesId: [{ required: true, message: '请选择业务员', trigger: 'blur' }],
  receiverName: [{ required: true, message: '请输入收货人', trigger: 'blur' }],
  receiverPhone: [{ required: true, message: '请输入收货人电话', trigger: 'blur' }],
  receiverAddress: [{ required: true, message: '请输入收货地址', trigger: 'blur' }]
  // 交付方、出货方已移至采购板块，销售侧不再校验
}

// 提交销售订单前清洗金额字符串，兼容 "22,105.00"、"￥22,105.00" 等格式
const DECIMAL_FIELD_NAMES = new Set([
  'taxIncludedPrice',
  'taxIncludedTotal',
  'deliveryPartyPurchasePrice',
  'offlineShippingPrice',
  'amount'
])

const normalizeDecimalString = (value: any) => {
  if (typeof value !== 'string') return value
  const cleaned = value
    .trim()
    .replace(/,/g, '')
    .replace(/，/g, '')
    .replace(/¥/g, '')
    .replace(/￥/g, '')
    .replace(/\s+/g, '')
  return cleaned === '' ? null : cleaned
}

const toNumberSafe = (value: any): number => {
  const normalized = normalizeDecimalString(value)
  if (normalized == null) return 0
  const n = Number(normalized)
  return Number.isFinite(n) ? n : 0
}

const sanitizeDecimalFieldsDeep = (input: any): any => {
  if (Array.isArray(input)) {
    return input.map(item => sanitizeDecimalFieldsDeep(item))
  }
  if (input && typeof input === 'object') {
    const out: any = {}
    Object.keys(input).forEach((key) => {
      const value = (input as any)[key]
      if (DECIMAL_FIELD_NAMES.has(key)) {
        out[key] = normalizeDecimalString(value)
      } else {
        out[key] = sanitizeDecimalFieldsDeep(value)
      }
    })
    return out
  }
  return input
}

// 订单提交白名单：避免编辑过发货信息后，发货要求字段被意外带入「新建订单」请求
const buildOrderPayloadBase = (source: any) => ({
  id: source.id,
  platformName: source.platformName,
  platformOrderNo: source.platformOrderNo,
  omsOrderNo: source.omsOrderNo,
  orderDate: source.orderDate,
  deliveryDate: source.deliveryDate,
  platformSku: source.platformSku,
  model: source.model,
  productName: source.productName,
  productConfig: source.productConfig,
  warrantyPeriod: source.warrantyPeriod,
  quantity: source.quantity,
  taxIncludedPrice: source.taxIncludedPrice,
  taxIncludedTotal: source.taxIncludedTotal,
  invoiceTitle: source.invoiceTitle,
  finalCustomerTitle: source.finalCustomerTitle,
  operationEntityTitle: source.operationEntityTitle,
  ecommerceSalesId: source.ecommerceSalesId,
  paymentMethod: source.paymentMethod,
  receiverName: source.receiverName,
  receiverPhone: source.receiverPhone,
  receiverAddress: source.receiverAddress,
  deliveryParty: source.deliveryParty,
  shippingParty: source.shippingParty,
  orderType: source.orderType,
  settlementNo: source.settlementNo,
  settlementUrl: source.settlementUrl,
  platformReconciliationNo: source.platformReconciliationNo,
  platformReconciliationUrl: source.platformReconciliationUrl,
  invoiceNumber: source.invoiceNumber,
  invoiceUrl: source.invoiceUrl,
  receiptTime: source.receiptTime,
  offlineSales: source.offlineSales,
  offlineContractNo: source.offlineContractNo,
  offlineShippingPrice: source.offlineShippingPrice,
  platformRefundStatus: source.platformRefundStatus,
  deductionRate: source.deductionRate,
  deliveryPartyPurchasePrice: source.deliveryPartyPurchasePrice,
  contractUrl: source.contractUrl,
  contractName: source.contractName,
  materialNo: source.materialNo
})

const handleAdd = async () => {
  dialogTitle.value = '新建销售订单'
  resetForm()
  // 设置默认订单日期为今天
  const today = new Date()
  orderForm.orderDate = today.getFullYear().toString() + '-' +
    (today.getMonth() + 1).toString().padStart(2, '0') + '-' +
    today.getDate().toString().padStart(2, '0')
  // 加载用户列表（业务员，含当前用户以便交付方账号如坚领可选自己）
  await fetchUsers()
  // 默认业务员为当前用户：先按 username 再按 realName 匹配，便于坚领等交付方账号带出自己
  const storedUsername = (localStorage.getItem('username') || '').trim()
  const storedRealName = (localStorage.getItem('realName') || '').trim()
  if (salesUsers.value.length > 0) {
    const currentUser = salesUsers.value.find((u: any) =>
      (storedUsername && (u.username || '').trim() === storedUsername) ||
      (storedRealName && (u.realName || '').trim() === storedRealName)
    )
    if (currentUser) {
      orderForm.ecommerceSalesId = currentUser.id
      orderForm.ecommerceSalesName = currentUser.realName || currentUser.username || ''
      orderForm.createdBy = currentUser.id
      orderForm.creatorName = currentUser.realName || ''
      orderForm.creatorUsername = currentUser.username || ''
      await fetchSalesPersonContact()
    }
  }
  // 加载商品列表
  await fetchProducts()
  // 加载合作方信息
  await fetchPartnerInfo()
  // 加载记忆选项
  loadMemoryOptions()
  // 添加默认商品
  addProduct()
  // 添加默认物流信息
  addLogistics()
  // 添加默认发票信息
  addInvoice()
  dialogVisible.value = true
}

const handleEdit = async (row: any) => {
  try {
    dialogTitle.value = '编辑销售订单'
    editingOrderRow.value = null
    await fetchUsers()
    // 始终按订单 id 拉取最新数据填充表单，避免列表展示价（如被指派方视角的 deliveryPartyPurchasePrice）被误当作原始含税价回写，导致创建人(sonmin)的震坤行等订单金额被联动改掉
    let dataSource = row
    try {
      const res: any = await request.get(`/sales-orders/${row.id}`, {
        skipErrorMsg: true,
        skipErrorLog: true
      } as any)
      dataSource = res
      if (row.assignedUsername === currentUsername.value) {
        editingOrderRow.value = {
          ...res,
          assignedUsername: res.assignedUsername ?? currentUsername.value,
          taxIncludedPrice: res.taxIncludedPrice,
          taxIncludedTotal: res.taxIncludedTotal,
          deductionRate: res.deductionRate,
          deliveryPartyPurchasePrice: res.deliveryPartyPurchasePrice
        }
      } else {
        editingOrderRow.value = { ...res }
      }
    } catch (e) {
      console.error('Fetch order for edit:', e)
      dataSource = row
      editingOrderRow.value = { ...row }
    }
    console.log('Editing order from API:', dataSource)
    Object.assign(orderForm, dataSource)
    fetchProducts()
    fetchPartnerInfo()
    loadMemoryOptions()
    const r = dataSource
    ;(orderForm as any).id = r?.id ?? row?.id ?? undefined
    orderForm.createdBy = r?.createdBy ?? ''
    let salesIdEdit = r.ecommerceSalesId ?? r.ecommerceSales?.id
    if ((salesIdEdit == null || salesIdEdit === '') && r.ecommerceSalesName && salesUsers.value.length > 0) {
      const byName = salesUsers.value.find((u: any) => u.realName === r.ecommerceSalesName)
      if (byName) salesIdEdit = byName.id
    }
    orderForm.ecommerceSalesId = salesIdEdit != null && salesIdEdit !== '' ? Number(salesIdEdit) : ''
    const creator = salesUsers.value.find((u: any) => String(u.id) === String(r?.createdBy))
    orderForm.creatorName = creator?.realName || ''
    orderForm.creatorUsername = creator?.username || ''
    if ((r.platformName || '').trim()) await onPlatformNameChange((r.platformName || '').trim())
    
    let taxIncludedPrice = r.taxIncludedPrice || ''
    let taxIncludedTotal = r.taxIncludedTotal || ''
    
    productList.value = [{
      orderType: r.orderType || '第三方订单',
      omsOrderNo: r.omsOrderNo || '',
      platformSku: r.platformSku || '',
      model: r.model || '',
      productName: r.productName || '',
      productConfig: r.productConfig || '',
      warrantyPeriod: r.warrantyPeriod || '',
      quantity: r.quantity || 1,
      taxIncludedPrice: taxIncludedPrice,
      taxIncludedTotal: taxIncludedTotal
    }]
    
    logisticsList.value = [{
      productIndex: 0,
      productQuantity: r.quantity || 1,
      receiverName: r.receiverName || '',
      receiverPhone: r.receiverPhone || '',
      receiverAddress: r.receiverAddress || '',
      deliveryParty: r.deliveryParty || '',
      shippingParty: r.shippingParty || '',
      deliveryDate: r.deliveryDate || ''
    }]
    
    orderForm.operationEntityTitle = (r.operationEntityTitle || '').trim() || ''
    invoiceList.value = [{
      productIndex: 0,
      invoiceTitle: r.invoiceTitle || '',
      finalCustomerTitle: r.finalCustomerTitle || '',
      paymentMethod: r.paymentMethod || ''
    }]
    if (r.paymentMethod && !paymentMethodOptions.value.includes(r.paymentMethod)) {
      paymentMethodOptions.value = [...paymentMethodOptions.value, r.paymentMethod]
    }
    if (r.finalCustomerTitle && !finalCustomerTitleOptions.value.includes(r.finalCustomerTitle)) {
      finalCustomerTitleOptions.value = [...finalCustomerTitleOptions.value, r.finalCustomerTitle]
    }
    reconciliationList.value = [{
      productIndex: 0,
      logisticsIndex: 0,
      deductionRate: r.deductionRate || '2%',
      deliveryPartyPurchasePrice: r.deliveryPartyPurchasePrice ?? '',
      offlineSales: r.offlineSales || '',
      offlineContractNo: r.offlineContractNo || '',
      offlineShippingPrice: r.offlineShippingPrice || ''
    }]
    const os = (r.offlineSales || '').trim()
    if (os && !reconciliationOfflineSalesOptions.value.includes(os)) {
      reconciliationOfflineSalesOptions.value = [...reconciliationOfflineSalesOptions.value, os]
      saveMemoryOptions()
    }
    
    dialogVisible.value = true
  } catch (error) {
    console.error('Load order error:', error)
    ElMessage.error('加载订单失败')
  }
}

const submitForm = async () => {
  try {
    if (!validateForm()) {
      return
    }
    
    const editingId = Number((orderForm as any).id)
    const isEdit = Number.isFinite(editingId) && editingId > 0
    if (isEdit) {
      let processedOrderForm = buildOrderPayloadBase(orderForm)
      let procurementPreserve: { deliveryParty?: string; shippingParty?: string; deductionRate?: any; deliveryPartyPurchasePrice?: any } = {}
      try {
        const full: any = await request.get(`/sales-orders/${editingId}`)
        procurementPreserve = {
          deliveryParty: full.deliveryParty,
          shippingParty: full.shippingParty,
          deductionRate: full.deductionRate,
          deliveryPartyPurchasePrice: full.deliveryPartyPurchasePrice
        }
      } catch (_) {}
      
      const firstProduct = productList.value[0]
      const firstLogistics = logisticsList.value[0]
      const firstInvoice = invoiceList.value[0]
      const firstReconciliation = reconciliationList.value[0]
      // 含税单价/总价始终以当前表单为准，避免 isAssigned 时用 orig（getOrder 返回的交付方采购价）覆盖用户刚改的 3950 等，导致「改不进列表」
      // 发票信息（账期、发票号码、最终客户抬头、甲方发票抬头等）以第一条发票为准，同步到顶层供后端保存到订单主表，列表与再次编辑才能带出
      const submitData = {
        ...buildOrderPayloadBase(processedOrderForm),
        paymentMethod: firstInvoice?.paymentMethod ?? processedOrderForm.paymentMethod,
        finalCustomerTitle: firstInvoice?.finalCustomerTitle ?? processedOrderForm.finalCustomerTitle ?? '',
        invoiceTitle: firstInvoice?.invoiceTitle ?? processedOrderForm.invoiceTitle ?? '',
        omsOrderNo: firstProduct?.omsOrderNo ?? processedOrderForm.omsOrderNo ?? '',
        platformSku: firstProduct?.platformSku ?? processedOrderForm.platformSku,
        model: firstProduct?.model ?? processedOrderForm.model,
        productName: firstProduct?.productName ?? processedOrderForm.productName,
        materialNo: firstProduct?.materialNo ?? processedOrderForm.materialNo,
        productConfig: firstProduct?.productConfig ?? processedOrderForm.productConfig,
        warrantyPeriod: firstProduct?.warrantyPeriod ?? processedOrderForm.warrantyPeriod,
        quantity: firstProduct?.quantity ?? processedOrderForm.quantity,
        taxIncludedPrice: firstProduct?.taxIncludedPrice ?? processedOrderForm.taxIncludedPrice,
        taxIncludedTotal: firstProduct?.taxIncludedTotal ?? processedOrderForm.taxIncludedTotal,
        orderType: firstProduct?.orderType ?? processedOrderForm.orderType,
        receiverName: firstLogistics?.receiverName ?? processedOrderForm.receiverName,
        receiverPhone: firstLogistics?.receiverPhone ?? processedOrderForm.receiverPhone,
        receiverAddress: firstLogistics?.receiverAddress ?? processedOrderForm.receiverAddress,
        deliveryParty: procurementPreserve.deliveryParty,
        shippingParty: procurementPreserve.shippingParty,
        deductionRate: procurementPreserve.deductionRate,
        deliveryPartyPurchasePrice: procurementPreserve.deliveryPartyPurchasePrice,
        offlineSales: firstReconciliation?.offlineSales ?? processedOrderForm.offlineSales ?? '',
        offlineContractNo: firstReconciliation?.offlineContractNo ?? processedOrderForm.offlineContractNo ?? '',
        offlineShippingPrice: firstReconciliation?.offlineShippingPrice ?? processedOrderForm.offlineShippingPrice ?? '',
        deliveryDate: firstLogistics?.deliveryDate ?? processedOrderForm.deliveryDate,
        ecommerceSalesId: orderForm.ecommerceSalesId != null && orderForm.ecommerceSalesId !== '' ? Number(orderForm.ecommerceSalesId) : processedOrderForm.ecommerceSalesId,
        ecommerceSalesName: orderForm.ecommerceSalesName || (orderForm.ecommerceSalesId && salesUsers.value.find((u: any) => u.id === orderForm.ecommerceSalesId)?.realName) || (processedOrderForm as any).ecommerceSalesName,
        operationEntityTitle: orderForm.operationEntityTitle || processedOrderForm.operationEntityTitle || '',
        products: productList.value,
        logistics: logisticsList.value,
        invoices: invoiceList.value,
        reconciliations: reconciliationList.value
      }
      
      const sanitizedSubmitData = sanitizeDecimalFieldsDeep(submitData)
      console.log('Submitting data:', sanitizedSubmitData)
      await request.put(`/sales-orders/${editingId}`, sanitizedSubmitData)
      ElMessage.success('订单更新成功')
      dialogVisible.value = false
      listRefreshKey.value++
      await fetchOrders(true)
    } else {
      const ordersToCreate: any[] = []
      const orderBase = buildOrderPayloadBase(orderForm)
      
      for (let i = 0; i < productList.value.length; i++) {
        const product = productList.value[i]
        const productLogistics = logisticsList.value.filter(l => l.productIndex === i)
        const productInvoices = invoiceList.value.filter(inv => inv.productIndex === i)
        const productReconciliations = reconciliationList.value.filter(r => r.productIndex === i)
        
        const orderData = {
          ...orderBase,
          omsOrderNo: product.omsOrderNo,
          platformSku: product.platformSku,
          model: product.model,
          productName: product.productName,
          materialNo: product.materialNo,
          productConfig: product.productConfig,
          warrantyPeriod: product.warrantyPeriod,
          quantity: product.quantity,
          taxIncludedPrice: product.taxIncludedPrice,
          taxIncludedTotal: product.taxIncludedTotal,
          orderType: product.orderType,
          products: [product],
          logistics: productLogistics,
          invoices: productInvoices,
          reconciliations: productReconciliations
        }
        
        if (productLogistics.length > 0) {
          const logistics = productLogistics[0]
          orderData.receiverName = logistics.receiverName
          orderData.receiverPhone = logistics.receiverPhone
          orderData.receiverAddress = logistics.receiverAddress
          orderData.deliveryParty = logistics.deliveryParty
          orderData.shippingParty = logistics.shippingParty
          orderData.deliveryDate = logistics.deliveryDate
        }
        
        if (productInvoices.length > 0) {
          const invoice = productInvoices[0]
          orderData.invoiceTitle = invoice.invoiceTitle
          orderData.finalCustomerTitle = invoice.finalCustomerTitle
          orderData.operationEntityTitle = invoice.operationEntityTitle
          orderData.paymentMethod = invoice.paymentMethod
        }
        
        if (productReconciliations.length > 0) {
          const reconciliation = productReconciliations[0]
          orderData.deliveryPartyPurchasePrice = reconciliation.deliveryPartyPurchasePrice
          orderData.settlementNo = reconciliation.settlementNo
          orderData.platformReconciliationNo = reconciliation.platformReconciliationNo
          orderData.offlineSales = reconciliation.offlineSales
          orderData.offlineContractNo = reconciliation.offlineContractNo
          orderData.offlineShippingPrice = reconciliation.offlineShippingPrice
        }
        // 自营订单扣点固定为 20%，不依赖对账明细（避免对账先创建为 2% 的情况）
        orderData.deductionRate = (product.orderType === '自营')
          ? (product.deductionRate || (orderForm as any).deductionRate || '20%')
          : (productReconciliations.length > 0 ? productReconciliations[0].deductionRate : (orderForm as any).deductionRate || '2%')

        ordersToCreate.push(orderData)
      }
      
      for (let idx = 0; idx < ordersToCreate.length; idx++) {
        const orderData = ordersToCreate[idx]
        const sanitizedOrderData = sanitizeDecimalFieldsDeep(orderData)
        try {
          await request.post('/sales-orders', sanitizedOrderData)
        } catch (e: any) {
          const msg = e?.response?.data?.message || e?.response?.data?.error || e?.message || '提交订单失败'
          throw new Error(`第${idx + 1}个商品创建失败：${msg}`)
        }
      }
      
      ElMessage.success(`成功创建 ${ordersToCreate.length} 个订单`)
      Object.keys(omsOrderSeqSeedByPrefix).forEach(key => delete omsOrderSeqSeedByPrefix[key])
      dialogVisible.value = false
      filterForm.status = ''
      filterForm.platformOrderNo = ''
      filterForm.omsOrderNo = ''
      currentPage.value = 1
      await fetchOrders()
    }
  } catch (error: any) {
    console.error('Submit form error:', error)
    console.error('Error response:', error.response)
    console.error('Error data:', error.response?.data)
    const msg = error.response?.data?.message || error.response?.data?.error || error.message || '提交订单失败'
    ElMessage.error(typeof msg === 'string' ? msg : '提交订单失败')
  }
}

const validateForm = () => {
  // 校验基本信息
  if (!orderForm.platformName) {
    ElMessage.error('请选择甲方抬头')
    return false
  }
  if (!orderForm.platformOrderNo) {
    ElMessage.error('请输入甲方订单号')
    return false
  }
  if (!orderForm.ecommerceSalesId) {
    ElMessage.error('请选择业务员')
    return false
  }
  
  // 校验商品信息
  if (productList.value.length === 0) {
    ElMessage.error('请至少添加一个商品')
    return false
  }
  
  // 校验每个商品的OMS订单号
  for (let i = 0; i < productList.value.length; i++) {
    const product = productList.value[i]
    if (!product.omsOrderNo) {
      ElMessage.error(`第${i + 1}个商品的OMS订单号未生成，请确保订单类型、业务员和订单日期都已填写`)
      return false
    }
    if (!product.model) {
      ElMessage.error(`请选择第${i + 1}个商品的型号`)
      return false
    }
    if (!product.quantity || product.quantity <= 0) {
      ElMessage.error(`请填写第${i + 1}个商品的数量`)
      return false
    }
    if (!product.taxIncludedPrice) {
      ElMessage.error(`请填写第${i + 1}个商品的含税单价`)
      return false
    }
    const totalNorm = normalizeDecimalString(product.taxIncludedTotal)
    if (totalNorm == null || totalNorm === '') {
      ElMessage.error(`请填写第${i + 1}个商品的含税总价（须等于数量×含税单价；OCR 有误时请手动改正）`)
      return false
    }
    const qtyN = toNumberSafe(product.quantity)
    const priceN = toNumberSafe(product.taxIncludedPrice)
    const totalN = toNumberSafe(product.taxIncludedTotal)
    const expectedTotal = qtyN * priceN
    if (Math.abs(expectedTotal - totalN) > 0.02) {
      ElMessage.error(
        `第${i + 1}个商品：含税总价与「数量×含税单价」不一致（请核对 OCR）。当前总价 ${totalN}，${qtyN}×${priceN}=${expectedTotal.toFixed(2)}`
      )
      return false
    }
    const hasLogistics = logisticsList.value.some(l => Number(l.productIndex) === i)
    if (!hasLogistics) {
      ElMessage.error(`第${i + 1}个商品未绑定物流信息，请为该商品新增一条物流`)
      return false
    }
    const hasInvoice = invoiceList.value.some(inv => Number(inv.productIndex) === i)
    if (!hasInvoice) {
      ElMessage.error(`第${i + 1}个商品未绑定发票信息，请为该商品新增一条发票`)
      return false
    }
  }
  
  // 校验物流信息
  if (logisticsList.value.length === 0) {
    ElMessage.error('请至少添加一条物流信息')
    return false
  }
  
  for (let i = 0; i < logisticsList.value.length; i++) {
    const logistics = logisticsList.value[i]
    
    if (logistics.productIndex === undefined || logistics.productIndex === null) {
      ElMessage.error(`请选择第${i + 1}条物流的商品信息`)
      return false
    }
    
    if (!logistics.productQuantity || logistics.productQuantity <= 0) {
      ElMessage.error(`请填写第${i + 1}条物流的发货数量`)
      return false
    }
    
    // 校验发货数量不超过商品数量
    const product = productList.value[logistics.productIndex]
    if (product && logistics.productQuantity > product.quantity) {
      ElMessage.error(`第${i + 1}条物流的发货数量不能超过商品数量（${product.quantity}）`)
      return false
    }
    
    if (!logistics.receiverName) {
      ElMessage.error(`请填写第${i + 1}条物流的收货人`)
      return false
    }
    if (!logistics.receiverPhone) {
      ElMessage.error(`请填写第${i + 1}条物流的电话`)
      return false
    }
    if (!logistics.receiverAddress) {
      ElMessage.error(`请填写第${i + 1}条物流的收货地址`)
      return false
    }
    // 交付方已移至采购板块，销售侧不再校验
    if (!logistics.deliveryDate) {
      ElMessage.error(`请选择第${i + 1}条物流的交货日期`)
      return false
    }
  }

  // 同一商品多行物流：各行发货数量合计须等于该商品订单数量（防止多行都默认整单数量导致远超订单）
  for (let pi = 0; pi < productList.value.length; pi++) {
    const product = productList.value[pi]
    const pq = toNumberSafe(product.quantity)
    const sum = logisticsList.value
      .filter(l => Number(l.productIndex) === pi)
      .reduce((s, l) => s + toNumberSafe(l.productQuantity), 0)
    const label = (product.platformSku || product.model || `第${pi + 1}个商品`).toString()
    if (sum > pq) {
      ElMessage.error(`「${label}」物流发货数量合计为 ${sum}，超过订单数量 ${pq}，请调整`)
      return false
    }
    if (sum < pq) {
      ElMessage.error(`「${label}」物流发货数量合计为 ${sum}，须等于订单数量 ${pq}（可分多行发货，但合计须一致）`)
      return false
    }
  }

  // 每个商品：物流行数、发票行数、对账行数须相同（与物流拆行一一对应）
  for (let pi = 0; pi < productList.value.length; pi++) {
    const product = productList.value[pi]
    const lc = logisticsRowCountForProduct(pi)
    const ic = invoiceRowCountForProduct(pi)
    const rc = reconciliationRowCountForProduct(pi)
    const label = (product.platformSku || product.model || `第${pi + 1}个商品`).toString()
    if (lc === 0) continue
    if (ic !== lc) {
      ElMessage.error(`「${label}」发票行数为 ${ic}，须与物流行数 ${lc} 一致（每行物流对应一行发票）`)
      return false
    }
    if (rc !== lc) {
      ElMessage.error(`「${label}」对账行数为 ${rc}，须与物流行数 ${lc} 一致（每行物流对应一行对账）`)
      return false
    }
  }
  
  // 校验发票信息
  if (invoiceList.value.length === 0) {
    ElMessage.error('请至少添加一条发票信息')
    return false
  }
  
  for (let i = 0; i < invoiceList.value.length; i++) {
    const invoice = invoiceList.value[i]
    if (!String(invoice.paymentMethod || '').trim()) {
      ElMessage.error(`请填写第${i + 1}条发票的账期`)
      return false
    }
  }

  // 第三方订单商品：对账明细须有关联行且「线下销售」必填（与列表 offlineSales、流转保存一致）
  for (let pi = 0; pi < productList.value.length; pi++) {
    const product = productList.value[pi]
    const orderType = String(product.orderType || '').trim()
    if (!orderType.includes('第三方')) continue
    const recs = reconciliationList.value.filter(r => Number(r.productIndex) === pi)
    if (recs.length === 0) {
      ElMessage.error(`第${pi + 1}个商品为第三方订单，请到「对账明细」页签新增一条并关联该商品`)
      return false
    }
    const allFilled = recs.every(r => String(r.offlineSales || '').trim())
    if (!allFilled) {
      ElMessage.error(`第${pi + 1}个商品为第三方订单，请到「对账明细」页签的「线下销售」列填写销售姓名`)
      return false
    }
  }
  
  return true
}

const resetForm = () => {
  editingOrderRow.value = null
  salesPersonContact.value = ''
  Object.keys(omsOrderSeqSeedByPrefix).forEach(key => delete omsOrderSeqSeedByPrefix[key])
  const realName = localStorage.getItem('realName') || ''
  const storedUsername = (localStorage.getItem('username') || '').trim()
  let salesId = ''
  if (salesUsers.value.length > 0) {
    const currentUser = salesUsers.value.find((u: any) =>
      (storedUsername && (u.username || '').trim() === storedUsername) ||
      (realName && (u.realName || '').trim() === realName)
    )
    if (currentUser) salesId = currentUser.id
  }
  const creatorUser = salesUsers.value.find((u: any) => String(u.id) === String(salesId))
  Object.assign(orderForm, {
    id: undefined,
    platformName: '',
    platformOrderNo: '',
    omsOrderNo: '',
    orderDate: '',
    deliveryDate: '',
    platformSku: '',
    model: '',
    productName: '',
    productConfig: '',
    warrantyPeriod: '',
    quantity: 1,
    taxIncludedPrice: '',
    taxIncludedTotal: '',
    invoiceTitle: '',
    finalCustomerTitle: '',
    operationEntityTitle: '',
    ecommerceSalesId: salesId,
    ecommerceSalesName: '',
    createdBy: salesId,
    creatorName: creatorUser?.realName || '',
    creatorUsername: creatorUser?.username || '',
    paymentMethod: '',
    receiverName: '',
    receiverPhone: '',
    receiverAddress: '',
    deliveryParty: '',
    shippingParty: '',
    orderType: '第三方订单',
    settlementNo: '',
    settlementUrl: '',
    platformReconciliationNo: '',
    platformReconciliationUrl: '',
    invoiceNumber: '',
    invoiceUrl: '',
    receiptTime: '',
    offlineSales: '',
    offlineContractNo: '',
    offlineShippingPrice: '',
    platformRefundStatus: '',
    deductionRate: '2%',
    deliveryPartyPurchasePrice: '',
    contractUrl: '',
    contractName: '',
    materialNo: ''
  })
  // 重置所有列表
  productList.value = []
  logisticsList.value = []
  invoiceList.value = []
  reconciliationList.value = []
  // 重置发票文件列表
  invoiceFileList.value = []
  // 重置新订单合同文件列表
  newOrderContractFileList.value = []
}

const calculateTotal = () => {
  const price = toNumberSafe(orderForm.taxIncludedPrice)
  const quantity = toNumberSafe(orderForm.quantity)
  orderForm.taxIncludedTotal = (price * quantity).toString()
  calculateDeliveryPartyPurchasePrice()
}

const calculateDeliveryPartyPurchasePrice = () => {
  const total = toNumberSafe(orderForm.taxIncludedTotal)
  let deductionRate = 0.02
  
  if (orderForm.deductionRate) {
    const rateStr = orderForm.deductionRate.toString().replace('%', '')
    deductionRate = toNumberSafe(rateStr) / 100
  }
  
  orderForm.deliveryPartyPurchasePrice = (total * (1 - deductionRate)).toFixed(2)
  syncOfflineShippingPrice()
}

const syncOfflineShippingPrice = () => {
  if (orderForm.orderType === '自营') {
    orderForm.offlineShippingPrice = orderForm.deliveryPartyPurchasePrice
  }
}

const getStatusTag = (status: string) => {
  switch (status) {
    case '待审核':
      return 'warning'
    case '待指派':
      return 'warning'
    case '待确认订单':
      return 'primary'
    case '待发货':
      return 'primary'
    case '待合同盖章':
      return 'info'
    case '已发货':
      return 'success'
    case '已到货':
      return 'success'
    case '已收货':
      return 'success'
    case '已开票':
      return 'success'
    case '已开票待结算':
      return 'success'
    case '已回款':
      return 'success'
    case '已支付':
      return 'success'
    case '平台已支付':
      return 'success'
    case '已结算':
      return 'success'
    case '已盖章':
      return 'success'
    case '已对账未开票':
      return 'warning'
    case '已取消':
      return 'info'
    case '已退回':
      return 'warning'
    default:
      return 'info'
  }
}

const handleAudit = async (row: any) => {
  try {
    const result = await ElMessageBox.confirm('确认审核通过该订单？', '提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    if (result === 'confirm') {
      auditDeliveryDate.value = row.deliveryDate || ''
      currentAuditOrder.value = row
      auditDialogVisible.value = true
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Audit order error:', error)
      ElMessage.error('订单审核失败')
    }
  }
}

const submitAudit = async () => {
  try {
    if (!auditDeliveryDate.value) {
      ElMessage.warning('请选择交货日期')
      return
    }
    
    await request.patch(`/sales-orders/${currentAuditOrder.value.id}/audit`, { deliveryDate: auditDeliveryDate.value })
    ElMessage.success('订单审核成功')
    auditDialogVisible.value = false
    fetchOrders()
  } catch (error) {
    console.error('Audit order error:', error)
    ElMessage.error('订单审核失败')
  }
}

const editDeliveryDate = (row: any) => {
  currentEditDeliveryDateOrder.value = row
  editDeliveryDateForm.deliveryDate = row.deliveryDate || ''
  editDeliveryDateDialogVisible.value = true
}

const submitEditDeliveryDate = async () => {
  try {
    if (!editDeliveryDateForm.deliveryDate) {
      ElMessage.warning('请选择交货日期')
      return
    }
    
    await request.patch(`/sales-orders/${currentEditDeliveryDateOrder.value.id}`, { deliveryDate: editDeliveryDateForm.deliveryDate })
    ElMessage.success('交货日期更新成功')
    editDeliveryDateDialogVisible.value = false
    fetchOrders()
  } catch (error) {
    console.error('Edit delivery date error:', error)
    ElMessage.error('交货日期更新失败')
  }
}

const handleInvoiceUploadSuccess = (response: any, file: File) => {
  if (response.success || response.url) {
    ElMessage.success('发票上传成功')
    // 保存发票文件URL，用于预览
    orderForm.invoiceUrl = response.url || file.name
    // 添加文件到文件列表
    invoiceFileList.value = [{
      name: file.name,
      url: response.url || file.name,
      uid: (file as any).uid
    }]
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

// 预览发票文件
const previewInvoice = (file: any) => {
  // 处理文件对象或URL字符串
  const url = typeof file === 'string' ? file : file.url
  if (url) {
    const absoluteUrl = url.startsWith('/') ? url : `/${url}`
    window.open(`${apiBase()}${absoluteUrl}`, '_blank')
  }
}

const goToContract = (row: any) => {
  currentContractOrder.value = row
  contractFileList.value = row.contractUrl ? [{
    name: '合同文件',
    url: row.contractUrl
  }] : []
  contractDialogVisible.value = true
}

const beforeContractUpload = (file: any) => {
  const isImage = file.type === 'image/png' || file.type === 'image/jpeg' || file.type === 'image/jpg'
  const isPDF = file.type === 'application/pdf'
  const isValid = isImage || isPDF
  if (!isValid) {
    ElMessage.error('只能上传PNG、JPG或PDF格式的文件！')
  }
  return isValid
}

const handleContractUploadSuccess = (response: any) => {
  if (response && response.url) {
    contractFileList.value = [{
      name: '合同文件',
      url: response.url
    }]
    ElMessage.success('合同上传成功')
  } else {
    ElMessage.error('合同上传失败')
  }
}

const handleNewOrderContractUploadSuccess = (response: any) => {
  if (response && response.url) {
    orderForm.contractUrl = response.url
    orderForm.contractName = '合同文件'
    newOrderContractFileList.value = [{
      name: '合同文件',
      url: response.url
    }]
    ElMessage.success('合同上传成功')
  } else {
    ElMessage.error('合同上传失败')
  }
}

// 新建销售订单：从合同 PDF/Word/图片解析并带出甲方、型号、金额等
const onNewOrderImportFileChange = async (file: any) => {
  if (!file?.raw) return
  orderImportLoading.value = true
  try {
    if (!products.value.length) {
      await fetchProducts()
    }
    const formData = new FormData()
    formData.append('file', file.raw)
    const res: any = await request.post('/orders/import-from-file', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (!res?.success) {
      ElMessage.warning(res?.message || '解析未成功')
      return
    }
    if (res.partyATitle) orderForm.platformName = res.partyATitle
    if (res.platformOrderNo) orderForm.platformOrderNo = res.platformOrderNo
    if (res.orderDate) orderForm.orderDate = res.orderDate
    if (res.paymentMethod) orderForm.paymentMethod = res.paymentMethod
    if (res.receiverName) orderForm.receiverName = res.receiverName
    if (res.receiverPhone) orderForm.receiverPhone = res.receiverPhone
    if (res.receiverAddress) orderForm.receiverAddress = res.receiverAddress
    if (invoiceList.value.length > 0) {
      const parsedPm = String(res.paymentMethod || '').trim()
      if (parsedPm) {
        invoiceList.value[0].paymentMethod = parsedPm
        if (!paymentMethodOptions.value.includes(parsedPm)) {
          paymentMethodOptions.value = [...paymentMethodOptions.value, parsedPm]
        }
      } else {
        applyRememberedCreditTermForNewOrder()
      }
      syncInvoicePaymentMethodFromFirst()
    }
    const lines = res.lines && res.lines.length > 0 ? res.lines : null
    if (lines) {
      productList.value = lines.map((line: any) => ({
        orderType: '第三方订单',
        omsOrderNo: '',
        platformSku: line.platformSku || '',
        model: line.model || '',
        productName: line.productName || '',
        productConfig: '',
        warrantyPeriod: '',
        materialNo: line.materialNo || '',
        quantity: line.quantity ?? 1,
        taxIncludedPrice: line.taxIncludedPrice != null ? String(line.taxIncludedPrice) : '',
        taxIncludedTotal: line.taxIncludedTotal != null ? String(line.taxIncludedTotal) : ''
      }))
      for (let i = 0; i < productList.value.length; i++) {
        handleProductModelChange(productList.value[i], { preserveParsedValues: true })
        generateOmsOrderNo(i)
      }
      ElMessage.success(`已解析 ${lines.length} 条商品明细，请核对后保存`)
    } else {
      if (productList.value.length === 0) addProduct()
      const row = productList.value[0]
      if (row) {
        if (res.platformSku) row.platformSku = res.platformSku
        if (res.model) row.model = res.model
        if (res.productName) row.productName = res.productName
        if (res.materialNo) row.materialNo = res.materialNo
        if (res.quantity != null) row.quantity = res.quantity
        if (res.taxIncludedPrice != null) row.taxIncludedPrice = String(res.taxIncludedPrice)
        if (res.taxIncludedTotal != null) row.taxIncludedTotal = String(res.taxIncludedTotal)
        handleProductModelChange(row, { preserveParsedValues: true })
      }
      ElMessage.success('已带出解析结果，请核对甲方抬头、订单号及商品明细')
    }
    if (res.deliveryDate || res.receiverName || res.receiverPhone || res.receiverAddress) {
      if (logisticsList.value.length === 0) addLogistics()
      const lg = logisticsList.value[0]
      if (lg) {
        if (res.deliveryDate) lg.deliveryDate = res.deliveryDate
        if (res.receiverName) lg.receiverName = res.receiverName
        if (res.receiverPhone) lg.receiverPhone = res.receiverPhone
        if (res.receiverAddress) lg.receiverAddress = res.receiverAddress
      }
    }
    newOrderImportUploadRef.value?.clearFiles()
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '解析失败'
    ElMessage.error(msg)
  } finally {
    orderImportLoading.value = false
  }
}

// 合同预览/下载走后端鉴权接口，仅飞础科、上海热像科技可访问
const openContractViaBackend = async (filePath: string, disposition: 'inline' | 'download' = 'inline') => {
  const path = filePath.startsWith('/') ? filePath : `/${filePath}`
  try {
    const res = await request.get('/files/preview', {
      params: { path },
      responseType: 'blob'
    })
    const blob = ((res as any)?.data ?? res) as Blob
    const url = URL.createObjectURL(blob)
    if (disposition === 'inline') {
      window.open(url, '_blank')
      setTimeout(() => URL.revokeObjectURL(url), 60000)
    } else {
      const link = document.createElement('a')
      link.href = url
      link.download = '合同文件.pdf'
      link.click()
      URL.revokeObjectURL(url)
      ElMessage.success('下载成功')
    }
  } catch (e: any) {
    const st = e?.response?.status
    if (st === 403) {
      ElMessage.error('无权限查看该合同')
    } else if (st === 401) {
      ElMessage.error('未登录或登录已失效，请重新登录后再预览合同')
    } else {
      ElMessage.error('打开失败')
    }
  }
}

const previewNewOrderContract = () => {
  if (orderForm.contractUrl && canViewContract.value) {
    openContractViaBackend(orderForm.contractUrl, 'inline')
  }
}

const previewContract = () => {
  let url = ''
  if (contractFileList.value.length > 0) {
    url = contractFileList.value[0].url
  } else if (currentContractOrder.value?.contractUrl) {
    url = currentContractOrder.value.contractUrl
  }
  if (url && canViewContract.value) {
    openContractViaBackend(url, 'inline')
  }
}

const downloadContract = async () => {
  if (isDownloadingContract.value) {
    ElMessage.warning('正在下载合同，请稍候...')
    return
  }
  try {
    isDownloadingContract.value = true
    let url = ''
    if (contractFileList.value.length > 0) {
      url = contractFileList.value[0].url
    } else if (currentContractOrder.value?.contractUrl) {
      url = currentContractOrder.value.contractUrl
    }
    if (url && canViewContract.value) {
      await openContractViaBackend(url, 'download')
    }
  } catch (error: any) {
    console.error('Download contract error:', error)
    ElMessage.error('下载失败')
  } finally {
    isDownloadingContract.value = false
  }
}

const closeContractDialog = () => {
  contractDialogVisible.value = false
  contractFileList.value = []
  currentContractOrder.value = null
}

const saveContract = async () => {
  if (!currentContractOrder.value || !contractFileList.value.length) {
    ElMessage.error('请先上传合同文件')
    return
  }
  
  try {
    const contractUrl = contractFileList.value[0].url
    await request.patch(`/sales-orders/${currentContractOrder.value.id}/contract`, {
      contractUrl: contractUrl
    })
    ElMessage.success('合同保存成功')
    contractDialogVisible.value = false
    fetchOrders()
  } catch (error: any) {
    console.error('保存合同错误:', error)
    console.error('错误响应:', error?.response)
    ElMessage.error('合同保存失败')
  }
}

// 发货相关变量
const shipmentDialogVisible = ref(false)
const currentShipmentOrder = ref<any>(null)
const boxLabelFileList = ref<any[]>([])
const LAST_SHIPMENT_SENDER_INFO_KEY = 'oms_last_shipment_sender_info'
const shipmentForm = reactive({
  salesOrderId: null,
  deliveryNoteUrl: '',
  needReceiptReturn: false,
  returnReceiptTrackingNumber: '',
  returnReceiptReceiverPhone: '',
  printQuantity: 1,
  forbiddenCouriers: '',
  printBoxLabel: false,
  printBarcode128: false,
  shippingAddress: '',
  shippingContact: '',
  shippingPhone: '',
  deliveryMethod: '',
  logisticsCompany: '',
  trackingNumber: '',
  vehiclePlate: '',
  logisticsContact: '',
  logisticsPhone: '',
  receiptUrl: '',
  platformSku: '',
  boxLabelUrls: '',
  receiptTime: '',
  snCode: '',
  receiverName: '',
  receiverPhone: '',
  receiverAddress: ''
})

const loadLastShipmentSenderInfo = () => {
  try {
    const raw = localStorage.getItem(LAST_SHIPMENT_SENDER_INFO_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') return null
    return {
      shippingAddress: (parsed.shippingAddress || '').toString(),
      shippingContact: (parsed.shippingContact || '').toString(),
      shippingPhone: (parsed.shippingPhone || '').toString()
    }
  } catch {
    return null
  }
}

const saveLastShipmentSenderInfo = () => {
  try {
    localStorage.setItem(LAST_SHIPMENT_SENDER_INFO_KEY, JSON.stringify({
      shippingAddress: shipmentForm.shippingAddress || '',
      shippingContact: shipmentForm.shippingContact || '',
      shippingPhone: shipmentForm.shippingPhone || ''
    }))
  } catch {
    // 忽略本地存储异常
  }
}

// 签收单上传弹窗（列表「签收单状态」列打开）
const receiptUploadDialogVisible = ref(false)
const currentReceiptOrder = ref<any>(null)
const receiptUploadForm = reactive({ receiptUrl: '', receiptTime: '', receiptStatus: '已上传' })
const receiptFileList = ref<{ name: string; url: string }[]>([])

const openReceiptUpload = (row: any) => {
  currentReceiptOrder.value = row
  receiptUploadForm.receiptUrl = row.receiptUrl || ''
  receiptUploadForm.receiptTime = row.receiptTime || ''
  receiptUploadForm.receiptStatus = row.receiptStatus || '已上传'
  receiptFileList.value = row.receiptUrl ? [{ name: '签收单', url: row.receiptUrl }] : []
  receiptUploadDialogVisible.value = true
}

const submitReceiptUpload = async () => {
  if (!currentReceiptOrder.value?.id) return
  if (!String(receiptUploadForm.receiptTime || '').trim()) {
    ElMessage.warning('请先选择签收时间')
    return
  }
  try {
    await request.patch(`/sales-orders/${currentReceiptOrder.value.id}/receipt`, {
      receiptUrl: receiptUploadForm.receiptUrl || undefined,
      receiptTime: receiptUploadForm.receiptTime || undefined,
      receiptStatus: receiptUploadForm.receiptStatus || '已上传'
    })
    ElMessage.success('签收单保存成功')
    receiptUploadDialogVisible.value = false
    fetchOrders()
  } catch (e) {
    ElMessage.error('签收单保存失败')
  }
}

const handleShipment = async (row: any) => {
  currentShipmentOrder.value = row
  shipmentForm.salesOrderId = row.id
  // 发货信息默认留空，避免与收货信息重复；若有“上次输入”再自动带出
  shipmentForm.shippingAddress = ''
  shipmentForm.shippingContact = ''
  shipmentForm.shippingPhone = ''
  shipmentForm.receiverName = row.receiverName || ''
  shipmentForm.receiverPhone = row.receiverPhone || ''
  shipmentForm.receiverAddress = row.receiverAddress || ''
  shipmentForm.platformSku = row.platformSku || ''
  shipmentForm.boxLabelUrls = row.boxLabelUrls || ''
  shipmentForm.receiptTime = row.receiptTime || ''
  shipmentForm.snCode = row.snCode || ''
  shipmentForm.deliveryNoteUrl = row.deliveryNoteUrl || ''
  shipmentForm.receiptUrl = row.receiptUrl || ''
  shipmentForm.logisticsCompany = row.logisticsCompany || ''
  shipmentForm.trackingNumber = row.trackingNumber || ''
  shipmentForm.returnReceiptTrackingNumber = row.returnReceiptTrackingNumber || ''
  shipmentForm.returnReceiptReceiverPhone = row.returnReceiptReceiverPhone || ''
  
  if (row.boxLabelUrls) {
    boxLabelFileList.value = row.boxLabelUrls.split(',').map((url: string, index: number) => ({
      name: `箱唛${index + 1}`,
      url: url
    }))
  } else {
    boxLabelFileList.value = []
  }
  
  // 按订单拉取已有发货信息（无记录时后端返回 200+null 或 404，均不报错）
  let hasExistingShipment = false
  try {
    const existingShipment = await request.get(`/order-shipments/sales-order/${row.id}`, {
      validateStatus: (s: number) => s === 200 || s === 404,
      skipErrorMsg: true,
      skipErrorLog: true
    } as any) as any
    if (existingShipment) {
      hasExistingShipment = true
      console.log('Loaded existing shipment:', existingShipment)
      Object.assign(shipmentForm, existingShipment)
      shipmentForm.salesOrderId = row.id
      if (existingShipment.boxLabelUrls) {
        boxLabelFileList.value = existingShipment.boxLabelUrls.split(',').map((url: string, index: number) => ({
          name: `箱唛${index + 1}`,
          url: url
        }))
      }
    }
  } catch (_) {
    // 忽略异常，使用默认值
  }

  if (!hasExistingShipment) {
    const lastSenderInfo = loadLastShipmentSenderInfo()
    if (lastSenderInfo) {
      shipmentForm.shippingAddress = lastSenderInfo.shippingAddress
      shipmentForm.shippingContact = lastSenderInfo.shippingContact
      shipmentForm.shippingPhone = lastSenderInfo.shippingPhone
    }
  }
  
  shipmentDialogVisible.value = true
}

const handleSettlement = (row: any) => {
  currentSettlementOrder.value = row
  settlementInvoiceOcrWarnings.value = []
  settlementInvoiceOcrNeedsConfirm.value = false
  settlementInvoiceBaiduVatUsed.value = false
  settlementInvoiceOcrBuyerTitle.value = ''
  settlementInvoiceOcrSellerTitle.value = ''
  settlementInvoiceOcrBuyerMatched.value = true
  settlementInvoiceOcrSellerMatched.value = true
  settlementForm.settlementNo = row.settlementNo || ''
  settlementForm.settlementUrl = row.settlementUrl || ''
  settlementForm.platformReconciliationNo = row.platformReconciliationNo || ''
  settlementForm.platformReconciliationUrl = row.platformReconciliationUrl || ''
  settlementForm.invoiceNumber = row.invoiceNumber || ''
  settlementForm.invoiceIssuedDate = formatExpectedRefundDate(row.invoiceIssuedDate) || formatTodayDate()
  settlementForm.invoiceUrl = row.invoiceUrl || ''
  settlementForm.platformRefundStatus = row.platformRefundStatus || ''
  settlementForm.platformRefundUrl = row.platformRefundUrl || ''
  settlementForm.expectedRefundDate = formatExpectedRefundDate(row.expectedRefundDate) || ''
  settlementForm.expectedRefundRuleDescription = row.expectedRefundRuleDescription || ''
  settlementForm.expectedRefundPendingReason = row.expectedRefundPendingReason || ''
  settlementForm.expectedRefundOverdue = isExpectedRefundOverdue(row)

  if (row.platformReconciliationUrl) {
    settlementReconciliationFileList.value = [{
      name: '对账单附件',
      url: row.platformReconciliationUrl
    }]
  } else {
    settlementReconciliationFileList.value = []
  }
  
  if (row.invoiceUrl) {
    settlementInvoiceFileList.value = [{
      name: '发票文件',
      url: row.invoiceUrl
    }]
  } else {
    settlementInvoiceFileList.value = []
  }

  if (row.settlementUrl) {
    settlementNoFileList.value = [{
      name: '结算单附件',
      url: row.settlementUrl
    }]
  } else {
    settlementNoFileList.value = []
  }

  if (row.platformRefundUrl) {
    settlementRefundFileList.value = [{
      name: '回款附件',
      url: row.platformRefundUrl
    }]
  } else {
    settlementRefundFileList.value = []
  }
  
  settlementDialogVisible.value = true
}

const handleDeliveryMethodChange = (val: string | undefined | null) => {
  shipmentForm.logisticsCompany = ''
  shipmentForm.trackingNumber = ''
  shipmentForm.vehiclePlate = ''
  shipmentForm.logisticsContact = ''
  shipmentForm.logisticsPhone = ''
  // 仅当 el-select 明确传出字符串且不是「商家联系物流」时清空回单（避免 undefined 等误触发清空）
  if (typeof val === 'string' && val.trim() !== '商家联系物流') {
    shipmentForm.returnReceiptTrackingNumber = ''
    shipmentForm.returnReceiptReceiverPhone = ''
  }
}

/** 关闭「需要签收单回传」时清空回单字段（仅与签收返单场景相关） */
function onNeedReceiptReturnChange(val: boolean | string | number) {
  if (val !== true && val !== 1 && val !== 'true') {
    shipmentForm.returnReceiptTrackingNumber = ''
    shipmentForm.returnReceiptReceiverPhone = ''
  }
}

/** 非「商家联系物流」或未勾选「需要签收单回传」时不提交回单号/手机，避免误写入列表 */
function clearReturnReceiptIfNotRequired() {
  const merchant = (shipmentForm.deliveryMethod || '').trim() === '商家联系物流'
  if (!merchant || !shipmentForm.needReceiptReturn) {
    shipmentForm.returnReceiptTrackingNumber = ''
    shipmentForm.returnReceiptReceiverPhone = ''
  }
}

const deliveryMethodSelectRef = ref<{ $el?: HTMLElement } | null>(null)

/** 保存前同步配送方式：从下拉输入框取当前显示值；不回写回单字段（避免 filterable DOM 与 v-model 不一致） */
function flushShipmentDeliveryMethod() {
  try {
    const sel = deliveryMethodSelectRef.value as any
    if (sel?.$el) {
      const input = sel.$el.querySelector?.('input')
      const v = input?.value?.trim()
      if (v) shipmentForm.deliveryMethod = v
    }
  } catch (_) {}
}

/** 配送方式下拉：失焦时把输入框内容写入表单，避免未点选导致未提交引发保存失败 */
function onShipmentDeliveryMethodBlur(e: FocusEvent) {
  const text = (e.target as HTMLInputElement)?.value?.trim()
  if (text) {
    shipmentForm.deliveryMethod = text
    if (text !== '商家联系物流') {
      shipmentForm.returnReceiptTrackingNumber = ''
      shipmentForm.returnReceiptReceiverPhone = ''
    }
  }
}

/**
 * Edge 下发货弹窗首次打开时会自动把焦点抢回到对话框内，个别环境下影响最小化。
 * 这里阻止 el-dialog 的默认自动聚焦，避免浏览器窗口被立即拉回前台。
 */
function preventShipmentDialogAutoFocus(event?: Event) {
  try {
    event?.preventDefault?.()
    const active = document.activeElement as HTMLElement | null
    active?.blur?.()
  } catch (_) {
    // ignore
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

const handleDeliveryNoteUploadSuccess = (response: any, file: File) => {
  if (response.success || response.url) {
    shipmentForm.deliveryNoteUrl = response.url || file.name
    ElMessage.success('甲方送货单模板上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const handleReceiptUploadSuccess = (response: any, file: any) => {
  if (response.success || response.url) {
    if (receiptUploadDialogVisible.value) {
      receiptUploadForm.receiptUrl = response.url
      receiptFileList.value = [{ name: '签收单', url: response.url }]
      ElMessage.success('签收单上传成功')
    } else {
      shipmentForm.receiptUrl = response.url || file.name
      ElMessage.success('签收单上传成功')
    }
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const handleSettlementInvoiceUploadSuccess = async (response: any, file: File) => {
  if (!canEditSettlementByFinance.value) {
    ElMessage.warning('无权限上传发票文件')
    return
  }
  if (response.success || response.url) {
    settlementForm.invoiceUrl = response.url || file.name
    settlementInvoiceFileList.value = [{
      name: file.name,
      url: response.url || file.name,
      uid: (file as any).uid
    }]
    setSettlementInvoiceFileListTitleAttr(file.name)
    ElMessage.success('发票上传成功')
    settlementInvoiceOcrWarnings.value = []
    settlementInvoiceOcrNeedsConfirm.value = false
    settlementInvoiceBaiduVatUsed.value = false
    settlementInvoiceOcrBuyerTitle.value = ''
    settlementInvoiceOcrSellerTitle.value = ''
    settlementInvoiceOcrBuyerMatched.value = true
    settlementInvoiceOcrSellerMatched.value = true
    try {
      const oid = currentSettlementOrder.value?.id
      if (!oid) return
      const ocr: any = await request.post(`/sales-orders/${oid}/invoice-ocr-validate`, {
        invoiceUrl: settlementForm.invoiceUrl,
        originalFilename: file.name
      })
      settlementInvoiceBaiduVatUsed.value = !!ocr?.baiduVatInvoiceUsed
      settlementInvoiceOcrBuyerTitle.value = String(ocr?.buyerTitle || '').trim()
      settlementInvoiceOcrSellerTitle.value = String(ocr?.sellerTitle || '').trim()
      settlementInvoiceOcrBuyerMatched.value = ocr?.buyerMatched !== false
      settlementInvoiceOcrSellerMatched.value = ocr?.sellerMatched !== false
      if (ocr?.invoiceNumber && String(ocr.invoiceNumber).trim()) {
        settlementForm.invoiceNumber = String(ocr.invoiceNumber).trim()
      }
      if (ocr?.invoiceDate && String(ocr.invoiceDate).trim()) {
        settlementForm.invoiceIssuedDate = String(ocr.invoiceDate).trim()
      }
      settlementInvoiceOcrWarnings.value = Array.isArray(ocr?.warnings) ? ocr.warnings : []
      settlementInvoiceOcrNeedsConfirm.value = !!(ocr?.requiresConfirmation || ocr?.duplicateInvoiceNumber)
      if (ocr?.success && settlementInvoiceOcrWarnings.value.length) {
        ElMessage.warning(ocr.message || '已识别发票，请核对提示')
      } else if (ocr?.success) {
        ElMessage.success('已识别发票号码与开票日期')
      }
    } catch (e: any) {
      console.error('invoice ocr', e)
      ElMessage.warning(e?.response?.data?.message || '发票自动识别失败，请手工填写')
    }
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const clearSettlementInvoiceFile = () => {
  settlementForm.invoiceUrl = ''
  settlementInvoiceFileList.value = []
  settlementInvoiceOcrWarnings.value = []
  settlementInvoiceOcrNeedsConfirm.value = false
  settlementInvoiceBaiduVatUsed.value = false
  settlementInvoiceOcrBuyerTitle.value = ''
  settlementInvoiceOcrSellerTitle.value = ''
  settlementInvoiceOcrBuyerMatched.value = true
  settlementInvoiceOcrSellerMatched.value = true
}

const handlePlatformReconciliationUploadSuccess = (response: any, file: File) => {
  if (!canEditSettlementBySales.value) {
    ElMessage.warning('无权限上传对账单附件')
    return
  }
  if (response.success || response.url) {
    settlementForm.platformReconciliationUrl = response.url || file.name
    settlementReconciliationFileList.value = [{
      name: file.name,
      url: response.url || file.name,
      uid: (file as any).uid
    }]
    ElMessage.success('对账单附件上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const clearSettlementReconciliationFile = () => {
  settlementForm.platformReconciliationUrl = ''
  settlementReconciliationFileList.value = []
}

const handleSettlementNoUploadSuccess = (response: any, file: File) => {
  if (!canEditSettlementBySales.value) {
    ElMessage.warning('无权限上传结算单附件')
    return
  }
  if (response.success || response.url) {
    settlementForm.settlementUrl = response.url || file.name
    settlementNoFileList.value = [{
      name: file.name,
      url: response.url || file.name,
      uid: (file as any).uid
    }]
    ElMessage.success('结算单附件上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const clearSettlementNoFile = () => {
  settlementForm.settlementUrl = ''
  settlementNoFileList.value = []
}

const handlePlatformRefundUploadSuccess = (response: any, file: File) => {
  if (!canEditSettlementByFinance.value) {
    ElMessage.warning('无权限上传回款附件')
    return
  }
  if (response.success || response.url) {
    settlementForm.platformRefundUrl = response.url || file.name
    settlementRefundFileList.value = [{
      name: file.name,
      url: response.url || file.name,
      uid: (file as any).uid
    }]
    ElMessage.success('回款附件上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const clearSettlementRefundFile = () => {
  settlementForm.platformRefundUrl = ''
  settlementRefundFileList.value = []
}

const handleBoxLabelUploadSuccess = (response: any, file: File) => {
  if (response.success || response.url) {
    boxLabelFileList.value.push({
      name: file.name,
      url: response.url
    })
    updateBoxLabelUrls()
    ElMessage.success('箱唛文件上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const handleBoxLabelRemove = (file: any) => {
  const index = boxLabelFileList.value.findIndex(item => item.name === file.name)
  if (index > -1) {
    boxLabelFileList.value.splice(index, 1)
    updateBoxLabelUrls()
  }
}

const updateBoxLabelUrls = () => {
  shipmentForm.boxLabelUrls = boxLabelFileList.value.map(item => item.url).join(',')
}

const previewFile = (fileUrl: string) => {
  if (fileUrl) {
    const fullUrl = fileUrl.startsWith('http') ? fileUrl : `${apiBase()}${fileUrl}`
    window.open(fullUrl, '_blank')
  }
}

const copyReceiverInfo = async (row: any) => {
  if (row.id == null || row.id === undefined || row.id === '') {
    ElMessage.warning('无法复制：订单ID无效')
    return
  }
  const text = `收货人：${row.receiverName || ''}\n联系电话：${row.receiverPhone || ''}\n收货地址：${row.receiverAddress || ''}`
  await copyWithPrivacyLog(text, {
    targetType: 'SALES_ORDER',
    targetId: String(row.id),
    action: 'COPY',
    field: 'receiverName,receiverPhone,receiverAddress'
  })
}

const copyLogisticsRow = async (row: any) => {
  const text = `收货人：${row.receiverName || ''}\n联系电话：${row.receiverPhone || ''}\n收货地址：${row.receiverAddress || ''}`
  const orderId = (orderForm as any).id
  if (orderId) {
    await copyWithPrivacyLog(text, {
      targetType: 'SALES_ORDER',
      targetId: String(orderId),
      action: 'COPY',
      field: 'receiverName,receiverPhone,receiverAddress'
    })
  } else {
    try {
      await navigator.clipboard.writeText(text)
      ElMessage.success('已复制到剪贴板')
    } catch {
      ElMessage.error('复制失败')
    }
  }
}

const copyShipmentReceiverInfo = async () => {
  const orderId = currentShipmentOrder.value?.id
  if (!orderId) return
  const text = `收货人：${shipmentForm.receiverName || ''}\n联系电话：${shipmentForm.receiverPhone || ''}\n收货地址：${shipmentForm.receiverAddress || ''}`
  await copyWithPrivacyLog(text, {
    targetType: 'SALES_ORDER',
    targetId: String(orderId),
    action: 'COPY',
    field: 'receiverName,receiverPhone,receiverAddress'
  })
}

const previewDeliveryNote = async (row: any) => {
  if (!row.deliveryNoteUrl) return
  await logPrivacyAccess({
    targetType: 'DELIVERY_NOTE',
    targetId: String(row.id),
    action: 'DOWNLOAD',
    field: '送货单'
  })
  previewFile(row.deliveryNoteUrl)
}

/** 订单发货弹窗内：预览当前表单中的甲方送货单模板（与列表「送货单-查看」一致） */
const previewShipmentDeliveryNoteTemplate = async () => {
  const url = (shipmentForm.deliveryNoteUrl || '').trim()
  if (!url) {
    ElMessage.warning('请先上传甲方送货单模板')
    return
  }
  const orderId = shipmentForm.salesOrderId ?? currentShipmentOrder.value?.id
  if (orderId != null && orderId !== '') {
    await logPrivacyAccess({
      targetType: 'DELIVERY_NOTE',
      targetId: String(orderId),
      action: 'DOWNLOAD',
      field: '送货单模板(发货弹窗)'
    })
  }
  previewFile(url)
}

// 128条码：网页预览/下载（新开页显示图片，可另存为）
const downloadBarcode128 = (platformSku: string) => {
  if (!platformSku) return
  const base = apiBase()
  const url = `${base}/api/barcode/code128/with-text?text=${encodeURIComponent(platformSku)}&width=400&height=130&labelText=${encodeURIComponent(platformSku)}`
  window.open(url, '_blank')
}

function getShipmentErrorMessage(error: any) {
  const msg = error?.response?.data?.message || error?.message || '发货信息保存失败'
  return typeof msg === 'string' ? msg : '发货信息保存失败'
}

// 仅当在「操作-发货」中选择了配送方式且填好了快递单号或车牌号时，才允许把订单状态置为已发货；仅填客户发货要求等不改变状态
const canSetOrderStatusToShipped = () => {
  const dm = (shipmentForm.deliveryMethod || '').trim()
  if (!dm) return false
  if (dm === '商家联系物流') return (shipmentForm.trackingNumber || '').trim().length > 0
  if (dm === '自主车辆配送') return (shipmentForm.vehiclePlate || '').trim().length > 0
  return false
}

const submitShipment = async () => {
  try {
    flushShipmentDeliveryMethod()
    clearReturnReceiptIfNotRequired()
    if (!shipmentForm.salesOrderId) {
      ElMessage.warning('请先选择订单后再发货')
      return
    }
    console.log('Submitting shipment:', shipmentForm)
    await request.post('/order-shipments', shipmentForm)
    saveLastShipmentSenderInfo()
    if (canSetOrderStatusToShipped()) {
      await request.patch(`/sales-orders/${currentShipmentOrder.value.id}/status`, { status: '已发货' })
      ElMessage.success('发货信息保存成功，订单状态已更新为已发货')
    } else {
      ElMessage.success('发货信息已保存。选择配送方式并填写快递单号或车牌号后再次保存，订单状态将变为已发货')
    }
    shipmentDialogVisible.value = false
    fetchOrders()
  } catch (error) {
    console.error('Submit shipment error:', error)
    ElMessage.error(getShipmentErrorMessage(error))
  }
}

const syncShipment = async () => {
  try {
    flushShipmentDeliveryMethod()
    clearReturnReceiptIfNotRequired()
    const orderId = currentShipmentOrder.value?.id ?? shipmentForm.salesOrderId
    if (!orderId) {
      ElMessage.warning('请先选择订单后再同步')
      return
    }
    await request.post('/order-shipments', shipmentForm)
    saveLastShipmentSenderInfo()
    await request.patch(`/sales-orders/${orderId}/receiver`, {
      receiverName: shipmentForm.receiverName || undefined,
      receiverPhone: shipmentForm.receiverPhone || undefined,
      receiverAddress: shipmentForm.receiverAddress || undefined
    })
    await request.post(`/sales-orders/${orderId}/sync-shipment-to-chain`)
    ElMessage.success('发货要求与物流信息已同步到交付方/出货方')
    fetchOrders()
  } catch (error) {
    console.error('Sync shipment error:', error)
    ElMessage.error(getShipmentErrorMessage(error))
  }
}

const submitSettlement = async () => {
  try {
    if (!canEditSettlementBySales.value && !canEditSettlementByFinance.value) {
      ElMessage.warning('无权限编辑结算信息')
      return
    }
    if (canEditSettlementByFinance.value && settlementInvoiceOcrNeedsConfirm.value) {
      const msg =
        settlementInvoiceOcrWarnings.value.filter(Boolean).join('\n') ||
        '发票识别结果与当前订单抬头可能不一致，或发票号码可能重复，请确认后再保存。'
      try {
        await ElMessageBox.confirm(msg, '请确认发票信息', {
          type: 'warning',
          confirmButtonText: '确认保存',
          cancelButtonText: '取消'
        })
      } catch {
        return
      }
      settlementInvoiceOcrNeedsConfirm.value = false
    }
    console.log('Submitting settlement:', settlementForm)
    const patchData: Record<string, any> = {}
    if (canEditSettlementBySales.value) {
      patchData.settlementNo = settlementForm.settlementNo
      patchData.settlementUrl = settlementForm.settlementUrl
      patchData.platformReconciliationNo = settlementForm.platformReconciliationNo
      patchData.platformReconciliationUrl = settlementForm.platformReconciliationUrl
    }
    if (canEditSettlementByFinance.value) {
      patchData.invoiceNumber = settlementForm.invoiceNumber
      patchData.invoiceIssuedDate = settlementForm.invoiceIssuedDate || ''
      patchData.invoiceUrl = settlementForm.invoiceUrl
      patchData.platformRefundStatus = settlementForm.platformRefundStatus
      patchData.platformRefundUrl = settlementForm.platformRefundUrl
    }
    await request.patch(`/sales-orders/${currentSettlementOrder.value.id}/settlement`, patchData)
    ElMessage.success('结算信息保存成功')
    settlementDialogVisible.value = false
    fetchOrders()
  } catch (error: any) {
    console.error('Submit settlement error:', error)
    const msg = error?.response?.data?.message || '结算信息保存失败'
    ElMessage.error(msg)
  }
}

// 结算相关变量
const settlementDialogVisible = ref(false)
const currentSettlementOrder = ref<any>(null)
const settlementReconciliationFileList = ref<any[]>([])
const settlementInvoiceFileList = ref<any[]>([])
const settlementNoFileList = ref<any[]>([])
const settlementRefundFileList = ref<any[]>([])
const settlementInvoiceOcrWarnings = ref<string[]>([])
const settlementInvoiceOcrNeedsConfirm = ref(false)
/** 后端是否合并了百度增值税发票专用接口结构化字段 */
const settlementInvoiceBaiduVatUsed = ref(false)
const settlementInvoiceOcrBuyerTitle = ref('')
const settlementInvoiceOcrSellerTitle = ref('')
const settlementInvoiceOcrBuyerMatched = ref(true)
const settlementInvoiceOcrSellerMatched = ref(true)
const settlementInvoiceUploadRef = ref<any>(null)

function setSettlementInvoiceFileListTitleAttr(fileName: string) {
  if (!fileName) return
  nextTick(() => {
    const root = settlementInvoiceUploadRef.value?.$el as HTMLElement | undefined
    if (!root) return
    const nameEl = root.querySelector('.el-upload-list__item-name') || root.querySelector('.el-upload-list__item-file-name')
    if (nameEl) nameEl.setAttribute('title', fileName)
  })
}
const settlementForm = reactive({
  settlementNo: '',
  settlementUrl: '',
  platformReconciliationNo: '',
  platformReconciliationUrl: '',
  invoiceNumber: '',
  invoiceIssuedDate: '',
  invoiceUrl: '',
  platformRefundStatus: '',
  platformRefundUrl: '',
  expectedRefundDate: '',
  expectedRefundRuleDescription: '',
  expectedRefundPendingReason: '',
  expectedRefundOverdue: false
})

const isDownloadingContract = ref(false)

// 合同相关变量
const contractDialogVisible = ref(false)
const currentContractOrder = ref<any>(null)
const contractFileList = ref<any[]>([])

// 确认订单相关变量
const auditDialogVisible = ref(false)
const currentAuditOrder = ref<any>(null)
const auditDeliveryDate = ref('')
const auditForm = reactive({
  deliveryDate: ''
})

const structureInspectVisible = ref(false)
const structureInspectLoading = ref(false)
const structureBackfillLoading = ref(false)
const currentStructureOrder = ref<any>(null)
const structureInspectMaster = ref<any>(null)
const structureInspectMasterSummary = ref<any>(null)
const structureInspectAllocation = ref<any>(null)
const structureInspectAllocationSummary = ref<any>(null)
const structureInspectOrders = ref<any[]>([])
const structureInspectAllocations = ref<any[]>([])
const structureInspectSerialItems = ref<any[]>([])
const structureInspectError = ref('')

// 编辑交货日期相关变量
const editDeliveryDateDialogVisible = ref(false)
const currentEditDeliveryDateOrder = ref<any>(null)
const editDeliveryDateForm = reactive({
  deliveryDate: ''
})

// 退回订单相关变量
const returnDialogVisible = ref(false)
const currentReturnOrder = ref<any>(null)
const returnForm = reactive({
  returnReason: ''
})

const handleReturn = (row: any) => {
  currentReturnOrder.value = row
  returnForm.returnReason = ''
  returnDialogVisible.value = true
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
  structureInspectError.value = ''
  resetStructureInspectState()
  try {
    const latestOrder: any = await request.get(`/sales-orders/${row.id}`)
    currentStructureOrder.value = latestOrder

    const requests: Promise<any>[] = []
    if (latestOrder.masterId) {
      requests.push(request.get(`/sales-order-masters/${latestOrder.masterId}`))
    } else {
      requests.push(Promise.resolve(null))
    }
    if (latestOrder.allocationId) {
      requests.push(request.get(`/sales-order-allocations/${latestOrder.allocationId}/detail`))
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

    if (!latestOrder.masterId && !latestOrder.allocationId) {
      structureInspectError.value = '当前订单尚未挂接主单/分配锚点，可点击“补挂当前订单”初始化。'
    }
  } catch (error: any) {
    console.error('Load structure inspect error:', error)
    structureInspectError.value = error?.response?.data?.message || error?.message || '链路观察数据加载失败'
  } finally {
    structureInspectLoading.value = false
  }
}

const openStructureInspect = async (row: any) => {
  currentStructureOrder.value = row
  structureInspectVisible.value = true
  await loadStructureInspect(row)
}

const openMasterViewDrawer = async () => {
  masterViewDrawerVisible.value = true
  masterCurrentPage.value = 1
  await fetchMasterOrders()
}

const handleMasterSizeChange = (size: number) => {
  masterPageSize.value = size
  fetchMasterOrders()
}

const handleMasterCurrentChange = (page: number) => {
  masterCurrentPage.value = page
  fetchMasterOrders()
}

const openMasterChainObserver = (row: any) => {
  const keyword = String(row?.masterNo || '').trim()
  if (!keyword) return
  router.push({ path: '/chain/observe', query: { type: 'masterNo', keyword } })
}

const handleMasterExpandChange = async (row: any, expandedRows: any[]) => {
  const isExpanded = Array.isArray(expandedRows) && expandedRows.some((item: any) => String(item?.id) === String(row?.id))
  const rowId = String(row?.id || '')
  if (!isExpanded || !rowId || masterDetailMap.value[rowId] || masterDetailLoadingMap.value[rowId]) {
    return
  }
  try {
    masterDetailLoadingMap.value = { ...masterDetailLoadingMap.value, [rowId]: true }
    const detail: any = await request.get(`/sales-order-masters/${row.id}`)
    masterDetailMap.value = { ...masterDetailMap.value, [rowId]: detail }
  } catch (error) {
    console.error('Load master detail error:', error)
    ElMessage.error('加载主单详情失败')
  } finally {
    masterDetailLoadingMap.value = { ...masterDetailLoadingMap.value, [rowId]: false }
  }
}

const refreshStructureInspect = async () => {
  if (!currentStructureOrder.value) return
  await loadStructureInspect(currentStructureOrder.value)
}

const backfillCurrentStructureOrder = async () => {
  if (!currentStructureOrder.value?.id) return
  try {
    structureBackfillLoading.value = true
    await request.post('/sales-order-masters/backfill', {
      orderIds: [currentStructureOrder.value.id],
      onlyMissing: true,
      limit: 1
    })
    await fetchOrders(true)
    await loadStructureInspect(currentStructureOrder.value)
    ElMessage.success('当前订单锚点补挂成功')
  } catch (error: any) {
    console.error('Backfill structure error:', error)
    ElMessage.error(error?.response?.data?.message || error?.message || '补挂失败')
  } finally {
    structureBackfillLoading.value = false
  }
}

const submitReturn = async () => {
  try {
    await request.patch(`/sales-orders/${currentReturnOrder.value.id}/return`, {
      returnReason: returnForm.returnReason
    })
    ElMessage.success('订单退回成功')
    returnDialogVisible.value = false
    fetchOrders()
  } catch (error: any) {
    console.error('Return order error:', error)
    const msg = error?.response?.data?.message || error?.message || '订单退回失败，请稍后重试'
    ElMessage.error(typeof msg === 'string' ? msg : '订单退回失败，请稍后重试')
  }
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除订单「${row.platformOrderNo || row.omsOrderNo}」（甲方订单号）吗？此操作不可恢复！`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await request.delete(`/sales-orders/${row.id}`)
    ElMessage.success('删除成功')
    fetchOrders()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('Delete order error:', error)
      ElMessage.error('删除失败，请稍后重试')
    }
  }
}

const handleOrderTypeChange = (value: string) => {
  // 这个函数现在主要用于向后兼容，订单类型已移到商品级别
  if (value === '自营') {
    orderForm.deliveryParty = '上海热像科技股份有限公司'
    orderForm.deductionRate = '20%'
    calculateDeliveryPartyPurchasePrice()
  } else {
    orderForm.deductionRate = '2%'
    calculateDeliveryPartyPurchasePrice()
  }
  // 为所有商品重新生成OMS订单号
  productList.value.forEach((_, index) => {
    generateOmsOrderNo(index)
  })
}

const handleSalesChange = (value: string) => {
  const selectedUser = salesUsersForSelect.value.find((user: any) => String(user.id) === String(value))
    || salesUsers.value.find((user: any) => String(user.id) === String(value))
  orderForm.ecommerceSalesName = selectedUser?.realName || selectedUser?.username || ''
  if (selectedUser && orderForm.orderType === '自营') {
    orderForm.offlineSales = selectedUser.realName
  }
  fetchSalesPersonContact()
  Object.keys(omsOrderSeqSeedByPrefix).forEach(key => delete omsOrderSeqSeedByPrefix[key])
  // 为所有商品重新生成OMS订单号
  productList.value.forEach((_, index) => {
    generateOmsOrderNo(index)
  })
}

/** 根据当前所选业务员从用户信息维护拉取并展示联系人（如坚领→袁星辉） */
const fetchSalesPersonContact = async () => {
  salesPersonContact.value = ''
  const uid = orderForm.ecommerceSalesId
  if (uid == null || uid === '') return
  const user = salesUsers.value.find((u: any) => u.id === uid || String(u.id) === String(uid))
  const username = (user?.username || '').trim()
  if (!username) return
  try {
    const res: any = await request.get('/partner-info/by-username', {
      params: { username },
      skipErrorMsg: true
    } as any)
    const list = Array.isArray(res) ? res : (res?.content ?? [])
    const first = list[0]
    if (first?.contactPerson) salesPersonContact.value = first.contactPerson
  } catch {
    // 忽略
  }
}

const handleOrderDateChange = (value: string) => {
  // 当订单日期改变时的处理逻辑
  console.log('Order date changed to:', value)
  Object.keys(omsOrderSeqSeedByPrefix).forEach(key => delete omsOrderSeqSeedByPrefix[key])
  // 为所有商品重新生成OMS订单号
  productList.value.forEach((_, index) => {
    generateOmsOrderNo(index)
  })
}

const handleModelChange = (value: string) => {
  // 当型号改变时的处理逻辑
  console.log('Model changed to:', value)
  // 查找商品并填充含税单价
  const product = products.value.find(p => p.model === value)
  if (product) {
    if (product.price) {
      orderForm.taxIncludedPrice = product.price.toString()
      calculateTotal()
    }
    if (product.materialNo) {
      orderForm.materialNo = product.materialNo
    }
    if (product.productConfig) {
      orderForm.productConfig = product.productConfig
    }
    if (product.warrantyPeriod) {
      orderForm.warrantyPeriod = product.warrantyPeriod
    }
  }
}

const buildOmsOrderNoPrefix = (product: any) => {
  if (!product?.orderType || !orderForm.ecommerceSalesId || !orderForm.orderDate) {
    return ''
  }

  const salesUser = salesUsers.value.find(u => u.id === orderForm.ecommerceSalesId)
  if (!salesUser) {
    return ''
  }

  const name = salesUser.realName || salesUser.username || ''
  const firstLetter = getPinyinFirstLetter(name)

  const date = new Date(orderForm.orderDate)
  if (Number.isNaN(date.getTime())) {
    return ''
  }

  const yyyy = date.getFullYear().toString()
  const yy = yyyy.slice(-2)
  const mm = (date.getMonth() + 1).toString().padStart(2, '0')
  const dd = date.getDate().toString().padStart(2, '0')
  const dateStr = product.orderType === '第三方订单'
    ? `${yy}${mm}${dd}`
    : `${yyyy}${mm}${dd}`

  return `${product.orderType === '第三方订单' ? 'D' : ''}${dateStr}${firstLetter}`
}

const extractOmsOrderNoSequence = (orderNo: string) => {
  const trimmed = (orderNo || '').trim()
  if (!trimmed || trimmed.length < 2) return 1
  const seqText = trimmed.slice(-2)
  const seq = Number.parseInt(seqText, 10)
  return Number.isFinite(seq) && seq > 0 ? seq : 1
}

const fetchOmsOrderSeqSeed = async (product: any, prefix: string) => {
  if (!prefix) return 1
  if (omsOrderSeqSeedByPrefix[prefix]) {
    return omsOrderSeqSeedByPrefix[prefix]
  }

  const response = await request.get('/sales-orders/generate-no', {
    params: {
      userId: orderForm.ecommerceSalesId,
      orderType: product.orderType,
      orderDate: orderForm.orderDate
    }
  })
  const generated = typeof response === 'string'
    ? response
    : (typeof (response as any)?.data === 'string' ? (response as any).data : '')
  const seed = extractOmsOrderNoSequence(generated)
  omsOrderSeqSeedByPrefix[prefix] = seed
  return seed
}

const generateOmsOrderNo = async (productIndex?: number) => {
  // 如果没有指定商品索引，则为第一个商品生成
  const index = productIndex !== undefined ? productIndex : 0
  if (!productList.value[index]) {
    return
  }
  
  const product = productList.value[index]

  const prefix = buildOmsOrderNoPrefix(product)
  if (!prefix) {
    return
  }

  try {
    const seed = await fetchOmsOrderSeqSeed(product, prefix)
    const samePrefixBeforeCount = productList.value.reduce((count, row, idx) => {
      if (idx >= index) return count
      return buildOmsOrderNoPrefix(row) === prefix ? count + 1 : count
    }, 0)
    product.omsOrderNo = `${prefix}${String(seed + samePrefixBeforeCount).padStart(2, '0')}`
  } catch (error) {
    console.error('Generate OMS order no error:', error)
    ElMessage.error('获取OMS订单号失败')
    return
  }
  
  // 如果是自营商品，同时设置离线合同号
  if (product.orderType === '自营') {
    product.offlineContractNo = product.omsOrderNo
  }
}

// 获取拼音首字母
const getPinyinFirstLetter = (str: string): string => {
  if (!str) return 'X'
  
  // 简单的拼音首字母映射
  const pinyinMap: Record<string, string> = {
    '王': 'W', '李': 'L', '张': 'Z', '刘': 'L', '陈': 'C', '杨': 'Y', '赵': 'Z', '黄': 'H', '周': 'Z', '吴': 'W',
    '徐': 'X', '孙': 'S', '胡': 'H', '朱': 'Z', '高': 'G', '林': 'L', '何': 'H', '郭': 'G', '马': 'M', '罗': 'L',
    '梁': 'L', '宋': 'S', '郑': 'Z', '谢': 'X', '韩': 'H', '唐': 'T', '冯': 'F', '于': 'Y', '董': 'D', '萧': 'X',
    '程': 'C', '曹': 'C', '袁': 'Y', '邓': 'D', '许': 'X', '傅': 'F', '沈': 'S', '曾': 'Z', '彭': 'P', '吕': 'L',
    '苏': 'S', '卢': 'L', '蒋': 'J', '蔡': 'C', '贾': 'J', '丁': 'D', '魏': 'W', '薛': 'X', '叶': 'Y', '阎': 'Y',
    '余': 'Y', '潘': 'P', '杜': 'D', '戴': 'D', '夏': 'X', '钟': 'Z', '汪': 'W', '田': 'T', '任': 'R', '姜': 'J',
    '范': 'F', '方': 'F', '石': 'S', '姚': 'Y', '谭': 'T', '廖': 'L', '邹': 'Z', '熊': 'X', '金': 'J', '陆': 'L',
    '郝': 'H', '孔': 'K', '白': 'B', '崔': 'C', '康': 'K', '毛': 'M', '邱': 'Q', '秦': 'Q', '江': 'J', '史': 'S',
    '顾': 'G', '侯': 'H', '邵': 'S', '孟': 'M', '龙': 'L', '万': 'W', '段': 'D', '漕': 'C', '钱': 'Q', '汤': 'T',
    '尹': 'Y', '黎': 'L', '易': 'Y', '常': 'C', '武': 'W', '乔': 'Q', '贺': 'H', '赖': 'L', '龚': 'G', '文': 'W',
    '敏': 'M', '明': 'M', '伟': 'W', '芳': 'F', '娜': 'N', '秀': 'X', '英': 'Y', '华': 'H', '强': 'Q', '磊': 'L',
    '军': 'J', '洋': 'Y', '勇': 'Y', '艳': 'Y', '杰': 'J', '娟': 'J', '涛': 'T', '超': 'C',
    '霞': 'X', '平': 'P', '刚': 'G', '桂': 'G', '丽': 'L', '国': 'G', '海': 'H', '波': 'B',
    '斌': 'B', '梅': 'M', '静': 'J', '玲': 'L', '燕': 'Y', '辉': 'H', '红': 'H', '建': 'J', '新': 'X',
    '志': 'Z', '永': 'Y', '玉': 'Y', '美': 'M', '春': 'C', '德': 'D', '俊': 'J', '雪': 'X',
    '峰': 'F', '飞': 'F', '晓': 'X', '慧': 'H', '萍': 'P', '健': 'J', '云': 'Y', '鹏': 'P', '庆': 'Q',
    '兵': 'B', '东': 'D', '小': 'X', '光': 'G', '亮': 'L'
  }
  
  let result = ''
  for (let i = 0; i < str.length; i++) {
    const char = str.charAt(i)
    
    // 检查是否是中文字符
    if (char.match(/[\u4e00-\u9fa5]/)) {
      result += pinyinMap[char] || 'X'
    } else {
      // 非中文字符，直接取首字母大写
      result += char.toUpperCase()
    }
    
    // 取前三个字符的拼音首字母（如：王潇龙 -> WXL）
    if (result.length >= 3) {
      break
    }
  }
  
  return result || 'X'
}

const handleTableSelectionChange = (rows: any[]) => {
  selectedSalesRows.value = Array.isArray(rows) ? rows : []
}

const handleBatchGenerateSalesReconciliation = async () => {
  if (selectedSalesRows.value.length === 0) {
    ElMessage.warning('请先选择销售订单')
    return
  }
  const invalidRows = selectedSalesRows.value.filter((row: any) => !canGenerateSalesReconciliationFromStatus(row?.status))
  if (invalidRows.length > 0) {
    const names = invalidRows
      .slice(0, 5)
      .map((row: any) => `${row?.omsOrderNo || row?.platformOrderNo || row?.id || '-'}(${row?.status || '-'})`)
      .join('，')
    ElMessage.warning(`仅“已签收/已妥投/已到货”状态的订单可生成销售对账单，当前不满足：${names}${invalidRows.length > 5 ? ' 等' : ''}`)
    return
  }
  try {
    const promptResult: any = await ElMessageBox.prompt(
      `已选择 ${selectedSalesRows.value.length} 条销售订单。请输入甲方对账单号；若客户没有自己的对账单号，可留空使用系统默认值。`,
      '生成销售对账单',
      {
        confirmButtonText: '确定生成',
        cancelButtonText: '取消',
        inputValue: getGenerateReconciliationDefaultNo(),
        inputPlaceholder: '请输入甲方对账单号，可留空',
        closeOnClickModal: false
      }
    )
    const platformReconciliationNo = String(promptResult?.value || '').trim()
    await request.post('/sales-reconciliations/generate', {
      orderIds: selectedSalesRows.value.map((row: any) => row.id),
      reconciliationDate: new Date().toISOString().slice(0, 10),
      platformReconciliationNo
    })
    ElMessage.success('销售对账单生成成功')
    selectedSalesRows.value = []
    await fetchOrders()
    router.push('/sales/reconciliation')
  } catch (error: any) {
    if (error !== 'cancel') {
      const msg = error?.response?.data?.message || error?.message || '销售对账单生成失败'
      ElMessage.error(msg)
    }
  }
}

const getGenerateReconciliationDefaultNo = () => {
  const set = new Set<string>()
  selectedSalesRows.value.forEach((row: any) => {
    const value = String(row?.platformReconciliationNo || '').trim()
    if (value) set.add(value)
  })
  return Array.from(set).join('/')
}

const canGenerateSalesReconciliationFromStatus = (status: string) => {
  const value = String(status || '').trim()
  return value === '已签收' || value === '已妥投' || value === '已到货' || value === '已到货待回单'
}
</script>

<style scoped>
.platform-name-hint .hint-link {
  color: var(--el-color-primary);
  margin: 0 2px;
}
.order-list-container {
  padding: 20px;
}

.filter-card {
  margin-bottom: 20px;
}

.filter-inline-hint {
  margin-top: 8px;
}

.more-filters-form {
  margin-top: 8px;
}

.table-card {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.table-toolbar {
  margin-bottom: 20px;
  display: flex;
  gap: 10px;
}

.shipping-requirements-popover .req-line,
.shipping-requirements-inline .req-line {
  margin-bottom: 6px;
  line-height: 1.5;
}
.shipping-requirements-inline .req-line:last-child {
  margin-bottom: 0;
}

.delivery-note-upload-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 12px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.master-group-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  margin-top: 4px;
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

.master-group-tip {
  margin-top: 2px;
  color: #909399;
  font-size: 12px;
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

.column-settings {
  max-height: 400px;
  overflow-y: auto;
  padding: 10px 0;
}

.column-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}

.column-item {
  display: flex;
  align-items: center;
  padding: 8px 10px;
  border-bottom: 1px solid #f0f0f0;
  cursor: move;
  transition: background-color 0.3s;
}

.column-item:hover {
  background-color: #f5f7fa;
}

.column-item.hidden-column {
  opacity: 0.5;
}

.drag-handle {
  margin-right: 10px;
  cursor: move;
  color: #909399;
  font-size: 16px;
  user-select: none;
}

.drag-ghost {
  opacity: 0.5;
  background: #c8ebfb;
}

.drag-chosen {
  background: #e6f7ff;
}

.settings-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid #ebeef5;
}

.structure-inspect-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 160px;
}

.master-expand-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 6px 4px;
}

.master-expand-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  color: #606266;
  font-size: 13px;
}

.master-expand-loading,
.master-expand-empty {
  color: #909399;
  font-size: 13px;
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

/* 物流信息脱敏展示（只读态） */
.logistics-masked {
  display: block;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
  padding: 2px 0;
}
.logistics-masked-address {
  white-space: pre-wrap;
  word-break: break-all;
}

.logistics-traces {
  margin-top: 20px;
}

.trace-list {
  position: relative;
  padding-left: 30px;
}

.trace-item {
  position: relative;
  padding-bottom: 30px;
}

.trace-item:last-child {
  padding-bottom: 0;
}

.trace-dot {
  position: absolute;
  left: -30px;
  top: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background-color: #dcdfe6;
  border: 3px solid #fff;
  box-shadow: 0 0 0 2px #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}

.trace-dot.active {
  background-color: #1890ff;
  box-shadow: 0 0 0 2px #1890ff;
}

.trace-dot .el-icon {
  color: #fff;
  font-size: 12px;
}

.trace-line {
  position: absolute;
  left: -20px;
  top: 20px;
  bottom: 0;
  width: 2px;
  background-color: #dcdfe6;
}

.trace-content {
  padding-left: 10px;
}

.trace-status {
  font-size: 14px;
  font-weight: 500;
  color: #909399;
  margin-bottom: 5px;
}

.trace-status.latest {
  color: #1890ff;
  font-weight: 600;
}

.trace-desc {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  margin-bottom: 5px;
}

.trace-desc.latest {
  color: #1890ff;
}

.trace-time {
  font-size: 13px;
  color: #909399;
}

/* 操作按钮响应式样式 */
.operation-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  justify-content: center;
}

/* 小屏幕适配 */
@media (max-width: 768px) {
  .operation-buttons {
    justify-content: flex-start;
    overflow-x: auto;
    white-space: nowrap;
    padding: 5px 0;
  }
  
  .table-toolbar {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .filter-card {
    margin-bottom: 15px;
  }
  
  .el-form {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .el-form-item {
    width: 100%;
    margin-bottom: 10px;
  }
  
  .el-input {
    width: 100%;
  }
  
  .el-select {
    width: 100%;
  }
}
.status-pending-assign-btn {
  padding: 0;
  height: auto;
  vertical-align: middle;
}
.status-pending-assign-btn .el-tag {
  cursor: pointer;
}
.status-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.status-layer-stack {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.status-layer-stack .el-tag {
  min-width: 64px;
  justify-content: center;
}
.status-extra-hint {
  display: block;
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
  line-height: 1.2;
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
.expected-refund-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.5;
}
.expected-refund-overdue {
  color: #f56c6c;
  font-weight: 600;
}
.expected-refund-overdue-hint {
  color: #f56c6c;
  font-size: 12px;
}
.sales-person-contact {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
}

/* 结算弹窗：发票上传长文件名不撑破弹窗，悬停可看完整名（浏览器原生 title） */
.settlement-invoice-upload-item :deep(.el-form-item__content) {
  min-width: 0;
  max-width: 100%;
}
.settlement-invoice-upload {
  width: 100%;
  max-width: 100%;
}
.settlement-invoice-upload :deep(.el-upload-list) {
  width: 100%;
}
.settlement-invoice-upload :deep(.el-upload-list__item) {
  margin-top: 10px;
  overflow: hidden;
}
.settlement-invoice-upload :deep(.el-upload-list__item-file-name),
.settlement-invoice-upload :deep(.el-upload-list__item-name) {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: inline-block;
  vertical-align: bottom;
}
.settlement-invoice-filename {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
  word-break: break-all;
  max-width: 100%;
}
.settlement-field-hint {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}
.settlement-invoice-ocr-result {
  width: 100%;
  margin: 2px 0 4px;
  padding: 14px 16px;
  border-radius: 10px;
  background: linear-gradient(180deg, #f8fafc 0%, #f5f7fa 100%);
  border: 1px solid #ebeef5;
  box-sizing: border-box;
  color: #606266;
}
.settlement-invoice-ocr-result__title {
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.settlement-invoice-ocr-result__source {
  font-size: 12px;
  font-weight: 500;
  color: #409eff;
  background: #ecf5ff;
  border-radius: 999px;
  padding: 2px 8px;
  line-height: 20px;
}
.settlement-invoice-ocr-result__row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  line-height: 1.6;
  flex-wrap: wrap;
}
.settlement-invoice-ocr-result__row + .settlement-invoice-ocr-result__row {
  margin-top: 10px;
}
.settlement-invoice-ocr-result__label {
  min-width: 52px;
  color: #909399;
}
.settlement-invoice-ocr-result__value {
  flex: 1;
  min-width: 0;
  color: #303133;
  word-break: break-all;
}
.settlement-invoice-ocr-result__tag {
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 22px;
}
.settlement-invoice-ocr-result__tag.is-match {
  background: #edf9f0;
  color: #2f9b59;
}
.settlement-invoice-ocr-result__tag.is-mismatch {
  background: #fef0f0;
  color: #f56c6c;
}
</style>
