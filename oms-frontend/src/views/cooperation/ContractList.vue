<template>
  <div class="contract-list">
    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm">
        <el-form-item label="合同编号">
          <el-input v-model="filterForm.contractNo" placeholder="请输入合同编号" />
        </el-form-item>
        <el-form-item label="合同状态">
          <el-select v-model="filterForm.status" placeholder="请选择合同状态" clearable>
            <el-option label="草稿" value="草稿" />
            <el-option label="待签署" value="待签署" />
            <el-option label="已签署" value="已签署" />
            <el-option label="已归档" value="已归档" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <div class="table-ops">
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
      <el-table :data="tableData" style="width: 100%" border stripe size="small" v-loading="loading">
        <el-table-column type="selection" width="55" />
        <el-table-column label="操作" width="300" fixed>
          <template #default="scope">
            <el-button link type="primary" @click="handleView(scope.row)">详情</el-button>
            <el-button link type="primary" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button link type="warning" @click="handleSignPartyB(scope.row)" v-if="canSignPartyB(scope.row)" :loading="signingPartyB">乙方签署</el-button>
            <el-button link type="warning" @click="handleDownload(scope.row)" :loading="downloadingId === scope.row.id">下载</el-button>
            <el-button link type="danger" @click="handleDelete(scope.row)">删除</el-button>
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
            <template #default="scope" v-else-if="column.label === 'totalAmount'">
              ¥{{ scope.row[column.label] }}
            </template>
            <template #default="scope" v-else-if="column.label === 'partyASigned'">
              <el-tag :type="scope.row[column.label] ? 'success' : 'info'" size="small">
                {{ scope.row[column.label] ? '已签署' : '未签署' }}
              </el-tag>
            </template>
            <template #default="scope" v-else-if="column.label === 'partyBSigned'">
              <el-tag :type="scope.row[column.label] ? 'success' : 'info'" size="small">
                {{ scope.row[column.label] ? '已签署' : '未签署' }}
              </el-tag>
            </template>
            <template #default="scope" v-else-if="['contractNo', 'partyAName', 'partyBName'].includes(column.label)">
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

    <el-dialog v-model="showDetailDialog" title="合同详情" width="800px">
      <el-descriptions v-if="currentContract" :column="2" border>
        <el-descriptions-item label="合同编号">{{ currentContract.contractNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(currentContract.status)">{{ currentContract.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="甲方名称">{{ currentContract.partyAName }}</el-descriptions-item>
        <el-descriptions-item label="乙方名称">{{ currentContract.partyBName }}</el-descriptions-item>
        <el-descriptions-item label="产品型号">{{ currentContract.productModel }}</el-descriptions-item>
        <el-descriptions-item label="物料号">{{ currentContract.materialNo }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ currentContract.quantity }}</el-descriptions-item>
        <el-descriptions-item label="单价">¥{{ currentContract.unitPrice }}</el-descriptions-item>
        <el-descriptions-item label="总金额">¥{{ currentContract.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="签署日期">{{ currentContract.signDate }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ currentContract.createTime }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="showEditDialog" title="编辑合同" width="800px">
      <el-form v-if="currentContract" :model="editForm" label-width="120px">
        <el-form-item label="合同编号">
          <el-input v-model="editForm.contractNo" disabled />
        </el-form-item>
        <el-form-item label="甲方名称">
          <el-input v-model="editForm.partyAName" />
        </el-form-item>
        <el-form-item label="乙方名称">
          <el-input v-model="editForm.partyBName" />
        </el-form-item>
        <el-form-item label="产品型号">
          <el-input v-model="editForm.productModel" />
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="editForm.quantity" :min="1" />
        </el-form-item>
        <el-form-item label="单价">
          <el-input-number v-model="editForm.unitPrice" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="总金额">
          <el-input-number v-model="editForm.totalAmount" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="签署日期">
          <el-date-picker v-model="editForm.signDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import draggable from 'vuedraggable'
import request from '@/utils/request'
import axios from 'axios'

interface ColumnConfig {
  label: string
  title: string
  width?: number
  minWidth?: number
  visible: boolean
}

interface FilterForm {
  contractNo: string
  status: string
}

interface Contract {
  id: number
  contractNo: string
  salesOrderId?: number
  salesId?: number
  salesName?: string
  partyAName?: string
  partyAAddress?: string
  partyABank?: string
  partyAAccount?: string
  partyATaxNo?: string
  partyAPhone?: string
  partyBName?: string
  partyBAddress?: string
  partyBBank?: string
  partyBAccount?: string
  partyBTaxNo?: string
  partyBPhone?: string
  productName?: string
  productModel?: string
  materialNo?: string
  productConfig?: string
  warrantyPeriod?: string
  quantity?: number
  unitPrice?: number
  totalAmount?: number
  deliveryMethod?: string
  signDate?: string
  status: string
  templateUrl?: string
  generatedUrl?: string
  signedUrl?: string
  protectedPdfUrl?: string
  protectedImageUrl?: string
  createTime?: string
  updateTime?: string
}

const filterForm = ref<FilterForm>({
  contractNo: '',
  status: ''
})

const tableData = ref<Contract[]>([])
const loading = ref(false)
const signingPartyB = ref(false)
/** 当前正在下载的合同 id，仅该行显示 loading */
const downloadingId = ref<number | null>(null)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const showDetailDialog = ref(false)
const showEditDialog = ref(false)
const currentContract = ref<Contract | null>(null)
const editForm = ref<any>({})

const allColumns = ref<ColumnConfig[]>([
  { label: 'contractNo', title: '合同编号', width: 150, visible: true },
  { label: 'salesName', title: '业务员', width: 120, visible: true },
  { label: 'partyAName', title: '甲方名称', minWidth: 180, visible: true },
  { label: 'partyASigned', title: '甲方签署', width: 100, visible: true },
  { label: 'partyBName', title: '乙方名称', minWidth: 180, visible: true },
  { label: 'partyBSigned', title: '乙方签署', width: 100, visible: true },
  { label: 'productModel', title: '产品型号', width: 150, visible: true },
  { label: 'materialNo', title: '物料号', width: 150, visible: true },
  { label: 'quantity', title: '数量', width: 80, visible: true },
  { label: 'totalAmount', title: '总金额', width: 120, visible: true },
  { label: 'signDate', title: '签署日期', width: 120, visible: true },
  { label: 'status', title: '状态', width: 100, visible: true },
  { label: 'createTime', title: '创建时间', width: 160, visible: true }
])

const orderedColumns = ref<ColumnConfig[]>([...allColumns.value])

const visibleColumns = computed<string[]>({
  get: () => orderedColumns.value.filter(col => col.visible).map(col => col.label),
  set: (value) => {
    allColumns.value.forEach(col => {
      col.visible = value.includes(col.label)
    })
    orderedColumns.value = allColumns.value.filter(col => value.includes(col.label))
      .concat(allColumns.value.filter(col => !value.includes(col.label)))
  }
})

const loadSettings = () => {
  const saved = localStorage.getItem('contractListColumns')
  if (saved) {
    try {
      const settings = JSON.parse(saved)
      if (settings.columnsOrder) {
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
  
  localStorage.setItem('contractListColumns', JSON.stringify({
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
  console.log('列顺序已更新')
}

const fetchContracts = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/contracts', {
      params: {
        ...filterForm.value,
        page: currentPage.value - 1,
        size: pageSize.value
      }
    })
    tableData.value = res.content || []
    total.value = res.totalElements || 0
  } catch (error) {
    console.error('Fetch contracts error:', error)
    ElMessage.error('获取合同列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchContracts()
}

const resetSearch = () => {
  filterForm.value = { contractNo: '', status: '' }
  currentPage.value = 1
  fetchContracts()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchContracts()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  fetchContracts()
}

const getStatusType = (status: string) => {
  switch (status) {
    case '已签署': return 'success'
    case '待签署': return 'warning'
    case '草稿': return 'info'
    case '已归档': return 'info'
    default: return 'info'
  }
}

const handleView = (row: Contract) => {
  currentContract.value = row
  showDetailDialog.value = true
}

const handleEdit = (row: Contract) => {
  currentContract.value = { ...row }
  editForm.value = { ...row }
  showEditDialog.value = true
}

const handleSaveEdit = async () => {
  try {
    await request.put(`/contracts/${editForm.value.id}`, editForm.value)
    ElMessage.success('合同更新成功')
    showEditDialog.value = false
    fetchContracts()
  } catch (error) {
    console.error('Update contract error:', error)
    ElMessage.error('合同更新失败')
  }
}

const canSignPartyB = (row: Contract) => {
  const companyTitle = localStorage.getItem('companyTitle')
  return !row.partyBSigned && row.partyBName === companyTitle
}

const handleSignPartyB = (row: Contract) => {
  ElMessageBox.confirm('确认作为乙方签署该合同？', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      signingPartyB.value = true
      await request.post(`/contracts/${row.id}/sign`, { partyType: 'B' })
      ElMessage.success('乙方签署成功')
      fetchContracts()
    } catch (error: any) {
      console.error('Sign contract error:', error)
      ElMessage.error(error.response?.data?.message || '合同签署失败')
    } finally {
      signingPartyB.value = false
    }
  }).catch(() => {})
}

const handleDownload = async (row: Contract) => {
  if (downloadingId.value != null) {
    ElMessage.warning('正在下载合同，请稍候...')
    return
  }
  
  try {
    downloadingId.value = row.id ?? null
    const token = localStorage.getItem('token')
    const response = await axios.get(`/api/contracts/${row.id}/download`, {
      responseType: 'blob',
      headers: {
        Authorization: token ? `Bearer ${token}` : ''
      }
    })
    
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    
    let fileName = `${row.contractNo}.docx`
    const contentDisposition = response.headers?.['content-disposition']
    if (contentDisposition) {
      const match = contentDisposition.match(/filename\*?=(?:UTF-8''|\"?)([^\";]+)/i)
      if (match && match[1]) {
        fileName = decodeURIComponent(match[1])
      }
    } else if (response.headers?.['content-type']?.includes('pdf')) {
      fileName = `${row.contractNo}.pdf`
    } else if (response.headers?.['content-type']?.includes('image/png')) {
      fileName = `${row.contractNo}.png`
    } else if (response.headers?.['content-type']?.includes('image/jpeg')) {
      fileName = `${row.contractNo}.jpg`
    } else if (response.headers?.['content-type']?.includes('zip')) {
      fileName = `${row.contractNo}.zip`
    } else if (row.protectedPdfUrl) {
      fileName = row.protectedPdfUrl.includes('_protected')
        ? `${row.contractNo}_protected.pdf`
        : `${row.contractNo}.pdf`
    } else if (row.protectedImageUrl) {
      fileName = row.protectedImageUrl.endsWith('.zip')
        ? `${row.contractNo}.zip`
        : `${row.contractNo}.png`
    }
    link.setAttribute('download', fileName)
    
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    
    ElMessage.success('下载成功')
  } catch (error: any) {
    console.error('Download contract error:', error)
    if (error.response && error.response.status === 404) {
      ElMessage.error('合同文件不存在，请重新生成合同')
    } else {
      ElMessage.error('下载失败')
    }
  } finally {
    downloadingId.value = null
  }
}

const handleDelete = (row: Contract) => {
  ElMessageBox.confirm('确认删除该合同？', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/contracts/${row.id}`)
      ElMessage.success('合同删除成功')
      fetchContracts()
    } catch (error) {
      console.error('Delete contract error:', error)
      ElMessage.error('合同删除失败')
    }
  }).catch(() => {})
}

onMounted(() => {
  fetchContracts()
  loadSettings()
})
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
