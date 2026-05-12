<template>
  <div class="page">
    <div class="page-header">
      <h2>{{ isEdit ? '编辑合同' : '新建合同' }}</h2>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <el-card>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" style="max-width: 760px">
        <el-form-item label="合同名称" prop="name">
          <el-input v-model="form.name" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="客户名称" prop="customerName">
          <el-input v-model="form.customerName" maxlength="100" placeholder="录入相同字符串将被视为同一客户参与统计" />
        </el-form-item>
        <el-form-item label="合同金额" prop="amount">
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" :controls="false" style="width: 200px" />
          <span style="margin-left: 8px; color: #909399">元</span>
        </el-form-item>
        <el-form-item label="签订日期" prop="signDate">
          <el-date-picker v-model="form.signDate" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="生效日期" prop="effectiveDate">
          <el-date-picker v-model="form.effectiveDate" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="到期日期" prop="expireDate">
          <el-date-picker v-model="form.expireDate" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="负责人" prop="ownerId">
          <el-select v-model="form.ownerId" placeholder="选择负责人" filterable style="width: 240px">
            <el-option v-for="u in owners" :key="u.id" :label="u.name" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="onSubmit">保存{{ isEdit ? '' : '并生成回款计划' }}</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { contractApi } from '@/api/contract'
import { userApi } from '@/api/user'
import type { User } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const owners = ref<User[]>([])

const isEdit = computed(() => !!route.params.id)
const contractId = computed(() => Number(route.params.id))

const form = reactive({
  name: '',
  customerName: '',
  amount: undefined as number | undefined,
  currency: 'CNY',
  signDate: '',
  effectiveDate: '',
  expireDate: '',
  ownerId: auth.user?.id || (undefined as unknown as number),
  remark: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入合同名称' }],
  customerName: [{ required: true, message: '请输入客户名称' }],
  amount: [{ required: true, message: '请输入合同金额' }],
  signDate: [{ required: true, message: '请选择签订日期' }],
  effectiveDate: [{ required: true, message: '请选择生效日期' }],
  expireDate: [{ required: true, message: '请选择到期日期' }],
  ownerId: [{ required: true, message: '请选择负责人' }],
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  loading.value = true
  try {
    if (isEdit.value) {
      await contractApi.update(contractId.value, form as never)
      ElMessage.success('保存成功')
      router.replace(`/contracts/${contractId.value}`)
    } else {
      const resp = await contractApi.create(form as never)
      ElMessage.success('合同创建成功，下一步生成回款计划')
      router.replace(`/contracts/${resp.data.id}/payment-plan`)
    }
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const userResp = await userApi.options()
  owners.value = userResp.data
  if (isEdit.value) {
    const c = (await contractApi.detail(contractId.value)).data
    Object.assign(form, {
      name: c.name,
      customerName: c.customerName,
      amount: Number(c.amount),
      currency: c.currency,
      signDate: c.signDate,
      effectiveDate: c.effectiveDate,
      expireDate: c.expireDate,
      ownerId: c.ownerId,
      remark: c.remark || '',
    })
  } else if (auth.user) {
    form.ownerId = auth.user.id
  }
})
</script>
