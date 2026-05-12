<template>
  <div class="page">
    <div class="page-header">
      <h2>首页驾驶舱</h2>
      <el-button type="primary" :icon="Plus" @click="$router.push('/contracts/new')">新建合同</el-button>
    </div>

    <el-row :gutter="16" v-loading="loading">
      <el-col :span="6">
        <div class="stat-card">
          <div class="label">合同总数</div>
          <div class="value">{{ stats?.totalCount ?? 0 }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="label">合同总金额 (元)</div>
          <div class="value">¥ {{ fmtMoney(stats?.totalAmount) }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="label">已回款金额 (元)</div>
          <div class="value" style="color: #67c23a">¥ {{ fmtMoney(stats?.paidAmount) }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="label">应收余额 (元)</div>
          <div class="value" style="color: #e6a23c">¥ {{ fmtMoney(stats?.remainingAmount) }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="8">
        <div class="stat-card">
          <div class="label">执行中合同</div>
          <div class="value">{{ stats?.executingCount ?? 0 }}</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card">
          <div class="label">已到期合同</div>
          <div class="value" style="color: #f56c6c">{{ stats?.expiredCount ?? 0 }}</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card">
          <div class="label">已完成合同</div>
          <div class="value" style="color: #909399">{{ stats?.completedCount ?? 0 }}</div>
        </div>
      </el-col>
    </el-row>

    <el-card style="margin-top: 16px">
      <template #header>快捷入口</template>
      <el-space>
        <el-button @click="$router.push('/contracts')">合同列表</el-button>
        <el-button @click="$router.push('/payments/new')">登记回款</el-button>
        <el-button @click="$router.push('/payments')">回款列表</el-button>
      </el-space>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { contractApi } from '@/api/contract'
import type { DashboardStats } from '@/api/types'
import { fmtMoney } from '@/utils/format'

const stats = ref<DashboardStats>()
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const resp = await contractApi.dashboard()
    stats.value = resp.data
  } finally {
    loading.value = false
  }
})
</script>
