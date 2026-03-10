<template>
  <div class="product-list-container">
    <!-- 搜索筛选区 -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm" class="demo-form-inline">
        <el-form-item label="商品名称">
          <el-input v-model="filterForm.name" placeholder="请输入商品名称" clearable />
        </el-form-item>
        <el-form-item label="品牌">
          <el-input v-model="filterForm.brand" placeholder="请输入品牌" clearable />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="filterForm.category" placeholder="请选择分类" clearable style="width: 200px">
            <el-option label="办公用品" value="office" />
            <el-option label="电子设备" value="electronics" />
            <el-option label="耗材" value="consumables" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 状态页签 -->
    <div class="status-tabs-container">
      <el-tabs v-model="activeStatus" @tab-click="handleStatusClick">
        <el-tab-pane label="全部商品" name="all" />
        <el-tab-pane label="上架中" name="active" />
        <el-tab-pane label="已下架" name="inactive" />
        <el-tab-pane label="库存预警" name="warning" />
      </el-tabs>
    </div>

    <!-- 数据列表 -->
    <el-card class="table-card">
      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增商品</el-button>
        <el-button type="success" @click="handleImport">批量导入</el-button>
        <el-button type="warning" @click="handleExport">导出商品</el-button>
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

      <el-table :data="tableData" border style="width: 100%">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="操作" width="150" fixed="left" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
        <el-table-column v-if="orderedColumns.find(c => c.label === 'image')?.visible" prop="image" label="图片" width="100" align="center">
          <template #default="scope">
            <el-image 
              style="width: 60px; height: 60px" 
              :src="scope.row.image" 
              :preview-src-list="[scope.row.image]"
              preview-teleported
              fit="cover"
            />
          </template>
        </el-table-column>
        <el-table-column v-if="orderedColumns.find(c => c.label === 'code')?.visible" prop="code" label="商品编码" width="140" />
        <el-table-column v-if="orderedColumns.find(c => c.label === 'barcode')?.visible" prop="barcode" label="条形码" width="140">
          <template #default="scope">
            {{ scope.row.barcode || '-' }}
          </template>
        </el-table-column>
        <el-table-column v-if="orderedColumns.find(c => c.label === 'materialNo')?.visible" prop="materialNo" label="物料号" width="120">
          <template #default="scope">
            {{ scope.row.materialNo || '-' }}
          </template>
        </el-table-column>
        <el-table-column v-if="orderedColumns.find(c => c.label === 'name')?.visible" prop="name" label="商品名称" min-width="200" show-overflow-tooltip />
        <el-table-column v-if="orderedColumns.find(c => c.label === 'brand')?.visible" prop="brand" label="品牌" width="120" />
        <el-table-column v-if="orderedColumns.find(c => c.label === 'model')?.visible" prop="model" label="型号" width="120" />
        <el-table-column v-if="orderedColumns.find(c => c.label === 'specs')?.visible" prop="specs" label="规格" width="120" />
        <el-table-column v-if="orderedColumns.find(c => c.label === 'productConfig')?.visible" prop="productConfig" label="产品配置" width="150" show-overflow-tooltip />
        <el-table-column v-if="orderedColumns.find(c => c.label === 'warrantyPeriod')?.visible" prop="warrantyPeriod" label="产品保修期" width="120" />
        <el-table-column v-if="orderedColumns.find(c => c.label === 'unit')?.visible" prop="unit" label="单位" width="80" align="center" />
        <el-table-column v-if="orderedColumns.find(c => c.label === 'price')?.visible" prop="price" label="销售价" width="120" align="right">
          <template #default="scope">
            <span class="price">¥{{ (scope.row.price || 0).toFixed(2) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="orderedColumns.find(c => c.label === 'stock')?.visible" prop="stock" label="库存" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.stock < 10 ? 'danger' : 'success'">{{ scope.row.stock }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="orderedColumns.find(c => c.label === 'competitorLink')?.visible" prop="competitorLink" label="链接" min-width="200">
          <template #default="scope">
            <template v-if="scope.row.competitorLink">
              <div v-for="(link, index) in parseLinks(scope.row.competitorLink)" :key="index" style="margin-bottom: 4px;">
                <el-link type="primary" :href="formatUrl(link)" target="_blank" :title="link">
                  查看链接{{ index + 1 }}
                </el-link>
              </div>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="orderedColumns.find(c => c.label === 'remark')?.visible" prop="remark" label="备注" min-width="150" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.remark || '-' }}
          </template>
        </el-table-column>
        <el-table-column v-if="orderedColumns.find(c => c.label === 'createTime')?.visible" prop="createTime" label="新增时间" width="180">
          <template #default="scope">
            {{ formatDateTime(scope.row.createTime) || '-' }}
          </template>
        </el-table-column>
        <el-table-column v-if="orderedColumns.find(c => c.label === 'updateTime')?.visible" prop="updateTime" label="更新时间" width="180">
          <template #default="scope">
            {{ formatDateTime(scope.row.updateTime) || '-' }}
          </template>
        </el-table-column>
        <el-table-column v-if="orderedColumns.find(c => c.label === 'status')?.visible" prop="status" label="状态" width="100" align="center">
          <template #default="scope">
            <el-switch
              v-model="scope.row.isActive"
              inline-prompt
              active-text="上架"
              inactive-text="下架"
              @change="(val: boolean) => handleStatusChange(val, scope.row)"
            />
          </template>
        </el-table-column>
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

    <!-- 商品详情/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'add' ? '新增商品' : (dialogType === 'edit' ? '编辑商品' : '商品详情')"
      width="800px"
    >
      <el-form ref="productFormRef" :model="productForm" label-width="100px" :disabled="dialogType === 'view'">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="商品名称" required>
              <el-input v-model="productForm.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品编码" required>
              <el-input v-model="productForm.code" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="条形码">
              <el-input v-model="productForm.barcode" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="物料号">
              <el-input v-model="productForm.materialNo" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌" required>
              <el-input v-model="productForm.brand" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="型号">
              <el-input v-model="productForm.model" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规格">
              <el-input v-model="productForm.specs" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产品配置">
              <el-input v-model="productForm.productConfig" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产品保修期">
              <el-input v-model="productForm.warrantyPeriod" placeholder="例如：1年" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单位" required>
              <el-input v-model="productForm.unit" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类" required>
              <el-select v-model="productForm.category" style="width: 100%">
                <el-option label="办公用品" value="office" />
                <el-option label="电子设备" value="electronics" />
                <el-option label="耗材" value="consumables" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="销售价" required>
              <el-input-number v-model="productForm.price" :precision="2" :step="0.1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库存" required>
              <el-input-number v-model="productForm.stock" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="商品图片">
              <el-upload
                class="product-uploader"
                :show-file-list="false"
                :auto-upload="true"
                accept="image/*"
                :before-upload="beforeImageUpload"
                :http-request="handleCustomUpload"
              >
                <div v-if="isUploading" class="upload-loading">
                  <el-progress type="circle" :percentage="uploadPercent" :width="80" />
                </div>
                <template v-else>
                  <img v-if="productForm.image" :src="productForm.image" class="product-preview" />
                  <el-icon v-else class="product-uploader-icon"><Plus /></el-icon>
                </template>
              </el-upload>
              <div class="upload-tip">建议尺寸 800x800，不超过 5MB</div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="productForm.remark" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="链接">
              <el-input v-model="productForm.competitorLink" type="textarea" :rows="3" placeholder="请输入链接 URL，多个链接可用分号或换行分隔" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">{{ dialogType === 'view' ? '关闭' : '取消' }}</el-button>
          <el-button v-if="dialogType !== 'view'" type="primary" @click="handleSubmit">确认</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 批量导入对话框 -->
    <el-dialog
      v-model="importVisible"
      title="批量导入商品"
      width="500px"
      @close="handleImportClose"
    >
      <div class="import-content">
        <el-upload
          class="import-upload"
          drag
          action="/products/import"
          :headers="{ Authorization: 'Bearer ' + token }"
          :on-success="handleImportSuccess"
          :on-error="handleImportError"
          :before-upload="beforeImportUpload"
          accept=".xls,.xlsx"
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">
            将文件拖到此处，或<em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              只能上传 .xls/.xlsx 文件，且不超过 10MB
            </div>
          </template>
        </el-upload>
        <div class="template-download">
          <el-link type="primary" :underline="false" @click="downloadTemplate">
            <el-icon><download /></el-icon> 下载导入模板
          </el-link>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { Plus, UploadFilled, Download } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage, ElForm } from 'element-plus'
import request from '../../utils/request'
import axios from 'axios'
import draggable from 'vuedraggable'

// 筛选表单
const filterForm = reactive({
  name: '',
  brand: '',
  category: ''
})

// 状态页签
const activeStatus = ref('all')

// 表格数据
const allTableData = ref<any[]>([])
const tableData = computed(() => {
  let list = [...allTableData.value]
  
  // 根据状态筛选
  if (activeStatus.value === 'active') {
    list = list.filter(item => item.isActive)
  } else if (activeStatus.value === 'inactive') {
    list = list.filter(item => !item.isActive)
  } else if (activeStatus.value === 'warning') {
    list = list.filter(item => item.stock < 10) // 库存小于10的预警
  }
  
  // 分页处理
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return list.slice(start, end)
})

// 分页
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 对话框
const dialogVisible = ref(false)
const importVisible = ref(false)
const dialogType = ref<'add' | 'edit' | 'view'>('add') // 类型：新增、编辑、查看

// 商品表单
const productForm = ref({
  id: null,
  name: '',
  code: '',
  barcode: '',
  materialNo: '',
  brand: 'FOTRIC',
  model: '',
  specs: '',
  productConfig: '标准配置',
  warrantyPeriod: '',
  unit: '',
  category: '',
  price: 0,
  stock: 0,
  image: '',
  remark: '',
  competitorLink: ''
})

// 上传相关
const isUploading = ref(false)
const uploadPercent = ref(0)

// Token（从localStorage获取）
const token = localStorage.getItem('token') || ''

// 定义所有列配置
interface ColumnConfig {
  label: string
  title: string
  width?: number
  minWidth?: number
  visible: boolean
}

const allColumns = ref<ColumnConfig[]>([
  { label: 'image', title: '图片', width: 100, visible: true },
  { label: 'code', title: '商品编码', width: 140, visible: true },
  { label: 'barcode', title: '条形码', width: 140, visible: true },
  { label: 'materialNo', title: '物料号', width: 120, visible: true },
  { label: 'name', title: '商品名称', minWidth: 200, visible: true },
  { label: 'brand', title: '品牌', width: 120, visible: true },
  { label: 'model', title: '型号', width: 120, visible: true },
  { label: 'specs', title: '规格', width: 120, visible: true },
  { label: 'productConfig', title: '产品配置', minWidth: 150, visible: true },
  { label: 'warrantyPeriod', title: '产品保修期', width: 120, visible: true },
  { label: 'unit', title: '单位', width: 80, visible: true },
  { label: 'price', title: '销售价', width: 120, visible: true },
  { label: 'stock', title: '库存', width: 100, visible: true },
  { label: 'competitorLink', title: '链接', minWidth: 200, visible: true },
  { label: 'remark', title: '备注', minWidth: 150, visible: true },
  { label: 'createTime', title: '新增时间', width: 180, visible: true },
  { label: 'updateTime', title: '更新时间', width: 180, visible: true },
  { label: 'status', title: '状态', width: 100, visible: true }
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
  const saved = localStorage.getItem('productListColumns')
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

// 保存列设置到本地存储
const saveColumns = () => {
  const visible = visibleColumns.value
  const columnsOrder = orderedColumns.value.map(col => col.label)
  
  localStorage.setItem('productListColumns', JSON.stringify({
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
  // 拖拽结束后可以做些事情，比如保存顺序
  console.log('列顺序已更新')
}

// 获取商品列表
const fetchProducts = async () => {
  try {
    console.log('正在获取商品列表，参数:', {
      ...filterForm,
      page: currentPage.value - 1,
      size: pageSize.value
    })
    const response = await request.get('/products', {
      params: {
        ...filterForm,
        page: currentPage.value - 1,
        size: pageSize.value
      }
    })
    
    console.log('获取商品列表响应:', response)
    
    // 处理分页数据
    if (response && response.content) {
      allTableData.value = response.content
      total.value = response.totalElements || 0
    } else {
      allTableData.value = Array.isArray(response) ? response : []
      total.value = allTableData.value.length
    }
    
    console.log(`获取到 ${allTableData.value.length} 条商品数据，总数: ${total.value}`)
  } catch (error) {
    console.error('获取商品列表失败:', error)
    ElMessage.error('获取商品列表失败')
  }
}

// 搜索
const handleSearch = () => {
  currentPage.value = 1
  fetchProducts()
}

// 重置搜索
const resetSearch = () => {
  filterForm.name = ''
  filterForm.brand = ''
  filterForm.category = ''
  fetchProducts()
}

// 状态切换
const handleStatusClick = () => {
  // 状态切换无需重新请求，通过computed属性过滤即可
}

// 分页事件
const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchProducts()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  fetchProducts()
}

// 新增商品
const handleAdd = () => {
  resetForm()
  dialogType.value = 'add'
  dialogVisible.value = true
}

// 编辑商品
const handleEdit = (row: any) => {
  productForm.value = { ...row }
  dialogType.value = 'edit'
  dialogVisible.value = true
}

// 查看详情
const viewDetail = (row: any) => {
  productForm.value = { ...row }
  dialogType.value = 'view'
  dialogVisible.value = true
}

// 删除商品
const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确认删除商品 "${row.name}"？`, '提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await request.delete(`/products/${row.id}`)
    ElMessage.success('删除成功')
    fetchProducts() // 重新获取列表
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除商品失败:', error)
      ElMessage.error('删除商品失败')
    }
  }
}

// 重置表单
const resetForm = () => {
  productForm.value = {
    id: null,
    name: '',
    code: '',
    barcode: '',
    materialNo: '',
    brand: 'FOTRIC',
    model: '',
    specs: '',
    productConfig: '标准配置',
    warrantyPeriod: '',
    unit: '',
    category: '',
    price: 0,
    stock: 0,
    image: '',
    remark: '',
    competitorLink: ''
  }
}

// 提交表单
const productFormRef = ref<InstanceType<typeof ElForm>>()
const handleSubmit = async () => {
  if (!productFormRef.value) return
  try {
    await productFormRef.value.validate()
    let response;
    if (dialogType.value === 'add') {
      console.log('正在新增商品:', productForm.value)
      response = await request.post('/products', productForm.value)
      console.log('新增商品响应:', response)
      ElMessage.success('新增商品成功')
    } else {
      console.log('正在更新商品，ID:', productForm.value.id)
      console.log('正在更新商品，完整数据:', JSON.stringify(productForm.value, null, 2))
      console.log('物料号 (materialNo):', productForm.value.materialNo)
      response = await request.put(`/products/${productForm.value.id}`, productForm.value)
      console.log('更新商品响应:', response)
      ElMessage.success('更新商品成功')
    }
    
    dialogVisible.value = false
    
    // 只在新增时重置表单，编辑时保持表单数据
    if (dialogType.value === 'add') {
      resetForm()
    }
    
    // 确保切换到显示所有商品的标签页，以便能看到新添加的商品
    activeStatus.value = 'all'
    console.log('正在刷新商品列表...')
    await fetchProducts() // 重新获取列表
    console.log('商品列表刷新完成，当前列表数据:', allTableData.value)
  } catch (error: any) {
    if (error.name === 'ValidationError') {
      ElMessage.warning('请完善必填字段信息')
      return
    }
    console.error('提交商品失败:', error)
    console.error('错误详情:', error.response || error.message)
    ElMessage.error(dialogType.value === 'add' ? '新增商品失败' : '更新商品失败')
  }
}

// 状态开关变化
const handleStatusChange = async (isActive: boolean, row: any) => {
  try {
    await request.patch(`/products/${row.id}/status`, { isActive })
    row.isActive = isActive
    ElMessage.success(isActive ? '上架成功' : '下架成功')
  } catch (error) {
    console.error('更新商品状态失败:', error)
    ElMessage.error('更新商品状态失败')
    // 如果失败，还原状态
    row.isActive = !isActive
  }
}

// 图片上传相关
const beforeImageUpload = (file: File) => {
  const isValidType = ['image/jpeg', 'image/jpg', 'image/png'].includes(file.type)
  const isLt5M = file.size / 1024 / 1024 < 5

  if (!isValidType) {
    ElMessage.error('上传图片只能是 JPG/PNG 格式!')
    return false
  }
  if (!isLt5M) {
    ElMessage.error('上传图片大小不能超过 5MB!')
    return false
  }

  isUploading.value = true
  uploadPercent.value = 0
  return true
}

const handleCustomUpload = async (options: any) => {
  const { file, onProgress, onSuccess, onError } = options
  
  try {
    const formData = new FormData()
    formData.append('file', file)
    
    const response = await request.post('/files/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      },
      onUploadProgress: (progressEvent: any) => {
        const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total)
        uploadPercent.value = percentCompleted
        if (onProgress) {
          onProgress({ percent: percentCompleted }, file)
        }
      }
    })
    
    isUploading.value = false
    uploadPercent.value = 0
    productForm.value.image = response.url || ''
    ElMessage.success('图片上传成功')
    if (onSuccess) {
      onSuccess(response, file)
    }
  } catch (error) {
    isUploading.value = false
    uploadPercent.value = 0
    ElMessage.error('图片上传失败')
    if (onError) {
      onError(error, file)
    }
  }
}

// 批量导入
const handleImport = () => {
  importVisible.value = true
}

const handleImportClose = () => {
  importVisible.value = false
}

const beforeImportUpload = (file: File) => {
  const isValidType = ['.xls', '.xlsx'].some(ext => file.name.toLowerCase().endsWith(ext))
  const isLt10M = file.size / 1024 / 1024 < 10

  if (!isValidType) {
    ElMessage.error('只支持上传 .xls 或 .xlsx 格式的文件!')
    return false
  }
  if (!isLt10M) {
    ElMessage.error('上传文件大小不能超过 10MB!')
    return false
  }

  return true
}

const handleImportSuccess = (response: any) => {
  ElMessage.success('批量导入成功')
  importVisible.value = false
  fetchProducts() // 重新获取列表
}

const handleImportError = (error: any) => {
  console.error('批量导入失败:', error)
  ElMessage.error('批量导入失败')
}

// 导出商品
const handleExport = async () => {
  try {
    // 这里实现导出功能
    ElMessage.success('导出功能开发中...')
  } catch (error) {
    console.error('导出商品失败:', error)
    ElMessage.error('导出商品失败')
  }
}

// 下载模板
const downloadTemplate = () => {
  // 这里实现下载模板功能
  ElMessage.success('下载模板功能开发中...')
}

// 格式化URL
const formatUrl = (url: string) => {
  if (!url) return '#'
  return url.startsWith('http') ? url : `http://${url}`
}

// 解析多个链接
const parseLinks = (linkString: string) => {
  if (!linkString) return []
  return linkString
    .split(/[;；\n\r]+/)
    .map(link => link.trim())
    .filter(link => link.length > 0)
}

// 格式化日期时间
const formatDateTime = (dateString: string) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  return isNaN(date.getTime()) ? '' : date.toLocaleString('zh-CN')
}

// 初始化
onMounted(() => {
  console.log('ProductList组件挂载，正在加载列设置和商品列表...')
  loadColumnSettings()
  fetchProducts()
})
</script>

<style scoped>
.product-list-container {
  padding: 20px;
}

.filter-card {
  margin-bottom: 20px;
}

.status-tabs-container {
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

.price {
  color: #f56c6c;
  font-weight: bold;
}

.product-uploader {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  width: 120px;
  height: 120px;
}

.product-uploader:hover {
  border-color: #409eff;
}

.column-settings {
  padding: 10px;
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
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #ebeef5;
}

.product-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  width: 120px;
  height: 120px;
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
}

.product-preview {
  width: 120px;
  height: 120px;
  display: block;
}

.upload-loading {
  width: 120px;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.upload-tip {
  font-size: 12px;
  color: #999;
  margin-top: 10px;
  text-align: center;
}

.import-content {
  text-align: center;
}

.import-upload {
  margin-bottom: 20px;
}

.template-download {
  margin-top: 20px;
}

.column-settings {
  max-height: 400px;
  overflow-y: auto;
  padding: 10px 0;
}

.settings-actions {
  display: flex;
  justify-content: space-between;
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid #eee;
}
</style>