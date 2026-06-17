<template>
  <div class="app-container">
    <el-container class="main-container">
      <el-header class="header">
        <div class="header-left">
          <el-icon class="logo-icon"><Ship /></el-icon>
          <h1 class="title">古代沙船稳性仿真与装载优化系统</h1>
        </div>
        <div class="header-center">
          <el-select v-model="currentShipId" placeholder="选择船舶" @change="onShipChange" size="default">
            <el-option
              v-for="ship in ships"
              :key="ship.id"
              :label="ship.name"
              :value="ship.id"
            />
          </el-select>
        </div>
        <div class="header-right">
          <el-badge :value="unreadAlarms" :hidden="unreadAlarms === 0" class="alarm-badge">
            <el-button type="danger" plain @click="showAlarms = true">
              <el-icon><Warning /></el-icon>
              告警
            </el-button>
          </el-badge>
          <div class="connection-status" :class="{ connected: wsConnected }">
            <span class="status-dot"></span>
            <span>{{ wsConnected ? '实时连接' : '已断开' }}</span>
          </div>
        </div>
      </el-header>

      <el-container class="content-container">
        <el-aside width="220px" class="sidebar">
          <el-menu
            :default-active="activeMenu"
            class="side-menu"
            background-color="transparent"
            text-color="#ffffff"
            active-text-color="#409EFF"
            @select="onMenuSelect"
          >
            <el-menu-item index="dashboard">
              <el-icon><DataAnalysis /></el-icon>
              <span>实时监控</span>
            </el-menu-item>
            <el-menu-item index="3d-view">
              <el-icon><View /></el-icon>
              <span>三维视窗</span>
            </el-menu-item>
            <el-menu-item index="stability">
              <el-icon><TrendCharts /></el-icon>
              <span>稳性分析</span>
            </el-menu-item>
            <el-menu-item index="loading">
              <el-icon><Box /></el-icon>
              <span>装载优化</span>
            </el-menu-item>
            <el-menu-item index="history">
              <el-icon><Histogram /></el-icon>
              <span>历史数据</span>
            </el-menu-item>
          </el-menu>
        </el-aside>

        <el-main class="main-content">
          <router-view v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <component :is="Component" :ship-id="currentShipId" :key="currentShipId" />
            </transition>
          </router-view>
        </el-main>
      </el-container>
    </el-container>

    <el-drawer
      v-model="showAlarms"
      title="告警中心"
      direction="rtl"
      size="480px"
    >
      <AlarmPanel :ship-id="currentShipId" @acknowledged="fetchUnreadCount" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Ship, Warning, DataAnalysis, View, TrendCharts, Box, Histogram } from '@element-plus/icons-vue'
import { getShips } from '@/api/ship'
import { getUnacknowledgedCount } from '@/api/alarm'
import { useWebSocket } from '@/composables/useWebSocket'
import AlarmPanel from '@/components/AlarmPanel.vue'

const router = useRouter()
const route = useRoute()

const ships = ref([])
const currentShipId = ref(null)
const activeMenu = ref('dashboard')
const showAlarms = ref(false)
const unreadAlarms = ref(0)
const { connected: wsConnected, reconnect } = useWebSocket()

const onShipChange = (shipId) => {
  localStorage.setItem('currentShipId', shipId)
  ElMessage.success(`已切换至 ${ships.value.find(s => s.id === shipId)?.name}`)
}

const onMenuSelect = (index) => {
  activeMenu.value = index
  router.push(`/${index}`)
}

const fetchShips = async () => {
  try {
    const res = await getShips()
    ships.value = res.data || []
    if (ships.value.length > 0 && !currentShipId.value) {
      const saved = localStorage.getItem('currentShipId')
      currentShipId.value = saved && ships.value.find(s => s.id === saved)
        ? saved
        : ships.value[0].id
    }
  } catch (e) {
    ElMessage.error('加载船舶列表失败')
  }
}

const fetchUnreadCount = async () => {
  if (currentShipId.value) {
    try {
      const res = await getUnacknowledgedCount(currentShipId.value)
      unreadAlarms.value = res.data?.count || 0
    } catch (e) {
      console.error('获取未读告警数失败', e)
    }
  }
}

watch(currentShipId, () => {
  fetchUnreadCount()
})

watch(
  () => route.name,
  (name) => {
    if (name) {
      activeMenu.value = name
    }
  },
  { immediate: true }
)

onMounted(() => {
  fetchShips()
  fetchUnreadCount()

  const alarmInterval = setInterval(fetchUnreadCount, 10000)

  onUnmounted(() => {
    clearInterval(alarmInterval)
  })
})
</script>

<style scoped lang="scss">
.app-container {
  width: 100%;
  height: 100%;
}

.main-container {
  height: 100%;
}

.header {
  background: linear-gradient(90deg, rgba(12, 25, 41, 0.98) 0%, rgba(26, 54, 93, 0.98) 100%);
  border-bottom: 1px solid rgba(64, 158, 255, 0.3);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 60px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;

    .logo-icon {
      font-size: 32px;
      color: #409EFF;
    }

    .title {
      font-size: 20px;
      font-weight: bold;
      color: #ffffff;
      margin: 0;
      background: linear-gradient(90deg, #409EFF, #67C23A);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 20px;

    .alarm-badge {
      :deep(.el-badge__content) {
        background-color: #F56C6C;
      }
    }

    .connection-status {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #a0aec0;
      font-size: 14px;

      .status-dot {
        width: 10px;
        height: 10px;
        border-radius: 50%;
        background: #F56C6C;
        animation: blink 2s infinite;
      }

      &.connected .status-dot {
        background: #67C23A;
        animation: none;
        box-shadow: 0 0 10px #67C23A;
      }
    }
  }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.content-container {
  flex: 1;
  overflow: hidden;
}

.sidebar {
  background: rgba(12, 25, 41, 0.95);
  border-right: 1px solid rgba(64, 158, 255, 0.2);
  padding-top: 10px;

  .side-menu {
    border-right: none;

    :deep(.el-menu-item) {
      height: 50px;
      line-height: 50px;
      margin: 4px 10px;
      border-radius: 6px;
      transition: all 0.3s;

      &:hover {
        background: rgba(64, 158, 255, 0.1);
      }

      &.is-active {
        background: linear-gradient(90deg, rgba(64, 158, 255, 0.2) 0%, transparent 100%);
      }
    }
  }
}

.main-content {
  padding: 20px;
  overflow: auto;
  background: linear-gradient(135deg, rgba(12, 25, 41, 0.9) 0%, rgba(26, 54, 93, 0.9) 100%);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
