<template>
  <div class="page">
    <div class="page-header">
      <h2>合同管理</h2>
      <el-button type="primary" :icon="Plus" @click="$router.push('/contracts/new')">新建合同</el-button>
    </div>

    <el-card class="search-card" shadow="never">
      <el-form :model="query" label-width="72px" @submit.prevent="onSearch">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12" :md="8" :lg="6" :xl="6">
            <el-form-item label="关键字">
              <el-input v-model="query.keyword" placeholder="合同编号 / 名称" clearable />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6" :xl="6">
            <el-form-item label="客户">
              <el-input v-model="query.customerName" placeholder="客户名称" clearable />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6" :xl="6">
            <el-form-item label="状态">
              <el-select v-model="query.status" placeholder="全部" clearable>
                <el-option label="执行中" value="EXECUTING" />
                <el-option label="已完成" value="COMPLETED" />
                <el-option label="已到期" value="EXPIRED" />
                <el-option label="已作废" value="VOIDED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="8" :lg="6" :xl="6">
            <el-form-item label="签订日期">
              <el-date-picker
                v-model="signRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始"
                end-placeholder="结束"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="search-actions">
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card style="margin-top: 12px">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="contractNo" label="合同编号" width="160" />
        <el-table-column prop="name" label="合同名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" min-width="140" show-overflow-tooltip />
        <el-table-column label="合同金额" width="140" align="right">
          <template #default="{ row }">¥ {{ fmtMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="已回款" width="140" align="right">
          <template #default="{ row }">¥ {{ fmtMoney(row.paidAmount) }}</template>
        </el-table-column>
        <el-table-column label="应收余额" width="140" align="right">
          <template #default="{ row }">
            <span :style="{ color: Number(row.remainingAmount) > 0 ? '#e6a23c' : '#67c23a' }">
              ¥ {{ fmtMoney(row.remainingAmount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="签订日期" width="110">
          <template #default="{ row }">{{ fmtDate(row.signDate) }}</template>
        </el-table-column>
        <el-table-column label="到期日期" width="110">
          <template #default="{ row }">{{ fmtDate(row.expireDate) }}</template>
        </el-table-column>
        <el-table-column prop="ownerName" label="负责人" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="STATUS_MAP[row.status as keyof typeof STATUS_MAP]?.type">
              {{ STATUS_MAP[row.status as keyof typeof STATUS_MAP]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="$router.push(`/contracts/${row.id}`)">查看</el-button>
            <el-button text type="primary" @click="$router.push(`/contracts/${row.id}/edit`)" v-if="row.status !== 'VOIDED'">
              编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @size-change="fetch"
        @current-change="fetch"
        style="margin-top: 12px; justify-content: flex-end"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import { contractApi } from '@/api/contract'
import type { Contract } from '@/api/types'
import { STATUS_MAP, fmtDate, fmtMoney } from '@/utils/format'

const list = ref<Contract[]>([])
const total = ref(0)
const loading = ref(false)
const signRange = ref<[string, string] | null>(null)

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  customerName: '',
  status: '',
  signDateFrom: '',
  signDateTo: '',
})

watch(signRange, (v) => {
  query.signDateFrom = v?.[0] || ''
  query.signDateTo = v?.[1] || ''
})

async function fetch() {
  loading.value = true
  try {
    const resp = await contractApi.page(query)
    list.value = resp.data.records
    total.value = resp.data.total
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  fetch()
}

function onReset() {
  query.keyword = ''
  query.customerName = ''
  query.status = ''
  query.signDateFrom = ''
  query.signDateTo = ''
  signRange.value = null
  query.page = 1
  fetch()
}

onMounted(fetch)
</script>

<style scoped lang="scss">
.search-card {
  :deep(.el-card__body) {
    padding: 18px 20px 4px;
  }

  :deep(.el-form-item) {
    margin-bottom: 16px;
  }

  :deep(.el-form-item__content) {
    flex: 1;
  }

  :deep(.el-input),
  :deep(.el-select),
  :deep(.el-date-editor.el-input__wrapper),
  :deep(.el-date-editor--daterange) {
    width: 100%;
  }

  .search-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    padding-bottom: 4px;
  }
}
</style>
