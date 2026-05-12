<template>
  <div class="wrap">
    <el-card class="card">
      <h3>{{ auth.mustChangePwd ? '首次登录，请设置新密码' : '修改密码' }}</h3>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" :disabled="loading" @click="onSubmit">提交</el-button>
          <el-button v-if="!auth.mustChangePwd" :disabled="loading" @click="$router.back()">返回</el-button>
        </el-form-item>
      </el-form>
      <p class="hint">提示：密码 8-32 位，必须同时包含字母和数字</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码' }],
  newPassword: [
    { required: true, message: '请输入新密码' },
    { min: 8, max: 32, message: '8-32 位' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/, message: '必须包含字母与数字' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码' },
    {
      validator: (_r, v, cb) => {
        if (v !== form.newPassword) cb(new Error('两次输入不一致'))
        else cb()
      },
    },
  ],
}

async function onSubmit() {
  if (!formRef.value || loading.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    await authApi.changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    })
    auth.setPasswordChanged()
    try {
      await auth.fetchMe()
    } catch {
      /* fetchMe 失败不阻塞跳转；本地状态已是最新 */
    }
    ElMessage.success('密码修改成功')
    await router.replace('/dashboard')
  } catch (e) {
    console.warn('[ChangePassword] submit failed', e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.wrap {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}

.card {
  width: 540px;
  padding: 8px 24px;

  h3 {
    margin: 0 0 24px;
  }
}

.hint {
  color: #909399;
  font-size: 12px;
}
</style>
