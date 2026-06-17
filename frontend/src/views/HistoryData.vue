<template>
  <div class="history-data">
    <div class="page-header">
      <h2 class="page-title">
        <el-icon><Histogram /></el-icon>
        历史数据
      </h2>
      <div class="header-filters">
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          size="default"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DDTHH:mm:ss"
          @change="loadData"
        />
        <el-select v-model="dataType" size="default" @change="loadData" style="width: 160px">
          <el-option label="传感器数据" value="sensor" />
          <el-option label="稳性数据" value="stability" />
          <el-option label="告警记录" value="alarm" />
        </el-select>
        <el-button type="primary" @click="loadData" :loading="loading">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
        <el-button @click="exportData">
          <el-icon><Download /></el-icon>
          导出
        </el-button>
      </div>
    </div>

    <div class="data-content">
      <el-tabs v-model="activeTab" @tab-change="onTabChange" class="data-tabs">
        <el-tab-pane label="传感器数据" name="sensor">
          <div class="table-card">
            <div class="card-header">
              <h3 class="card-title">
                <el-icon><Monitor /></el-icon>
                传感器历史数据
              </h3>
              <div class="stats-summary">
                <el-tag type="primary" effect="dark">
                  共 {{ sensorPagination.total }} 条记录
                </el-tag>
              </div>
            </div>
            <div class="table-container">
              <el-table
                :data="sensorData"
                v-loading="loading"
                stripe
                border
                style="width: 100%"
              >
                <el-table-column prop="sensorTime" label="上报时间" width="180">
                  <template #default="{ row }">
                    <span class="time-text">{{ formatTime(row.sensorTime) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="draftDepth" label="吃水深度 (m)" width="140">
                  <template #default="{ row }">
                    <span class="metric-value">{{ row.draftDepth?.toFixed(3) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="rollAngle" label="横摇角 (°)" width="130">
                  <template #default="{ row }">
                    <span :class="getRollClass(row.rollAngle)">
                      {{ row.rollAngle?.toFixed(2) }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="pitchAngle" label="纵摇角 (°)" width="130">
                  <template #default="{ row }">
                    <span class="metric-value">{{ row.pitchAngle?.toFixed(2) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="bilgeWaterLevel" label="舱底水位 (m)" width="140">
                  <template #default="{ row }">
                    <span :class="getBilgeClass(row.bilgeWaterLevel)">
                      {{ row.bilgeWaterLevel?.toFixed(3) }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="yawAngle" label="首摇角 (°)" width="130">
                  <template #default="{ row }">
                    <span class="metric-value">{{ row.yawAngle?.toFixed(2) || '-' }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="cargoDistributionJson" label="货物分布" min-width="200">
                  <template #default="{ row }">
                    <span class="json-text">{{ formatCargoDistribution(row.cargoDistributionJson) }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <div class="pagination-container">
              <el-pagination
                v-model:current-page="sensorPagination.page"
                v-model:page-size="sensorPagination.size"
                :page-sizes="[10, 20, 50, 100]"
                :total="sensorPagination.total"
                layout="total, sizes, prev, pager, next, jumper"
                @size-change="loadSensorData"
                @current-change="loadSensorData"
              />
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="稳性数据" name="stability">
          <div class="table-card">
            <div class="card-header">
              <h3 class="card-title">
                <el-icon><TrendCharts /></el-icon>
                稳性计算历史
              </h3>
              <div class="stats-summary">
                <el-tag type="success" effect="dark">
                  最优 GM: {{ bestGm?.toFixed(3) || '--' }} m
                </el-tag>
                <el-tag type="warning" effect="dark">
                  共 {{ stabilityPagination.total }} 条记录
                </el-tag>
              </div>
            </div>
            <div class="table-container">
              <el-table
                :data="stabilityData"
                v-loading="loading"
                stripe
                border
                style="width: 100%"
              >
                <el-table-column prop="calculationTime" label="计算时间" width="180">
                  <template #default="{ row }">
                    <span class="time-text">{{ formatTime(row.calculationTime) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="gmValue" label="GM值 (m)" width="130">
                  <template #default="{ row }">
                    <span :class="getGmClass(row.gmValue)">
                      {{ row.gmValue?.toFixed(4) }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="rollPeriod" label="横摇周期 (s)" width="130">
                  <template #default="{ row }">
                    <span class="metric-value">{{ row.rollPeriod?.toFixed(3) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="rightingMoment" label="复原力矩 (kN·m)" width="150">
                  <template #default="{ row }">
                    <span class="metric-value">{{ row.rightingMoment?.toFixed(2) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="rightingArm" label="复原力臂 (m)" width="130">
                  <template #default="{ row }">
                    <span class="metric-value">{{ row.rightingArm?.toFixed(4) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="displacementActual" label="实际排水量 (t)" width="150">
                  <template #default="{ row }">
                    <span class="metric-value">{{ row.displacementActual?.toFixed(1) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="cgZ" label="重心KG (m)" width="120">
                  <template #default="{ row }">
                    <span class="metric-value">{{ row.cgZ?.toFixed(3) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="stabilityStatus" label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag :type="getStatusType(row.stabilityStatus)" size="small" effect="dark">
                      {{ getStatusText(row.stabilityStatus) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="100" fixed="right">
                  <template #default="{ row }">
                    <el-button type="primary" link size="small" @click="viewStabilityDetail(row)">
                      详情
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <div class="pagination-container">
              <el-pagination
                v-model:current-page="stabilityPagination.page"
                v-model:page-size="stabilityPagination.size"
                :page-sizes="[10, 20, 50, 100]"
                :total="stabilityPagination.total"
                layout="total, sizes, prev, pager, next, jumper"
                @size-change="loadStabilityData"
                @current-change="loadStabilityData"
              />
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="告警记录" name="alarm">
          <div class="table-card">
            <div class="card-header">
              <h3 class="card-title">
                <el-icon><Warning /></el-icon>
                告警历史
              </h3>
              <div class="stats-summary">
                <el-tag type="danger" effect="dark">
                  未确认: {{ unacknowledgedCount }} 条
                </el-tag>
                <el-tag type="warning" effect="dark">
                  共 {{ alarmPagination.total }} 条记录
                </el-tag>
              </div>
            </div>
            <div class="table-container">
              <el-table
                :data="alarmData"
                v-loading="loading"
                stripe
                border
                style="width: 100%"
                :row-class-name="getAlarmRowClass"
              >
                <el-table-column prop="alarmTime" label="告警时间" width="180">
                  <template #default="{ row }">
                    <span class="time-text">{{ formatTime(row.alarmTime) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="alarmType" label="告警类型" width="150">
                  <template #default="{ row }">
                    <el-tag :type="getAlarmTypeClass(row.alarmType)" size="small" effect="dark">
                      {{ getAlarmTypeText(row.alarmType) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="alarmLevel" label="级别" width="100">
                  <template #default="{ row }">
                    <el-tag :type="getAlarmLevelClass(row.alarmLevel)" size="small" effect="dark">
                      {{ getAlarmLevelText(row.alarmLevel) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="message" label="告警消息" min-width="250">
                  <template #default="{ row }">
                    <span class="alarm-message">{{ row.message }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="thresholdValue" label="阈值" width="120">
                  <template #default="{ row }">
                    <span class="metric-value">{{ row.thresholdValue }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="actualValue" label="实际值" width="120">
                  <template #default="{ row }">
                    <span class="metric-value danger">{{ row.actualValue }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="acknowledged" label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag :type="row.acknowledged ? 'success' : 'danger'" size="small">
                      {{ row.acknowledged ? '已确认' : '未确认' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="120" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      v-if="!row.acknowledged"
                      type="success"
                      link
                      size="small"
                      @click="acknowledgeAlarm(row.id)"
                    >
                      确认
                    </el-button>
                    <el-button type="primary" link size="small" @click="viewAlarmDetail(row)">
                      详情
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <div class="pagination-container">
              <el-pagination
                v-model:current-page="alarmPagination.page"
                v-model:page-size="alarmPagination.size"
                :page-sizes="[10, 20, 50, 100]"
                :total="alarmPagination.total"
                layout="total, sizes, prev, pager, next, jumper"
                @size-change="loadAlarmData"
                @current-change="loadAlarmData"
              />
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog
      v-model="detailDialogVisible"
      :title="detailDialogTitle"
      width="700px"
      destroy-on-close
    >
      <div v-if="currentStabilityDetail" class="stability-detail">
        <div class="detail-grid">
          <div class="detail-item">
            <span class="detail-label">计算时间</span>
            <span class="detail-value">{{ formatTime(currentStabilityDetail.calculationTime) }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">GM值</span>
            <span class="detail-value" :class="getGmClass(currentStabilityDetail.gmValue)">
              {{ currentStabilityDetail.gmValue?.toFixed(4) }} m
            </span>
          </div>
          <div class="detail-item">
            <span class="detail-label">横摇周期</span>
            <span class="detail-value">{{ currentStabilityDetail.rollPeriod?.toFixed(3) }} s</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">复原力矩</span>
            <span class="detail-value">{{ currentStabilityDetail.rightingMoment?.toFixed(2) }} kN·m</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">复原力臂</span>
            <span class="detail-value">{{ currentStabilityDetail.rightingArm?.toFixed(4) }} m</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">排水量</span>
            <span class="detail-value">{{ currentStabilityDetail.displacementActual?.toFixed(1) }} t</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">重心高度 KG</span>
            <span class="detail-value">{{ currentStabilityDetail.cgZ?.toFixed(3) }} m</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">浮心高度 KB</span>
            <span class="detail-value">{{ currentStabilityDetail.cbZ?.toFixed(3) }} m</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">稳心高度 KM</span>
            <span class="detail-value">{{ currentStabilityDetail.kmValue?.toFixed(3) }} m</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">稳性状态</span>
            <el-tag :type="getStatusType(currentStabilityDetail.stabilityStatus)" effect="dark">
              {{ getStatusText(currentStabilityDetail.stabilityStatus) }}
            </el-tag>
          </div>
        </div>
        <div v-if="currentStabilityDetail.warningMessage" class="warning-box">
          <el-icon><Warning /></el-icon>
          <span>{{ currentStabilityDetail.warningMessage }}</span>
        </div>
        <div class="curve-preview">
          <h4 class="preview-title">静稳性曲线</h4>
          <canvas ref="detailChartRef" width="600" height="250"></canvas>
        </div>
      </div>

      <div v-if="currentAlarmDetail" class="alarm-detail">
        <div class="detail-grid">
          <div class="detail-item">
            <span class="detail-label">告警时间</span>
            <span class="detail-value">{{ formatTime(currentAlarmDetail.alarmTime) }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">告警类型</span>
            <el-tag :type="getAlarmTypeClass(currentAlarmDetail.alarmType)" effect="dark">
              {{ getAlarmTypeText(currentAlarmDetail.alarmType) }}
            </el-tag>
          </div>
          <div class="detail-item">
            <span class="detail-label">告警级别</span>
            <el-tag :type="getAlarmLevelClass(currentAlarmDetail.alarmLevel)" effect="dark">
              {{ getAlarmLevelText(currentAlarmDetail.alarmLevel) }}
            </el-tag>
          </div>
          <div class="detail-item">
            <span class="detail-label">状态</span>
            <el-tag :type="currentAlarmDetail.acknowledged ? 'success' : 'danger'" effect="dark">
              {{ currentAlarmDetail.acknowledged ? '已确认' : '未确认' }}
            </el-tag>
          </div>
          <div class="detail-item full-width">
            <span class="detail-label">告警消息</span>
            <span class="detail-value">{{ currentAlarmDetail.message }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">阈值</span>
            <span class="detail-value">{{ currentAlarmDetail.thresholdValue }}</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">实际值</span>
            <span class="detail-value danger">{{ currentAlarmDetail.actualValue }}</span>
          </div>
          <div class="detail-item" v-if="currentAlarmDetail.acknowledgedBy">
            <span class="detail-label">确认人</span>
            <span class="detail-value">{{ currentAlarmDetail.acknowledgedBy }}</span>
          </div>
          <div class="detail-item" v-if="currentAlarmDetail.acknowledgedTime">
            <span class="detail-label">确认时间</span>
            <span class="detail-value">{{ formatTime(currentAlarmDetail.acknowledgedTime) }}</span>
          </div>
          <div class="detail-item full-width" v-if="currentAlarmDetail.acknowledgeNote">
            <span class="detail-label">确认备注</span>
            <span class="detail-value">{{ currentAlarmDetail.acknowledgeNote }}</span>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import {
  Histogram, Search, Download, Monitor, TrendCharts, Warning
} from '@element-plus/icons-vue'
import { Chart, registerables } from 'chart.js'
import { getSensorDataHistory } from '@/api/sensor'
import { getStabilityHistory, getStabilityById } from '@/api/stability'
import { getAlarms, acknowledgeAlarm, getUnacknowledgedCount } from '@/api/alarm'
import { ElMessage } from 'element-plus'

Chart.register(...registerables)

const props = defineProps({
  shipId: {
    type: String,
    default: null
  }
})

const loading = ref(false)
const activeTab = ref('sensor')
const dataType = ref('sensor')
const dateRange = ref(null)
const unacknowledgedCount = ref(0)

const sensorData = ref([])
const stabilityData = ref([])
const alarmData = ref([])

const sensorPagination = ref({ page: 0, size: 20, total: 0 })
const stabilityPagination = ref({ page: 0, size: 20, total: 0 })
const alarmPagination = ref({ page: 0, size: 20, total: 0 })

const detailDialogVisible = ref(false)
const detailDialogTitle = ref('')
const currentStabilityDetail = ref(null)
const currentAlarmDetail = ref(null)
const detailChartRef = ref(null)
let detailChart = null

const bestGm = computed(() => {
  if (!stabilityData.value.length) return null
  return Math.max(...stabilityData.value.map(s => s.gmValue || 0))
})

const formatTime = (time) => {
  if (!time) return '--'
  return new Date(time).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

const formatCargoDistribution = (json) => {
  if (!json) return '-'
  try {
    const obj = typeof json === 'string' ? JSON.parse(json) : json
    return Object.entries(obj).map(([k, v]) => `${k}: ${v}`).join(', ')
  } catch {
    return '-'
  }
}

const getRollClass = (angle) => {
  if (Math.abs(angle || 0) > 15) return 'metric-value danger'
  if (Math.abs(angle || 0) > 10) return 'metric-value warning'
  return 'metric-value'
}

const getBilgeClass = (level) => {
  if ((level || 0) > 0.5) return 'metric-value danger'
  if ((level || 0) > 0.3) return 'metric-value warning'
  return 'metric-value'
}

const getGmClass = (gm) => {
  if (gm == null) return 'metric-value'
  if (gm < 0.3) return 'metric-value danger'
  if (gm < 0.5) return 'metric-value warning'
  return 'metric-value success'
}

const getStatusType = (status) => {
  const types = {
    'NORMAL': 'success',
    'CAUTION': 'warning',
    'WARNING': 'warning',
    'CRITICAL': 'danger'
  }
  return types[status] || 'info'
}

const getStatusText = (status) => {
  const texts = {
    'NORMAL': '正常',
    'CAUTION': '注意',
    'WARNING': '警告',
    'CRITICAL': '危险'
  }
  return texts[status] || '未知'
}

const getAlarmTypeClass = (type) => {
  const types = {
    'GM_TOO_LOW': 'danger',
    'ROLL_EXCEEDED': 'warning',
    'BILGE_WATER_HIGH': 'warning',
    'DRAFT_EXCEEDED': 'danger'
  }
  return types[type] || 'info'
}

const getAlarmTypeText = (type) => {
  const texts = {
    'GM_TOO_LOW': 'GM值过低',
    'ROLL_EXCEEDED': '横摇角超限',
    'BILGE_WATER_HIGH': '舱底水位过高',
    'DRAFT_EXCEEDED': '吃水超限'
  }
  return texts[type] || type
}

const getAlarmLevelClass = (level) => {
  const levels = {
    'LOW': 'info',
    'MEDIUM': 'warning',
    'HIGH': 'danger',
    'CRITICAL': 'danger'
  }
  return levels[level] || 'info'
}

const getAlarmLevelText = (level) => {
  const texts = {
    'LOW': '低',
    'MEDIUM': '中',
    'HIGH': '高',
    'CRITICAL': '严重'
  }
  return texts[level] || level
}

const getAlarmRowClass = ({ row }) => {
  if (!row.acknowledged) return 'unacknowledged-row'
  return ''
}

const onTabChange = (tab) => {
  dataType.value = tab
  loadData()
}

const loadData = () => {
  if (activeTab.value === 'sensor') {
    loadSensorData()
  } else if (activeTab.value === 'stability') {
    loadStabilityData()
  } else if (activeTab.value === 'alarm') {
    loadAlarmData()
  }
  loadUnacknowledgedCount()
}

const loadSensorData = async () => {
  if (!props.shipId) return
  loading.value = true
  try {
    const params = {
      page: sensorPagination.value.page - 1,
      size: sensorPagination.value.size
    }
    if (dateRange.value?.length === 2) {
      params.startTime = dateRange.value[0]
      params.endTime = dateRange.value[1]
    }
    const res = await getSensorDataHistory(props.shipId, params.startTime, params.endTime)
    const data = res.data || []
    const startIdx = (sensorPagination.value.page - 1) * sensorPagination.value.size
    sensorData.value = data.slice(startIdx, startIdx + sensorPagination.value.size)
    sensorPagination.value.total = data.length
  } catch (e) {
    ElMessage.error('加载传感器数据失败')
  } finally {
    loading.value = false
  }
}

const loadStabilityData = async () => {
  if (!props.shipId) return
  loading.value = true
  try {
    const res = await getStabilityHistory(
      props.shipId,
      stabilityPagination.value.page - 1,
      stabilityPagination.value.size
    )
    stabilityData.value = res.data?.content || []
    stabilityPagination.value.total = res.data?.totalElements || 0
  } catch (e) {
    ElMessage.error('加载稳性数据失败')
  } finally {
    loading.value = false
  }
}

const loadAlarmData = async () => {
  if (!props.shipId) return
  loading.value = true
  try {
    const res = await getAlarms(
      props.shipId,
      alarmPagination.value.page - 1,
      alarmPagination.value.size
    )
    alarmData.value = res.data?.content || []
    alarmPagination.value.total = res.data?.totalElements || 0
  } catch (e) {
    ElMessage.error('加载告警数据失败')
  } finally {
    loading.value = false
  }
}

const loadUnacknowledgedCount = async () => {
  if (!props.shipId) return
  try {
    const res = await getUnacknowledgedCount(props.shipId)
    unacknowledgedCount.value = res.data?.count || 0
  } catch (e) {
    console.error('加载未确认告警数失败', e)
  }
}

const viewStabilityDetail = async (row) => {
  try {
    const res = await getStabilityById(row.id)
    currentStabilityDetail.value = res.data
    detailDialogTitle.value = '稳性计算详情'
    detailDialogVisible.value = true

    await nextTick()
    if (detailChartRef.value && currentStabilityDetail.value?.curvePoints) {
      initDetailChart()
    }
  } catch (e) {
    ElMessage.error('加载详情失败')
  }
}

const viewAlarmDetail = (row) => {
  currentAlarmDetail.value = row
  detailDialogTitle.value = '告警详情'
  detailDialogVisible.value = true
}

const acknowledgeAlarm = async (alarmId) => {
  try {
    await acknowledgeAlarm(alarmId, { acknowledgeNote: '系统自动确认' })
    ElMessage.success('告警已确认')
    loadAlarmData()
    loadUnacknowledgedCount()
  } catch (e) {
    ElMessage.error('确认失败')
  }
}

const initDetailChart = () => {
  if (!detailChartRef.value || !currentStabilityDetail.value?.curvePoints) return

  if (detailChart) detailChart.destroy()

  const curvePoints = currentStabilityDetail.value.curvePoints
  const labels = curvePoints.map(p => p.angle)
  const gzData = curvePoints.map(p => p.gz)

  const ctx = detailChartRef.value.getContext('2d')
  detailChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label: '复原力臂 GZ (m)',
        data: gzData,
        borderColor: '#409EFF',
        backgroundColor: 'rgba(64, 158, 255, 0.2)',
        fill: true,
        tension: 0.3,
        pointRadius: 0,
        pointHoverRadius: 4
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false }
      },
      scales: {
        x: {
          title: { display: true, text: '横倾角 φ (°)', color: '#a0aec0' },
          ticks: { color: '#a0aec0', stepSize: 5 },
          grid: { color: 'rgba(255,255,255,0.05)' }
        },
        y: {
          title: { display: true, text: '复原力臂 GZ (m)', color: '#409EFF' },
          ticks: { color: '#409EFF' },
          grid: { color: 'rgba(255,255,255,0.05)' }
        }
      }
    }
  })
}

const exportData = () => {
  ElMessage.info('数据导出功能开发中...')
}

watch(() => props.shipId, () => {
  loadData()
}, { immediate: true })
</script>

<style scoped lang="scss">
.history-data {
  width: 100%;
  padding: 0 4px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 16px;

  .page-title {
    display: flex;
    align-items: center;
    gap: 10px;
    color: #ffffff;
    font-size: 22px;
    margin: 0;

    .el-icon {
      color: #409EFF;
    }
  }

  .header-filters {
    display: flex;
    gap: 12px;
    align-items: center;
    flex-wrap: wrap;
  }
}

.data-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 0;
    background: rgba(12, 25, 41, 0.6);
    border-radius: 12px 12px 0 0;
    padding: 0 20px;
    border: 1px solid rgba(64, 158, 255, 0.2);
    border-bottom: none;
  }

  :deep(.el-tabs__nav-wrap::after) {
    display: none;
  }

  :deep(.el-tabs__item) {
    color: #a0aec0;
    height: 50px;
    line-height: 50px;
    font-size: 15px;

    &.is-active {
      color: #409EFF;
    }
  }

  :deep(.el-tabs__active-bar) {
    background-color: #409EFF;
  }
}

.table-card {
  background: rgba(12, 25, 41, 0.6);
  border: 1px solid rgba(64, 158, 255, 0.2);
  border-top: none;
  border-radius: 0 0 12px 12px;
  overflow: hidden;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px 20px;
    border-bottom: 1px solid rgba(64, 158, 255, 0.15);

    .card-title {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0;
      font-size: 16px;
      color: #ffffff;

      .el-icon {
        color: #409EFF;
      }
    }

    .stats-summary {
      display: flex;
      gap: 12px;
    }
  }

  .table-container {
    padding: 0;
    max-height: 500px;
    overflow: auto;
  }

  :deep(.el-table) {
    background: transparent;

    th.el-table__cell {
      background: rgba(0, 0, 0, 0.3);
      color: #a0aec0;
      font-weight: 500;
      border-bottom: 1px solid rgba(64, 158, 255, 0.15);
    }

    td.el-table__cell {
      border-bottom: 1px solid rgba(64, 158, 255, 0.08);
      color: #ffffff;
    }

    tr:hover > td {
      background: rgba(64, 158, 255, 0.05) !important;
    }

    tr.el-table__row--striped td {
      background: rgba(0, 0, 0, 0.1);
    }

    .unacknowledged-row {
      background: rgba(245, 108, 108, 0.05);

      td {
        background: rgba(245, 108, 108, 0.05) !important;
      }
    }
  }

  .pagination-container {
    padding: 16px 20px;
    border-top: 1px solid rgba(64, 158, 255, 0.15);
    display: flex;
    justify-content: flex-end;

    :deep(.el-pagination) {
      --el-pagination-hover-color: #409EFF;

      .btn-prev, .btn-next, .el-pager li {
        background: rgba(0, 0, 0, 0.3);
        color: #a0aec0;

        &:hover {
          color: #409EFF;
        }

        &.is-active {
          background: #409EFF;
          color: #fff;
        }
      }

      .el-pagination__total, .el-pagination__jump {
        color: #a0aec0;
      }

      .el-select__wrapper {
        background: rgba(0, 0, 0, 0.3);
        color: #a0aec0;
      }
    }
  }
}

.time-text {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  color: #a0aec0;
}

.metric-value {
  font-family: 'Courier New', monospace;
  font-weight: 500;

  &.danger { color: #F56C6C; }
  &.warning { color: #E6A23C; }
  &.success { color: #67C23A; }
}

.json-text {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #a0aec0;
}

.alarm-message {
  color: #F56C6C;
  font-weight: 500;
}

.stability-detail,
.alarm-detail {
  .detail-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
    margin-bottom: 20px;

    .detail-item {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 12px;
      background: rgba(0, 0, 0, 0.2);
      border-radius: 8px;

      &.full-width {
        grid-column: span 2;
      }

      .detail-label {
        color: #a0aec0;
        font-size: 12px;
      }

      .detail-value {
        color: #ffffff;
        font-size: 14px;
        font-weight: 500;

        &.danger {
          color: #F56C6C;
        }
      }
    }
  }

  .warning-box {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px;
    background: rgba(245, 108, 108, 0.1);
    border: 1px solid rgba(245, 108, 108, 0.3);
    border-radius: 8px;
    color: #F56C6C;
    margin-bottom: 20px;
  }

  .curve-preview {
    .preview-title {
      color: #ffffff;
      font-size: 14px;
      margin: 0 0 12px 0;
    }

    canvas {
      width: 100% !important;
      height: 250px !important;
    }
  }
}

@media (max-width: 1200px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .stability-detail,
  .alarm-detail {
    .detail-grid {
      grid-template-columns: 1fr;

      .detail-item.full-width {
        grid-column: span 1;
      }
    }
  }
}
</style>
