<template>  <div class="order-list-container">
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
          <el-select v-model="filterForm.status" placeholder="选择状态" clearable style="width: 120px">
            <el-option label="待指派" value="待指派" />
            <el-option label="待确认订单" value="待确认订单" />
            <el-option label="待合同盖章" value="待合同盖章" />
            <el-option label="待发货" value="待发货" />
            <el-option label="已发货" value="已发货" />
            <el-option label="已退回" value="已退回" />
          </el-select>
        </el-form-item>
        <el-form-item label="平台回款状态">
          <el-select v-model="filterForm.platformRefundStatus" placeholder="全部" clearable style="width: 120px">
            <el-option label="未回款" value="未回款" />
            <el-option label="部分回款" value="部分回款" />
            <el-option label="已回款" value="已回款" />
          </el-select>
        </el-form-item>
        <el-form-item label="线下销售">
          <el-select v-model="filterForm.offlineSales" placeholder="全部" clearable style="width: 100px">
            <el-option label="是" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="待妥投">
          <el-select v-model="filterForm.needReceiptSlip" placeholder="全部" clearable style="width: 100px">
            <el-option label="是(需签收单)" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据列表 -->
    <el-card class="table-card">
      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新建销售订单</el-button>
        <el-button type="success" plain @click="handleExport">批量导出</el-button>
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

      <el-table :key="listRefreshKey" :data="tableData" border style="width: 100%" stripe size="small" v-loading="loading">
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
                  <el-dropdown-item v-if="!isWarehouseRole && (scope.row.status === '待指派' || scope.row.status === '待确认订单')" @click="handleAudit(scope.row)">
                    <el-icon><Check /></el-icon>
                    <span>确认订单</span>
                  </el-dropdown-item>
                  <el-dropdown-item @click="handleShipment(scope.row)">
                    <el-icon><Van /></el-icon>
                    <span>发货</span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="!isWarehouseRole && !isAssignedUserForOrder(scope.row)" @click="handleSettlement(scope.row)">
                    <el-icon><Money /></el-icon>
                    <span>结算</span>
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
        <el-table-column v-if="shouldShowColumn('status') && orderedColumns.find(c => c.label === 'status')?.visible" prop="status" label="状态" width="90" align="center">
          <template #default="scope">
            <el-tooltip v-if="scope.row.status === '已退回' && scope.row.returnReason" :content="scope.row.returnReason" placement="top">
              <el-tag :type="getStatusTag(scope.row.status)">{{ scope.row.status }}</el-tag>
            </el-tooltip>
            <el-tag v-else :type="getStatusTag(scope.row.status)">{{ scope.row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('deliveryNote') && orderedColumns.find(c => c.label === 'deliveryNote')?.visible" label="送货单" width="120" align="center">
          <template #default="scope">
            <template v-if="scope.row.deliveryNoteUrl">
              <div style="display: flex; flex-direction: column; align-items: center;">
                <el-button link type="primary" size="small" @click="previewFile(scope.row.deliveryNoteUrl)">
                  <el-icon><Document /></el-icon>
                  查看
                </el-button>
              </div>
            </template>
            <span v-else style="color: #c0c4cc;">-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('receiptSlip') && orderedColumns.find(c => c.label === 'receiptSlip')?.visible" label="签收单" width="120" align="center">
          <template #default="scope">
            <template v-if="scope.row.receiptUrl">
              <div style="display: flex; flex-direction: column; align-items: center;">
                <el-button link type="primary" size="small" @click="previewFile(scope.row.receiptUrl)">
                  <el-icon><Document /></el-icon>
                  查看
                </el-button>
              </div>
            </template>
            <span v-else style="color: #c0c4cc;">-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('boxMark') && orderedColumns.find(c => c.label === 'boxMark')?.visible" label="箱唛" width="120" align="center">
          <template #default="scope">
            <template v-if="scope.row.boxLabelUrls">
              <div style="display: flex; flex-direction: column; align-items: center;">
                <el-popover placement="top" trigger="click" width="200">
                  <template #reference>
                    <el-button link type="primary" size="small">
                      <el-icon><Picture /></el-icon>
                      查看 ({{ scope.row.boxLabelUrls.split(',').length }})
                    </el-button>
                  </template>
                  <div style="display: flex; flex-direction: column; gap: 8px;">
                    <el-button 
                      v-for="(url, index) in scope.row.boxLabelUrls.split(',')" 
                      :key="index"
                      link 
                      type="primary" 
                      size="small"
                      @click="previewFile(url)"
                    >
                      箱唛 {{ index + 1 }}
                    </el-button>
                  </div>
                </el-popover>
              </div>
            </template>
            <span v-else style="color: #c0c4cc;">-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('snCode') && orderedColumns.find(c => c.label === 'snCode')?.visible" label="SN编码" prop="snCode" width="180">
          <template #default="scope">
            {{ scope.row.snCode || '-' }}
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('trackingNumber') && orderedColumns.find(c => c.label === 'trackingNumber')?.visible" label="物流单号" prop="trackingNumber" width="150">
          <template #default="scope">
            <template v-if="scope.row.trackingNumber">
              <el-button link type="primary" size="small" @click="showLogisticsInfo(scope.row)">
                {{ scope.row.trackingNumber }}
              </el-button>
            </template>
            <span v-else style="color: #c0c4cc;">-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('platformName') && orderedColumns.find(c => c.label === 'platformName')?.visible" prop="platformName" label="甲方抬头" width="120" />
        <el-table-column v-if="shouldShowColumn('platformOrderNo') && orderedColumns.find(c => c.label === 'platformOrderNo')?.visible" label="甲方订单号" width="180" show-overflow-tooltip>
          <template #default="scope">
            <div style="display: flex; flex-direction: column;">
              <span style="font-weight: 500;">{{ scope.row.platformOrderNo }}</span>
              <span v-if="getPlatformOrderIndex(scope.row) > 0" style="font-size: 12px; color: #909399;">
                第 {{ getPlatformOrderIndex(scope.row) + 1 }} / {{ getPlatformOrderCount(scope.row) }} 个商品
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('omsOrderNo') && orderedColumns.find(c => c.label === 'omsOrderNo')?.visible" prop="omsOrderNo" label="工业电商销售订单号" width="180" />
        <el-table-column v-if="shouldShowColumn('createTime') && orderedColumns.find(c => c.label === 'createTime')?.visible" prop="createTime" label="创建时间" width="160" show-overflow-tooltip />
        <el-table-column v-if="shouldShowColumn('orderDate') && orderedColumns.find(c => c.label === 'orderDate')?.visible" prop="orderDate" label="订单时间" width="110" />
        <el-table-column v-if="shouldShowColumn('materialNo') && orderedColumns.find(c => c.label === 'materialNo')?.visible" prop="materialNo" label="物料号" width="120" />
        <el-table-column v-if="shouldShowColumn('platformSku') && orderedColumns.find(c => c.label === 'platformSku')?.visible" label="商品信息" width="260">
          <template #default="scope">
            <div style="display: flex; flex-direction: column;">
              <div style="display: flex; align-items: center; gap: 8px;">
                <span style="font-weight: 500; color: #303133;">{{ scope.row.platformSku || '-' }}</span>
                <el-tag v-if="scope.row.quantity > 1" type="warning" size="small" effect="light">×{{ scope.row.quantity }}</el-tag>
              </div>
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
        <el-table-column v-if="shouldShowColumn('invoiceTitle') && orderedColumns.find(c => c.label === 'invoiceTitle')?.visible" label="发票信息" width="200">
          <template #default="scope">
            <div style="display: flex; flex-direction: column;">
              <span style="font-weight: 500; color: #303133; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;" :title="scope.row.invoiceTitle">{{ scope.row.invoiceTitle || '-' }}</span>
              <span style="font-size: 12px; color: #909399; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;" :title="scope.row.invoiceNumber">{{ scope.row.invoiceNumber ? '发票号: ' + scope.row.invoiceNumber : '' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('ecommerceSalesName') && orderedColumns.find(c => c.label === 'ecommerceSalesName')?.visible" prop="ecommerceSalesName" label="业务员" width="100" />
        <el-table-column v-if="shouldShowColumn('paymentMethod') && orderedColumns.find(c => c.label === 'paymentMethod')?.visible" prop="paymentMethod" label="支付方式" width="100" />
        <el-table-column v-if="shouldShowColumn('paymentStatus') && orderedColumns.find(c => c.label === 'paymentStatus')?.visible" prop="paymentStatus" label="支付状态" width="100" />
        <el-table-column v-if="shouldShowColumn('platformRefundStatus') && orderedColumns.find(c => c.label === 'platformRefundStatus')?.visible" prop="platformRefundStatus" label="平台回款状态" width="120" />
        <el-table-column v-if="shouldShowColumn('orderType') && orderedColumns.find(c => c.label === 'orderType')?.visible" prop="orderType" label="订单类型" width="100" />
        <el-table-column v-if="shouldShowColumn('receiverName') && orderedColumns.find(c => c.label === 'receiverName')?.visible" label="物流信息" width="200">
          <template #default="scope">
            <div style="display: flex; flex-direction: column;">
              <span style="font-weight: 500; color: #303133;">{{ scope.row.receiverName || '-' }}</span>
              <span style="font-size: 12px; color: #909399; margin-top: 2px;">{{ scope.row.receiverPhone || '' }}</span>
              <span style="font-size: 11px; color: #c0c4cc; margin-top: 1px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;" :title="scope.row.receiverAddress">{{ scope.row.receiverAddress || '' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="shouldShowColumn('settlementNo') && orderedColumns.find(c => c.label === 'settlementNo')?.visible" prop="settlementNo" label="对账单号" width="160" show-overflow-tooltip />
        <el-table-column v-if="shouldShowColumn('receiptStatus') && orderedColumns.find(c => c.label === 'receiptStatus')?.visible" prop="receiptStatus" label="签收状态" width="100" />
        <el-table-column v-if="shouldShowColumn('offlineSales') && orderedColumns.find(c => c.label === 'offlineSales')?.visible" prop="offlineSales" label="线下销售" width="120" />
        <el-table-column v-if="shouldShowColumn('offlineContractNo') && orderedColumns.find(c => c.label === 'offlineContractNo')?.visible" prop="offlineContractNo" label="线下销售合同号" width="150" />
        <el-table-column v-if="shouldShowColumn('offlineShippingPrice') && orderedColumns.find(c => c.label === 'offlineShippingPrice')?.visible" prop="offlineShippingPrice" label="线下销售出货价" width="130" align="right" />
        <el-table-column v-if="shouldShowColumn('receiptTime') && orderedColumns.find(c => c.label === 'receiptTime')?.visible" prop="receiptTime" label="签收时间" width="160" />
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
                <el-tag :type="getStatusTag(orderForm.status)" size="small" effect="dark">{{ orderForm.status || '待审核' }}</el-tag>
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
                    placeholder="请选择或输入"
                    style="width: 100%"
                    @blur="onPlatformNameBlur"
                  >
                    <el-option v-for="item in platformOptions" :key="item" :label="item" :value="item" />
                  </el-select>
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
                    <el-option v-for="user in salesUsers" :key="user.id" :label="user.realName" :value="user.id" />
                  </el-select>
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
                  <div v-if="orderForm.contractUrl" style="margin-top: 10px;">
                    <el-button link type="primary" size="small" @click="previewNewOrderContract">预览合同</el-button>
                  </div>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>

          <!-- 商品表格 -->
          <el-divider content-position="left">商品明细</el-divider>
          <div style="margin-bottom: 10px;">
            <el-button type="primary" @click="addProduct" size="small">
              <el-icon><Plus /></el-icon>
              添加商品
            </el-button>
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
            <el-table-column prop="platformSku" label="平台SKU" min-width="120">
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
                <el-input v-model="scope.row.taxIncludedPrice" size="small" placeholder="请输入" :disabled="isAssignedUserEditing" @input="calculateProductTotal(scope.row)" />
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
            <el-button type="primary" @click="addLogistics" size="small">
              <el-icon><Plus /></el-icon>
              添加物流
            </el-button>
          </div>
          <el-table :data="logisticsList" border style="width: 100%;">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column label="操作" width="80" align="center">
              <template #default="scope">
                <el-button link type="danger" size="small" @click="removeLogistics(scope.$index)">删除</el-button>
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
            <el-table-column prop="productQuantity" label="发货数量" width="100">
              <template #default="scope">
                <el-input-number 
                  v-model="scope.row.productQuantity" 
                  :min="1" 
                  :max="productList[scope.row.productIndex]?.quantity || 9999" 
                  size="small" 
                  style="width: 100%;"
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
            <el-button type="primary" @click="addInvoice" size="small">
              <el-icon><Plus /></el-icon>
              添加发票
            </el-button>
          </div>
          <el-table :data="invoiceList" border style="width: 100%;">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column label="操作" width="80" align="center">
              <template #default="scope">
                <el-button link type="danger" size="small" @click="removeInvoice(scope.$index)">删除</el-button>
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
            <el-table-column prop="invoiceTitle" label="甲方发票抬头" min-width="180">
              <template #default="scope">
                <el-select v-model="scope.row.invoiceTitle" filterable allow-create default-first-option size="small" style="width: 100%;" @blur="(e: FocusEvent) => onFilterableSelectBlur(e, (v) => { scope.row.invoiceTitle = v }, invoiceTitleOptions, saveMemoryOptions)">
                  <el-option v-for="(item, index) in platformPartnerList" :key="'partner-' + item.id" :label="item.title" :value="item.title" />
                  <el-option v-for="(item, index) in invoiceTitleOptions" :key="'memory-' + index" :label="item" :value="item" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="finalCustomerTitle" label="最终客户抬头" min-width="180">
              <template #default="scope">
                <el-select v-model="scope.row.finalCustomerTitle" filterable allow-create default-first-option size="small" style="width: 100%;" @blur="(e: FocusEvent) => onFilterableSelectBlur(e, (v) => { scope.row.finalCustomerTitle = v }, finalCustomerTitleOptions, saveMemoryOptions)">
                  <el-option v-for="item in finalCustomerTitleOptions" :key="item" :label="item" :value="item" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="paymentMethod" label="支付方式" min-width="100">
              <template #default="scope">
                <el-select v-model="scope.row.paymentMethod" filterable allow-create default-first-option size="small" style="width: 100%;" @blur="(e: FocusEvent) => onFilterableSelectBlur(e, (v) => { scope.row.paymentMethod = v }, paymentMethodOptions, saveMemoryOptions)">
                  <el-option v-for="item in paymentMethodOptions" :key="item" :label="item" :value="item" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column prop="invoiceNumber" label="发票号码" min-width="150">
              <template #default="scope">
                <el-input v-model="scope.row.invoiceNumber" size="small" placeholder="请输入" />
              </template>
            </el-table-column>
            <el-table-column prop="platformRefundStatus" label="平台回款状态" width="120">
              <template #default="scope">
                <el-select v-model="scope.row.platformRefundStatus" size="small" style="width: 100%;" :disabled="!canEditPlatformRefund">
                  <el-option label="未回款" value="未回款" />
                  <el-option label="部分回款" value="部分回款" />
                  <el-option label="已回款" value="已回款" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="发票文件" width="150">
              <template #default="scope">
                <el-upload
                  class="upload-demo"
                  action="/api/upload"
                  :on-success="(res, file) => handleInvoiceItemUploadSuccess(res, file, scope.$index)"
                  :before-upload="beforeUpload"
                  :file-list="scope.row.invoiceUrl ? [{ name: '发票文件', url: scope.row.invoiceUrl }] : []"
                  :limit="1"
                >
                  <el-button type="primary" size="small">上传</el-button>
                </el-upload>
                <el-button v-if="scope.row.invoiceUrl" link type="primary" size="small" @click="previewInvoice(scope.row.invoiceUrl)">预览</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="对账明细" name="reconciliation">
          <!-- 对账明细表格 -->
          <div style="margin-bottom: 10px;">
            <el-button type="primary" @click="addReconciliation" size="small">
              <el-icon><Plus /></el-icon>
              添加对账
            </el-button>
          </div>
          <el-table :data="reconciliationList" border style="width: 100%;">
            <el-table-column type="index" label="序号" width="60" align="center" />
            <el-table-column label="操作" width="80" align="center">
              <template #default="scope">
                <el-button link type="danger" size="small" @click="removeReconciliation(scope.$index)">删除</el-button>
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
            <el-table-column prop="settlementNo" label="结算单号" min-width="150">
              <template #default="scope">
                <el-input v-model="scope.row.settlementNo" size="small" placeholder="请输入" />
              </template>
            </el-table-column>
            <el-table-column prop="platformReconciliationNo" label="平台对账单号" min-width="150">
              <template #default="scope">
                <el-input v-model="scope.row.platformReconciliationNo" size="small" placeholder="请输入" />
              </template>
            </el-table-column>
            <el-table-column prop="receiptTime" label="签收时间" width="160">
              <template #default="scope">
                <el-date-picker v-model="scope.row.receiptTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="请选择" style="width: 100%;" size="small" />
              </template>
            </el-table-column>
            <el-table-column prop="offlineSales" label="线下销售" min-width="120">
              <template #default="scope">
                <el-input v-model="scope.row.offlineSales" size="small" placeholder="请输入" />
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
    >
      <el-form :model="shipmentForm" label-width="140px" label-position="right">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="平台送货单模板">
              <el-upload
                class="upload-demo"
                action="/api/upload"
                :on-success="handleDeliveryNoteUploadSuccess"
                :before-upload="beforeUpload"
                :limit="1"
              >
                <el-button type="primary">上传文件</el-button>
                <template #tip>
                  <div class="el-upload__tip">请上传平台送货单模板</div>
                </template>
              </el-upload>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="需要签收单回传">
              <el-switch v-model="shipmentForm.needReceiptReturn" />
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

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="SN编码">
              <el-input v-model="shipmentForm.snCode" placeholder="请输入SN编码" />
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
                平台SKU: {{ shipmentForm.platformSku }}
              </div>
            </div>
          </el-col>
        </el-row>

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
        </el-row>

        <template v-if="shipmentForm.deliveryMethod === '商家联系物流'">
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
              <el-form-item label="物流单号">
                <el-input v-model="shipmentForm.trackingNumber" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <template v-if="shipmentForm.deliveryMethod === '自主车辆配送'">
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

        <template v-if="shipmentForm.needReceiptReturn">
          <el-divider content-position="left">签收单</el-divider>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="签收时间">
                <el-date-picker v-model="shipmentForm.receiptTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item label="上传签收单">
                <el-upload
                  class="upload-demo"
                  action="/api/upload"
                  :on-success="handleReceiptUploadSuccess"
                  :before-upload="beforeUpload"
                  :limit="1"
                >
                  <el-button type="primary">上传文件</el-button>
                  <template #tip>
                    <div class="el-upload__tip">请上传签收单</div>
                  </template>
                </el-upload>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="shipmentDialogVisible = false">取消</el-button>
          <el-button type="success" @click="syncShipment">同步</el-button>
          <el-button type="primary" @click="submitShipment">确定</el-button>
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
            <el-form-item label="结算单号" prop="settlementNo">
              <el-input v-model="settlementForm.settlementNo" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="平台对账单号" prop="platformReconciliationNo">
              <el-input v-model="settlementForm.platformReconciliationNo" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="发票号码" prop="invoiceNumber">
              <el-input v-model="settlementForm.invoiceNumber" placeholder="请输入发票号码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="平台回款状态" prop="platformRefundStatus">
              <el-select v-model="settlementForm.platformRefundStatus" style="width: 100%" :disabled="!canEditPlatformRefund">
                <el-option label="未回款" value="未回款" />
                <el-option label="部分回款" value="部分回款" />
                <el-option label="已回款" value="已回款" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="发票上传" prop="invoiceUrl">
              <el-upload
                class="upload-demo"
                action="/api/upload"
                :on-success="handleSettlementInvoiceUploadSuccess"
                :before-upload="beforeUpload"
                :file-list="settlementInvoiceFileList"
                :limit="1"
              >
                <el-button type="primary">上传文件</el-button>
                <template #tip>
                  <div class="el-upload__tip">支持上传图片、PDF、Word、Excel文件</div>
                </template>
              </el-upload>
              <el-button v-if="settlementForm.invoiceUrl" link type="primary" @click="previewInvoice(settlementForm.invoiceUrl)">预览发票</el-button>
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

        <el-row :gutter="20" v-if="currentContractOrder?.contractUrl">
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
      title="快递物流信息"
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, CircleCheck, MoreFilled, Edit, Check, Van, UserFilled, Document, Money, Plus, Delete, Picture } from '@element-plus/icons-vue'
import request from '../../utils/request'
import { apiBase } from '@/utils/apiBase'
import draggable from 'vuedraggable'

const loading = ref(false)
const currentUsername = ref(localStorage.getItem('username') || '')
const route = useRoute()
const filterForm = reactive({
  platformOrderNo: '',
  omsOrderNo: '',
  status: '',
  platformRefundStatus: '' as string,
  offlineSales: '' as string,
  needReceiptSlip: '' as string
})
const canEditPlatformRefund = computed(() => {
  const p = localStorage.getItem('permissions') || ''
  return p.split(',').map((s: string) => s.trim()).includes('platform_refund') || localStorage.getItem('username') === 'admin'
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
  'deliveryNote',
  'receiptSlip',
  'boxMark',
  'snCode',
  'trackingNumber',
  'platformName',
  'platformOrderNo',
  'omsOrderNo',
  'orderDate',
  'materialNo',
  'platformSku',
  'model',
  'quantity',
  'taxIncludedPrice',
  'taxIncludedTotal',
  'invoiceTitle',
  'paymentMethod',
  'orderType',
  'receiverName',
  'receiverPhone',
  'receiverAddress',
  'purchaseOrderNo',
  'offlineSales',
  'offlineContractNo',
  'offlineShippingPrice',
  'deliveryDate',
  'logisticsNo',
  'receiptStatus',
  'receiptTime',
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

const alwaysVisibleColumns = ['操作', '送货单', '箱唛']

const isAssignedUserForOrder = (row: any) => {
  if (row.assignedUsername && row.assignedUsername === currentUsername.value) {
    return true
  }
  return false
}

// 是否可退回：当前用户为订单被指派方，或当前用户所属公司等于订单交付方（如同一公司多账号如热像科技-商务）
const canReturnOrder = (row: any) => {
  if (isAssignedUserForOrder(row)) return true
  const companyTitle = (localStorage.getItem('companyTitle') || '').trim()
  if (companyTitle && row.deliveryParty && (row.deliveryParty + '').trim()) {
    if ((row.deliveryParty + '').trim() === companyTitle) return true
    if ((row.deliveryParty + '').trim().indexOf(companyTitle) >= 0 || companyTitle.indexOf((row.deliveryParty + '').trim()) >= 0) return true
  }
  return false
}

// 当前是否为「被指派方」在编辑（用于禁止修改指派方推送的交付方采购价/扣点/含税价）
const isAssignedUserEditing = computed(() =>
  !!editingOrderRow.value && currentUsername.value === editingOrderRow.value.assignedUsername
)

const isAssignedUserOnly = computed(() => {
  if (tableData.value.length === 0) return false
  return tableData.value.every((row: any) => isAssignedUserForOrder(row))
})

const shouldShowColumn = (columnLabel: string) => {
  if (!isAssignedUserOnly.value) return true
  return assignedUserVisibleColumns.includes(columnLabel)
}

// 甲方抬头默认兜底（无平台方数据时）；有数据时以用户信息维护-平台方为准
const DEFAULT_PLATFORM_OPTIONS = ['震坤行', '西域', '京东工业', '京东世纪贸易', '领先未来', '科力普', '浙江宏伟', '欧菲斯', '鑫方盛', '阳采史泰博', '得力', '浙江物产', '嘉立创', '淘宝', '喀斯玛']
const platformOptions = ref<string[]>([...DEFAULT_PLATFORM_OPTIONS])
const platformNameSelectRef = ref<any>(null)
const salesUsers = ref<any[]>([])
const partnerInfoList = ref<any[]>([])
const products = ref<any[]>([])
const tableData = ref([])
const total = ref(0)
const listRefreshKey = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// 记忆功能选项
const invoiceTitleOptions = ref<string[]>([])
const finalCustomerTitleOptions = ref<string[]>([])
const receiverNameOptions = ref<string[]>([])
const receiverPhoneOptions = ref<string[]>([])
const paymentMethodOptions = ref<string[]>(['账期', '背靠背', '全款'])
const deliveryPartyOptions = ref<string[]>([])
const shippingPartyOptions = ref<string[]>([])

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

// 当天订单计数
const dailyOrderCount = ref(0)

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
      deliveryPartyOptions.value = options.deliveryParty || []
      shippingPartyOptions.value = options.shippingParty || []
    } catch {
      console.error('Load memory options error:', error)
    }
  }
  
  // 加载当天订单计数
  const today = new Date().toDateString()
  const savedDate = localStorage.getItem('lastOrderDate')
  if (savedDate === today) {
    const savedCount = localStorage.getItem('dailyOrderCount')
    if (savedCount) {
      dailyOrderCount.value = parseInt(savedCount) || 0
    }
  } else {
    dailyOrderCount.value = 0
    localStorage.setItem('lastOrderDate', today)
    localStorage.setItem('dailyOrderCount', '0')
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
    deliveryParty: deliveryPartyOptions.value,
    shippingParty: shippingPartyOptions.value
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
    default:
      return
  }
  if (!options.includes(value)) {
    options.push(value)
    saveMemoryOptions()
  }
}

// 甲方抬头：失焦时提交到 v-model；若为新输入则加入本地选项并保存到用户信息维护（平台方）
async function onPlatformNameBlur() {
  const sel = platformNameSelectRef.value
  const input = sel?.$el?.querySelector?.('input')
  const text = input?.value?.trim()
  if (!text) return
  orderForm.platformName = text
  if (!platformOptions.value.includes(text)) {
    platformOptions.value = [...platformOptions.value, text]
    try {
      await request.post('/partner-info', {
        name: text,
        title: text,
        identities: '平台方'
      })
      ElMessage.success('已保存到用户信息维护（平台方）')
    } catch (e) {
      console.error('保存平台方抬头失败:', e)
      ElMessage.warning('已填入当前订单，但保存到用户信息维护失败，下次请从合作管理维护')
    }
  }
}

// 通用：任意 filterable allow-create 下拉失焦时提交输入到 row 并加入记忆
function onFilterableSelectBlur(
  e: FocusEvent,
  setValue: (v: string) => void,
  optionsRef: { value: string[] },
  saveMemory?: () => void
) {
  const text = (e.target as HTMLInputElement)?.value?.trim()
  if (text) {
    setValue(text)
    if (!optionsRef.value.includes(text)) {
      optionsRef.value = [...optionsRef.value, text]
      saveMemory?.()
    }
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

const fetchUsers = async () => {
  try {
    const res: any = await request.get('/users', { 
      params: { 
        role: 'ROLE_ECOMMERCE',
        page: 0,
        size: 1000
      } 
    })
    const content = res?.content || (Array.isArray(res) ? res : [])
    salesUsers.value = content
    console.log('Fetched sales users:', content)
    console.log('Full response:', res)
  } catch (error) {
    console.error('Fetch users error:', error)
  }
}

const fetchProducts = async () => {
  try {
    const res: any = await request.get('/products?page=0&size=1000')
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
    const res: any = await request.get('/partner-info?page=0&size=1000')
    const content = res?.content || (Array.isArray(res) ? res : [])
    partnerInfoList.value = content
    const platformTitles = (platformPartnerList.value || []).map((p: any) => (p.title || '').trim()).filter((t: string) => t)
    platformOptions.value = [...new Set([...platformTitles, ...DEFAULT_PLATFORM_OPTIONS])]
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
      page: currentPage.value - 1,
      size: pageSize.value,
      sort: 'createTime,desc'
    }
    if (!params.needReceiptSlip) delete params.needReceiptSlip
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

const getPlatformOrderIndex = (row: any) => {
  if (!tableData.value || !row.platformOrderNo) return 0
  const samePlatformOrders = tableData.value.filter((o: any) => o.platformOrderNo === row.platformOrderNo)
  return samePlatformOrders.findIndex((o: any) => o.id === row.id)
}

const getPlatformOrderCount = (row: any) => {
  if (!tableData.value || !row.platformOrderNo) return 1
  return tableData.value.filter((o: any) => o.platformOrderNo === row.platformOrderNo).length
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

const mockLogisticsTraces = [
  { time: '2025-11-14 17:57:35', status: '已签收', desc: '您的包裹已签收，感谢您使用本物流，期待下一次为您服务！您专心工作，琐事我来做！' },
  { time: '2025-11-14 10:03:35', status: '派件中', desc: '您的订单已由本人签收。感谢您在本平台购物，欢迎再次光临。' },
  { time: '2025-11-14 17:57:05', status: '派件中', desc: '您的包裹正在派送中，请保持电话畅通以便司机联系，收到货请清点无误后请完成签收(派件人: 宋敏 电话: 18725533183)' },
  { time: '2025-11-14 09:56:14', status: '在途中', desc: '您的订单已拣货打包完成，订单正在配送途中，请您耐心等待' },
  { time: '2025-11-10 21:10:47', status: '已揽收', desc: '您的包裹由【集团控股】网点完成揽收，等待物流运输。' },
  { time: '2025-11-10 17:57:02', status: '已揽收', desc: '您的包裹由【本物流】网点完成揽收，等待物流运输。' }
]

const showLogisticsInfo = async (row: any) => {
  currentLogisticsOrder.value = row
  let phone = row.receiverPhone
  if (!phone && row.id && (row.logisticsCompany === '顺丰' || row.logisticsCompany === '顺丰速运')) {
    try {
      const orderRes: any = await request.get(`/sales-orders/${row.id}`)
      phone = orderRes.receiverPhone || (orderRes.logistics && orderRes.logistics[0] && orderRes.logistics[0].receiverPhone)
      if (phone) currentLogisticsOrder.value = { ...row, receiverPhone: phone }
    } catch (_) { /* 忽略，下面用无手机号请求 */ }
  }
  await fetchLogisticsInfo(row.logisticsCompany, row.trackingNumber, phone)
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
    fetchLogisticsInfo(r.logisticsCompany, r.trackingNumber, r.receiverPhone)
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
  { label: 'status', title: '状态', width: 90, visible: true },
  { label: 'deliveryNote', title: '送货单', width: 120, visible: true },
  { label: 'receiptSlip', title: '签收单', width: 120, visible: true },
  { label: 'boxMark', title: '箱唛', width: 120, visible: true },
  { label: 'snCode', title: 'SN编码', width: 180, visible: true },
  { label: 'trackingNumber', title: '物流单号', width: 150, visible: true },
  { label: 'platformName', title: '甲方抬头', width: 120, visible: true },
  { label: 'platformOrderNo', title: '甲方订单号', width: 150, visible: true },
  { label: 'omsOrderNo', title: '工业电商销售订单号', width: 180, visible: true },
  { label: 'createTime', title: '创建时间', width: 160, visible: true },
  { label: 'orderDate', title: '订单时间', width: 110, visible: true },
  { label: 'materialNo', title: '物料号', width: 120, visible: true },
  { label: 'platformSku', title: '商品信息', width: 260, visible: true },
  { label: 'quantity', title: '数量', width: 80, visible: true },
  { label: 'taxIncludedPrice', title: '含税单价', width: 110, visible: true },
  { label: 'taxIncludedTotal', title: '含税总价', width: 110, visible: true },
  { label: 'invoiceTitle', title: '发票信息', width: 200, visible: true },
  { label: 'ecommerceSalesName', title: '业务员', width: 100, visible: true },
  { label: 'paymentMethod', title: '支付方式', width: 100, visible: true },
  { label: 'paymentStatus', title: '支付状态', width: 100, visible: true },
  { label: 'platformRefundStatus', title: '平台回款状态', width: 120, visible: true },
  { label: 'orderType', title: '订单类型', width: 100, visible: true },
  { label: 'receiverName', title: '物流信息', width: 200, visible: true },
  { label: 'settlementNo', title: '对账单号', width: 160, visible: true },
  { label: 'receiptStatus', title: '签收状态', width: 100, visible: true },
  { label: 'offlineSales', title: '线下销售', width: 120, visible: true },
  { label: 'offlineContractNo', title: '线下销售合同号', width: 150, visible: true },
  { label: 'offlineShippingPrice', title: '线下销售出货价', width: 130, visible: true },
  { label: 'receiptTime', title: '签收时间', width: 160, visible: true },
  { label: 'deliveryDate', title: '交货日期', width: 120, visible: true },
  { label: 'returnInfo', title: '退回信息', width: 220, visible: true }
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

// 加载本地存储的列设置
const loadColumnSettings = () => {
  const saved = localStorage.getItem('orderListColumns')
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
    } catch {
      orderedColumns.value = [...allColumns.value]
    }
  }
}

// 保存列设置到本地存储
const saveColumns = () => {
  const visible = visibleColumns.value
  const columnsOrder = orderedColumns.value.map(col => col.label)
  
  localStorage.setItem('orderListColumns', JSON.stringify({
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

onMounted(() => {
  const q = route.query
  if (q.status) filterForm.status = String(q.status)
  if (q.platformRefundStatus) filterForm.platformRefundStatus = String(q.platformRefundStatus)
  if (q.offlineSales) filterForm.offlineSales = String(q.offlineSales)
  if (q.filter === 'receipt') {
    filterForm.needReceiptSlip = '1'
    if (!filterForm.status) filterForm.status = '已发货'
  }
  loadColumnSettings()
  fetchUsers()
  fetchOrders()
})

const handleSearch = () => {
  currentPage.value = 1
  fetchOrders()
}

const resetSearch = () => {
  Object.assign(filterForm, {
    platformOrderNo: '',
    omsOrderNo: '',
    status: '',
    platformRefundStatus: '',
    offlineSales: '',
    needReceiptSlip: ''
  })
  handleSearch()
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

// 订单基本信息
const orderForm = reactive({
  platformName: '',
  platformOrderNo: '',
  omsOrderNo: '',
  orderDate: '',
  deliveryDate: '',
  platformSku: '',
  model: '',
  productConfig: '',
  warrantyPeriod: '',
  quantity: 1,
  taxIncludedPrice: '',
  taxIncludedTotal: '',
  invoiceTitle: '',
  finalCustomerTitle: '',
  operationEntityTitle: '',
  ecommerceSalesId: '',
  paymentMethod: '',
  receiverName: '',
  receiverPhone: '',
  receiverAddress: '',
  deliveryParty: '',
  shippingParty: '',
  orderType: '第三方订单',
  settlementNo: '',
  platformReconciliationNo: '',
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

// 添加商品
const addProduct = () => {
  const newIndex = productList.value.length
  productList.value.push({
    orderType: '第三方订单',
    omsOrderNo: '',
    platformSku: '',
    model: '',
    productConfig: '',
    warrantyPeriod: '',
    quantity: 1,
    taxIncludedPrice: '',
    taxIncludedTotal: ''
  })
  // 自动为新商品生成OMS订单号
  generateOmsOrderNo(newIndex)
}

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

// 处理商品型号变化，自动从商品库带出信息
const handleProductModelChange = (product: any) => {
  const selectedProduct = products.value.find((p: any) => p.model === product.model)
  if (selectedProduct) {
    if (selectedProduct.materialNo) {
      product.materialNo = selectedProduct.materialNo
    }
    if (selectedProduct.price) {
      product.taxIncludedPrice = selectedProduct.price.toString()
      calculateProductTotal(product)
    }
    if (selectedProduct.productConfig) {
      product.productConfig = selectedProduct.productConfig
    }
    if (selectedProduct.warrantyPeriod) {
      product.warrantyPeriod = selectedProduct.warrantyPeriod
    }
  }
}

// 删除商品
const removeProduct = (index: number) => {
  productList.value.splice(index, 1)
}

// 计算商品总价
const calculateProductTotal = (product: any) => {
  if (product.quantity && product.taxIncludedPrice) {
    product.taxIncludedTotal = (parseFloat(product.quantity) * parseFloat(product.taxIncludedPrice)).toFixed(2)
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
    return sum + (parseFloat(product.taxIncludedTotal) || 0)
  }, 0).toFixed(2)
}

// 添加物流
const addLogistics = () => {
  const usedProductIndexes = logisticsList.value.map(l => l.productIndex)
  let productIndex = 0
  for (let i = 0; i < productList.value.length; i++) {
    if (!usedProductIndexes.includes(i)) {
      productIndex = i
      break
    }
  }
  
  logisticsList.value.push({
    productIndex: productIndex,
    productQuantity: 1,
    receiverName: '',
    receiverPhone: '',
    receiverAddress: '',
    deliveryParty: '',
    shippingParty: '',
    deliveryDate: ''
  })
}

// 删除物流
const removeLogistics = (index: number) => {
  logisticsList.value.splice(index, 1)
}

// 添加发票
const addInvoice = () => {
  const usedProductIndexes = invoiceList.value.map(inv => inv.productIndex)
  let productIndex = 0
  for (let i = 0; i < productList.value.length; i++) {
    if (!usedProductIndexes.includes(i)) {
      productIndex = i
      break
    }
  }
  
  invoiceList.value.push({
    productIndex: productIndex,
    invoiceTitle: '',
    finalCustomerTitle: '',
    operationEntityTitle: '',
    paymentMethod: '',
    invoiceNumber: '',
    invoiceUrl: '',
    platformRefundStatus: ''
  })
  
  addReconciliationFromInvoice(productIndex)
}

// 从发票添加对账
const addReconciliationFromInvoice = (productIndex: number | undefined) => {
  if (productIndex === undefined) return
  
  const product = productList.value[productIndex]
  if (!product) return
  
  const orderType = product.orderType || '第三方订单'
  const defaultDeductionRate = (orderType === '自营' || orderType === '自营订单') ? '20%' : '2%'

  const logisticsIndex = logisticsList.value.findIndex(l => l.productIndex === productIndex)
  
  const newReconciliation = {
    productIndex: productIndex,
    logisticsIndex: logisticsIndex >= 0 ? logisticsIndex : undefined,
    settlementNo: '',
    platformReconciliationNo: '',
    deductionRate: defaultDeductionRate,
    deliveryPartyPurchasePrice: '',
    receiptTime: '',
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
  const invoice = invoiceList.value[index]
  invoiceList.value.splice(index, 1)
  
  const reconciliationIndex = reconciliationList.value.findIndex(r => r.productIndex === invoice.productIndex)
  if (reconciliationIndex >= 0) {
    reconciliationList.value.splice(reconciliationIndex, 1)
  }
}

// 添加对账
const addReconciliation = () => {
  const productIndex = productList.value.length > 0 ? 0 : undefined
  const product = productIndex !== undefined ? productList.value[productIndex] : null
  const orderType = product?.orderType || '第三方订单'
  const defaultDeductionRate = (orderType === '自营' || orderType === '自营订单') ? '20%' : '2%'

  reconciliationList.value.push({
    productIndex: productIndex,
    logisticsIndex: logisticsList.value.length > 0 ? 0 : undefined,
    settlementNo: '',
    platformReconciliationNo: '',
    deductionRate: defaultDeductionRate,
    deliveryPartyPurchasePrice: '',
    receiptTime: '',
    offlineSales: '',
    offlineContractNo: '',
    offlineShippingPrice: ''
  })
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
  
  const taxIncludedTotal = parseFloat(product.taxIncludedTotal) || 0
  console.log('taxIncludedTotal:', taxIncludedTotal)
  const deductionRateStr = reconciliation.deductionRate?.toString().replace('%', '') || '2'
  console.log('deductionRateStr:', deductionRateStr)
  const deductionRate = parseFloat(deductionRateStr) / 100
  console.log('deductionRate:', deductionRate)
  
  const calculatedPrice = (taxIncludedTotal * (1 - deductionRate)).toFixed(2)
  console.log('calculatedPrice:', calculatedPrice)
  
  reconciliation.deliveryPartyPurchasePrice = calculatedPrice
}

// 删除对账
const removeReconciliation = (index: number) => {
  reconciliationList.value.splice(index, 1)
}

// 处理发票表格项上传成功
const handleInvoiceItemUploadSuccess = (response: any, file: any, index: number) => {
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

const rules = {
platformName: [{ required: true, message: '请选择甲方抬头', trigger: 'blur' }],
    platformOrderNo: [{ required: true, message: '请输入甲方订单号', trigger: 'blur' }],
  ecommerceSalesId: [{ required: true, message: '请选择业务员', trigger: 'blur' }],
  invoiceTitle: [{ required: true, message: '请输入发票抬头', trigger: 'blur' }],
  receiverName: [{ required: true, message: '请输入收货人', trigger: 'blur' }],
  receiverPhone: [{ required: true, message: '请输入收货人电话', trigger: 'blur' }],
  receiverAddress: [{ required: true, message: '请输入收货地址', trigger: 'blur' }]
  // 交付方、出货方已移至采购板块，销售侧不再校验
}

const handleAdd = async () => {
  dialogTitle.value = '新建销售订单'
  resetForm()
  // 设置默认订单日期为今天
  const today = new Date()
  orderForm.orderDate = today.getFullYear().toString() + '-' +
    (today.getMonth() + 1).toString().padStart(2, '0') + '-' +
    today.getDate().toString().padStart(2, '0')
  // 加载用户列表（业务员）
  await fetchUsers()
  console.log('salesUsers loaded:', salesUsers.value)
  // 重新设置业务员，因为现在salesUsers已经加载了
  const realName = localStorage.getItem('realName') || ''
  console.log('Current realName from localStorage:', realName)
  if (realName && salesUsers.value.length > 0) {
    const currentUser = salesUsers.value.find((u: any) => u.realName === realName)
    console.log('Found currentUser:', currentUser)
    if (currentUser) {
      orderForm.ecommerceSalesId = currentUser.id
      console.log('Set ecommerceSalesId to:', currentUser.id)
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
      const res: any = await request.get(`/sales-orders/${row.id}`)
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
    let salesIdEdit = r.ecommerceSalesId ?? r.ecommerceSales?.id
    if ((salesIdEdit == null || salesIdEdit === '') && r.ecommerceSalesName && salesUsers.value.length > 0) {
      const byName = salesUsers.value.find((u: any) => u.realName === r.ecommerceSalesName)
      if (byName) salesIdEdit = byName.id
    }
    orderForm.ecommerceSalesId = salesIdEdit != null && salesIdEdit !== '' ? Number(salesIdEdit) : ''
    
    let taxIncludedPrice = r.taxIncludedPrice || ''
    let taxIncludedTotal = r.taxIncludedTotal || ''
    
    productList.value = [{
      orderType: r.orderType || '第三方订单',
      omsOrderNo: r.omsOrderNo || '',
      platformSku: r.platformSku || '',
      model: r.model || '',
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
      paymentMethod: r.paymentMethod || '',
      invoiceNumber: r.invoiceNumber || '',
      invoiceUrl: r.invoiceUrl || '',
      platformRefundStatus: r.platformRefundStatus || ''
    }]
    
    reconciliationList.value = [{
      productIndex: 0,
      logisticsIndex: 0,
      settlementNo: r.settlementNo || '',
      platformReconciliationNo: r.platformReconciliationNo || '',
      deductionRate: r.deductionRate || '2%',
      deliveryPartyPurchasePrice: r.deliveryPartyPurchasePrice ?? '',
      receiptTime: r.receiptTime || '',
      offlineSales: r.offlineSales || '',
      offlineContractNo: r.offlineContractNo || '',
      offlineShippingPrice: r.offlineShippingPrice || ''
    }]
    
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
    
    if (orderForm.id) {
      let processedOrderForm = { ...orderForm }
      let procurementPreserve: { deliveryParty?: string; shippingParty?: string; deductionRate?: any; deliveryPartyPurchasePrice?: any } = {}
      try {
        const full: any = await request.get(`/sales-orders/${orderForm.id}`)
        procurementPreserve = {
          deliveryParty: full.deliveryParty,
          shippingParty: full.shippingParty,
          deductionRate: full.deductionRate,
          deliveryPartyPurchasePrice: full.deliveryPartyPurchasePrice
        }
      } catch (_) {}
      
      const firstProduct = productList.value[0]
      const firstLogistics = logisticsList.value[0]
      // 含税单价/总价始终以当前表单为准，避免 isAssigned 时用 orig（getOrder 返回的交付方采购价）覆盖用户刚改的 3950 等，导致「改不进列表」
      const submitData = {
        ...processedOrderForm,
        platformSku: firstProduct?.platformSku ?? processedOrderForm.platformSku,
        model: firstProduct?.model ?? processedOrderForm.model,
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
        deliveryDate: firstLogistics?.deliveryDate ?? processedOrderForm.deliveryDate,
        ecommerceSalesId: orderForm.ecommerceSalesId != null && orderForm.ecommerceSalesId !== '' ? Number(orderForm.ecommerceSalesId) : processedOrderForm.ecommerceSalesId,
        ecommerceSalesName: orderForm.ecommerceSalesName || (orderForm.ecommerceSalesId && salesUsers.value.find((u: any) => u.id === orderForm.ecommerceSalesId)?.realName) || processedOrderForm.ecommerceSalesName,
        operationEntityTitle: orderForm.operationEntityTitle || processedOrderForm.operationEntityTitle || '',
        products: productList.value,
        logistics: logisticsList.value,
        invoices: invoiceList.value,
        reconciliations: reconciliationList.value
      }
      
      console.log('Submitting data:', submitData)
      await request.put(`/sales-orders/${orderForm.id}`, submitData)
      ElMessage.success('订单更新成功')
      dialogVisible.value = false
      listRefreshKey.value++
      await fetchOrders(true)
    } else {
      const ordersToCreate = []
      
      for (let i = 0; i < productList.value.length; i++) {
        const product = productList.value[i]
        const productLogistics = logisticsList.value.filter(l => l.productIndex === i)
        const productInvoices = invoiceList.value.filter(inv => inv.productIndex === i)
        const productReconciliations = reconciliationList.value.filter(r => r.productIndex === i)
        
        const orderData = {
          ...orderForm,
          omsOrderNo: product.omsOrderNo,
          platformSku: product.platformSku,
          model: product.model,
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
        }
        // 自营订单扣点固定为 20%，不依赖对账明细（避免对账先创建为 2% 的情况）
        orderData.deductionRate = (product.orderType === '自营')
          ? (product.deductionRate || (orderForm as any).deductionRate || '20%')
          : (productReconciliations.length > 0 ? productReconciliations[0].deductionRate : (orderForm as any).deductionRate || '2%')

        ordersToCreate.push(orderData)
      }
      
      for (const orderData of ordersToCreate) {
        await request.post('/sales-orders', orderData)
      }
      
      ElMessage.success(`成功创建 ${ordersToCreate.length} 个订单`)
      dailyOrderCount.value += ordersToCreate.length
      localStorage.setItem('dailyOrderCount', dailyOrderCount.value.toString())
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
    if (!product.platformSku) {
      ElMessage.error(`请填写第${i + 1}个商品的平台SKU`)
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
  
  // 校验发票信息
  if (invoiceList.value.length === 0) {
    ElMessage.error('请至少添加一条发票信息')
    return false
  }
  
  for (let i = 0; i < invoiceList.value.length; i++) {
    const invoice = invoiceList.value[i]
    if (!invoice.paymentMethod) {
      ElMessage.error(`请选择第${i + 1}条发票的支付方式`)
      return false
    }
  }
  
  return true
}

const resetForm = () => {
  editingOrderRow.value = null
  const realName = localStorage.getItem('realName') || ''
  let salesId = ''
  if (realName && salesUsers.value.length > 0) {
    const currentUser = salesUsers.value.find((u: any) => u.realName === realName)
    if (currentUser) {
      salesId = currentUser.id
    }
  }
  Object.assign(orderForm, {
    platformName: '',
    platformOrderNo: '',
    omsOrderNo: '',
    orderDate: '',
    deliveryDate: '',
    platformSku: '',
    model: '',
    productConfig: '',
    warrantyPeriod: '',
    quantity: 1,
    taxIncludedPrice: '',
    taxIncludedTotal: '',
    invoiceTitle: '',
    finalCustomerTitle: '',
    operationEntityTitle: '',
    ecommerceSalesId: salesId,
    paymentMethod: '',
    receiverName: '',
    receiverPhone: '',
    receiverAddress: '',
    deliveryParty: '',
    shippingParty: '',
    orderType: '第三方订单',
    settlementNo: '',
    platformReconciliationNo: '',
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
  const price = parseFloat(orderForm.taxIncludedPrice) || 0
  const quantity = parseInt(orderForm.quantity.toString()) || 0
  orderForm.taxIncludedTotal = (price * quantity).toString()
  calculateDeliveryPartyPurchasePrice()
}

const calculateDeliveryPartyPurchasePrice = () => {
  const total = parseFloat(orderForm.taxIncludedTotal) || 0
  let deductionRate = 0.02
  
  if (orderForm.deductionRate) {
    const rateStr = orderForm.deductionRate.toString().replace('%', '')
    deductionRate = parseFloat(rateStr) / 100
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
    case '已支付':
      return 'success'
    case '平台已支付':
      return 'success'
    case '已结算':
      return 'success'
    case '已盖章':
      return 'success'
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
    
    if (result === 'confirm' || result === 'success') {
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
      uid: file.uid
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

const previewNewOrderContract = () => {
  if (orderForm.contractUrl) {
    const absoluteUrl = orderForm.contractUrl.startsWith('/') ? orderForm.contractUrl : `/${orderForm.contractUrl}`
    window.open(`${apiBase()}${absoluteUrl}`, '_blank')
  }
}

const previewContract = () => {
  let url = ''
  if (contractFileList.value.length > 0) {
    url = contractFileList.value[0].url
  } else if (currentContractOrder.value?.contractUrl) {
    url = currentContractOrder.value.contractUrl
  }
  
  if (url) {
    const absoluteUrl = url.startsWith('/') ? url : `/${url}`
    window.open(`${apiBase()}${absoluteUrl}`, '_blank')
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
    
    if (url) {
      const absoluteUrl = url.startsWith('/') ? url : `/${url}`
      const link = document.createElement('a')
      link.href = `${apiBase()}${absoluteUrl}`
      link.download = '合同文件'
      link.click()
      ElMessage.success('下载成功')
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
  } catch (error) {
    console.error('保存合同错误:', error)
    console.error('错误响应:', error.response)
    ElMessage.error('合同保存失败')
  }
}

// 发货相关变量
const shipmentDialogVisible = ref(false)
const currentShipmentOrder = ref<any>(null)
const boxLabelFileList = ref<any[]>([])
const shipmentForm = reactive({
  salesOrderId: null,
  deliveryNoteUrl: '',
  needReceiptReturn: false,
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
  snCode: ''
})

const handleShipment = async (row: any) => {
  currentShipmentOrder.value = row
  shipmentForm.salesOrderId = row.id
  shipmentForm.shippingAddress = row.receiverAddress || ''
  shipmentForm.shippingContact = row.receiverName || ''
  shipmentForm.shippingPhone = row.receiverPhone || ''
  shipmentForm.platformSku = row.platformSku || ''
  shipmentForm.boxLabelUrls = row.boxLabelUrls || ''
  shipmentForm.receiptTime = row.receiptTime || ''
  shipmentForm.snCode = row.snCode || ''
  shipmentForm.deliveryNoteUrl = row.deliveryNoteUrl || ''
  shipmentForm.receiptUrl = row.receiptUrl || ''
  shipmentForm.logisticsCompany = row.logisticsCompany || ''
  shipmentForm.trackingNumber = row.trackingNumber || ''
  
  if (row.boxLabelUrls) {
    boxLabelFileList.value = row.boxLabelUrls.split(',').map((url: string, index: number) => ({
      name: `箱唛${index + 1}`,
      url: url
    }))
  } else {
    boxLabelFileList.value = []
  }
  
  // 按订单拉取已有发货信息（无记录时后端返回 200+null 或 404，均不报错）
  try {
    const existingShipment = await request.get(`/order-shipments/sales-order/${row.id}`, {
      validateStatus: (s) => s === 200 || s === 404,
    }) as any
    if (existingShipment) {
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
  
  shipmentDialogVisible.value = true
}

const handleSettlement = (row: any) => {
  currentSettlementOrder.value = row
  settlementForm.settlementNo = row.settlementNo || ''
  settlementForm.platformReconciliationNo = row.platformReconciliationNo || ''
  settlementForm.invoiceNumber = row.invoiceNumber || ''
  settlementForm.invoiceUrl = row.invoiceUrl || ''
  settlementForm.platformRefundStatus = row.platformRefundStatus || ''
  
  if (row.invoiceUrl) {
    settlementInvoiceFileList.value = [{
      name: '发票文件',
      url: row.invoiceUrl
    }]
  } else {
    settlementInvoiceFileList.value = []
  }
  
  settlementDialogVisible.value = true
}

const handleDeliveryMethodChange = () => {
  shipmentForm.logisticsCompany = ''
  shipmentForm.trackingNumber = ''
  shipmentForm.vehiclePlate = ''
  shipmentForm.logisticsContact = ''
  shipmentForm.logisticsPhone = ''
}

const deliveryMethodSelectRef = ref<{ $el?: HTMLElement } | null>(null)

/** 保存前同步配送方式：从下拉输入框取当前显示值，避免未 blur 导致未提交 */
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
  if (text) shipmentForm.deliveryMethod = text
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
    ElMessage.success('平台送货单模板上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const handleReceiptUploadSuccess = (response: any, file: File) => {
  if (response.success || response.url) {
    shipmentForm.receiptUrl = response.url || file.name
    ElMessage.success('签收单上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
}

const handleSettlementInvoiceUploadSuccess = (response: any, file: File) => {
  if (response.success || response.url) {
    settlementForm.invoiceUrl = response.url || file.name
    ElMessage.success('发票上传成功')
  } else {
    ElMessage.error(response.message || '上传失败')
  }
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

const submitShipment = async () => {
  try {
    flushShipmentDeliveryMethod()
    console.log('Submitting shipment:', shipmentForm)
    await request.post('/order-shipments', shipmentForm)
    // 更新订单状态为已发货
    await request.patch(`/sales-orders/${currentShipmentOrder.value.id}/status`, { status: '已发货' })
    ElMessage.success('发货信息保存成功，订单状态已更新为已发货')
    fetchOrders()
  } catch (error) {
    console.error('Submit shipment error:', error)
    ElMessage.error('发货信息保存失败')
  }
}

const syncShipment = async () => {
  try {
    flushShipmentDeliveryMethod()
    console.log('Syncing shipment:', shipmentForm)
    await request.post('/order-shipments', shipmentForm)
    ElMessage.success('发货信息同步成功')
    fetchOrders()
  } catch (error) {
    console.error('Sync shipment error:', error)
    ElMessage.error('发货信息同步失败')
  }
}

const submitSettlement = async () => {
  try {
    console.log('Submitting settlement:', settlementForm)
    // 先获取完整的订单数据
    const orderData = { ...currentSettlementOrder.value }
    // 更新结算相关字段
    orderData.settlementNo = settlementForm.settlementNo
    orderData.platformReconciliationNo = settlementForm.platformReconciliationNo
    orderData.invoiceNumber = settlementForm.invoiceNumber
    orderData.invoiceUrl = settlementForm.invoiceUrl
    orderData.platformRefundStatus = settlementForm.platformRefundStatus
    
    await request.put(`/sales-orders/${currentSettlementOrder.value.id}`, orderData)
    ElMessage.success('结算信息保存成功')
    settlementDialogVisible.value = false
    fetchOrders()
  } catch (error) {
    console.error('Submit settlement error:', error)
    ElMessage.error('结算信息保存失败')
  }
}

// 结算相关变量
const settlementDialogVisible = ref(false)
const currentSettlementOrder = ref<any>(null)
const settlementInvoiceFileList = ref<any[]>([])
const settlementForm = reactive({
  settlementNo: '',
  platformReconciliationNo: '',
  invoiceNumber: '',
  invoiceUrl: '',
  platformRefundStatus: ''
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

const submitReturn = async () => {
  try {
    await request.patch(`/sales-orders/${currentReturnOrder.value.id}/return`, {
      returnReason: returnForm.returnReason
    })
    ElMessage.success('订单退回成功')
    returnDialogVisible.value = false
    fetchOrders()
  } catch (error) {
    console.error('Return order error:', error)
    ElMessage.error('订单退回失败，请稍后重试')
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
  const selectedUser = salesUsers.value.find((user: any) => user.id === value)
  if (selectedUser && orderForm.orderType === '自营') {
    orderForm.offlineSales = selectedUser.realName
  }
  // 为所有商品重新生成OMS订单号
  productList.value.forEach((_, index) => {
    generateOmsOrderNo(index)
  })
}

const handleOrderDateChange = (value: string) => {
  // 当订单日期改变时的处理逻辑
  console.log('Order date changed to:', value)
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

const generateOmsOrderNo = (productIndex?: number) => {
  // 如果没有指定商品索引，则为第一个商品生成
  const index = productIndex !== undefined ? productIndex : 0
  if (!productList.value[index]) {
    return
  }
  
  const product = productList.value[index]
  
  // 检查必要字段
  if (!product.orderType || !orderForm.ecommerceSalesId || !orderForm.orderDate) {
    return
  }
  
  // 查找业务员信息
  const salesUser = salesUsers.value.find(u => u.id === orderForm.ecommerceSalesId)
  if (!salesUser) {
    return
  }
  
  // 获取业务员拼音首字母大写
  const name = salesUser.realName || salesUser.username || ''
  const firstLetter = getPinyinFirstLetter(name)
  
  // 获取当前日期
  const date = new Date(orderForm.orderDate)
  const dateStr = date.getFullYear().toString() +
    (date.getMonth() + 1).toString().padStart(2, '0') +
    date.getDate().toString().padStart(2, '0')
  
  // 生成订单号
  let orderNo = dateStr + firstLetter
  if (product.orderType === '第三方订单') {
    orderNo = 'D' + orderNo
  }
  
  // 添加序号（为每个商品生成唯一序号）
  const sequence = (dailyOrderCount.value + 1 + index).toString().padStart(2, '0')
  orderNo += sequence
  
  product.omsOrderNo = orderNo
  
  // 如果是自营商品，同时设置离线合同号
  if (product.orderType === '自营') {
    product.offlineContractNo = orderNo
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
    '军': 'J', '洋': 'Y', '勇': 'Y', '艳': 'Y', '杰': 'J', '娟': 'J', '涛': 'T', '明': 'M', '超': 'C', '秀': 'X',
    '霞': 'X', '平': 'P', '刚': 'G', '桂': 'G', '霞': 'X', '丽': 'L', '军': 'J', '国': 'G', '海': 'H', '波': 'B',
    '斌': 'B', '梅': 'M', '静': 'J', '玲': 'L', '燕': 'Y', '辉': 'H', '红': 'H', '丽': 'L', '建': 'J', '新': 'X',
    '志': 'Z', '永': 'Y', '林': 'L', '金': 'J', '玉': 'Y', '美': 'M', '春': 'C', '德': 'D', '俊': 'J', '雪': 'X',
    '峰': 'F', '飞': 'F', '晓': 'X', '慧': 'H', '萍': 'P', '健': 'J', '云': 'Y', '鹏': 'P', '文': 'W', '庆': 'Q',
    '兵': 'B', '东': 'D', '小': 'X', '志': 'Z', '光': 'G', '涛': 'T', '伟': 'W', '亮': 'L', '勇': 'Y', '刚': 'G'
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
    
    // 只取前两个字符的拼音首字母
    if (result.length >= 2) {
      break
    }
  }
  
  return result || 'X'
}

const handleExport = () => {
  ElMessage.success('批量导出功能开发中...')
}
</script>

<style scoped>
.order-list-container {
  padding: 20px;
}

.filter-card {
  margin-bottom: 20px;
}

.table-card {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.table-toolbar {
  margin-bottom: 20px;
  display: flex;
  gap: 10px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
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
</style>
