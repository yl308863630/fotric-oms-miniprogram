<template>
  <div class="party-a-payment-rule-list mobile-list-layout">
    <el-card class="filter-card">
      <el-form :inline="true" :model="filterForm">
        <el-form-item label="甲方抬头">
          <el-input v-model="filterForm.partyATitle" placeholder="请输入甲方抬头" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterForm.enabled" placeholder="全部" clearable style="width: 120px">
            <el-option label="全部" :value="undefined" />
            <el-option label="启用" :value="true" />
            <el-option label="停用" :value="false" />
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
        <div class="table-ops-left">
          <el-button type="primary" @click="handleAdd">新增规则</el-button>
          <el-button @click="recalculateCurrentTitle" :disabled="!filterForm.partyATitle.trim()">重算当前抬头订单</el-button>
          <el-button type="warning" plain @click="recalculateAllOpenOrders">重算全部未结算订单</el-button>
        </div>
      </div>

      <el-table :data="tableData" border stripe size="small" v-loading="loading">
        <el-table-column label="操作" width="180" fixed>
          <template #default="scope">
            <el-button link type="primary" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button link type="warning" @click="triggerRowRecalculate(scope.row)">重算</el-button>
            <el-button link type="danger" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="partyATitle" label="甲方抬头" min-width="240" show-overflow-tooltip />
        <el-table-column label="启用" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="基准事件" width="120">
          <template #default="scope">{{ baseEventLabel(scope.row.baseEventType) }}</template>
        </el-table-column>
        <el-table-column prop="offsetDays" label="偏移天数" width="100" align="center" />
        <el-table-column label="付款节点" min-width="220">
          <template #default="scope">
            <div class="rule-summary">
              <span>{{ anchorTypeLabel(scope.row.paymentAnchorType) }}</span>
              <span v-if="scope.row.paymentAnchorType === 'FIXED_DAY_OF_NEXT_MONTH'">下月{{ scope.row.anchorDay1 || '-' }}号</span>
              <span v-else-if="scope.row.paymentAnchorType === 'INTERVAL_DAY_BUCKET'">
                1-{{ scope.row.anchorDay1 || '-' }} => {{ scope.row.anchorDay1 || '-' }}号；
                {{ ((scope.row.anchorDay1 || 0) + 1) || '-' }}-{{ scope.row.anchorDay2 || '-' }} => {{ scope.row.anchorDay2 || '-' }}号；
                超出 => 次月{{ scope.row.anchorDay3 || '-' }}号
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="规则说明" min-width="280" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.description || scope.row.exampleRuleText || '-' }}</template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="180" />
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="900px">
      <el-form :model="form" label-width="140px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="甲方抬头" required>
              <el-select
                v-model="form.partyATitle"
                filterable
                allow-create
                default-first-option
                placeholder="请选择或输入甲方抬头"
                style="width: 100%"
              >
                <el-option v-for="item in platformTitleOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否启用">
              <el-switch v-model="form.enabled" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="基准事件" required>
              <el-select v-model="form.baseEventType" style="width: 100%">
                <el-option label="交货日期" value="DELIVERY_DATE" />
                <el-option label="开票日期" value="INVOICE_DATE" />
                <el-option label="对账日期" value="RECONCILIATION_DATE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="偏移天数">
              <el-input-number v-model="form.offsetDays" :min="0" :max="3650" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="基准落点日">
              <el-input-number v-model="form.baseDayOfMonth" :min="1" :max="31" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="周期截点日">
              <el-input-number v-model="form.cycleCutoffDay" :min="1" :max="31" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="超期顺延下周期">
              <el-switch v-model="form.carryOverToNextCycle" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="付款节点类型" required>
              <el-select v-model="form.paymentAnchorType" style="width: 100%">
                <el-option label="直接按偏移日" value="NONE" />
                <el-option label="偏移后取次月固定日" value="FIXED_DAY_OF_NEXT_MONTH" />
                <el-option label="偏移后按区间付款日" value="INTERVAL_DAY_BUCKET" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row v-if="form.paymentAnchorType === 'FIXED_DAY_OF_NEXT_MONTH'" :gutter="20">
          <el-col :span="12">
            <el-form-item label="次月付款日">
              <el-input-number v-model="form.anchorDay1" :min="1" :max="31" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row v-if="form.paymentAnchorType === 'INTERVAL_DAY_BUCKET'" :gutter="20">
          <el-col :span="8">
            <el-form-item label="区间日1">
              <el-input-number v-model="form.anchorDay1" :min="1" :max="31" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="区间日2">
              <el-input-number v-model="form.anchorDay2" :min="1" :max="31" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="次月付款日">
              <el-input-number v-model="form.anchorDay3" :min="1" :max="31" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="规则说明">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="例如：25日前交货，当月25日对账，60天后次月5号付款" />
        </el-form-item>
        <el-form-item label="示例说明">
          <el-input v-model="form.exampleRuleText" type="textarea" :rows="3" placeholder="可填写业务口径、例外说明等" />
        </el-form-item>

        <el-alert :closable="false" type="info" show-icon>
          <template #title>
            <div class="preview-text">{{ previewText }}</div>
          </template>
        </el-alert>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('新增甲方回款规则')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref<any[]>([])
const platformTitleOptions = ref<string[]>([])

const filterForm = reactive({
  partyATitle: '',
  enabled: undefined as boolean | undefined
})

const createEmptyForm = () => ({
  id: undefined as number | undefined,
  partyATitle: '',
  enabled: true,
  baseEventType: 'DELIVERY_DATE',
  baseDayOfMonth: undefined as number | undefined,
  cycleCutoffDay: undefined as number | undefined,
  carryOverToNextCycle: true,
  offsetDays: 0,
  paymentAnchorType: 'NONE',
  anchorDay1: undefined as number | undefined,
  anchorDay2: undefined as number | undefined,
  anchorDay3: undefined as number | undefined,
  description: '',
  exampleRuleText: ''
})

const form = reactive(createEmptyForm())

const baseEventLabel = (value: string) => {
  if (value === 'DELIVERY_DATE') return '交货日期'
  if (value === 'INVOICE_DATE') return '开票日期'
  if (value === 'RECONCILIATION_DATE') return '对账日期'
  return value || '-'
}

const anchorTypeLabel = (value: string) => {
  if (value === 'NONE') return '直接按偏移日'
  if (value === 'FIXED_DAY_OF_NEXT_MONTH') return '偏移后取次月固定日'
  if (value === 'INTERVAL_DAY_BUCKET') return '偏移后按区间付款日'
  return value || '-'
}

const previewText = computed(() => {
  const parts: string[] = []
  parts.push(`基准事件：${baseEventLabel(form.baseEventType)}`)
  if (form.baseDayOfMonth) parts.push(`基准落点日：每月${form.baseDayOfMonth}日`)
  if (form.cycleCutoffDay) parts.push(`周期截点：${form.cycleCutoffDay}日前`)
  parts.push(`偏移：${form.offsetDays || 0}天`)
  if (form.paymentAnchorType === 'FIXED_DAY_OF_NEXT_MONTH') {
    parts.push(`付款节点：偏移后次月${form.anchorDay1 || '-'}号`)
  } else if (form.paymentAnchorType === 'INTERVAL_DAY_BUCKET') {
    parts.push(`付款节点：1-${form.anchorDay1 || '-'} -> ${form.anchorDay1 || '-'}号，${(form.anchorDay1 || 0) + 1}-${form.anchorDay2 || '-'} -> ${form.anchorDay2 || '-'}号，其余次月${form.anchorDay3 || '-'}号`)
  } else {
    parts.push('付款节点：直接按偏移结果')
  }
  return parts.join('；')
})

const fetchRules = async () => {
  loading.value = true
  try {
    const params: Record<string, any> = {
      page: currentPage.value - 1,
      size: pageSize.value
    }
    if (filterForm.partyATitle.trim()) params.partyATitle = filterForm.partyATitle.trim()
    if (typeof filterForm.enabled === 'boolean') params.enabled = filterForm.enabled
    const res: any = await request.get('/party-a-payment-rules', { params })
    tableData.value = res?.content || []
    total.value = Number(res?.totalElements || 0)
  } catch (error) {
    console.error('Fetch party payment rules error:', error)
    ElMessage.error('获取甲方回款规则失败')
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const fetchPlatformTitles = async () => {
  try {
    const res: any = await request.get('/partner-info', {
      params: { page: 0, size: 1000 },
      skipErrorMsg: true,
      skipErrorLog: true
    } as any)
    const content = res?.content || []
    const titles = content
      .map((item: any) => String(item?.title || item?.name || '').trim())
      .filter((item: string) => !!item)
    platformTitleOptions.value = Array.from(new Set<string>(titles))
  } catch {
    platformTitleOptions.value = []
  }
}

const resetForm = () => {
  Object.assign(form, createEmptyForm())
}

const handleAdd = () => {
  resetForm()
  dialogTitle.value = '新增甲方回款规则'
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  resetForm()
  Object.assign(form, {
    id: row.id,
    partyATitle: row.partyATitle || '',
    enabled: row.enabled !== false,
    baseEventType: row.baseEventType || 'DELIVERY_DATE',
    baseDayOfMonth: row.baseDayOfMonth ?? undefined,
    cycleCutoffDay: row.cycleCutoffDay ?? undefined,
    carryOverToNextCycle: row.carryOverToNextCycle !== false,
    offsetDays: row.offsetDays ?? 0,
    paymentAnchorType: row.paymentAnchorType || 'NONE',
    anchorDay1: row.anchorDay1 ?? undefined,
    anchorDay2: row.anchorDay2 ?? undefined,
    anchorDay3: row.anchorDay3 ?? undefined,
    description: row.description || '',
    exampleRuleText: row.exampleRuleText || ''
  })
  dialogTitle.value = '编辑甲方回款规则'
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!form.partyATitle.trim()) {
    ElMessage.warning('请先填写甲方抬头')
    return
  }
  if (!form.baseEventType) {
    ElMessage.warning('请选择基准事件')
    return
  }
  if (!form.paymentAnchorType) {
    ElMessage.warning('请选择付款节点类型')
    return
  }
  const payload = {
    ...form,
    partyATitle: form.partyATitle.trim()
  }
  try {
    if (form.id) {
      await request.put(`/party-a-payment-rules/${form.id}`, payload)
      ElMessage.success('规则更新成功')
    } else {
      await request.post('/party-a-payment-rules', payload)
      ElMessage.success('规则新增成功')
    }
    dialogVisible.value = false
    fetchRules()
  } catch (error: any) {
    const message = error?.response?.data?.message || error?.message || '保存失败'
    ElMessage.error(message)
  }
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定删除「${row.partyATitle}」的回款规则吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await request.delete(`/party-a-payment-rules/${row.id}`)
    ElMessage.success('删除成功')
    fetchRules()
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      const message = error?.response?.data?.message || error?.message || '删除失败'
      ElMessage.error(message)
    }
  }
}

const postRecalculate = async (payload: Record<string, any>) => {
  const res: any = await request.post('/party-a-payment-rules/recalculate', payload)
  const count = Number(res?.updatedCount || 0)
  ElMessage.success(`重算完成，已更新 ${count} 条订单`)
}

const triggerRowRecalculate = async (row: any) => {
  await postRecalculate({ partyATitle: row.partyATitle })
}

const recalculateCurrentTitle = async () => {
  if (!filterForm.partyATitle.trim()) {
    ElMessage.warning('请先输入甲方抬头再重算')
    return
  }
  await postRecalculate({ partyATitle: filterForm.partyATitle.trim() })
}

const recalculateAllOpenOrders = async () => {
  try {
    await ElMessageBox.confirm('确定重算全部未结算订单的预计回款时间吗？', '批量重算确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await postRecalculate({ allOpenOrders: true })
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      console.error('Recalculate all open orders error:', error)
    }
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchRules()
}

const resetSearch = () => {
  filterForm.partyATitle = ''
  filterForm.enabled = undefined
  handleSearch()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  fetchRules()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  fetchRules()
}

onMounted(() => {
  fetchPlatformTitles()
  fetchRules()
})
</script>

<style scoped>
.table-ops {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 12px;
  flex-wrap: wrap;
}

.table-ops-left {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.rule-summary {
  display: flex;
  flex-direction: column;
  line-height: 1.6;
}

.preview-text {
  line-height: 1.7;
}
</style>
