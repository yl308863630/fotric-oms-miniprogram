<template>
  <div class="chain-observer-page">
    <el-card class="search-card">
      <el-form :inline="true" :model="searchForm" class="search-form" @submit.prevent>
        <el-form-item label="查询类型">
          <el-select v-model="searchForm.type" style="width: 180px">
            <el-option label="OMS订单号" value="oms" />
            <el-option label="主单号" value="masterNo" />
            <el-option label="分配单号" value="allocationNo" />
            <el-option label="SN编码" value="snCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="searchForm.keyword"
            :placeholder="keywordPlaceholder"
            clearable
            style="width: 360px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
      <el-alert
        title="输入 OMS订单号 / 主单号 / 分配单号 / SN 后，会自动定位主单和当前分配，并展开整条链路。"
        type="info"
        :closable="false"
        show-icon
      />
    </el-card>

    <el-card v-loading="loading" class="result-card">
      <template v-if="errorMessage">
        <el-empty :description="errorMessage" />
      </template>

      <template v-else-if="hasResult">
        <div class="match-summary">
          <div>命中方式：{{ matchSummary.typeLabel }}</div>
          <div>命中关键字：{{ matchSummary.keyword }}</div>
          <div v-if="matchSummary.hitValue">命中值：{{ matchSummary.hitValue }}</div>
          <div v-if="matchSummary.masterNo">主单号：{{ matchSummary.masterNo }}</div>
          <div v-if="matchSummary.allocationNo">分配单号：{{ matchSummary.allocationNo }}</div>
        </div>

        <div v-if="masterDetail?.master || allocationDetail?.allocation || matchedSerialItem" class="section-block">
          <div class="section-title">当前定位</div>
          <div class="observe-anchor-summary">
            <div v-if="masterDetail?.master">主单ID：{{ masterDetail.master.id || '-' }}</div>
            <div v-if="masterDetail?.master">主单号：{{ masterDetail.master.masterNo || '-' }}</div>
            <div v-if="allocationDetail?.allocation">分配ID：{{ allocationDetail.allocation.id || '-' }}</div>
            <div v-if="allocationDetail?.allocation">分配单号：{{ allocationDetail.allocation.allocationNo || '-' }}</div>
            <div v-if="matchedSerialItem">SN：{{ matchedSerialItem.snCode || '-' }}</div>
          </div>
        </div>

        <div v-if="matchedSerialItem" class="section-block">
          <div class="section-title">命中 SN</div>
          <el-descriptions :column="4" border size="small">
            <el-descriptions-item label="SN编码">{{ matchedSerialItem.snCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="型号">{{ matchedSerialItem.productModel || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ matchedSerialItem.serialStatus || '-' }}</el-descriptions-item>
            <el-descriptions-item label="分配ID">{{ matchedSerialItem.allocationId || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div v-if="masterDetail?.master" class="section-block">
          <div class="section-title">主单摘要</div>
          <el-descriptions :column="4" border size="small">
            <el-descriptions-item label="主单号">{{ masterDetail.master.masterNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="主单状态">{{ masterDetail.master.masterStatus || '-' }}</el-descriptions-item>
            <el-descriptions-item label="指派状态">{{ masterDetail.master.assignStatus || '-' }}</el-descriptions-item>
            <el-descriptions-item label="财务状态">{{ masterDetail.master.financeStatus || '-' }}</el-descriptions-item>
            <el-descriptions-item label="甲方订单号">{{ masterDetail.master.platformOrderNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="根OMS订单号">{{ masterDetail.master.rootOmsOrderNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="甲方抬头">{{ masterDetail.master.partyATitle || '-' }}</el-descriptions-item>
            <el-descriptions-item label="总数量">{{ masterDetail.summary?.totalQuantity ?? masterDetail.master.totalQuantity ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="创建人">{{ masterDetail.master.creator || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(masterDetail.master.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="商品行数">{{ masterDetail.summary?.orderCount ?? masterDetail.master.totalLineCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="分配数">{{ masterDetail.summary?.allocationCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="已指派分配数">{{ masterDetail.summary?.assignedAllocationCount ?? 0 }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div v-if="allocationDetail?.allocation" class="section-block">
          <div class="section-title">当前分配摘要</div>
          <el-descriptions :column="4" border size="small">
            <el-descriptions-item label="分配单号">{{ allocationDetail.allocation.allocationNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="分配状态">{{ allocationDetail.allocation.allocationStatus || '-' }}</el-descriptions-item>
            <el-descriptions-item label="分配数量">{{ allocationDetail.summary?.allocatedQty ?? allocationDetail.allocation.allocatedQty ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="链尾">{{ allocationDetail.allocation.isChainTail ? '是' : '否' }}</el-descriptions-item>
            <el-descriptions-item label="被分配方">{{ allocationDetail.allocation.assignedCompanyTitle || '-' }}</el-descriptions-item>
            <el-descriptions-item label="被分配用户">{{ allocationDetail.allocation.assignedUsername || '-' }}</el-descriptions-item>
            <el-descriptions-item label="子分配数">{{ allocationDetail.summary?.childCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="SN数">{{ allocationDetail.summary?.serialCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="根分配ID">{{ allocationDetail.allocation.rootAllocationId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="父分配ID">{{ allocationDetail.allocation.parentAllocationId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="来源类型">{{ allocationDetail.allocation.sourceType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(allocationDetail.allocation.createTime) }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="section-block">
          <div class="section-title">商品行</div>
          <el-table :data="masterOrders" border stripe size="small" max-height="260">
            <el-table-column prop="id" label="订单ID" width="90" />
            <el-table-column prop="lineNo" label="行号" width="80" />
            <el-table-column prop="platformOrderNo" label="甲方订单号" min-width="180" show-overflow-tooltip />
            <el-table-column prop="omsOrderNo" label="OMS订单号" min-width="170" show-overflow-tooltip />
            <el-table-column prop="model" label="型号" min-width="160" show-overflow-tooltip />
            <el-table-column prop="quantity" label="数量" width="90" />
            <el-table-column prop="status" label="状态" width="120" />
            <el-table-column prop="deliveryParty" label="交付方" min-width="180" show-overflow-tooltip />
            <el-table-column prop="assignedUsername" label="被指派用户" width="120" />
            <el-table-column prop="masterId" label="主单ID" width="90" />
            <el-table-column prop="allocationId" label="分配ID" width="90" />
          </el-table>
        </div>

        <div class="section-block">
          <div class="section-title">分配分支</div>
          <el-table :data="masterAllocations" border stripe size="small" max-height="260">
            <el-table-column prop="id" label="分配ID" width="90" />
            <el-table-column prop="allocationNo" label="分配单号" min-width="180" show-overflow-tooltip />
            <el-table-column prop="allocatedQty" label="数量" width="90" />
            <el-table-column prop="allocationStatus" label="状态" width="120" />
            <el-table-column prop="assignedCompanyTitle" label="被分配方" min-width="180" show-overflow-tooltip />
            <el-table-column prop="assignedUsername" label="被分配用户" width="120" />
            <el-table-column prop="hopNo" label="跳数" width="80" />
            <el-table-column label="链尾" width="80">
              <template #default="{ row }">{{ row.isChainTail ? '是' : '否' }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div class="section-block">
          <div class="section-title">SN明细</div>
          <el-table :data="masterSerialItems" border stripe size="small" max-height="260">
            <el-table-column prop="id" label="SN ID" width="90" />
            <el-table-column prop="snCode" label="SN编码" min-width="220" show-overflow-tooltip />
            <el-table-column prop="productModel" label="型号" min-width="160" show-overflow-tooltip />
            <el-table-column prop="serialStatus" label="状态" width="120" />
            <el-table-column prop="salesOrderId" label="订单ID" width="90" />
            <el-table-column prop="allocationId" label="分配ID" width="90" />
            <el-table-column prop="batchId" label="批次ID" width="90" />
          </el-table>
        </div>
      </template>

      <template v-else>
        <el-empty description="请输入关键字后开始观察链路" />
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import request from '@/utils/request'

type SearchType = 'oms' | 'masterNo' | 'allocationNo' | 'snCode'

const route = useRoute()

const searchForm = reactive({
  type: 'oms' as SearchType,
  keyword: ''
})

const loading = ref(false)
const errorMessage = ref('')
const masterDetail = ref<any>(null)
const allocationDetail = ref<any>(null)
const matchedSerialItem = ref<any>(null)
const matchSummary = reactive({
  typeLabel: '',
  keyword: '',
  hitValue: '',
  masterNo: '',
  allocationNo: ''
})

const keywordPlaceholder = computed(() => {
  switch (searchForm.type) {
    case 'masterNo':
      return '请输入主单号，如 MASTER-xxx'
    case 'allocationNo':
      return '请输入分配单号，如 ALLOC-xxx'
    case 'snCode':
      return '请输入 SN 编码'
    default:
      return '请输入 OMS 订单号'
  }
})

const hasResult = computed(() =>
  !!masterDetail.value || !!allocationDetail.value || !!matchedSerialItem.value
)

const masterOrders = computed(() => masterDetail.value?.orders || [])
const masterAllocations = computed(() => masterDetail.value?.allocations || [])
const masterSerialItems = computed(() => masterDetail.value?.serialItems || [])

const formatDateTime = (value: string | null | undefined) => {
  if (!value) return '-'
  return String(value).replace('T', ' ')
}

const resetResult = () => {
  errorMessage.value = ''
  masterDetail.value = null
  allocationDetail.value = null
  matchedSerialItem.value = null
  matchSummary.typeLabel = ''
  matchSummary.keyword = ''
  matchSummary.hitValue = ''
  matchSummary.masterNo = ''
  matchSummary.allocationNo = ''
}

const loadMasterDetail = async (masterId: number | string) => {
  if (!masterId) return
  masterDetail.value = await request.get(`/sales-order-masters/${masterId}`)
  if (masterDetail.value?.master?.masterNo) {
    matchSummary.masterNo = masterDetail.value.master.masterNo
  }
}

const loadAllocationDetail = async (allocationId: number | string) => {
  if (!allocationId) return
  allocationDetail.value = await request.get(`/sales-order-allocations/${allocationId}/detail`)
  if (allocationDetail.value?.allocation?.allocationNo) {
    matchSummary.allocationNo = allocationDetail.value.allocation.allocationNo
  }
}

const handleSearch = async () => {
  const keyword = String(searchForm.keyword || '').trim()
  if (!keyword) {
    errorMessage.value = '请输入查询关键字'
    return
  }
  resetResult()
  loading.value = true
  try {
    matchSummary.keyword = keyword

    if (searchForm.type === 'oms') {
      matchSummary.typeLabel = 'OMS订单号'
      const res: any = await request.get('/sales-order-masters', {
        params: { rootOmsOrderNo: keyword, page: 0, size: 20 }
      })
      const content = Array.isArray(res?.content) ? res.content : []
      const target = content.find((item: any) => String(item?.rootOmsOrderNo || '').trim() === keyword) || content[0]
      if (!target?.id) {
        throw new Error('未找到对应主单')
      }
      matchSummary.hitValue = target.rootOmsOrderNo || keyword
      await loadMasterDetail(target.id)
      return
    }

    if (searchForm.type === 'masterNo') {
      matchSummary.typeLabel = '主单号'
      const res: any = await request.get('/sales-order-masters', {
        params: { masterNo: keyword, page: 0, size: 20 }
      })
      const content = Array.isArray(res?.content) ? res.content : []
      const target = content.find((item: any) => String(item?.masterNo || '').trim() === keyword) || content[0]
      if (!target?.id) {
        throw new Error('未找到对应主单')
      }
      matchSummary.hitValue = target.masterNo || keyword
      await loadMasterDetail(target.id)
      return
    }

    if (searchForm.type === 'allocationNo') {
      matchSummary.typeLabel = '分配单号'
      const list: any[] = await request.get('/sales-order-allocations', {
        params: { allocationNo: keyword }
      })
      const target = Array.isArray(list)
        ? (list.find((item: any) => String(item?.allocationNo || '').trim() === keyword) || list[0])
        : null
      if (!target?.id) {
        throw new Error('未找到对应分配')
      }
      matchSummary.hitValue = target.allocationNo || keyword
      await loadAllocationDetail(target.id)
      if (target.masterId) {
        await loadMasterDetail(target.masterId)
      }
      return
    }

    matchSummary.typeLabel = 'SN编码'
    const list: any[] = await request.get('/sales-serial-items', {
      params: { snCode: keyword }
    })
    const target = Array.isArray(list)
      ? (list.find((item: any) => String(item?.snCode || '').trim() === keyword) || list[0])
      : null
    if (!target?.id) {
      throw new Error('未找到对应 SN')
    }
    matchedSerialItem.value = target
    matchSummary.hitValue = target.snCode || keyword
    if (target.allocationId) {
      await loadAllocationDetail(target.allocationId)
    }
    if (target.masterId) {
      await loadMasterDetail(target.masterId)
    } else if (allocationDetail.value?.master?.id) {
      await loadMasterDetail(allocationDetail.value.master.id)
    }
  } catch (error: any) {
    errorMessage.value = error?.response?.data?.message || error?.message || '链路查询失败'
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  searchForm.type = 'oms'
  searchForm.keyword = ''
  resetResult()
}

const applyQuerySearch = () => {
  const type = String(route.query.type || '').trim()
  const keyword = String(route.query.keyword || '').trim()
  if (!keyword) {
    return
  }
  if (type === 'oms' || type === 'masterNo' || type === 'allocationNo' || type === 'snCode') {
    searchForm.type = type
  }
  searchForm.keyword = keyword
  handleSearch()
}

onMounted(() => {
  applyQuerySearch()
})

watch(() => route.query, () => {
  applyQuerySearch()
}, { deep: true })
</script>

<style scoped>
.chain-observer-page {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.search-form {
  margin-bottom: 12px;
}

.result-card {
  min-height: 420px;
}

.match-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin-bottom: 16px;
  color: #606266;
  font-size: 13px;
}

.observe-anchor-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  color: #606266;
  font-size: 13px;
}

.section-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 18px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
</style>
