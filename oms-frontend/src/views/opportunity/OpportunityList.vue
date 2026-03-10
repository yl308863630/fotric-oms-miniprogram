<template>
  <div class="opportunity-container">
    <el-card class="box-card">
      <template #header>
        <div class="card-header">
          <span>商机管理</span>
          <el-button type="primary" @click="handleAdd">新增商机</el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-input
          v-model="searchQuery"
          placeholder="搜索客户名称/标题/型号/联系人/电话"
          style="width: 320px; margin-right: 10px;"
          clearable
        />
        <el-select
          v-model="salesFilter"
          placeholder="筛选业务员"
          style="width: 200px; margin-right: 10px;"
          clearable
          filterable
        >
          <el-option
            v-for="user in users"
            :key="user.id"
            :label="user.realName || user.username"
            :value="user.realName || user.username"
          />
        </el-select>
        <el-button type="primary" @click="fetchData">查询</el-button>
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

      <!-- 表格 -->
      <el-table :data="filteredData" border style="width: 100%" v-loading="loading">
        <el-table-column label="操作" width="150" fixed="left">
          <template #default="scope">
            <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
        <el-table-column v-if="orderedColumns.find(col => col.label === 'customerCode' && col.visible)" prop="customerCode" label="客户编码" width="150" fixed />
        <template v-for="column in orderedColumns" :key="column.label">
          <el-table-column
            v-if="column.visible && column.label !== 'customerCode' && column.label !== '操作'"
            :prop="column.label"
            :label="column.title"
            :width="column.width"
            :min-width="column.minWidth"
            :fixed="column.fixed"
            show-overflow-tooltip
          >
            <template #default="scope" v-if="column.label === 'estimatedAmount'">
              <span class="amount-text">{{ formatPrice(scope.row[column.label]) }}</span>
            </template>
            <template #default="scope" v-else-if="column.label === 'stage'">
              <el-tag :type="getStageType(scope.row[column.label])">{{ getStageLabel(scope.row[column.label]) }}</el-tag>
            </template>
            <template #default="scope" v-else-if="column.label === 'followUpTime' || column.label === 'createTime'">
              {{ formatDateTime(scope.row[column.label]) }}
            </template>
            <template #default="scope" v-else-if="column.label === 'expectedDate'">
              {{ formatDateTime(scope.row[column.label], false) }}
            </template>
            <template #default="scope" v-else>
              {{ scope.row[column.label] }}
            </template>
          </el-table-column>
        </template>
      </el-table>

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

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      :title="dialogTitle"
      v-model="dialogVisible"
      width="800px"
      @close="resetForm"
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="商机标题" prop="title">
              <el-input v-model="form.title" placeholder="请输入商机标题" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商机分类" prop="category">
              <el-select v-model="form.category" placeholder="请选择" style="width: 100%">
                <el-option label="报备" value="reported" />
                <el-option label="非报备" value="not_reported" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="客户名称" prop="customerName">
              <el-input v-model="form.customerName" placeholder="请输入客户名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户来源" prop="source">
              <el-select v-model="form.source" placeholder="请选择" style="width: 100%">
                <el-option label="搜索引擎(FOTRIC)" value="search_engine" />
                <el-option label="招标数据" value="tender_data" />
                <el-option label="信息流推广(FOTRIC)" value="info_flow" />
                <el-option label="自媒体推广(FOTRIC)" value="social_media" />
                <el-option label="销售开拓(FOTRIC)" value="sales_dev" />
                <el-option label="投标信息(FOTRIC)" value="bid_info" />
                <el-option label="研讨会(FOTRIC)" value="seminar" />
                <el-option label="展会(FOTRIC)" value="exhibition" />
                <el-option label="电商(FOTRIC)" value="ecommerce" />
                <el-option label="转介绍(FOTRIC)" value="referral" />
                <el-option label="分销商线上推广" value="distributor_online" />
                <el-option label="分销商线下开拓" value="distributor_offline" />
                <el-option label="未知" value="unknown" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="联系人" prop="contact">
              <el-input v-model="form.contact" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入电话" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="关联产品*">
              <div class="product-list-header">
                <span class="selected-count">已选 {{ opportunityProducts.length }} 个</span>
                <div class="product-actions">
                  <el-button type="primary" size="small" @click="showAddProductDialog = true">+ 添加</el-button>
                  <el-button size="small" @click="showBatchAddProductDialog = true">批量添加</el-button>
                </div>
              </div>
              <el-table :data="opportunityProducts" border style="width: 100%; margin-top: 10px">
                <el-table-column type="selection" width="55" />
                <el-table-column prop="productName" label="产品" min-width="120" />
                <el-table-column prop="productCategory" label="产品分类" min-width="100" />
                <el-table-column prop="productCode" label="产品编号" min-width="100" />
                <el-table-column prop="unit" label="销售单位" min-width="80" />
                <el-table-column prop="standardPrice" label="标准单价" min-width="100" :formatter="formatCurrency" />
                <el-table-column prop="productModel" label="产品属性" min-width="100" />
                <el-table-column prop="sellingPrice" label="售价" min-width="100" :formatter="formatCurrency" />
                <el-table-column prop="quantity" label="数量" min-width="80" />
                <el-table-column prop="discount" label="折扣" min-width="80" :formatter="formatDiscount" />
                <el-table-column prop="totalPrice" label="总价" min-width="100" :formatter="formatCurrency" />
                <el-table-column prop="remarks" label="备注" min-width="100" />
                <el-table-column label="操作" width="80" fixed="right">
                  <template #default="scope">
                    <el-button type="danger" size="small" @click="removeProduct(scope.row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="电商平台" prop="platform">
              <el-input v-model="form.platform" placeholder="请输入电商平台" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="所在地区" prop="region">
              <el-input v-model="form.region" placeholder="请输入地区" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属行业" prop="industry">
              <el-input v-model="form.industry" placeholder="请输入行业" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="预计签单日期" prop="expectedDate">
              <el-date-picker
                v-model="form.expectedDate"
                type="date"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="采购预算" prop="budget">
              <el-input v-model="form.budget" placeholder="请输入预算" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="电商业务员" prop="ecommerceSales">
              <el-select v-model="form.ecommerceSales" placeholder="请选择业务员" style="width: 100%" filterable>
                <el-option
                  v-for="user in users"
                  :key="user.id"
                  :label="user.realName || user.username"
                  :value="user.realName || user.username"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="线下配合销售" prop="offlineSales">
              <el-input v-model="form.offlineSales" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="跟进时间" prop="followUpTime">
              <el-date-picker
                v-model="form.followUpTime"
                type="datetime"
                placeholder="选择跟进时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="业务阶段" prop="stage">
              <el-select v-model="form.stage" placeholder="请选择" style="width: 100%">
                <el-option label="初步洽谈" value="negotiating" />
                <el-option label="方案报价" value="quoted" />
                <el-option label="合同签订" value="contracted" />
                <el-option label="项目交付" value="delivered" />
                <el-option label="售后维护" value="after-sale" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="最新跟进记录" prop="latestFollowUpRecord">
          <el-input v-model="form.latestFollowUpRecord" type="textarea" :rows="2" placeholder="请输入最新跟进内容" />
        </el-form-item>

        <el-form-item label="解决哪些问题" prop="problemSolved">
          <el-input v-model="form.problemSolved" type="textarea" :rows="3" />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="询价抬头" prop="inquiryHeader">
              <el-input v-model="form.inquiryHeader" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下单抬头" prop="orderHeader">
              <el-input v-model="form.orderHeader" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="服务商抬头" prop="serviceProvider">
              <el-input v-model="form.serviceProvider" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出货渠道抬头" prop="shippingChannel">
              <el-input v-model="form.shippingChannel" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="竞争对手" prop="competitor">
          <el-input v-model="form.competitor" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 添加产品弹窗 -->
    <el-dialog
      title="添加产品"
      v-model="showAddProductDialog"
      width="600px"
    >
      <el-form :model="addProductForm" :rules="addProductRules" ref="addProductFormRef" label-width="100px">
        <el-form-item label="产品" prop="productId">
          <el-select
            v-model="addProductForm.productId"
            placeholder="请选择产品"
            style="width: 100%"
            filterable
            @change="handleAddProductChange"
          >
            <el-option
              v-for="item in products"
              :key="item.id"
              :label="item.name + ' (' + item.model + ')'"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number v-model="addProductForm.quantity" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="售价" prop="sellingPrice">
          <el-input v-model.number="addProductForm.sellingPrice" type="number" style="width: 100%" />
        </el-form-item>
        <el-form-item label="折扣" prop="discount">
          <el-input v-model.number="addProductForm.discount" type="number" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注" prop="remarks">
          <el-input v-model="addProductForm.remarks" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showAddProductDialog = false">取消</el-button>
          <el-button type="primary" @click="addProduct">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 批量添加产品弹窗 -->
    <el-dialog
      title="批量添加产品"
      v-model="showBatchAddProductDialog"
      width="800px"
    >
      <el-table :data="batchSelectProducts" border style="width: 100%" ref="batchProductTable">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="name" label="产品" min-width="150" />
        <el-table-column prop="model" label="型号" min-width="100" />
        <el-table-column prop="code" label="编号" min-width="100" />
        <el-table-column prop="category" label="分类" min-width="100" />
        <el-table-column prop="price" label="标准价格" min-width="100" :formatter="formatCurrency" />
        <el-table-column prop="unit" label="单位" min-width="80" />
      </el-table>
      <div style="margin-top: 20px">
        <el-button type="primary" @click="batchAddProducts">批量添加选中产品</el-button>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showBatchAddProductDialog = false">取消</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../utils/request'
import draggable from 'vuedraggable'

interface ColumnConfig {
  label: string
  title: string
  width?: number
  minWidth?: number
  fixed?: 'left' | 'right'
  visible: boolean
}

const loading = ref(false)
const tableData = ref<any[]>([])
const searchQuery = ref('')
const salesFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('新增商机')
const formRef = ref()
const products = ref<any[]>([])
const users = ref<any[]>([])
const opportunityProducts = ref<any[]>([])
const showAddProductDialog = ref(false)
const showBatchAddProductDialog = ref(false)
const addProductFormRef = ref()
const batchProductTable = ref()
const batchSelectProducts = ref<any[]>([])

// 定义所有列配置
const allColumns = ref<ColumnConfig[]>([
  { label: 'customerCode', title: '客户编码', width: 150, fixed: 'left', visible: true },
  { label: 'title', title: '商机标题', width: 180, visible: true },
  { label: 'customerName', title: '客户名称', width: 150, visible: true },
  { label: 'ecommerceSales', title: '电商业务员', width: 120, visible: true },
  { label: 'productModel', title: '商品型号', width: 150, visible: true },
  { label: 'quantity', title: '数量', width: 80, visible: true },
  { label: 'estimatedAmount', title: '预计金额', width: 130, visible: true },
  { label: 'stage', title: '业务阶段', width: 120, visible: true },
  { label: 'contact', title: '联系人', width: 120, visible: true },
  { label: 'phone', title: '电话', width: 120, visible: true },
  { label: 'latestFollowUpRecord', title: '最新跟进记录', width: 200, visible: true },
  { label: 'followUpTime', title: '跟进时间', width: 160, visible: true },
  { label: 'createTime', title: '创建时间', width: 160, visible: true },
  { label: 'expectedDate', title: '预计签单日期', width: 160, visible: true }
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
  const saved = localStorage.getItem('opportunityListColumns')
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
  
  localStorage.setItem('opportunityListColumns', JSON.stringify({
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

const form = reactive({
  id: undefined,
  title: '',
  category: '',
  source: '',
  customerName: '',
  inquiryHeader: '',
  orderHeader: '',
  contact: '',
  phone: '',
  platform: '',
  serviceProvider: '',
  shippingChannel: '',
  expectedDate: '',
  region: '',
  industry: '',
  problemSolved: '',
  budget: '',
  competitor: '',
  ecommerceSales: '',
  offlineSales: '',
  stage: 'negotiating',
  followUpTime: '',
  latestFollowUpRecord: ''
})

const addProductForm = reactive({
  productId: null as number | null,
  quantity: 1,
  sellingPrice: 0,
  discount: 100,
  remarks: ''
})

const rules = {
  title: [{ required: true, message: '请输入商机标题', trigger: 'blur' }],
  customerName: [{ required: true, message: '请输入客户名称', trigger: 'blur' }]
}

const addProductRules = {
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  sellingPrice: [{ required: true, message: '请输入售价', trigger: 'blur' }]
}

const filteredData = computed(() => {
  let result = [...tableData.value]
  
  // 关键词搜索
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(item => 
      (item.customerName && item.customerName.toLowerCase().includes(query)) ||
      (item.title && item.title.toLowerCase().includes(query)) ||
      (item.productModel && item.productModel.toLowerCase().includes(query)) ||
      (item.contact && item.contact.toLowerCase().includes(query)) ||
      (item.phone && item.phone.toLowerCase().includes(query)) ||
      (item.customerCode && item.customerCode.toLowerCase().includes(query))
    )
  }
  
  // 业务员筛选
  if (salesFilter.value) {
    result = result.filter(item => item.ecommerceSales === salesFilter.value)
  }
  
  return result
})

const fetchData = async () => {
  loading.value = true
  try {
    const res = await request.get('/opportunities', {
      params: {
        page: currentPage.value - 1,
        size: pageSize.value
      }
    })
    tableData.value = res.content || []
    total.value = res.totalElements || 0
  } catch (error) {
    console.error('Fetch opportunities error:', error)
    ElMessage.error('获取商机列表失败')
  } finally {
    loading.value = false
  }
}

const fetchProducts = async () => {
  try {
    const res = await request.get('/products', {
      params: {
        page: 0,
        size: 1000 // 一次性获取足够多的商品
      }
    })
    products.value = res.content || []
    batchSelectProducts.value = res.content || []
    console.log('获取到的商品数据:', products.value)
  } catch (error) {
    console.error('Fetch products error:', error)
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
    users.value = content.filter((user: any) => user.enabled !== false)
    console.log('获取到的用户数据:', users.value)
  } catch (error) {
    console.error('Fetch users error:', error)
  }
}

const fetchOpportunityProducts = async (opportunityId: number) => {
  try {
    const res = await request.get(`/opportunities/${opportunityId}/products`)
    opportunityProducts.value = res || []
  } catch (error) {
    console.error('Fetch opportunity products error:', error)
    opportunityProducts.value = []
  }
}

const handleAdd = () => {
  dialogTitle.value = '新增商机'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = async (row: any) => {
  dialogTitle.value = '编辑商机'
  Object.assign(form, row)
  // 清空产品列表
  opportunityProducts.value = []
  // 如果有ID，加载关联的产品
  if (row.id) {
    await fetchOpportunityProducts(row.id)
  }
  dialogVisible.value = true
}

const handleDelete = (row: any) => {
  ElMessageBox.confirm('确定要删除该商机吗？', '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/opportunities/${row.id}`)
      ElMessage.success('删除成功')
      fetchData()
    } catch (error) {
      ElMessage.error('删除失败')
    }
  })
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchData()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  fetchData()
}

const handleAddProductChange = (productId: number) => {
  const product = products.value.find(p => p.id === productId)
  if (product) {
    addProductForm.sellingPrice = product.price || 0
  }
}

const addProduct = async () => {
  if (!addProductFormRef.value) return
  await addProductFormRef.value.validate(async (valid: boolean) => {
    if (valid) {
      const product = products.value.find(p => p.id === addProductForm.productId)
      if (product) {
        const newProduct = {
          productId: product.id,
          productName: product.name,
          productModel: product.model,
          productCategory: product.category,
          productCode: product.code,
          unit: product.unit,
          standardPrice: product.price,
          sellingPrice: addProductForm.sellingPrice,
          quantity: addProductForm.quantity,
          discount: addProductForm.discount,
          totalPrice: addProductForm.sellingPrice * addProductForm.quantity * (addProductForm.discount / 100),
          remarks: addProductForm.remarks
        }
        opportunityProducts.value.push(newProduct)
        showAddProductDialog.value = false
        // 重置表单
        Object.assign(addProductForm, {
          productId: null,
          quantity: 1,
          sellingPrice: 0,
          discount: 100,
          remarks: ''
        })
      }
    }
  })
}

const batchAddProducts = () => {
  // 获取选中的产品
  const selectedProducts = (batchProductTable.value as any)?.getSelection() || []
  if (selectedProducts.length === 0) {
    ElMessage.warning('请至少选择一个产品')
    return
  }
  
  // 批量添加产品到商机
  selectedProducts.forEach((product: any) => {
    const newProduct = {
      productId: product.id,
      productName: product.name,
      productModel: product.model,
      productCategory: product.category,
      productCode: product.code,
      unit: product.unit,
      standardPrice: product.price,
      sellingPrice: product.price,
      quantity: 1,
      discount: 100,
      totalPrice: product.price * 1 * (100 / 100),
      remarks: ''
    }
    opportunityProducts.value.push(newProduct)
  })
  
  ElMessage.success(`成功添加 ${selectedProducts.length} 个产品`)
  showBatchAddProductDialog.value = false
}

const removeProduct = (product: any) => {
  const index = opportunityProducts.value.findIndex(p => p.id === product.id || p.productId === product.productId)
  if (index > -1) {
    opportunityProducts.value.splice(index, 1)
  }
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      try {
        if (form.id) {
          // 更新商机
          await request.put(`/opportunities/${form.id}`, form)
          // 更新产品
          if (opportunityProducts.value.length > 0) {
            await request.put(`/opportunities/${form.id}/products`, opportunityProducts.value)
          } else {
            await request.put(`/opportunities/${form.id}/products`, [])
          }
          ElMessage.success('更新成功')
        } else {
          // 创建商机
          const newOpportunity = await request.post('/opportunities', form)
          // 添加产品
          if (opportunityProducts.value.length > 0) {
            await request.put(`/opportunities/${newOpportunity.id}/products`, opportunityProducts.value)
          }
          ElMessage.success('新增成功')
        }
        dialogVisible.value = false
        fetchData()
      } catch (error) {
        ElMessage.error('提交失败')
      }
    }
  })
}

const resetForm = () => {
  Object.assign(form, {
    id: undefined,
    title: '',
    category: '',
    source: '',
    customerName: '',
    inquiryHeader: '',
    orderHeader: '',
    contact: '',
    phone: '',
    platform: '',
    serviceProvider: '',
    shippingChannel: '',
    expectedDate: '',
    region: '',
    industry: '',
    problemSolved: '',
    budget: '',
    competitor: '',
    ecommerceSales: '',
    offlineSales: '',
    stage: 'negotiating',
    followUpTime: '',
    latestFollowUpRecord: ''
  })
  opportunityProducts.value = []
  if (formRef.value) formRef.value.resetFields()
}

const formatPrice = (price: number) => {
  return price ? `￥${Number(price).toFixed(2)}` : '￥0.00'
}

const formatCurrency = (row: any, column: any, cellValue: number) => {
  return formatPrice(cellValue)
}

const formatDiscount = (row: any, column: any, cellValue: number) => {
  return `${Number(cellValue).toFixed(1)}%`
}

const formatDateTime = (date: string, showTime = true) => {
  if (!date) return '-'
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  if (!showTime) return `${year}-${month}-${day}`
  const hour = String(d.getHours()).padStart(2, '0')
  const minute = String(d.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}`
}

const getStageType = (stage: string) => {
  const types: Record<string, string> = {
    negotiating: 'info',
    quoted: 'primary',
    contracted: 'success',
    delivered: 'warning',
    'after-sale': 'success'
  }
  return types[stage] || 'info'
}

const getStageLabel = (stage: string) => {
  const labels: Record<string, string> = {
    negotiating: '初步洽谈',
    quoted: '方案报价',
    contracted: '合同签订',
    delivered: '项目交付',
    'after-sale': '售后维护'
  }
  return labels[stage] || stage
}

onMounted(() => {
  fetchData()
  fetchProducts()
  fetchUsers()
  loadSettings()
})
</script>

<style scoped>
.opportunity-container {
  padding: 20px;
}
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
.box-card {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.search-bar {
  margin-bottom: 20px;
  display: flex;
  gap: 10px;
  align-items: center;
}
.amount-text {
  color: #f56c6c;
  font-weight: bold;
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
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.amount-input {
  background-color: #f5f7fa;
}
.product-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.selected-count {
  font-weight: bold;
  color: #666;
}
.product-actions {
  display: flex;
  gap: 8px;
}
</style>
