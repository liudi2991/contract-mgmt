<template>
  <div class="page">
    <div class="page-header">
      <h2>回款管理</h2>
      <el-button type="primary" :icon="Plus" @click="$router.push('/payments/new')">登记回款</el-button>
    </div>

    <el-card>
      <el-form :inline="true" :model="query" @submit.prevent="onSearch">
        <el-form-item label="到账日期">
          <el-date-picker
            v-model="range"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card style="margin-top: 12px">
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column label="到账日期" width="120">
          <template #default="{ row }">{{ fmtDate(row.receivedDate) }}</template>
        </el-table-column>
        <el-table-column label="合同编号" prop="contractNo" width="160" />
        <el-table-column label="客户" prop="customerName" min-width="160" show-overflow-tooltip />
        <el-table-column label="金额" align="right" width="140">
          <template #default="{ row }">¥ {{ fmtMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="付款方" prop="payer" min-width="160" show-overflow-tooltip />
        <el-table-column label="流水号" prop="bankSerial" min-width="160" show-overflow-tooltip />
        <el-table-column label="登记人" prop="createdByName" width="100" />
        <el-table-column label="登记时间" width="160">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="$router.push(`/contracts/${row.contractId}`)">查看合同</el-button>
            <el-popconfirm title="确认删除？将自动反向核销。" @confirm="onDelete(row.id)">
              <template #reference><el-button text type="danger">删除</el-button></template>
            </el-popconfirm>
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
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { paymentApi } from '@/api/payment'
import type { Payment } from '@/api/types'
import { fmtDate, fmtDateTime, fmtMoney } from '@/utils/format'

const list = ref<Payment[]>([])
const total = ref(0)
const loading = ref(false)

const range = ref<[string, string] | null>(null)
const query = reactive({ page: 1, size: 20, dateFrom: '', dateTo: '' })

watch(range, (v) => {
  query.dateFrom = v?.[0] || ''
  query.dateTo = v?.[1] || ''
})

async function fetch() {
  loading.value = true
  try {
    const resp = await paymentApi.page(query)
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
  range.value = null
  query.dateFrom = ''
  query.dateTo = ''
  query.page = 1
  fetch()
}

async function onDelete(id: number) {
  await paymentApi.delete(id)
  ElMessage.success('删除成功')
  fetch()
}

onMounted(fetch)
</script>
