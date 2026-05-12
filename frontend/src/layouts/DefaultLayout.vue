<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">合同回款管理</div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/dashboard">
          <el-icon><DataLine /></el-icon>
          <span>首页驾驶舱</span>
        </el-menu-item>
        <el-menu-item index="/contracts">
          <el-icon><Document /></el-icon>
          <span>合同管理</span>
        </el-menu-item>
        <el-menu-item index="/payments">
          <el-icon><Money /></el-icon>
          <span>回款管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="breadcrumb">{{ currentTitle }}</div>
        <el-dropdown @command="onCommand">
          <span class="user-info">
            {{ auth.user?.name }}（{{ auth.isAdmin ? '管理员' : '销售员' }}）
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="change-password">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ArrowDown, DataLine, Document, Money } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activeMenu = computed(() => {
  if (route.path.startsWith('/contracts')) return '/contracts'
  if (route.path.startsWith('/payments')) return '/payments'
  return route.path
})

const currentTitle = computed(() => (route.meta.title as string) || '')

async function onCommand(cmd: string) {
  if (cmd === 'logout') {
    await auth.logout()
    router.replace('/login')
  } else if (cmd === 'change-password') {
    router.push('/change-password')
  }
}
</script>

<style scoped lang="scss">
.layout {
  height: 100vh;
}

.aside {
  background: #001529;
  color: #fff;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  background: #002140;
}

.menu {
  border-right: none;
  background: #001529;

  :deep(.el-menu-item) {
    color: rgba(255, 255, 255, 0.75);

    &:hover,
    &.is-active {
      background: #1890ff !important;
      color: #fff;
    }
  }
}

.header {
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;

  .breadcrumb {
    font-size: 15px;
    color: #303133;
  }

  .user-info {
    cursor: pointer;
    color: #606266;
    display: flex;
    align-items: center;
    gap: 4px;
  }
}

.main {
  background: #f0f2f5;
  padding: 0;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
