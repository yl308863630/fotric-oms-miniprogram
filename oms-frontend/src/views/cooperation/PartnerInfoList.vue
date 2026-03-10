<template>
  <div class="partner-info-list">
    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm">
        <el-form-item label="名称">
          <el-input v-model="filterForm.name" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="身份">
          <el-select v-model="filterForm.identity" placeholder="请选择身份" clearable>
            <el-option label="代运营方" value="代运营方" />
            <el-option label="交付方" value="交付方" />
            <el-option label="出货方" value="出货方" />
            <el-option label="平台方" value="平台方" />
            <el-option label="工厂方" value="工厂方" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert v-if="isDeliveryOnlyScope" type="info" :closable="false" show-icon class="scope-tip">
      您仅可查看和编辑自己创建的合作方信息
    </el-alert>

    <el-card>
      <div class="table-ops">
        <el-button type="primary" @click="handleAdd">新增用户信息</el-button>
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
      <el-table :data="tableData" style="width: 100%" border stripe size="small">
        <el-table-column type="selection" width="55" />
        <el-table-column label="操作" width="120" fixed>
          <template #default="scope">
            <el-button link type="primary" @click="handleEdit(scope.row)">编辑</el-button>
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
            <template #default="scope" v-if="column.label === 'identities'">
              <el-tag v-for="identity in scope.row[column.label].split(',')" :key="identity" size="small" style="margin-right: 5px;">
                {{ identity }}
              </el-tag>
            </template>
            <template #default="scope" v-else-if="['name', 'title', 'taxNumber', 'bankName', 'bankAccount', 'bankAddress', 'contactPerson', 'contactPhone', 'email'].includes(column.label)">
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="800px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入名称（简称或显示名）" />
          <div class="form-item-tip">简称或显示名，用于列表展示</div>
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-select
            v-model="form.username"
            placeholder="本公司用户可从下拉选择；为其他抬头维护时请输入对方提供的登录名"
            clearable
            filterable
            allow-create
            default-first-option
            style="width: 100%"
          >
            <el-option v-for="user in users" :key="user.id" :label="user.username" :value="user.username" />
          </el-select>
          <div class="form-item-tip">选填。本公司用户可选；为其他抬头主体维护银行信息等时，可输入对方提供的登录名以便后续指派</div>
        </el-form-item>
        <el-form-item label="用户身份" prop="identities">
          <el-checkbox-group v-model="form.identities">
            <el-checkbox label="代运营方" />
            <el-checkbox label="交付方" />
            <el-checkbox label="出货方" />
            <el-checkbox label="平台方" />
            <el-checkbox label="工厂方" />
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="抬头信息" prop="title">
          <el-input v-model="form.title" placeholder="请输入抬头信息（合同/发票抬头）" />
          <div class="form-item-tip">合同/发票抬头，需与用户列表中「所在公司抬头」一致以便指派</div>
        </el-form-item>
        <el-form-item label="税号" prop="taxNumber">
          <el-input v-model="form.taxNumber" placeholder="请输入税号" />
        </el-form-item>
        <el-form-item label="开户行" prop="bankName">
          <el-input v-model="form.bankName" placeholder="请输入开户行" />
        </el-form-item>
        <el-form-item label="银行账号" prop="bankAccount">
          <el-input v-model="form.bankAccount" placeholder="请输入银行账号" />
        </el-form-item>
        <el-form-item label="开户行地址" prop="bankAddress">
          <el-input v-model="form.bankAddress" placeholder="请输入开户行地址" />
        </el-form-item>
        <el-form-item label="联系人" prop="contactPerson">
          <el-input v-model="form.contactPerson" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import draggable from 'vuedraggable'
import request from '@/utils/request'

interface ColumnConfig {
  label: string
  title: string
  width?: number
  minWidth?: number
  visible: boolean
}

interface FilterForm {
  name: string
  identity: string
}

const filterForm = ref<FilterForm>({
  name: '',
  identity: ''
})

const isDeliveryOnlyScope = computed(() => {
  const username = localStorage.getItem('username') || ''
  const companyTitle = localStorage.getItem('companyTitle') || ''
  if (username === 'admin') return false
  return !companyTitle.includes('飞础科智慧科技（上海）有限公司')
})

const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const tableData = ref<any[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()

const users = ref<any[]>([])

const form = ref({
  id: null,
  name: '',
  identities: [],
  title: '',
  taxNumber: '',
  bankName: '',
  bankAccount: '',
  bankAddress: '',
  contactPerson: '',
  contactPhone: '',
  email: '',
  username: ''
})

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  identities: [{ required: true, message: '请选择用户身份', trigger: 'change' }],
  username: [],
  title: [{ required: true, message: '请输入抬头信息', trigger: 'blur' }],
  taxNumber: [{ required: true, message: '请输入税号', trigger: 'blur' }],
  bankName: [{ required: true, message: '请输入开户行', trigger: 'blur' }],
  bankAccount: [{ required: true, message: '请输入银行账号', trigger: 'blur' }],
  bankAddress: [{ required: true, message: '请输入开户行地址', trigger: 'blur' }],
  contactPerson: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  contactPhone: [],
  email: []
}

const allColumns = ref<ColumnConfig[]>([
  { label: 'name', title: '名称', width: 150, visible: true },
  { label: 'username', title: '用户名', width: 120, visible: true },
  { label: 'identities', title: '用户身份', width: 200, visible: true },
  { label: 'title', title: '抬头信息', width: 180, visible: true },
  { label: 'taxNumber', title: '税号', width: 150, visible: true },
  { label: 'bankName', title: '开户行', width: 150, visible: true },
  { label: 'bankAccount', title: '银行账号', width: 150, visible: true },
  { label: 'bankAddress', title: '开户行地址', width: 180, visible: true },
  { label: 'contactPerson', title: '联系人', width: 120, visible: true },
  { label: 'contactPhone', title: '联系电话', width: 130, visible: true },
  { label: 'email', title: '邮箱', width: 180, visible: true },
  { label: 'createTime', title: '创建时间', width: 160, visible: true },
  { label: 'updateTime', title: '更新时间', width: 160, visible: true }
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
  const saved = localStorage.getItem('partnerInfoListColumns')
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
  
  localStorage.setItem('partnerInfoListColumns', JSON.stringify({
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

const fetchPartnerInfo = async () => {
  try {
    const res: any = await request.get('/partner-info', {
      params: {
        name: filterForm.value.name,
        identity: filterForm.value.identity,
        page: currentPage.value - 1,
        size: pageSize.value
      }
    })
    tableData.value = res.content || []
    total.value = res.totalElements || 0
  } catch (error) {
    console.error('获取用户信息失败:', error)
    ElMessage.error('获取用户信息失败')
  }
}

const handleAdd = () => {
  dialogTitle.value = '新增用户信息'
  resetForm()
  dialogVisible.value = true
}

const fetchUsers = async () => {
  try {
    const res: any = await request.get('/users', {
      params: {
        page: 0,
        size: 1000
      }
    })
    users.value = res.content || []
  } catch (error) {
    console.error('获取用户列表失败:', error)
  }
}

const handleEdit = (row: any) => {
  dialogTitle.value = '编辑用户信息'
  form.value = {
    id: row.id,
    name: row.name,
    identities: row.identities ? row.identities.split(',') : [],
    username: row.username,
    title: row.title,
    taxNumber: row.taxNumber,
    bankName: row.bankName,
    bankAccount: row.bankAccount,
    bankAddress: row.bankAddress,
    contactPerson: row.contactPerson,
    contactPhone: row.contactPhone,
    email: row.email
  }
  dialogVisible.value = true
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确认删除用户信息 "${row.name}"？`, '提示', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await request.delete(`/partner-info/${row.id}`)
    ElMessage.success('删除成功')
    fetchPartnerInfo()
  } catch (error: any) {
    if (error === 'cancel') return
    if (error?.response?.status === 403) {
      ElMessage.error('无权限操作该合作方信息')
    } else {
      console.error('删除用户信息失败:', error)
      ElMessage.error('删除用户信息失败')
    }
  }
}

const submitForm = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    const submitData = {
      ...form.value,
      identities: form.value.identities.join(',')
    }
    if (form.value.id) {
      await request.put(`/partner-info/${form.value.id}`, submitData)
      ElMessage.success('更新成功')
    } else {
      await request.post('/partner-info', submitData)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchPartnerInfo()
  } catch (error: any) {
    if (error?.name === 'ValidationError') {
      ElMessage.warning('请完善必填字段信息')
      return
    }
    if (error?.response?.status === 403) {
      ElMessage.error('无权限操作该合作方信息')
      return
    }
    console.error('提交失败:', error)
    ElMessage.error('提交失败')
  }
}

const resetForm = () => {
  form.value = {
    id: null,
    name: '',
    identities: [],
    username: '',
    title: '',
    taxNumber: '',
    bankName: '',
    bankAccount: '',
    bankAddress: '',
    contactPerson: '',
    contactPhone: '',
    email: ''
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchPartnerInfo()
}

const resetSearch = () => {
  filterForm.value = { name: '', identity: '' }
  currentPage.value = 1
  fetchPartnerInfo()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchPartnerInfo()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  fetchPartnerInfo()
}

onMounted(() => {
  fetchPartnerInfo()
  fetchUsers()
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
.form-item-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}
.scope-tip {
  margin-bottom: 12px;
}
</style>