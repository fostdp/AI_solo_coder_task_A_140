<template>
  <div class="alarm-panel">
    <div class="panel-header">
      <div class="alarm-stats">
        <el-tag type="danger" effect="dark">
          未处理: {{ unacknowledgedCount }}
        </el-tag>
        <el-button type="primary" size="small" @click="handleAcknowledgeAll" :disabled="unacknowledgedCount === 0">
          <el-icon><Check /></el-icon>
          全部确认
        </el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="alarm-tabs">
      <el-tab-pane label="未处理" name="unacknowledged">
        <div v-loading="loading" class="alarm-list">
          <div v-if="activeAlarms.length === 0" class="empty-state">
            <el-icon size="48"><CircleCheck /></el-icon>
            <p>暂无未处理告警</p>
          </div>
          <div
            v-for="alarm in activeAlarms"
            :key="alarm.id"
            class="alarm-item"
            :class="`level-${alarm.alarmLevel.toLowerCase()}`"
          >
            <div class="alarm-icon">
              <el-icon v-if="alarm.alarmLevel === 'CRITICAL'"><WarningFilled /></el-icon>
              <el-icon v-else><Warning /></el-icon>
            </div>
            <div class="alarm-content">
              <div class="alarm-header">
                <span class="alarm-type">{{ getAlarmTypeName(alarm.alarmType) }}</span>
                <el-tag :type="getTagType(alarm.alarmLevel)" size="small">
                  {{ alarm.alarmLevel }}
                </el-tag>
              </div>
              <p class="alarm-message">{{ alarm.alarmMessage }}</p>
              <div class="alarm-meta">
                <span>{{ formatTime(alarm.alarmTime) }}</span>
                <span v-if="alarm.parameterName">
                  {{ alarm.parameterName }}: {{ alarm.parameterValue }} / 阈值: {{ alarm.thresholdValue }}
                </span>
              </div>
            </div>
            <el-button
              type="success"
              size="small"
              plain
              @click="handleAcknowledge(alarm.id)"
            >
              确认
            </el-button>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="历史记录" name="history">
        <div v-loading="loadingHistory" class="alarm-list">
          <div
            v-for="alarm in alarmHistory"
            :key="alarm.id"
            class="alarm-item history"
            :class="`level-${alarm.alarmLevel.toLowerCase()}`"
          >
            <div class="alarm-icon">
              <el-icon><Bell /></el-icon>
            </div>
            <div class="alarm-content">
              <div class="alarm-header">
                <span class="alarm-type">{{ getAlarmTypeName(alarm.alarmType) }}</span>
                <el-tag :type="alarm.isAcknowledged ? 'info' : getTagType(alarm.alarmLevel)" size="small">
                  {{ alarm.isAcknowledged ? '已确认' : alarm.alarmLevel }}
                </el-tag>
              </div>
              <p class="alarm-message">{{ alarm.alarmMessage }}</p>
              <div class="alarm-meta">
                <span>{{ formatTime(alarm.alarmTime) }}</span>
              </div>
            </div>
          </div>
          <el-empty v-if="alarmHistory.length === 0" description="暂无历史告警" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Warning, WarningFilled, Check, Bell, CircleCheck } from '@element-plus/icons-vue'
import { getActiveAlarms, getAlarmHistory, acknowledgeAlarm, acknowledgeAllAlarms } from '@/api/alarm'

const props = defineProps({
  shipId: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['acknowledged'])

const activeTab = ref('unacknowledged')
const loading = ref(false)
const loadingHistory = ref(false)
const activeAlarms = ref([])
const alarmHistory = ref([])
const unacknowledgedCount = ref(0)

const fetchActiveAlarms = async () => {
  if (!props.shipId) return
  loading.value = true
  try {
    const res = await getActiveAlarms(props.shipId)
    activeAlarms.value = res.data || []
    unacknowledgedCount.value = activeAlarms.value.length
  } catch (e) {
    console.error('加载活跃告警失败', e)
  } finally {
    loading.value = false
  }
}

const fetchAlarmHistory = async () => {
  if (!props.shipId) return
  loadingHistory.value = true
  try {
    const res = await getAlarmHistory(props.shipId, 0, 50)
    alarmHistory.value = res.data?.content || []
  } catch (e) {
    console.error('加载告警历史失败', e)
  } finally {
    loadingHistory.value = false
  }
}

const handleAcknowledge = async (id) => {
  try {
    await acknowledgeAlarm(id)
    ElMessage.success('告警已确认')
    fetchActiveAlarms()
    emit('acknowledged')
  } catch (e) {
    ElMessage.error('确认失败')
  }
}

const handleAcknowledgeAll = async () => {
  try {
    await acknowledgeAllAlarms(props.shipId)
    ElMessage.success('所有告警已确认')
    fetchActiveAlarms()
    emit('acknowledged')
  } catch (e) {
    ElMessage.error('确认失败')
  }
}

const getAlarmTypeName = (type) => {
  const types = {
    'GM_TOO_LOW': 'GM值过低',
    'ROLL_EXCEEDED': '横摇角过大',
    'BILGE_WATER_HIGH': '舱底水位过高',
    'DRAFT_EXCEEDED': '吃水超限',
    'TRIM_EXCEEDED': '纵倾超限'
  }
  return types[type] || type
}

const getTagType = (level) => {
  const types = {
    'CRITICAL': 'danger',
    'WARNING': 'warning',
    'CAUTION': 'info',
    'INFO': 'info'
  }
  return types[level] || 'info'
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

watch(
  () => props.shipId,
  () => {
    fetchActiveAlarms()
    if (activeTab.value === 'history') {
      fetchAlarmHistory()
    }
  },
  { immediate: true }
)

watch(activeTab, (val) => {
  if (val === 'history') {
    fetchAlarmHistory()
  }
})
</script>

<style scoped lang="scss">
.alarm-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.panel-header {
  padding: 16px 0;
  border-bottom: 1px solid rgba(64, 158, 255, 0.2);

  .alarm-stats {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
}

.alarm-tabs {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;

  :deep(.el-tabs__content) {
    flex: 1;
    overflow: auto;
  }
}

.alarm-list {
  padding: 10px 0;
}

.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: #a0aec0;

  .el-icon {
    color: #67C23A;
    margin-bottom: 12px;
  }
}

.alarm-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  margin-bottom: 10px;
  background: rgba(15, 35, 60, 0.85);
  border: 1px solid rgba(64, 158, 255, 0.3);
  border-left: 4px solid #409EFF;
  border-radius: 6px;
  transition: all 0.3s;

  &.level-critical {
    border-left-color: #F56C6C;
    background: rgba(245, 108, 108, 0.1);
  }

  &.level-warning {
    border-left-color: #E6A23C;
    background: rgba(230, 162, 60, 0.1);
  }

  &.history {
    opacity: 0.8;
  }

  .alarm-icon {
    font-size: 24px;
    flex-shrink: 0;
  }

  .level-critical .alarm-icon {
    color: #F56C6C;
    animation: pulse 1s infinite;
  }

  .level-warning .alarm-icon {
    color: #E6A23C;
  }

  .alarm-content {
    flex: 1;
    min-width: 0;

    .alarm-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 4px;

      .alarm-type {
        font-weight: bold;
        color: #ffffff;
      }
    }

    .alarm-message {
      color: #e2e8f0;
      margin: 4px 0;
      font-size: 14px;
    }

    .alarm-meta {
      display: flex;
      gap: 16px;
      color: #a0aec0;
      font-size: 12px;
    }
  }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
</style>
