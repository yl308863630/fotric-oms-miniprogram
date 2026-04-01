<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="720px"
    @close="emit('update:modelValue', false)"
  >
    <div v-if="currentRow" class="erp-entry-dialog">
      <el-alert
        :title="canEdit ? (isBatchMode ? `将批量维护 ${targetRows.length} 条商务ERP录单信息` : '可维护商务ERP录单信息') : '当前账号仅可查看录单信息'"
        :type="canEdit ? 'info' : 'warning'"
        :closable="false"
        show-icon
      />

      <div v-if="isBatchMode" class="batch-summary-card mt-16">
        <div class="batch-summary-title">本次批量写入范围</div>
        <div class="batch-summary-meta">
          <span>目标条数：{{ targetRows.length }}</span>
          <span>OMS订单数：{{ batchOmsCount }}</span>
        </div>
        <el-table :data="batchPreviewRows" size="small" border max-height="220">
          <el-table-column type="index" label="#" width="60" align="center" />
          <el-table-column prop="omsOrderNo" label="OMS订单号" min-width="170" show-overflow-tooltip />
          <el-table-column prop="model" label="型号" min-width="180" show-overflow-tooltip />
          <el-table-column prop="quantity" label="数量" width="80" align="center" />
          <el-table-column prop="erpEntryStatus" label="当前录单状态" width="120" align="center" />
        </el-table>
      </div>

      <el-form :model="form" label-width="100px" class="mt-16">
        <el-form-item label="当前状态">
          <el-tag :type="form.erpEntryStatus === '已录单' ? 'success' : 'warning'">
            {{ form.erpEntryStatus || '待系统录单' }}
          </el-tag>
        </el-form-item>

        <el-form-item label="录单人">
          <el-select
            v-model="form.erpEntryOperator"
            filterable
            allow-create
            default-first-option
            clearable
            :disabled="!canEdit"
            placeholder="请输入或选择录单人"
            style="width: 100%;"
            @change="rememberOperator"
          >
            <el-option v-for="item in operatorOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>

        <el-form-item label="录单时间">
          <el-date-picker
            v-model="form.erpEntryTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            :disabled="!canEdit"
            style="width: 100%;"
            placeholder="请选择录单时间"
          />
        </el-form-item>

        <el-form-item label="ERP截图">
          <div class="erp-upload-block">
            <div class="erp-upload-actions">
              <el-upload
                action="/api/upload"
                :show-file-list="false"
                :before-upload="beforeUpload"
                :on-success="handleUploadSuccess"
                :disabled="!canEdit"
              >
                <el-button type="primary" :disabled="!canEdit">上传截图</el-button>
              </el-upload>
              <el-button
                v-if="canPreview && hasSavedScreenshot"
                link
                type="primary"
                @click="openPreview('inline')"
              >
                网页预览
              </el-button>
              <el-button
                v-if="canPreview && hasSavedScreenshot"
                link
                type="primary"
                @click="openPreview('attachment')"
              >
                下载
              </el-button>
            </div>

            <div
              class="erp-paste-box"
              :class="{ disabled: !canEdit }"
              tabindex="0"
              @paste="handlePaste"
            >
              点击这里后按 `Ctrl+V` 可直接粘贴截图，也可使用左侧上传按钮
            </div>

            <div class="erp-upload-tip">
              <span v-if="form.erpEntryScreenshotUrl">已上传截图</span>
              <span v-else>尚未上传截图</span>
            </div>
          </div>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="emit('update:modelValue', false)">关闭</el-button>
        <el-button type="primary" :disabled="!canEdit" :loading="saving" @click="submit">
          保存
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

type TargetType = 'sales' | 'purchase'

const props = defineProps<{
  modelValue: boolean
  targetType: TargetType
  row: any | null
  rows?: any[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'saved', value: any): void
}>()

const saving = ref(false)
const operatorOptions = ref<string[]>([])
const form = reactive({
  erpEntryStatus: '待系统录单',
  erpEntryScreenshotUrl: '',
  erpEntryOperator: '',
  erpEntryTime: ''
})

const OPERATOR_STORAGE_KEY = 'erp_entry_operator_options'

const targetRows = computed(() => {
  const rows = Array.isArray(props.rows) ? props.rows.filter(Boolean) : []
  if (rows.length > 0) return rows
  return props.row ? [props.row] : []
})
const currentRow = computed(() => targetRows.value[0] || null)
const isBatchMode = computed(() => targetRows.value.length > 1)
const dialogTitle = computed(() => {
  const base = props.targetType === 'sales' ? '商务ERP录单（销售）' : '商务ERP录单（采购）'
  return isBatchMode.value ? `${base} - 批量模式` : base
})
const canEdit = computed(() => targetRows.value.length > 0 && targetRows.value.every(row => Boolean(row?.erpEntryCanEdit)))
const canPreview = computed(() => !isBatchMode.value && Boolean(currentRow.value?.erpEntryCanPreviewScreenshot))
const hasSavedScreenshot = computed(() => !isBatchMode.value && Boolean(String(currentRow.value?.erpEntryScreenshotUrl || '').trim()))
const batchPreviewRows = computed(() => targetRows.value.slice(0, 20))
const batchOmsCount = computed(() => new Set(
  targetRows.value
    .map(row => String(row?.omsOrderNo || '').trim())
    .filter(Boolean)
).size)

const loadOperatorOptions = () => {
  try {
    const saved = localStorage.getItem(OPERATOR_STORAGE_KEY)
    const parsed = saved ? JSON.parse(saved) : []
    operatorOptions.value = Array.isArray(parsed)
      ? parsed.map((item: any) => String(item || '').trim()).filter(Boolean)
      : []
  } catch {
    operatorOptions.value = []
  }
}

const rememberOperator = (value?: string) => {
  const text = String(value || form.erpEntryOperator || '').trim()
  if (!text) return
  if (!operatorOptions.value.includes(text)) {
    operatorOptions.value = [...operatorOptions.value, text]
    localStorage.setItem(OPERATOR_STORAGE_KEY, JSON.stringify(operatorOptions.value))
  }
}

const resetForm = () => {
  const now = new Date()
  const pad = (v: number) => `${v}`.padStart(2, '0')
  const currentIso = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  const operator = localStorage.getItem('realName') || localStorage.getItem('username') || ''
  form.erpEntryStatus = currentRow.value?.erpEntryStatus || '待系统录单'
  form.erpEntryScreenshotUrl = currentRow.value?.erpEntryScreenshotUrl || ''
  form.erpEntryOperator = currentRow.value?.erpEntryOperator || operator
  form.erpEntryTime = currentRow.value?.erpEntryTime || currentIso
}

watch(() => props.modelValue, (visible) => {
  if (visible) {
    loadOperatorOptions()
    resetForm()
  }
}, { immediate: true })

watch(() => form.erpEntryOperator, (value) => {
  if (value) rememberOperator(value)
})

const beforeUpload = (file: File) => {
  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    ElMessage.error('请上传图片格式的ERP截图')
    return false
  }
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    ElMessage.error('截图大小不能超过 10MB')
    return false
  }
  return true
}

const handleUploadSuccess = (response: any) => {
  if (!response?.url) {
    ElMessage.error('截图上传失败')
    return
  }
  form.erpEntryScreenshotUrl = response.url
  form.erpEntryStatus = '已录单'
  if (!form.erpEntryTime) {
    resetForm()
  }
  ElMessage.success('ERP截图上传成功')
}

const uploadClipboardImage = async (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  const response: any = await request.post('/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  handleUploadSuccess(response)
}

const handlePaste = async (event: ClipboardEvent) => {
  if (!canEdit.value) return
  const items = Array.from(event.clipboardData?.items || [])
  for (const item of items) {
    if (item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) {
        event.preventDefault()
        await uploadClipboardImage(file)
        return
      }
    }
  }
}

const openPreview = async (disposition: 'inline' | 'attachment') => {
  const rawUrl = String(currentRow.value?.erpEntryScreenshotUrl || '').trim()
  if (!rawUrl) {
    ElMessage.warning('未找到已保存的截图')
    return
  }
  try {
    const url = rawUrl.startsWith('/') ? rawUrl : `/${rawUrl}`
    if (disposition === 'inline') {
      window.open(url, '_blank')
    } else {
      const pathname = new URL(url, window.location.origin).pathname
      const basename = pathname.split('/').filter(Boolean).pop() || ''
      const extension = basename.includes('.') ? `.${basename.split('.').pop()}` : ''
      const link = document.createElement('a')
      link.href = url
      link.download = `${props.targetType}-erp-entry${extension}`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
    }
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '打开截图失败')
  }
}

const submit = async () => {
  if (!currentRow.value?.id) {
    ElMessage.warning('未找到业务单据')
    return
  }
  if (!form.erpEntryOperator.trim()) {
    ElMessage.warning('请填写录单人')
    return
  }
  saving.value = true
  try {
    const payload = {
      erpEntryScreenshotUrl: form.erpEntryScreenshotUrl,
      erpEntryOperator: form.erpEntryOperator.trim(),
      erpEntryTime: form.erpEntryTime
    }
    const requestRows = dedupeRowsForSubmit(targetRows.value)
    const result = await Promise.all(requestRows.map((row) => {
      const url = props.targetType === 'sales'
        ? `/sales-orders/${row.id}/erp-entry`
        : `/purchase-orders/${row.id}/erp-entry`
      return request.patch(url, payload)
    }))
    rememberOperator(form.erpEntryOperator)
    emit('saved', result)
    emit('update:modelValue', false)
    ElMessage.success(isBatchMode.value
      ? `已批量保存 ${targetRows.value.length} 条商务ERP录单信息`
      : '商务ERP录单信息已保存')
  } finally {
    saving.value = false
  }
}

const dedupeRowsForSubmit = (rows: any[]) => {
  const map = new Map<string, any>()
  rows.forEach((row) => {
    if (!row?.id) return
    const omsKey = String(row?.omsOrderNo || '').trim()
    const key = omsKey
      ? `${props.targetType}:oms:${omsKey}`
      : `${props.targetType}:id:${row.id}`
    if (!map.has(key)) {
      map.set(key, row)
    }
  })
  return Array.from(map.values())
}
</script>

<style scoped>
.mt-16 {
  margin-top: 16px;
}

.batch-summary-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  background: #fafafa;
}

.batch-summary-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.batch-summary-meta {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 13px;
  color: #606266;
  margin-bottom: 12px;
}

.erp-upload-block {
  width: 100%;
}

.erp-upload-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.erp-paste-box {
  min-height: 72px;
  border: 1px dashed #c0c4cc;
  border-radius: 6px;
  padding: 12px;
  color: #606266;
  background: #fafafa;
  outline: none;
}

.erp-paste-box.disabled {
  background: #f5f7fa;
  color: #c0c4cc;
  cursor: not-allowed;
}

.erp-upload-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
</style>
