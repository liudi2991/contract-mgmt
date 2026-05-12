<template>
  <div class="login-wrap">
    <div class="login-card">
      <h2>合同回款管理系统</h2>
      <p class="sub">公司内部使用 · 请使用账号登录</p>
      <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password />
        </el-form-item>
        <el-button type="primary" size="large" :loading="loading" native-type="submit" style="width: 100%">
          登录
        </el-button>
      </el-form>
      <p class="tip">默认管理员：admin / Admin@1234（首次登录需改密）</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function onSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    if (auth.mustChangePwd) {
      router.replace('/change-password')
    } else {
      router.replace('/dashboard')
    }
  } catch (_) {
    /* error already shown by interceptor */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-wrap {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1890ff 0%, #002140 100%);
}

.login-card {
  width: 400px;
  background: #fff;
  border-radius: 8px;
  padding: 40px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.16);

  h2 {
    text-align: center;
    margin: 0 0 8px;
    color: #303133;
  }

  .sub {
    text-align: center;
    color: #909399;
    font-size: 13px;
    margin: 0 0 24px;
  }

  .tip {
    text-align: center;
    color: #c0c4cc;
    font-size: 12px;
    margin-top: 24px;
  }
}
</style>
