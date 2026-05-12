<template>
  <div class="page" v-loading="loading">
    <div class="page-header">
      <h2>
        合同详情
        <el-tag v-if="contract" :type="STATUS_MAP[contract.status]?.type" style="margin-left: 8px">
          {{ STATUS_MAP[contract.status]?.label }}
        </el-tag>
      </h2>
      <el-space>
        <el-button v-if="contract && contract.status !== 'VOIDED'" type="primary" @click="$router.push(`/contracts/${id}/edit`)">
          编辑
        </el-button>
        <el-button v-if="contract && contract.status !== 'VOIDED'" @click="onVoid">作废</el-button>
        <el-button @click="$router.back()">返回</el-button>
      </el-space>
    </div>

    <el-card v-if="contract">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="合同编号">{{ contract.contractNo }}</el-descriptions-item>
        <el-descriptions-item label="合同名称">{{ contract.name }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ contract.customerName }}</el-descriptions-item>
        <el-descriptions-item label="合同金额">¥ {{ fmtMoney(contract.amount) }}</el-descriptions-item>
        <el-descriptions-item label="已回款">¥ {{ fmtMoney(contract.paidAmount) }}</el-descriptions-item>
        <el-descriptions-item label="应收余额">¥ {{ fmtMoney(contract.remainingAmount) }}</el-descriptions-item>
        <el-descriptions-item label="签订日期">{{ fmtDate(contract.signDate) }}</el-descriptions-item>
        <el-descriptions-item label="生效日期">{{ fmtDate(contract.effectiveDate) }}</el-descriptions-item>
        <el-descriptions-item label="到期日期">{{ fmtDate(contract.expireDate) }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ contract.ownerName }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fmtDateTime(contract.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ contract.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card style="margin-top: 12px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>回款计划</span>
          <el-space>
            <el-button v-if="contract && contract.status !== 'VOIDED'" type="primary" size="small" @click="goRegister">
              登记回款
            </el-button>
            <el-button v-if="contract && contract.status !== 'VOIDED' && plan.length === 0" size="small" @click="goPlan">
              生成计划
            </el-button>
          </el-space>
        </div>
      </template>
      <el-table :data="plan" border>
        <el-table-column label="期次" prop="periodNo" width="80" align="center" />
        <el-table-column label="计划日期" width="120">
          <template #default="{ row }">{{ fmtDate(row.plannedDate) }}</template>
        </el-table-column>
        <el-table-column label="计划金额" align="right" width="140">
          <template #default="{ row }">¥ {{ fmtMoney(row.plannedAmount) }}</template>
        </el-table-column>
        <el-table-column label="已回款" align="right" width="140">
          <template #default="{ row }">¥ {{ fmtMoney(row.receivedAmount) }}</template>
        </el-table-column>
        <el-table-column label="进度" width="220">
          <template #default="{ row }">
            <el-progress :percentage="progressOf(row)" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="PLAN_STATUS_MAP[row.status as keyof typeof PLAN_STATUS_MAP]?.type">
              {{ PLAN_STATUS_MAP[row.status as keyof typeof PLAN_STATUS_MAP]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-card style="margin-top: 12px">
      <template #header>实际回款流水</template>
      <el-table :data="payments" border>
        <el-table-column label="到账日期" width="120">
          <template #default="{ row }">{{ fmtDate(row.receivedDate) }}</template>
        </el-table-column>
        <el-table-column label="金额" align="right" width="140">
          <template #default="{ row }">¥ {{ fmtMoney(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="付款方" prop="payer" min-width="160" show-overflow-tooltip />
        <el-table-column label="流水号" prop="bankSerial" min-width="160" show-overflow-tooltip />
        <el-table-column label="登记人" prop="createdByName" width="100" />
        <el-table-column label="登记时间" width="160">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-popconfirm title="确定删除该笔回款？将自动反向核销。" @confirm="onDelete(row.id)">
              <template #reference><el-button text type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { contractApi } from '@/api/contract'
import { paymentApi, paymentPlanApi } from '@/api/payment'
import type { Contract, Payment, PaymentPlanItem } from '@/api/types'
import { PLAN_STATUS_MAP, STATUS_MAP, fmtDate, fmtDateTime, fmtMoney } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const id = computed(() => Number(route.params.id))

const contract = ref<Contract>()
const plan = ref<PaymentPlanItem[]>([])
const payments = ref<Payment[]>([])
const loading = ref(false)

async function fetch() {
  loading.value = true
  try {
    contract.value = (await contractApi.detail(id.value)).data
    plan.value = (await paymentPlanApi.list(id.value)).data
    payments.value = (await paymentApi.page({ contractId: id.value, size: 200 })).data.records
  } finally {
    loading.value = false
  }
}

function progressOf(row: PaymentPlanItem) {
  const planned = Number(row.plannedAmount)
  const received = Number(row.receivedAmount)
  if (!planned) return 0
  return Math.min(100, Math.round((received / planned) * 100))
}

function goRegister() {
  router.push({ path: '/payments/new', query: { contractId: id.value } })
}

function goPlan() {
  router.push(`/contracts/${id.value}/payment-plan`)
}

async function onVoid() {
  try {
    const { value } = await ElMessageBox.prompt('请输入作废原因', '作废合同', {
      confirmButtonText: '确定作废',
      cancelButtonText: '取消',
      inputPattern: /.{2,}/,
      inputErrorMessage: '请输入至少 2 个字符',
    })
    await contractApi.voidContract(id.value, value)
    ElMessage.success('已作废')
    fetch()
  } catch (_) {}
}

async function onDelete(paymentId: number) {
  await paymentApi.delete(paymentId)
  ElMessage.success('删除成功')
  fetch()
}

onMounted(fetch)
</script>
