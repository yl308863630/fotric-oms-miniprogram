<template>
  <div class="partner-info-list mobile-list-layout">
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
            <el-option label="甲方" value="平台方" />
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
      合作方主数据现已支持跨主体直接引用；编辑和删除仍仅限创建人、管理员或飞础科账号
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
      <div class="table-wrapper">
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
            <template #default="scope" v-else-if="column.label === 'contactPerson'">
              <div style="display: flex; align-items: center; gap: 4px;">
                <span>{{ maskName(scope.row.contactPerson) }}</span>
                <el-button link type="primary" size="small" @click="copyPartnerField(scope.row, 'contactPerson', scope.row.contactPerson)" title="复制">
                  <el-icon><CopyDocument /></el-icon>
                </el-button>
              </div>
            </template>
            <template #default="scope" v-else-if="column.label === 'contactPhone'">
              <div style="display: flex; align-items: center; gap: 4px;">
                <span>{{ maskPhone(scope.row.contactPhone) }}</span>
                <el-button link type="primary" size="small" @click="copyPartnerField(scope.row, 'contactPhone', scope.row.contactPhone)" title="复制">
                  <el-icon><CopyDocument /></el-icon>
                </el-button>
              </div>
            </template>
            <template #default="scope" v-else-if="column.label === 'subjectGroupStatus'">
              <template v-if="scope.row.subjectGroupStatus">
                <el-tag :type="scope.row.subjectGroupStatusType || 'info'" size="small">
                  {{ scope.row.subjectGroupStatus }}
                </el-tag>
              </template>
              <span v-else>-</span>
            </template>
            <template #default="scope" v-else-if="column.label === 'email'">
              <div style="display: flex; align-items: center; gap: 4px;">
                <span>{{ maskEmail(scope.row.email) }}</span>
                <el-button link type="primary" size="small" @click="copyPartnerField(scope.row, 'email', scope.row.email)" title="复制">
                  <el-icon><CopyDocument /></el-icon>
                </el-button>
              </div>
            </template>
            <template #default="scope" v-else-if="['name', 'title', 'taxNumber', 'bankName', 'bankAccount', 'bankAddress'].includes(column.label)">
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="800px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入名称（简称或显示名）" @blur="onNameBlur" />
          <div class="form-item-tip">简称或显示名，用于列表展示；与联系人一一对应时可失焦自动带出</div>
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input
            v-if="form.id"
            v-model="form.username"
            :disabled="true"
          />
          <el-select
            v-else
            v-model="form.username"
            filterable
            allow-create
            default-first-option
            clearable
            placeholder="请选择已有账号，或直接输入新账号"
            @change="handleUsernameChange"
          >
            <el-option
              v-for="user in userSelectOptions"
              :key="user.username"
              :label="user.label"
              :value="user.username"
            />
          </el-select>
          <div class="form-item-tip">可直接引用用户库已有账号；若输入新账号并填写下方密码，保存时会同时创建账号</div>
        </el-form-item>
        <el-alert
          v-if="duplicateCheckResult?.matched"
          :type="duplicateCheckResult.block ? 'warning' : 'info'"
          :closable="false"
          show-icon
          class="duplicate-alert"
        >
          <template #title>
            {{ duplicateCheckResult.message || '检测到可能重复的主体账号，请优先引用已有账号。' }}
          </template>
          <div v-if="duplicateCheckResult.suggestedPrimaryUsername" class="duplicate-alert-line">
            建议主账号：{{ duplicateCheckResult.suggestedPrimaryUsername }}
          </div>
          <div v-if="Array.isArray(duplicateCheckResult.candidates) && duplicateCheckResult.candidates.length" class="duplicate-alert-line">
            已有记录：
            {{ formatDuplicateCandidates(duplicateCheckResult.candidates) }}
          </div>
        </el-alert>
        <el-form-item v-if="!form.id" label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="仅新建账号时填写" show-password />
        </el-form-item>
        <el-form-item v-if="!form.id" label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="仅新建账号时填写" show-password />
        </el-form-item>
        <el-form-item v-if="form.id && canEditPassword" label="修改密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" placeholder="留空则不修改密码" show-password />
        </el-form-item>
        <el-form-item v-if="form.id && canEditPassword" label="确认密码" prop="confirmNewPassword">
          <el-input v-model="form.confirmNewPassword" type="password" placeholder="留空则不修改" show-password />
        </el-form-item>
        <el-form-item label="用户身份" prop="identities">
          <el-checkbox-group v-model="form.identities">
            <el-checkbox label="代运营方" />
            <el-checkbox label="交付方" />
            <el-checkbox label="出货方" />
            <el-checkbox label="平台方">甲方</el-checkbox>
            <el-checkbox label="工厂方" />
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="智能识别（文本）">
          <el-input
            v-model="partnerInfoPasteText"
            type="textarea"
            :rows="5"
            placeholder="请粘贴整段文字（每行「标签：内容」），例如：公司名称、税号、开户银行、帐号、地址、电话、行号等；点击下方按钮自动填入下方表单。"
          />
          <div class="smart-parse-actions">
            <el-button type="danger" plain @click="applyPartnerInfoSmartParse">识别并填充</el-button>
            <el-button @click="partnerInfoPasteText = ''">清空文本</el-button>
          </div>
          <div class="form-item-tip">
            按行识别中英文冒号后的内容；「地址」会填入「开户行地址」；「行号」会附在该字段末尾（系统暂无单独行号字段）。可与下方「从文件解析」配合使用。
          </div>
        </el-form-item>
        <el-form-item label="从文件解析">
          <el-upload
            ref="partnerInfoImportUploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="onPartnerInfoImportFileChange"
            accept=".pdf,.doc,.docx,image/*"
            show-file-list
          >
            <el-button type="success" plain :loading="partnerInfoImportLoading">上传 PDF/Word/图片解析</el-button>
          </el-upload>
          <div class="form-item-tip">上传合同或证照 PDF、Word 或图片，自动识别抬头、税号、开户行、银行账号、开户行地址、联系人、电话、邮箱（Tesseract 免费开源，图片需服务器已安装）</div>
        </el-form-item>
        <el-form-item label="抬头信息" prop="title">
          <el-input v-model="form.title" placeholder="请输入抬头信息（合同/发票抬头）" @blur="handleTitleBlur" />
          <div class="form-item-tip">合同/发票抬头，需与用户列表中「所在公司抬头」一致；与已有记录一致时可自动带出税号/开户行等</div>
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
          <el-input v-model="form.contactPerson" placeholder="请输入联系人" @blur="triggerDuplicateCheck" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" placeholder="请输入联系电话" @blur="triggerDuplicateCheck" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <div v-if="!form.id" class="form-item-tip default-permissions-tip">
          默认开放权限：首页、我的商品、销售管理、采购管理、客户结算、结算-销售维护、结算-财务维护、采购-发起付款申请、采购-进项发票跟进、采购-财务打款与凭证、合作管理
        </div>
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
import { CopyDocument } from '@element-plus/icons-vue'
import draggable from 'vuedraggable'
import request from '@/utils/request'
import { maskName, maskPhone, maskEmail, copyWithPrivacyLog } from '@/utils/privacy'
import { parsePartnerInfoFromPastedText } from '@/utils/partnerInfoSmartParse'

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
const partnerInfoImportLoading = ref(false)
const partnerInfoImportUploadRef = ref<any>(null)
/** 智能识别：用户粘贴的多行文本 */
const partnerInfoPasteText = ref('')
const duplicateCheckResult = ref<any>(null)
const subjectGroupMap = ref<Record<string, any>>({})

const users = ref<any[]>([])
const userSelectOptions = computed(() => {
  return (users.value || [])
    .map((u: any) => {
      const username = String(u?.username || '').trim()
      const realName = String(u?.realName || '').trim()
      const companyTitle = String(u?.companyTitle || '').trim()
      return {
        username,
        label: [realName ? `${realName} (${username})` : username, companyTitle].filter(Boolean).join(' - '),
        raw: u
      }
    })
    .filter((u: any) => !!u.username)
    .sort((a: any, b: any) => a.username.localeCompare(b.username, 'zh-CN'))
})

const form = ref({
  id: null as number | null,
  name: '',
  identities: [] as string[],
  title: '',
  taxNumber: '',
  bankName: '',
  bankAccount: '',
  bankAddress: '',
  contactPerson: '',
  contactPhone: '',
  email: '',
  username: '',
  password: '',
  confirmPassword: '',
  newPassword: '',
  confirmNewPassword: ''
})

const currentUserId = ref<number | null>(null)
const currentUsername = ref('')
const editingRowCreatedBy = ref<number | null>(null)
const editingRowUsername = ref('')

const canEditPassword = computed(() => {
  if (!editingRowCreatedBy.value && !editingRowUsername.value) return false
  if (currentUserId.value != null && editingRowCreatedBy.value != null && currentUserId.value === editingRowCreatedBy.value) return true
  if (currentUsername.value && editingRowUsername.value && currentUsername.value === editingRowUsername.value) return true
  return false
})

const validateConfirmPassword = (_rule: any, value: string, callback: (e?: Error) => void) => {
  if (!form.value.id && form.value.password && value !== form.value.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}
const validateConfirmNewPassword = (_rule: any, value: string, callback: (e?: Error) => void) => {
  if (form.value.newPassword && value !== form.value.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  identities: [{ required: true, message: '请选择用户身份', trigger: 'change' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }],
  newPassword: [],
  confirmNewPassword: [{ validator: validateConfirmNewPassword, trigger: 'blur' }],
  title: [{ required: true, message: '请输入抬头信息', trigger: 'blur' }],
  taxNumber: [{ required: true, message: '请输入税号', trigger: 'blur' }],
  bankName: [{ required: true, message: '请输入开户行', trigger: 'blur' }],
  bankAccount: [{ required: true, message: '请输入银行账号', trigger: 'blur' }],
  bankAddress: [{ required: true, message: '请输入开户行地址', trigger: 'blur' }],
  contactPerson: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  contactPhone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }],
  email: []
}

const allColumns = ref<ColumnConfig[]>([
  { label: 'name', title: '名称', width: 150, visible: true },
  { label: 'username', title: '用户名', width: 120, visible: true },
  { label: 'subjectGroupStatus', title: '归组状态', width: 180, visible: true },
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
    await populateSubjectGroupStatus()
    total.value = res.totalElements || 0
  } catch (error) {
    console.error('获取用户信息失败:', error)
    ElMessage.error('获取用户信息失败')
  }
}

const canManageSubjectGroups = computed(() => {
  const username = (localStorage.getItem('username') || '').trim()
  const companyTitle = (localStorage.getItem('companyTitle') || '').trim()
  return username === 'admin' || companyTitle.includes('飞础科智慧科技（上海）有限公司')
})

const buildSubjectGroupStatus = (username: string) => {
  const normalized = String(username || '').trim()
  if (!normalized) return { text: '', type: '' }
  const detail = subjectGroupMap.value[normalized]
  if (!detail?.manualBound) {
    return { text: '未手工归组', type: 'info' }
  }
  if (detail?.primaryActor) {
    return { text: '主账号', type: 'success' }
  }
  const primary = String(detail?.primaryUsername || '').trim()
  return { text: primary ? `次账号 -> ${primary}` : '次账号', type: 'warning' }
}

const populateSubjectGroupStatus = async () => {
  const usernames = (tableData.value || [])
    .map((item: any) => String(item?.username || '').trim())
    .filter(Boolean)
  if (!canManageSubjectGroups.value || !usernames.length) {
    subjectGroupMap.value = {}
    tableData.value = (tableData.value || []).map((item: any) => ({
      ...item,
      subjectGroupStatus: item?.username ? '无查看权限' : '',
      subjectGroupStatusType: item?.username ? '' : ''
    }))
    return
  }
  try {
    const res: any = await request.post('/subject-account-groups/lookup-batch', { usernames })
    subjectGroupMap.value = res && typeof res === 'object' ? res : {}
  } catch (error) {
    console.warn('获取归组状态失败', error)
    subjectGroupMap.value = {}
  }
  tableData.value = (tableData.value || []).map((item: any) => {
    const status = buildSubjectGroupStatus(item?.username)
    return {
      ...item,
      subjectGroupStatus: status.text,
      subjectGroupStatusType: status.type
    }
  })
}

const handleAdd = () => {
  dialogTitle.value = '新增用户信息'
  resetForm()
  dialogVisible.value = true
}

const fetchUsers = async () => {
  try {
    const res: any = await request.get('/partner-info/reference-users')
    users.value = Array.isArray(res) ? res : []
  } catch (error) {
    console.error('获取用户列表失败:', error)
  }
}

const onNameBlur = () => {
  if (form.value.name && !form.value.contactPerson) {
    form.value.contactPerson = form.value.name
  }
}

const handleUsernameChange = async (username: string) => {
  const normalized = String(username || '').trim()
  const selected = (users.value || []).find((u: any) => String(u?.username || '').trim() === normalized)
  const selectedFallbackName = selected?.realName || selected?.username || ''
  if (normalized) {
    try {
      const list: any = await request.get('/partner-info/by-username', { params: { username: normalized }, skipErrorMsg: true } as any)
      const info = Array.isArray(list) && list.length ? list[0] : null
      if (info) {
        const currentName = String(form.value.name || '').trim()
        const currentContactPerson = String(form.value.contactPerson || '').trim()
        const currentContactPhone = String(form.value.contactPhone || '').trim()
        if (!currentName || currentName === selectedFallbackName) form.value.name = info.name || normalized
        if (!currentContactPerson || currentContactPerson === selectedFallbackName || currentContactPerson === normalized) {
          form.value.contactPerson = info.contactPerson || ''
        }
        if (!currentContactPhone || (selected?.phone && currentContactPhone === selected.phone)) form.value.contactPhone = info.contactPhone || ''
        if (!String(form.value.title || '').trim() || (selected?.companyTitle && String(form.value.title || '').trim() === String(selected.companyTitle || '').trim())) {
          form.value.title = info.title || ''
        }
        if (!String(form.value.email || '').trim()) form.value.email = info.email || ''
      }
    } catch {
      // 静默忽略，仅作为增强带出
    }
  }
  if (selected) {
    if (!String(form.value.name || '').trim()) form.value.name = selectedFallbackName
    if (!String(form.value.contactPerson || '').trim()) form.value.contactPerson = selectedFallbackName
    if (!String(form.value.contactPhone || '').trim()) form.value.contactPhone = selected.phone || ''
    if (!String(form.value.title || '').trim()) form.value.title = selected.companyTitle || ''
  }
  triggerDuplicateCheck()
}

const formatDuplicateCandidates = (candidates: any[]) => {
  return (Array.isArray(candidates) ? candidates : [])
    .map((item: any) => `${item.title || item.name || '-'} / ${item.contactPerson || '-'} / ${item.username || '未绑定账号'}${item.primary ? '（主账号）' : ''}`)
    .join('；')
}

const onTitleBlur = async () => {
  const title = form.value.title?.trim()
  if (!title) return
  try {
    // 404 表示无匹配记录，视为正常不触发全局报错
    const res: any = await request.get('/partner-info/by-title', {
      params: { title },
      validateStatus: (status) => status === 200 || status === 404
    })
    if (res && (res.taxNumber || res.bankName || res.bankAccount || res.bankAddress)) {
      if (res.taxNumber) form.value.taxNumber = res.taxNumber
      if (res.bankName) form.value.bankName = res.bankName
      if (res.bankAccount) form.value.bankAccount = res.bankAccount
      if (res.bankAddress) form.value.bankAddress = res.bankAddress
      ElMessage.success('已根据抬头自动带出税号、开户行等信息')
    }
  } catch {
    // 其他异常静默忽略
  }
}

const triggerDuplicateCheck = async () => {
  const payload = {
    ...form.value,
    identities: Array.isArray(form.value.identities) ? form.value.identities.join(',') : form.value.identities
  }
  const shouldCheck = !!String(payload.title || '').trim()
    && (!!String(payload.contactPerson || '').trim() || !!String(payload.contactPhone || '').trim() || !!String(payload.username || '').trim())
  if (!shouldCheck) {
    duplicateCheckResult.value = null
    return null
  }
  try {
    const res: any = await request.post('/partner-info/duplicate-check', payload, { skipErrorMsg: true } as any)
    duplicateCheckResult.value = res || null
    return res || null
  } catch {
    duplicateCheckResult.value = null
    return null
  }
}

const handleTitleBlur = async () => {
  await onTitleBlur()
  await triggerDuplicateCheck()
}

/** 从粘贴文本解析并写入表单（不覆盖已有「名称」除非为空） */
const applyPartnerInfoSmartParse = () => {
  const parsed = parsePartnerInfoFromPastedText(partnerInfoPasteText.value)
  const filled: string[] = []
  if (parsed.title) {
    form.value.title = parsed.title
    filled.push('抬头信息')
    if (!form.value.name?.trim()) {
      form.value.name = parsed.title
      filled.push('名称')
    }
  } else if (parsed.name && !form.value.name?.trim()) {
    form.value.name = parsed.name
    filled.push('名称')
  }
  if (parsed.taxNumber) {
    form.value.taxNumber = parsed.taxNumber
    filled.push('税号')
  }
  if (parsed.bankName) {
    form.value.bankName = parsed.bankName
    filled.push('开户行')
  }
  if (parsed.bankAccount) {
    form.value.bankAccount = parsed.bankAccount
    filled.push('银行账号')
  }
  if (parsed.bankAddress) {
    form.value.bankAddress = parsed.bankAddress
    filled.push('开户行地址')
  }
  if (parsed.contactPhone) {
    form.value.contactPhone = parsed.contactPhone
    filled.push('联系电话')
  }
  if (parsed.contactPerson) {
    form.value.contactPerson = parsed.contactPerson
    filled.push('联系人')
  }
  if (parsed.email) {
    form.value.email = parsed.email
    filled.push('邮箱')
  }
  if (filled.length === 0) {
    ElMessage.warning('未能识别出有效字段，请确认每行格式为「标签：内容」（支持中文或英文冒号）')
    return
  }
  // 与「名称」失焦逻辑一致：无联系人时用名称兜底
  if (!form.value.contactPerson?.trim() && form.value.name?.trim()) {
    form.value.contactPerson = form.value.name
  }
  ElMessage.success(`已填充：${[...new Set(filled)].join('、')}，请核对后保存`)
  partnerInfoPasteText.value = ''
}

// 从 PDF/Word/图片解析抬头、税号、开户行等信息并填入表单
const onPartnerInfoImportFileChange = async (file: any) => {
  if (!file?.raw) return
  partnerInfoImportLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file.raw)
    const res: any = await request.post('/orders/import-from-file', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (!res?.success) {
      ElMessage.warning(res?.message || '解析未成功')
      return
    }
    if (res.partnerName) form.value.name = res.partnerName
    if (res.title) form.value.title = res.title
    if (res.taxNumber) form.value.taxNumber = res.taxNumber
    if (res.bankName) form.value.bankName = res.bankName
    if (res.bankAccount) form.value.bankAccount = res.bankAccount
    if (res.bankAddress) form.value.bankAddress = res.bankAddress
    if (res.contactPerson) form.value.contactPerson = res.contactPerson
    if (res.contactPhone) form.value.contactPhone = res.contactPhone
    if (res.email) form.value.email = res.email
    partnerInfoImportUploadRef.value?.clearFiles()
    ElMessage.success('已带出解析结果，请核对后保存')
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '解析失败'
    ElMessage.error(msg)
  } finally {
    partnerInfoImportLoading.value = false
  }
}

const copyPartnerField = async (row: any, field: string, text: string) => {
  const value = text != null ? String(text).trim() : ''
  if (!value) {
    ElMessage.warning('无内容可复制')
    return
  }
  await copyWithPrivacyLog(value, {
    targetType: 'PARTNER',
    targetId: String(row.id),
    action: 'COPY',
    field
  })
}

const handleEdit = (row: any) => {
  partnerInfoPasteText.value = ''
  dialogTitle.value = '编辑用户信息'
  editingRowCreatedBy.value = row.createdBy != null ? row.createdBy : null
  editingRowUsername.value = row.username || ''
  form.value = {
    id: row.id,
    name: row.name,
    identities: row.identities ? row.identities.split(',') : [],
    username: row.username || '',
    title: row.title || '',
    taxNumber: row.taxNumber || '',
    bankName: row.bankName || '',
    bankAccount: row.bankAccount || '',
    bankAddress: row.bankAddress || '',
    contactPerson: row.contactPerson || '',
    contactPhone: row.contactPhone || '',
    email: row.email || '',
    password: '',
    confirmPassword: '',
    newPassword: '',
    confirmNewPassword: ''
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
    const isAdd = !form.value.id
    const createAccount = isAdd && form.value.password && form.value.password.trim()
    if (createAccount && form.value.password !== form.value.confirmPassword) {
      ElMessage.warning('两次输入的密码不一致')
      return
    }
    if (!isAdd && form.value.newPassword && form.value.newPassword !== form.value.confirmNewPassword) {
      ElMessage.warning('两次输入的密码不一致')
      return
    }
    const duplicateResult = await triggerDuplicateCheck()
    if (duplicateResult?.block) {
      ElMessage.warning(duplicateResult.message || '检测到重复主体，请优先引用已有账号')
      return
    }
    const submitData: Record<string, unknown> = {
      ...form.value,
      identities: Array.isArray(form.value.identities) ? form.value.identities.join(',') : form.value.identities
    }
    if (isAdd) {
      if (createAccount) {
        submitData.password = form.value.password
      }
      delete submitData.id
      delete submitData.newPassword
      delete submitData.confirmNewPassword
    } else {
      if (form.value.newPassword && form.value.newPassword.trim()) {
        submitData.newPassword = form.value.newPassword
      }
      delete submitData.password
      delete submitData.confirmPassword
    }
    delete submitData.confirmPassword
    delete submitData.confirmNewPassword
    if (form.value.id) {
      await request.put(`/partner-info/${form.value.id}`, submitData)
      ElMessage.success('更新成功')
    } else {
      await request.post('/partner-info', submitData)
      ElMessage.success(createAccount ? '新增成功，已创建登录账号' : '新增成功')
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
    const msg = error?.response?.data?.message || error?.message || '提交失败'
    ElMessage.error(msg)
  }
}

const resetForm = () => {
  editingRowCreatedBy.value = null
  editingRowUsername.value = ''
  partnerInfoPasteText.value = ''
  duplicateCheckResult.value = null
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
    email: '',
    password: '',
    confirmPassword: '',
    newPassword: '',
    confirmNewPassword: ''
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

const fetchCurrentUser = async () => {
  try {
    const res: any = await request.get('/users/me')
    if (res?.id != null) currentUserId.value = res.id
    if (res?.username) currentUsername.value = res.username
  } catch {
    currentUsername.value = localStorage.getItem('username') || ''
  }
}

onMounted(() => {
  fetchCurrentUser()
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
.duplicate-alert {
  margin-bottom: 12px;
}
.duplicate-alert-line {
  font-size: 12px;
  line-height: 1.6;
}
.default-permissions-tip {
  margin-top: 8px;
  padding: 8px 0;
  color: #909399;
  font-size: 12px;
}
.smart-parse-actions {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
</style>