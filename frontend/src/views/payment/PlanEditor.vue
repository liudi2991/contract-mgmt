<template>
  <div class="page">
    <div class="page-header">
      <h2>生成回款计划</h2>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <el-card v-loading="loading">
      <el-alert v-if="contract" type="info" :closable="false">
        合同：<b>{{ contract.contractNo }}</b> · {{ contract.name }} · 客户 {{ contract.customerName }} · 总金额
        <b style="color: #1890ff">¥ {{ fmtMoney(contract.amount) }}</b>
      </el-alert>

      <el-form label-width="120px" style="margin-top: 16px">
        <el-form-item label="生成方式">
          <el-radio-group v-model="mode">
            <el-radio value="UNIFORM">等额均分</el-radio>
            <el-radio value="RATIO">按比例自定义</el-radio>
            <el-radio value="MANUAL">手工录入</el-radio>
          </el-radio-group>
        </el-form-item>

        <template v-if="mode !== 'MANUAL'">
          <el-form-item label="分期数">
            <el-input-number v-model="periods" :min="1" :max="60" />
          </el-form-item>
          <el-form-item label="相邻间隔">
            <el-input-number v-model="intervalDays" :min="1" :max="365" />
            <span style="margin-left: 8px; color: #909399">天</span>
          </el-form-item>
          <el-form-item label="首期日期">
            <el-date-picker v-model="firstDate" value-format="YYYY-MM-DD" placeholder="默认：签订日 + 间隔天" />
          </el-form-item>
          <el-form-item v-if="mode === 'RATIO'" label="比例分配">
            <div style="display: flex; flex-wrap: wrap; gap: 8px">
              <el-input-number
                v-for="(_, idx) in ratios"
                :key="idx"
                v-model="ratios[idx]"
                :min="0"
                :max="100"
                :precision="2"
                :controls="false"
                style="width: 90px"
              />
              <span :style="{ color: ratioSum === 100 ? '#67c23a' : '#f56c6c' }">合计 {{ ratioSum }}%</span>
            </div>
          </el-form-item>
        </template>

        <template v-else>
          <el-table :data="manualItems" border style="margin-bottom: 12px">
            <el-table-column label="期次" width="80" align="center">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column label="计划日期" width="180">
              <template #default="{ row }">
                <el-date-picker v-model="row.plannedDate" value-format="YYYY-MM-DD" />
              </template>
            </el-table-column>
            <el-table-column label="计划金额" width="180">
              <template #default="{ row }">
                <el-input-number v-model="row.plannedAmount" :min="0.01" :precision="2" :controls="false" />
              </template>
            </el-table-column>
            <el-table-column label="备注">
              <template #default="{ row }">
                <el-input v-model="row.remark" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ $index }">
                <el-button text type="danger" @click="manualItems.splice($index, 1)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button @click="addRow">+ 添加一期</el-button>
          <span style="margin-left: 16px; color: #909399">
            合计：¥ {{ fmtMoney(manualSum) }} / 合同 ¥ {{ fmtMoney(contract?.amount) }}
          </span>
        </template>

        <el-form-item style="margin-top: 24px">
          <el-button type="primary" :loading="submitting" @click="onSubmit">保存计划</el-button>
          <el-button @click="$router.push(`/contracts/${contractId}`)">跳过</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { contractApi } from '@/api/contract'
import { paymentPlanApi } from '@/api/payment'
import type { Contract } from '@/api/types'
import { fmtMoney } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const contractId = Number(route.params.id)

const contract = ref<Contract>()
const loading = ref(false)
const submitting = ref(false)

const mode = ref<'UNIFORM' | 'RATIO' | 'MANUAL'>('UNIFORM')
const periods = ref(3)
const intervalDays = ref(30)
const firstDate = ref('')
const ratios = ref<number[]>([])
const manualItems = ref<{ plannedDate: string; plannedAmount: number; remark: string }[]>([])

watch(periods, (n) => {
  if (mode.value === 'RATIO') {
    const each = Math.floor(10000 / n) / 100
    ratios.value = Array.from({ length: n }, (_, i) => (i === n - 1 ? 100 - each * (n - 1) : each))
  }
})

watch(mode, (v) => {
  if (v === 'RATIO') {
    const n = periods.value
    const each = Math.floor(10000 / n) / 100
    ratios.value = Array.from({ length: n }, (_, i) => (i === n - 1 ? 100 - each * (n - 1) : each))
  } else if (v === 'MANUAL' && manualItems.value.length === 0) {
    addRow()
  }
})

const ratioSum = computed(() =>
  Math.round(ratios.value.reduce((s, v) => s + (v || 0), 0) * 100) / 100,
)

const manualSum = computed(() =>
  manualItems.value.reduce((s, it) => s + (Number(it.plannedAmount) || 0), 0),
)

function addRow() {
  manualItems.value.push({ plannedDate: '', plannedAmount: 0, remark: '' })
}

async function onSubmit() {
  submitting.value = true
  try {
    if (mode.value === 'MANUAL') {
      const items = manualItems.value.map((it, idx) => ({
        periodNo: idx + 1,
        plannedDate: it.plannedDate,
        plannedAmount: it.plannedAmount,
        remark: it.remark,
      }))
      await paymentPlanApi.generate(contractId, { mode: 'MANUAL', items })
    } else if (mode.value === 'RATIO') {
      if (ratioSum.value !== 100) {
        ElMessage.error('比例之和必须等于 100%')
        return
      }
      await paymentPlanApi.generate(contractId, {
        mode: 'RATIO',
        periods: periods.value,
        ratios: ratios.value,
        intervalDays: intervalDays.value,
        firstDate: firstDate.value || undefined,
      })
    } else {
      await paymentPlanApi.generate(contractId, {
        mode: 'UNIFORM',
        periods: periods.value,
        intervalDays: intervalDays.value,
        firstDate: firstDate.value || undefined,
      })
    }
    ElMessage.success('回款计划已生成')
    router.replace(`/contracts/${contractId}`)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    contract.value = (await contractApi.detail(contractId)).data
  } finally {
    loading.value = false
  }
})
</script>
