<template>
  <div class="dashboard">
    <el-tabs v-model="activeTab" class="main-tabs">
      <el-tab-pane label="待办事宜" name="todos">
        <div class="todo-sections">
          <!-- 第一行：核心流程 -->
          <div class="section-title">订单与交付</div>
          <el-row :gutter="16" class="card-row">
            <el-col :xs="12" :sm="8" :md="6">
              <el-card shadow="hover" class="todo-card" @click="goTo('/purchase', { tab: 'assign', status: '待指派' })">
                <div class="todo-card-inner">
                  <el-icon class="todo-icon"><Document /></el-icon>
                  <div class="todo-main">{{ todos.pendingAccept ?? 0 }}</div>
                  <div class="todo-label">待接单</div>
                  <div class="todo-sub">即将逾期 {{ todos.pendingAcceptApproaching ?? 0 }} · 已逾期 {{ todos.pendingAcceptOverdue ?? 0 }}</div>
                </div>
              </el-card>
            </el-col>
            <el-col :xs="12" :sm="8" :md="6">
              <el-card shadow="hover" class="todo-card" @click="goTo('/sales', { status: '待确认订单' })">
                <div class="todo-card-inner">
                  <el-icon class="todo-icon"><CircleCheck /></el-icon>
                  <div class="todo-main">{{ todos.pendingConfirmOrder ?? 0 }}</div>
                  <div class="todo-label">待确认订单</div>
                  <div class="todo-sub">被指派方确认后进入合同盖章</div>
                </div>
              </el-card>
            </el-col>
            <el-col :xs="12" :sm="8" :md="6">
              <el-card shadow="hover" class="todo-card" @click="goTo('/purchase', { tab: 'purchase', status: 'confirmed' })">
                <div class="todo-card-inner">
                  <el-icon class="todo-icon"><Van /></el-icon>
                  <div class="todo-main">{{ todos.pendingShip ?? 0 }}</div>
                  <div class="todo-label">待发货</div>
                  <div class="todo-sub">即将逾期 {{ todos.pendingShipApproaching ?? 0 }} · 已逾期 {{ todos.pendingShipOverdue ?? 0 }}</div>
                </div>
              </el-card>
            </el-col>
            <el-col :xs="12" :sm="8" :md="6">
              <el-card shadow="hover" class="todo-card" @click="goTo('/sales', { status: '已发货', filter: 'receipt' })">
                <div class="todo-card-inner">
                  <el-icon class="todo-icon"><Box /></el-icon>
                  <div class="todo-main">{{ todos.pendingDelivery ?? 0 }}</div>
                  <div class="todo-label">待妥投</div>
                  <div class="todo-sub">即将逾期 {{ todos.pendingDeliveryApproaching ?? 0 }} · 已逾期 {{ todos.pendingDeliveryOverdue ?? 0 }}</div>
                </div>
              </el-card>
            </el-col>
            <el-col :xs="12" :sm="8" :md="6">
              <el-card shadow="hover" class="todo-card" @click="goTo('/sales', { status: '已发货', filter: 'receipt' })">
                <div class="todo-card-inner">
                  <el-icon class="todo-icon"><EditPen /></el-icon>
                  <div class="todo-main">{{ todos.pendingReceipt ?? 0 }}</div>
                  <div class="todo-label">待签收</div>
                  <div class="todo-sub">即将逾期 {{ todos.pendingReceiptApproaching ?? 0 }} · 已逾期 {{ todos.pendingReceiptOverdue ?? 0 }}</div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <!-- 第二行：其他待办 -->
          <div class="section-title">其他待办</div>
          <el-row :gutter="16" class="card-row">
            <el-col :xs="12" :sm="8" :md="6">
              <el-card shadow="hover" class="todo-card small" @click="goTo('/opportunity')">
                <div class="todo-card-inner">
                  <el-icon class="todo-icon"><ChatDotRound /></el-icon>
                  <div class="todo-main">{{ todos.pendingAfterSales ?? 0 }}</div>
                  <div class="todo-label">待处理售后</div>
                  <div class="todo-sub">即将逾期 0 · 已逾期 0</div>
                </div>
              </el-card>
            </el-col>
            <el-col :xs="12" :sm="8" :md="6">
              <el-card shadow="hover" class="todo-card small" @click="goTo('/sales')">
                <div class="todo-card-inner">
                  <el-icon class="todo-icon"><Tickets /></el-icon>
                  <div class="todo-main">{{ todos.pendingWorkOrder ?? 0 }}</div>
                  <div class="todo-label">待处理工单</div>
                  <div class="todo-sub">即将逾期 0 · 已逾期 0</div>
                </div>
              </el-card>
            </el-col>
            <el-col :xs="12" :sm="8" :md="6">
              <el-card shadow="hover" class="todo-card small" @click="goTo('/sales')">
                <div class="todo-card-inner">
                  <el-icon class="todo-icon"><Warning /></el-icon>
                  <div class="todo-main">{{ todos.pendingClaim ?? 0 }}</div>
                  <div class="todo-label">待处理索赔单</div>
                  <div class="todo-sub">待申诉 {{ todos.pendingClaimAppeal ?? 0 }} · 待支付 {{ todos.pendingClaimPayment ?? 0 }}</div>
                </div>
              </el-card>
            </el-col>
            <el-col :xs="12" :sm="8" :md="6">
              <el-card shadow="hover" class="todo-card small" @click="goTo('/sales')">
                <div class="todo-card-inner">
                  <el-icon class="todo-icon"><CircleClose /></el-icon>
                  <div class="todo-main">{{ todos.rejectedReceipt ?? 0 }}</div>
                  <div class="todo-label">已拒绝签收单</div>
                </div>
              </el-card>
            </el-col>
            <el-col :xs="12" :sm="8" :md="6">
              <el-card shadow="hover" class="todo-card small" @click="goTo('/product')">
                <div class="todo-card-inner">
                  <el-icon class="todo-icon"><Goods /></el-icon>
                  <div class="todo-main">{{ todos.pendingProduct ?? 0 }}</div>
                  <div class="todo-label">待处理商品</div>
                </div>
              </el-card>
            </el-col>
            <el-col :xs="12" :sm="8" :md="6">
              <el-card shadow="hover" class="todo-card small" @click="goTo('/opportunity')">
                <div class="todo-card-inner">
                  <el-icon class="todo-icon"><Notebook /></el-icon>
                  <div class="todo-main">{{ todos.pendingFiling ?? 0 }}</div>
                  <div class="todo-label">待处理报备</div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <!-- 第三行：客户结算、合作商结算 -->
          <div class="section-title">客户结算</div>
          <el-row :gutter="16" class="card-row">
            <el-col :xs="12" :sm="12" :md="6">
              <el-card shadow="hover" class="todo-card" @click="goTo('/sales', { platformRefundStatus: '未回款,部分回款' })">
                <div class="todo-card-inner">
                  <el-icon class="todo-icon"><Money /></el-icon>
                  <div class="todo-main">{{ todos.pendingCustomerPayment ?? 0 }}</div>
                  <div class="todo-label">待客户回款</div>
                  <div class="todo-sub">即将逾期 {{ todos.pendingCustomerPaymentApproaching ?? 0 }} · 已逾期 {{ todos.pendingCustomerPaymentOverdue ?? 0 }}</div>
                </div>
              </el-card>
            </el-col>
            <el-col :xs="12" :sm="12" :md="6">
              <el-card shadow="hover" class="todo-card" @click="goTo('/settlement/invoice')">
                <div class="todo-card-inner">
                  <el-icon class="todo-icon"><Stamp /></el-icon>
                  <div class="todo-main">{{ todos.pendingInvoiceConfirm ?? 0 }}</div>
                  <div class="todo-label">待确认开票</div>
                  <div class="todo-sub">即将逾期 {{ todos.pendingInvoiceConfirmApproaching ?? 0 }} · 已逾期 {{ todos.pendingInvoiceConfirmOverdue ?? 0 }}</div>
                </div>
              </el-card>
            </el-col>
          </el-row>
          <div class="section-title">合作商结算</div>
          <el-row :gutter="16" class="card-row">
            <el-col :xs="12" :sm="12" :md="6">
              <el-card shadow="hover" class="todo-card" @click="goTo('/settlement/invoice')">
                <div class="todo-card-inner">
                  <el-icon class="todo-icon"><DocumentCopy /></el-icon>
                  <div class="todo-main">{{ todos.pendingInboundInvoice ?? 0 }}</div>
                  <div class="todo-label">待开进项发票</div>
                </div>
              </el-card>
            </el-col>
            <el-col :xs="12" :sm="12" :md="6">
              <el-card shadow="hover" class="todo-card" @click="goTo('/settlement/invoice')">
                <div class="todo-card-inner">
                  <el-icon class="todo-icon"><RefreshLeft /></el-icon>
                  <div class="todo-main">{{ todos.pendingInboundReturn ?? 0 }}</div>
                  <div class="todo-label">待处理进项退票</div>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </div>
      </el-tab-pane>

      <el-tab-pane label="采销数据" name="bi">
        <el-row :gutter="20" class="mt-20">
          <el-col :span="24">
            <el-card header="订单金额趋势" shadow="never">
              <div class="chart-container">
                <v-chart class="chart" :option="chartOption" autoresize />
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-row :gutter="20" class="mt-20">
          <el-col :span="12">
            <el-card header="线下销售汇总" shadow="never">
              <div v-if="offlineSummary.count !== undefined" class="offline-summary">
                <p>笔数：<strong>{{ offlineSummary.count }}</strong></p>
                <p>金额合计：<strong>¥ {{ (offlineSummary.totalAmount || 0).toLocaleString() }}</strong></p>
                <el-button type="primary" link @click="goTo('/sales', { offlineSales: '1' })">查看线下销售列表</el-button>
              </div>
              <el-table v-if="offlineSummary.recent && offlineSummary.recent.length" :data="offlineSummary.recent" size="small" max-height="200">
                <el-table-column prop="omsOrderNo" label="订单号" width="140" />
                <el-table-column prop="amount" label="金额" width="100">
                  <template #default="{ row }">{{ row.amount != null ? Number(row.amount).toLocaleString() : '-' }}</template>
                </el-table-column>
                <el-table-column prop="offlineContractNo" label="线下合同号" />
              </el-table>
              <div v-else-if="offlineSummary.count === 0" class="empty-tip">暂无线下销售数据</div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card header="概览" shadow="never">
              <div class="stat-cards-mini">
                <div class="stat-mini">
                  <span class="stat-mini-label">总订单数</span>
                  <span class="stat-mini-value">{{ stats.orderCount }}</span>
                </div>
                <div class="stat-mini">
                  <span class="stat-mini-label">待处理对账</span>
                  <span class="stat-mini-value">{{ stats.pendingInvoiceCount }}</span>
                </div>
                <div class="stat-mini">
                  <span class="stat-mini-label">已结算</span>
                  <span class="stat-mini-value">{{ stats.settledCount }}</span>
                </div>
                <div class="stat-mini">
                  <span class="stat-mini-label">有效合同</span>
                  <span class="stat-mini-value">{{ stats.contractCount }}</span>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  List, Bell, Finished, Document, Van, Box, EditPen, ChatDotRound, Tickets, Warning,
  CircleClose, CircleCheck, Goods, Notebook, Money, Stamp, DocumentCopy, RefreshLeft
} from '@element-plus/icons-vue'
import request from '../utils/request'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent
} from 'echarts/components'
import VChart from 'vue-echarts'

use([
  CanvasRenderer,
  LineChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent
])

const router = useRouter()
const activeTab = ref('todos')

const stats = ref<any>({
  orderCount: 0,
  pendingInvoiceCount: 0,
  settledCount: 0,
  contractCount: 0,
  trend: { dates: [], orderAmounts: [], invoiceAmounts: [] },
  todos: {}
})

const todos = computed(() => stats.value.todos || {})

const offlineSummary = ref<{ count?: number; totalAmount?: number; recent?: any[] }>({})

const chartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['订单金额', '对账金额'] },
  grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
  xAxis: { type: 'category', boundaryGap: false, data: stats.value.trend?.dates || [] },
  yAxis: { type: 'value' },
  series: [
    { name: '订单金额', type: 'line', smooth: true, data: stats.value.trend?.orderAmounts || [], itemStyle: { color: '#409eff' } },
    { name: '对账金额', type: 'line', smooth: true, data: stats.value.trend?.invoiceAmounts || [], itemStyle: { color: '#67c23a' } }
  ]
}))

function goTo(path: string, query?: Record<string, string>) {
  router.push({ path, query })
}

const fetchStats = async () => {
  try {
    const res: any = await request.get('/dashboard/stats')
    stats.value = res
  } catch (error) {
    console.error('Fetch dashboard stats error:', error)
  }
}

const fetchOfflineSummary = async () => {
  try {
    const res: any = await request.get('/dashboard/offline-summary')
    offlineSummary.value = res
  } catch (error) {
    console.error('Fetch offline summary error:', error)
  }
}

onMounted(() => {
  fetchStats()
})

watch(activeTab, (name) => {
  if (name === 'bi') fetchOfflineSummary()
})
</script>

<style scoped>
.dashboard {
  padding: 10px;
}
.main-tabs {
  margin-top: 8px;
}
.section-title {
  font-size: 14px;
  color: #909399;
  margin: 16px 0 8px;
}
.card-row {
  margin-bottom: 8px;
}
.todo-card {
  cursor: pointer;
  border-radius: 8px;
  margin-bottom: 8px;
}
.todo-card:hover {
  border-color: var(--el-color-primary);
}
.todo-card-inner {
  text-align: center;
  padding: 8px 0;
}
.todo-icon {
  font-size: 28px;
  color: #409eff;
  opacity: 0.8;
  margin-bottom: 8px;
}
.todo-main {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}
.todo-label {
  font-size: 13px;
  color: #606266;
  margin-top: 4px;
}
.todo-sub {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}
.todo-card.small .todo-main { font-size: 20px; }
.todo-card.small .todo-icon { font-size: 24px; }

.mt-20 { margin-top: 20px; }
.chart-container { height: 320px; }
.chart { height: 100%; }

.offline-summary p { margin: 8px 0; }
.empty-tip { color: #909399; padding: 16px; text-align: center; }
.stat-cards-mini { display: flex; flex-wrap: wrap; gap: 16px; }
.stat-mini { min-width: 100px; }
.stat-mini-label { display: block; font-size: 12px; color: #909399; }
.stat-mini-value { font-size: 20px; font-weight: bold; }
</style>
