<template>
  <div class="user-list" v-if="canAccessUserList">
    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm">
        <el-form-item label="用户名">
          <el-input v-model="filterForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="真实姓名">
          <el-input v-model="filterForm.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="filterForm.role" placeholder="请选择角色" clearable style="width: 150px">
            <el-option label="管理员" value="ROLE_ADMIN" />
            <el-option label="工业电商" value="ROLE_ECOMMERCE" />
            <el-option label="交付方" value="ROLE_DELIVERY" />
            <el-option label="出货方" value="ROLE_SHIPPING" />
            <el-option label="仓库" value="ROLE_WAREHOUSE" />
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
        <el-button type="primary" @click="handleAdd">新增用户</el-button>
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
        <el-table-column label="操作" width="120" fixed>
          <template #default="scope">
            <el-button link type="primary" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button link :type="scope.row.enabled ? 'danger' : 'success'" @click="handleToggleStatus(scope.row)">
              {{ scope.row.enabled ? '禁用' : '启用' }}
            </el-button>
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
            <template #default="scope" v-if="column.label === 'role'">
              <el-tag size="small" :type="getRoleTagType(scope.row[column.label])">
                {{ getRoleLabel(scope.row[column.label]) }}
              </el-tag>
            </template>
            <template #default="scope" v-else-if="column.label === 'createTime' || column.label === 'lastLoginTime'">
              {{ formatDateTime(scope.row[column.label]) }}
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

    <!-- 用户编辑/新增弹窗 -->
    <el-dialog
      :title="dialogTitle"
      v-model="dialogVisible"
      width="600px"
      @close="resetForm"
    >
      <el-form :model="userForm" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="userForm.username" placeholder="请输入用户名" :disabled="!!userForm.id" />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="userForm.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!userForm.id">
          <el-input v-model="userForm.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="修改密码" v-else>
          <el-input v-model="userForm.password" type="password" placeholder="留空则不修改密码" show-password />
        </el-form-item>
        <el-form-item label="手机号码" prop="phone">
          <el-input v-model="userForm.phone" placeholder="请输入手机号码" />
        </el-form-item>
        <el-form-item label="所属部门" prop="department">
          <el-input v-model="userForm.department" placeholder="请输入所属部门" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="userForm.role" placeholder="请选择角色" style="width: 100%">
            <el-option label="管理员" value="ROLE_ADMIN" />
            <el-option label="工业电商" value="ROLE_ECOMMERCE" />
            <el-option label="交付方" value="ROLE_DELIVERY" />
            <el-option label="出货方" value="ROLE_SHIPPING" />
            <el-option label="仓库" value="ROLE_WAREHOUSE" />
          </el-select>
        </el-form-item>
        <el-form-item label="所在公司抬头" prop="companyTitle">
          <el-select
            v-model="userForm.companyTitle"
            placeholder="请选择或输入所在公司抬头（可输入新抬头）"
            clearable
            filterable
            allow-create
            default-first-option
            :disabled="companyTitleDisabled"
            style="width: 100%"
          >
            <el-option
              v-for="partner in partnerInfoList"
              :key="partner.id"
              :label="partner.title"
              :value="partner.title"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="权限配置">
          <el-checkbox-group v-model="userForm.permissions">
            <el-checkbox label="dashboard">首页</el-checkbox>
            <el-checkbox label="product">我的商品</el-checkbox>
            <el-checkbox label="opportunity">商机管理</el-checkbox>
            <el-checkbox label="sales">销售管理</el-checkbox>
            <el-checkbox label="purchase">采购管理</el-checkbox>
            <el-checkbox label="warehouse">仓库（可见全量销售订单便于发货）</el-checkbox>
            <el-checkbox label="settlement">客户结算</el-checkbox>
            <el-checkbox label="cooperation">合作管理</el-checkbox>
            <el-checkbox label="user">用户管理</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
  <div v-else class="no-access">
    <el-result icon="warning" title="无权访问" sub-title="该页面仅限管理员或具有用户管理权限的交付方访问">
    </el-result>
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
  visible: boolean
}

const isAdmin = computed(() => localStorage.getItem('username') === 'admin')

const currentUserRole = computed(() => localStorage.getItem('role') || '')
const currentUserPermissions = computed(() => {
  const p = localStorage.getItem('permissions')
  return p ? p.split(',').map((s: string) => s.trim()).filter(Boolean) : []
})
const canAccessUserList = computed(() => {
  if (isAdmin.value) return true
  return currentUserRole.value === 'ROLE_DELIVERY' && currentUserPermissions.value.includes('user')
})
const isDeliveryOnly = computed(() => !isAdmin.value && currentUserRole.value === 'ROLE_DELIVERY')
const companyTitleDisabled = computed(() => isDeliveryOnly.value && !userForm.id)

const loading = ref(false)
const filterForm = ref({
  username: '',
  realName: '',
  role: ''
})

const tableData = ref<any[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增用户')
const formRef = ref()
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const partnerInfoList = ref<any[]>([])

// 定义所有列配置
const allColumns = ref<ColumnConfig[]>([
  { label: 'username', title: '用户名', width: 120, visible: true },
  { label: 'realName', title: '真实姓名', width: 120, visible: true },
  { label: 'phone', title: '手机号码', width: 120, visible: true },
  { label: 'department', title: '所属部门', width: 150, visible: true },
  { label: 'role', title: '角色', width: 120, visible: true },
  { label: 'companyTitle', title: '所在公司抬头', width: 200, visible: true },
  { label: 'createTime', title: '创建时间', width: 160, visible: true },
  { label: 'lastLoginTime', title: '最后登录时间', width: 160, visible: true }
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
  const saved = localStorage.getItem('userListColumns')
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
  
  localStorage.setItem('userListColumns', JSON.stringify({
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

const userForm = reactive({
  id: undefined,
  username: '',
  password: '',
  realName: '',
  phone: '',
  department: '',
  role: 'ROLE_ECOMMERCE',
  companyTitle: '',
  permissions: [] as string[],
  enabled: true
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号码', trigger: 'blur' }],
  department: [{ required: true, message: '请输入所属部门', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  companyTitle: [{ required: true, message: '请选择所在公司抬头', trigger: 'change' }]
}

const getRoleLabel = (role: string) => {
  const map: any = {
    'ROLE_ADMIN': '管理员',
    'ROLE_ECOMMERCE': '工业电商',
    'ROLE_DELIVERY': '交付方',
    'ROLE_SHIPPING': '出货方',
    'ROLE_WAREHOUSE': '仓库'
  }
  return map[role] || role
}

const getRoleTagType = (role: string) => {
  const map: any = {
    'ROLE_ADMIN': 'danger',
    'ROLE_ECOMMERCE': 'success',
    'ROLE_DELIVERY': 'warning',
    'ROLE_SHIPPING': 'info',
    'ROLE_WAREHOUSE': ''
  }
  return map[role] || ''
}

const fetchUsers = async () => {
  loading.value = true
  try {
    const res: any = await request.get('/users', {
      params: {
        ...filterForm.value,
        page: currentPage.value - 1,
        size: pageSize.value
      }
    })
    const content = res?.content || (Array.isArray(res) ? res : [])
    tableData.value = content.map((u: any) => ({
      ...u,
      permissions: u.permissions ? u.permissions.split(',') : []
    }))
    total.value = res?.totalElements || content.length || 0
  } catch (error) {
    console.error('Fetch users error:', error)
    ElMessage.error('获取用户列表失败')
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  dialogTitle.value = '新增用户'
  resetForm()
  if (isDeliveryOnly.value) {
    userForm.companyTitle = localStorage.getItem('companyTitle') || ''
  }
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  dialogTitle.value = '编辑用户'
  Object.assign(userForm, {
    ...row,
    password: '' // 编辑时密码留空
  })
  dialogVisible.value = true
}

const handleToggleStatus = (row: any) => {
  const status = !row.enabled
  ElMessageBox.confirm(`确定要${status ? '启用' : '禁用'}该用户吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      const data = {
        ...row,
        enabled: status,
        permissions: Array.isArray(row.permissions) ? row.permissions.join(',') : row.permissions
      }
      await request.put(`/users/${row.id}`, data)
      ElMessage.success('操作成功')
      fetchUsers()
    } catch (error) {
      ElMessage.error('操作失败')
    }
  })
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      try {
        const data = {
          ...userForm,
          permissions: userForm.permissions.join(',')
        }
        if (userForm.id) {
          await request.put(`/users/${userForm.id}`, data)
          ElMessage.success('更新成功')
        } else {
          await request.post('/users', data)
          ElMessage.success('新增成功')
        }
        dialogVisible.value = false
        fetchUsers()
      } catch (error: any) {
        if (error?.response?.status === 403) {
          ElMessage.error('无权限操作其他公司用户')
        } else {
          ElMessage.error('保存失败')
        }
      }
    }
  })
}

const resetForm = () => {
  Object.assign(userForm, {
    id: undefined,
    username: '',
    password: '',
    realName: '',
    phone: '',
    department: '',
    role: 'ROLE_ECOMMERCE',
    companyTitle: '',
    permissions: [],
    enabled: true
  })
  if (formRef.value) formRef.value.resetFields()
}

const formatDateTime = (date: string) => {
  if (!date) return '-'
  const d = new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const fetchPartnerInfoList = async () => {
  try {
    const res: any = await request.get('/partner-info')
    const content = res?.content || (Array.isArray(res) ? res : [])
    partnerInfoList.value = content
  } catch (error) {
    console.error('Fetch partner info list error:', error)
  }
}

onMounted(() => {
  if (isAdmin.value) {
    fetchUsers()
    fetchPartnerInfoList()
    loadSettings()
  }
})

const handleSearch = () => {
  currentPage.value = 1
  fetchUsers()
}

const resetSearch = () => {
  filterForm.value = { username: '', realName: '', role: '' }
  currentPage.value = 1
  fetchUsers()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  fetchUsers()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  fetchUsers()
}
</script>

<style scoped>
.user-list {
  padding: 20px;
}
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
.no-access {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 80vh;
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
