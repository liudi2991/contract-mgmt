<template>
  <div class="page">
    <div class="page-header">
      <h2>登记实际回款</h2>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <el-card>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" style="max-width: 720px">
        <el-form-item label="关联合同" prop="contractId">
          <el-select
            v-model="form.contractId"
            placeholder="搜索合同编号或名称"
            filterable
            remote
            :remote-method="onSearchContract"
            :loading="searchLoading"
            style="width: 100%"
            @change="onContractChange"
          >
            <el-option
              v-for="c in contractOptions"
              :key="c.id"
              :label="`${c.contractNo} - ${c.name}（${c.customerName}，余额 ¥${fmtMoney(c.remainingAmount)}）`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="关联期次">
          <el-select v-model="form.planItemId" placeholder="不选则按计划日期升序自动核销" clearable style="width: 100%">
            <el-option
              v-for="p in planItems"
              :key="p.id"
              :label="`第 ${p.periodNo} 期 · 计划 ${p.plannedDate} · ¥${fmtMoney(p.plannedAmount)} · 已收 ¥${fmtMoney(p.receivedAmount)}`"
              :value="p.id"
              :disabled="p.status === 'SETTLED'"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="到账日期" prop="receivedDate">
          <el-date-picker v-model="form.receivedDate" value-format="YYYY-MM-DD" />
        </el-form-item>

        <el-form-item label="实际金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" :controls="false" style="width: 200px" />
          <span style="margin-left: 8px; color: #909399">元</span>
        </el-form-item>

        <el-form-item label="付款方">
          <el-input v-model="form.payer" placeholder="默认带入合同客户名称" maxlength="100" />
        </el-form-item>

        <el-form-item label="银行流水号">
          <el-input v-model="form.bankSerial" maxlength="64" />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import dayjs from 'dayjs'
import { contractApi } from '@/api/contract'
import { paymentApi, paymentPlanApi } from '@/api/payment'
import type { Contract, PaymentPlanItem } from '@/api/types'
import { fmtMoney } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const searchLoading = ref(false)

const form = reactive({
  contractId: undefined as number | undefined,
  planItemId: undefined as number | undefined,
  receivedDate: dayjs().format('YYYY-MM-DD'),
  amount: undefined as number | undefined,
  payer: '',
  bankSerial: '',
  remark: '',
})

const rules: FormRules = {
  contractId: [{ required: true, message: '请选择合同' }],
  receivedDate: [{ required: true, message: '请选择到账日期' }],
  amount: [{ required: true, message: '请输入实际金额' }],
}

const contractOptions = ref<Contract[]>([])
const planItems = ref<PaymentPlanItem[]>([])

async function onSearchContract(keyword: string) {
  searchLoading.value = true
  try {
    const resp = await contractApi.page({ keyword, status: 'EXECUTING', size: 50 })
    contractOptions.value = resp.data.records
  } finally {
    searchLoading.value = false
  }
}

async function onContractChange(contractId: number) {
  form.planItemId = undefined
  if (!contractId) {
    planItems.value = []
    return
  }
  const selected = contractOptions.value.find((c) => c.id === contractId)
  if (selected) form.payer = form.payer || selected.customerName
  planItems.value = (await paymentPlanApi.list(contractId)).data
}

watch(() => route.query.contractId, async (v) => {
  if (v) {
    const id = Number(v)
    const c = (await contractApi.detail(id)).data
    contractOptions.value = [c]
    form.contractId = id
    await onContractChange(id)
  }
}, { immediate: true })

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    const resp = await paymentApi.create(form as never)
    const settled = resp.data.settlements?.map((s) => `第 ${s.periodNo} 期 ¥${fmtMoney(s.settledAmount)}`).join('、')
    ElMessage.success(`登记成功${settled ? '；核销：' + settled : ''}`)
    router.replace(`/contracts/${form.contractId}`)
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (!contractOptions.value.length) onSearchContract('')
})
</script>
