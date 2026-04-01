<template>
  <div v-if="isAdmin" class="login-security-page">
    <el-card class="security-overview">
      <div class="security-overview__header">
        <div>
          <div class="security-overview__title">验证码 IP 白名单</div>
          <div class="security-overview__desc">
            命中白名单的网络可跳过登录验证码。支持单 IP 或 CIDR，例如 `127.0.0.1`、`192.168.1.0/24`。
          </div>
        </div>
        <el-button type="primary" @click="openCreateDialog">新增规则</el-button>
      </div>
      <el-alert
        title="这里维护的是数据库动态规则；如生产环境还配置了 application.yml 静态白名单，也会一并生效。"
        type="info"
        :closable="false"
        show-icon
      />
    </el-card>

    <el-card class="security-table-card">
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="ipCidr" label="IP / CIDR" min-width="220" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? '生效中' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="220" show-overflow-tooltip />
        <el-table-column prop="updatedBy" label="最后更新人" width="140" />
        <el-table-column label="最后更新时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button
              link
              :type="row.enabled ? 'warning' : 'success'"
              @click="toggleRuleStatus(row)"
            >
              {{ row.enabled ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="removeRule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && tableData.length === 0" description="暂无白名单规则" />
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑白名单规则' : '新增白名单规则'"
      width="560px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="IP / CIDR" prop="ipCidr">
          <el-input v-model="form.ipCidr" placeholder="例如 127.0.0.1 或 10.0.0.0/24" />
        </el-form-item>
        <el-form-item label="是否启用" prop="enabled">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="可填写使用场景、归属环境、负责人等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>

  <div v-else class="no-access">
    <el-result icon="warning" title="无权访问" sub-title="该页面仅限管理员维护登录白名单策略" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import request from '@/utils/request'

interface AllowlistItem {
  id: number
  ipCidr: string
  enabled: boolean
  remark?: string
  createdBy?: string
  updatedBy?: string
  createTime?: string
  updateTime?: string
}

const isAdmin = computed(() => localStorage.getItem('username') === 'admin')
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const tableData = ref<AllowlistItem[]>([])
const formRef = ref<FormInstance>()
const form = reactive({
  ipCidr: '',
  enabled: true,
  remark: ''
})

const rules: FormRules = {
  ipCidr: [{ required: true, message: '请输入 IP 或 CIDR', trigger: 'blur' }]
}

const resetForm = () => {
  editingId.value = null
  form.ipCidr = ''
  form.enabled = true
  form.remark = ''
}

const loadData = async () => {
  if (!isAdmin.value) return
  loading.value = true
  try {
    tableData.value = await request.get('/admin/security/captcha-ip-allowlist')
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  resetForm()
  dialogVisible.value = true
}

const openEditDialog = (row: AllowlistItem) => {
  editingId.value = row.id
  form.ipCidr = row.ipCidr
  form.enabled = row.enabled
  form.remark = row.remark || ''
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    const payload = {
      ipCidr: form.ipCidr.trim(),
      enabled: form.enabled,
      remark: form.remark.trim()
    }
    if (editingId.value) {
      await request.put(`/admin/security/captcha-ip-allowlist/${editingId.value}`, payload)
      ElMessage.success('白名单规则已更新')
    } else {
      await request.post('/admin/security/captcha-ip-allowlist', payload)
      ElMessage.success('白名单规则已新增')
    }
    dialogVisible.value = false
    resetForm()
    await loadData()
  } finally {
    submitting.value = false
  }
}

const toggleRuleStatus = async (row: AllowlistItem) => {
  await request.put(`/admin/security/captcha-ip-allowlist/${row.id}`, {
    ...row,
    enabled: !row.enabled
  })
  ElMessage.success(row.enabled ? '规则已停用' : '规则已启用')
  await loadData()
}

const removeRule = async (row: AllowlistItem) => {
  await ElMessageBox.confirm(`确认删除规则「${row.ipCidr}」吗？`, '删除确认', {
    type: 'warning'
  })
  await request.delete(`/admin/security/captcha-ip-allowlist/${row.id}`)
  ElMessage.success('规则已删除')
  await loadData()
}

const formatDateTime = (value?: string) => {
  if (!value) return '-'
  const normalized = value.replace('T', ' ')
  return normalized.length > 19 ? normalized.slice(0, 19) : normalized
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.login-security-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.security-overview__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.security-overview__title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.security-overview__desc {
  margin-top: 6px;
  color: #606266;
  line-height: 1.6;
}

.security-table-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.no-access {
  padding: 24px;
}

@media (max-width: 768px) {
  .security-overview__header {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
