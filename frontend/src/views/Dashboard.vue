<template>
  <div class="dashboard">
    <div v-if="loading" class="loading-container">
      <el-icon class="loading-icon"><Loading /></el-icon>
      <span>加载数据中...</span>
    </div>

    <div v-else class="dashboard-content">
      <div class="status-overview">
        <div class="overview-card">
          <div class="overview-icon draft">
            <el-icon><WaterLevel /></el-icon>
          </div>
          <div class="overview-info">
            <span class="overview-label">吃水深度</span>
            <span class="overview-value">{{ sensorData?.draftDepth?.toFixed(2) || '--' }} <small>m</small></span>
          </div>
        </div>

        <div class="overview-card">
          <div class="overview-icon roll">
            <el-icon><Switch /></el-icon>
          </div>
          <div class="overview-info">
            <span class="overview-label">横摇角</span>
            <span class="overview-value" :class="{ danger: Math.abs(sensorData?.rollAngle || 0) > 15 }">
              {{ sensorData?.rollAngle?.toFixed(1) || '--' }} <small>°</small>
            </span>
          </div>
        </div>

        <div class="overview-card">
          <div class="overview-icon pitch">
            <el-icon><Top /></el-icon>
          </div>
          <div class="overview-info">
            <span class="overview-label">纵摇角</span>
            <span class="overview-value">{{ sensorData?.pitchAngle?.toFixed(1) || '--' }} <small>°</small></span>
          </div>
        </div>

        <div class="overview-card">
          <div class="overview-icon bilge">
            <el-icon><Watermelon /></el-icon>
          </div>
          <div class="overview-info">
            <span class="overview-label">舱底水位</span>
            <span class="overview-value" :class="{ warning: (sensorData?.bilgeWaterLevel || 0) > 0.5 }">
              {{ sensorData?.bilgeWaterLevel?.toFixed(3) || '--' }} <small>m</small>
            </span>
          </div>
        </div>
      </div>

      <div class="main-grid">
        <div class="grid-row">
          <div class="grid-col col-1-3">
            <StabilityCard :stability-data="stabilityData" :min-gm="0.3" />
          </div>

          <div class="grid-col col-2-3">
            <div class="card cargo-card">
              <div class="card-header">
                <h3 class="card-title">
                  <el-icon><Box /></el-icon>
                  货物装载分布
                </h3>
                <el-tag type="primary" effect="dark">
                  总载重: {{ loadingSummary?.totalWeight?.toFixed(1) || 0 }} t
                </el-tag>
              </div>
              <div class="cargo-visualization">
                <div
                  v-for="hold in cargoHoldsWithCargo"
                  :key="hold.id"
                  class="hold-item"
                >
                  <div class="hold-header">
                    <span class="hold-name">{{ hold.holdName }}</span>
                    <span class="hold-weight">{{ hold.currentWeight?.toFixed(1) || 0 }} / {{ hold.maxWeight }} t</span>
                  </div>
                  <div class="hold-bar-container">
                    <div
                      class="hold-bar"
                      :style="{ width: getHoldFillPercent(hold) + '%' }"
                    >
                      <div
                        v-for="(cargo, idx) in hold.cargoList"
                        :key="idx"
                        class="cargo-segment"
                        :style="{
                          width: getCargoSegmentWidth(hold, cargo) + '%',
                          backgroundColor: cargo.color
                        }"
                        :title="`${cargo.cargoName}: ${cargo.weight}t`"
                      ></div>
                    </div>
                  </div>
                  <div class="hold-legend">
                    <span
                      v-for="(cargo, idx) in hold.cargoList"
                      :key="idx"
                      class="legend-item"
                    >
                      <span class="legend-dot" :style="{ backgroundColor: cargo.color }"></span>
                      {{ cargo.cargoName }}
                    </span>
                  </div>
                </div>
              </div>
              <div class="cargo-stats">
                <div class="stat-item">
                  <span class="stat-label">粮</span>
                  <span class="stat-value">{{ loadingSummary?.grainWeight?.toFixed(1) || 0 }} t</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">盐</span>
                  <span class="stat-value">{{ loadingSummary?.saltWeight?.toFixed(1) || 0 }} t</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">其他</span>
                  <span class="stat-value">{{ loadingSummary?.otherWeight?.toFixed(1) || 0 }} t</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">利用率</span>
                  <span class="stat-value">{{ loadingSummary?.utilizationPercent?.toFixed(1) || 0 }}%</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="grid-row">
          <div class="grid-col col-1-2">
            <div class="card chart-card">
              <div class="card-header">
                <h3 class="card-title">
                  <el-icon><TrendCharts /></el-icon>
                  吃水深度趋势
                </h3>
                <el-radio-group v-model="chartTimeRange" size="small" @change="refreshCharts">
                  <el-radio-button value="1h">1小时</el-radio-button>
                  <el-radio-button value="6h">6小时</el-radio-button>
                  <el-radio-button value="24h">24小时</el-radio-button>
                </el-radio-group>
              </div>
              <div class="chart-container">
                <canvas ref="draftChartRef"></canvas>
              </div>
            </div>
          </div>

          <div class="grid-col col-1-2">
            <div class="card chart-card">
              <div class="card-header">
                <h3 class="card-title">
                  <el-icon><TrendCharts /></el-icon>
                  横摇角趋势
                </h3>
                <div class="threshold-line">
                  <span class="threshold-label">阈值: ±15°</span>
                </div>
              </div>
              <div class="chart-container">
                <canvas ref="rollChartRef"></canvas>
              </div>
            </div>
          </div>
        </div>

        <div class="grid-row">
          <div class="grid-col col-full">
            <div class="card info-card">
              <div class="card-header">
                <h3 class="card-title">
                  <el-icon><InfoFilled /></el-icon>
                  船舶信息
                </h3>
                <el-tag type="info" effect="plain">
                  最后更新: {{ formatTime(sensorData?.sensorTime) }}
                </el-tag>
              </div>
              <div class="ship-info-grid">
                <div class="info-item">
                  <span class="info-label">船舶总长</span>
                  <span class="info-value">{{ shipInfo?.lengthOverall || '--' }} m</span>
                </div>
                <div class="info-item">
                  <span class="info-label">型宽</span>
                  <span class="info-value">{{ shipInfo?.breadthMolded || '--' }} m</span>
                </div>
                <div class="info-item">
                  <span class="info-label">型深</span>
                  <span class="info-value">{{ shipInfo?.depthMolded || '--' }} m</span>
                </div>
                <div class="info-item">
                  <span class="info-label">设计吃水</span>
                  <span class="info-value">{{ shipInfo?.designDraft || '--' }} m</span>
                </div>
                <div class="info-item">
                  <span class="info-label">设计排水量</span>
                  <span class="info-value">{{ shipInfo?.designDisplacement || '--' }} t</span>
                </div>
                <div class="info-item">
                  <span class="info-label">设计GM</span>
                  <span class="info-value">{{ shipInfo?.designGm || '--' }} m</span>
                </div>
                <div class="info-item">
                  <span class="info-label">空载重量</span>
                  <span class="info-value">{{ shipInfo?.lightshipWeight || '--' }} t</span>
                </div>
                <div class="info-item">
                  <span class="info-label">载重量</span>
                  <span class="info-value">{{ shipInfo?.deadweight || '--' }} t</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { Loading, WaterLevel, Switch, Top, Watermelon, Box, TrendCharts, InfoFilled } from '@element-plus/icons-vue'
import { Chart, registerables } from 'chart.js'
import StabilityCard from '@/components/StabilityCard.vue'
import { useShipData } from '@/composables/useShipData'
import { getShipById } from '@/api/ship'

Chart.register(...registerables)

const props = defineProps({
  shipId: {
    type: String,
    default: null
  }
})

const shipIdRef = computed(() => props.shipId)

const { sensorData, stabilityData, loadingSummary, cargoLoadings, cargoHolds, loading, fetchSensorHistory } = useShipData(shipIdRef)

const shipInfo = ref(null)
const chartTimeRange = ref('1h')
const draftChartRef = ref(null)
const rollChartRef = ref(null)
let draftChart = null
let rollChart = null

const cargoHoldsWithCargo = computed(() => {
  return cargoHolds.value.map(hold => {
    const cargosInHold = cargoLoadings.value.filter(cl => cl.cargoHoldId === hold.id)
    const cargoList = cargosInHold.map(cl => ({
      cargoName: cl.cargoTypeName,
      weight: cl.weight,
      color: cl.cargoColor || '#409EFF'
    }))
    const currentWeight = cargosInHold.reduce((sum, cl) => sum + (cl.weight || 0), 0)
    return {
      ...hold,
      cargoList,
      currentWeight
    }
  })
})

const getHoldFillPercent = (hold) => {
  if (!hold.maxWeight || hold.maxWeight <= 0) return 0
  return Math.min((hold.currentWeight / hold.maxWeight) * 100, 100)
}

const getCargoSegmentWidth = (hold, cargo) => {
  if (!hold.currentWeight || hold.currentWeight <= 0) return 0
  return (cargo.weight / hold.currentWeight) * 100
}

const formatTime = (time) => {
  if (!time) return '--'
  return new Date(time).toLocaleString('zh-CN')
}

const fetchShipInfo = async () => {
  if (!props.shipId) return
  try {
    const res = await getShipById(props.shipId)
    shipInfo.value = res.data
  } catch (e) {
    console.error('获取船舶信息失败', e)
  }
}

const initCharts = () => {
  if (draftChartRef.value && !draftChart) {
    const ctx = draftChartRef.value.getContext('2d')
    draftChart = new Chart(ctx, {
      type: 'line',
      data: {
        labels: [],
        datasets: [{
          label: '吃水深度 (m)',
          data: [],
          borderColor: '#409EFF',
          backgroundColor: 'rgba(64, 158, 255, 0.1)',
          fill: true,
          tension: 0.4,
          pointRadius: 2,
          pointHoverRadius: 5
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
            ticks: { color: '#a0aec0', maxTicksLimit: 8 },
            grid: { color: 'rgba(255,255,255,0.05)' }
          },
          y: {
            ticks: { color: '#a0aec0' },
            grid: { color: 'rgba(255,255,255,0.05)' }
          }
        }
      }
    })
  }

  if (rollChartRef.value && !rollChart) {
    const ctx = rollChartRef.value.getContext('2d')
    rollChart = new Chart(ctx, {
      type: 'line',
      data: {
        labels: [],
        datasets: [{
          label: '横摇角 (°)',
          data: [],
          borderColor: '#E6A23C',
          backgroundColor: 'rgba(230, 162, 60, 0.1)',
          fill: true,
          tension: 0.4,
          pointRadius: 2,
          pointHoverRadius: 5
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
            ticks: { color: '#a0aec0', maxTicksLimit: 8 },
            grid: { color: 'rgba(255,255,255,0.05)' }
          },
          y: {
            ticks: { color: '#a0aec0' },
            grid: { color: 'rgba(255,255,255,0.05)' }
          }
        }
      }
    })
  }
}

const refreshCharts = async () => {
  const limitMap = { '1h': 60, '6h': 360, '24h': 1440 }
  const limit = limitMap[chartTimeRange.value] || 60
  const history = await fetchSensorHistory(limit)

  if (history.length > 0 && draftChart && rollChart) {
    const labels = history.map(d => new Date(d.sensorTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }))
    const draftData = history.map(d => d.draftDepth)
    const rollData = history.map(d => d.rollAngle)

    draftChart.data.labels = labels
    draftChart.data.datasets[0].data = draftData
    draftChart.update()

    rollChart.data.labels = labels
    rollChart.data.datasets[0].data = rollData
    rollChart.update()
  }
}

watch(() => props.shipId, () => {
  fetchShipInfo()
}, { immediate: true })

let chartInterval = null

onMounted(() => {
  setTimeout(() => {
    initCharts()
    refreshCharts()
  }, 100)

  chartInterval = setInterval(refreshCharts, 60000)
})

onUnmounted(() => {
  if (chartInterval) clearInterval(chartInterval)
  if (draftChart) draftChart.destroy()
  if (rollChart) rollChart.destroy()
})
</script>

<style scoped lang="scss">
.dashboard {
  width: 100%;
  height: 100%;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  color: #a0aec0;
  gap: 12px;

  .loading-icon {
    font-size: 48px;
    color: #409EFF;
    animation: rotate 1s linear infinite;
  }
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.status-overview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;

  .overview-card {
    background: linear-gradient(135deg, rgba(64, 158, 255, 0.1) 0%, rgba(64, 158, 255, 0.05) 100%);
    border: 1px solid rgba(64, 158, 255, 0.2);
    border-radius: 10px;
    padding: 20px;
    display: flex;
    align-items: center;
    gap: 16px;
    transition: all 0.3s;

    &:hover {
      transform: translateY(-2px);
      border-color: rgba(64, 158, 255, 0.4);
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
    }

    .overview-icon {
      width: 56px;
      height: 56px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 28px;

      &.draft {
        background: linear-gradient(135deg, rgba(64, 158, 255, 0.3) 0%, rgba(64, 158, 255, 0.1) 100%);
        color: #409EFF;
      }

      &.roll {
        background: linear-gradient(135deg, rgba(230, 162, 60, 0.3) 0%, rgba(230, 162, 60, 0.1) 100%);
        color: #E6A23C;
      }

      &.pitch {
        background: linear-gradient(135deg, rgba(103, 194, 58, 0.3) 0%, rgba(103, 194, 58, 0.1) 100%);
        color: #67C23A;
      }

      &.bilge {
        background: linear-gradient(135deg, rgba(245, 108, 108, 0.3) 0%, rgba(245, 108, 108, 0.1) 100%);
        color: #F56C6C;
      }
    }

    .overview-info {
      flex: 1;

      .overview-label {
        display: block;
        color: #a0aec0;
        font-size: 13px;
        margin-bottom: 4px;
      }

      .overview-value {
        display: block;
        color: #ffffff;
        font-size: 24px;
        font-weight: bold;
        font-family: 'Courier New', monospace;

        small {
          font-size: 12px;
          color: #a0aec0;
          font-weight: normal;
        }

        &.danger {
          color: #F56C6C;
        }

        &.warning {
          color: #E6A23C;
        }
      }
    }
  }
}

.main-grid {
  .grid-row {
    display: grid;
    gap: 16px;
    margin-bottom: 16px;

    &:last-child {
      margin-bottom: 0;
    }
  }

  .grid-col {
    &.col-1-3 { grid-column: span 1; }
    &.col-2-3 { grid-column: span 2; }
    &.col-1-2 { grid-column: span 1; }
    &.col-full { grid-column: span 1; }
  }

  .grid-row:first-child {
    grid-template-columns: 1fr 2fr;
  }

  .grid-row:nth-child(2) {
    grid-template-columns: 1fr 1fr;
  }
}

.card {
  background: rgba(12, 25, 41, 0.6);
  border: 1px solid rgba(64, 158, 255, 0.2);
  border-radius: 10px;
  backdrop-filter: blur(10px);
}

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

  .threshold-line {
    .threshold-label {
      font-size: 12px;
      color: #F56C6C;
    }
  }
}

.cargo-card {
  .cargo-visualization {
    padding: 20px;

    .hold-item {
      margin-bottom: 20px;

      &:last-child {
        margin-bottom: 0;
      }

      .hold-header {
        display: flex;
        justify-content: space-between;
        margin-bottom: 8px;

        .hold-name {
          color: #ffffff;
          font-weight: 500;
          font-size: 14px;
        }

        .hold-weight {
          color: #a0aec0;
          font-size: 13px;
          font-family: 'Courier New', monospace;
        }
      }

      .hold-bar-container {
        height: 24px;
        background: rgba(0, 0, 0, 0.3);
        border-radius: 4px;
        overflow: hidden;
        margin-bottom: 8px;

        .hold-bar {
          height: 100%;
          display: flex;
          transition: width 0.5s ease;

          .cargo-segment {
            height: 100%;
            transition: all 0.3s;

            &:hover {
              filter: brightness(1.2);
            }
          }
        }
      }

      .hold-legend {
        display: flex;
        gap: 16px;
        flex-wrap: wrap;

        .legend-item {
          display: flex;
          align-items: center;
          gap: 4px;
          font-size: 12px;
          color: #a0aec0;

          .legend-dot {
            width: 10px;
            height: 10px;
            border-radius: 2px;
          }
        }
      }
    }
  }

  .cargo-stats {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    padding: 16px 20px;
    border-top: 1px solid rgba(64, 158, 255, 0.15);
    background: rgba(0, 0, 0, 0.2);

    .stat-item {
      text-align: center;

      .stat-label {
        display: block;
        color: #a0aec0;
        font-size: 12px;
        margin-bottom: 4px;
      }

      .stat-value {
        display: block;
        color: #409EFF;
        font-size: 18px;
        font-weight: bold;
        font-family: 'Courier New', monospace;
      }
    }
  }
}

.chart-card {
  .chart-container {
    height: 280px;
    padding: 16px 20px;
  }
}

.info-card {
  .ship-info-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 1px;
    background: rgba(64, 158, 255, 0.1);

    .info-item {
      background: rgba(12, 25, 41, 0.6);
      padding: 16px 20px;
      text-align: center;

      .info-label {
        display: block;
        color: #a0aec0;
        font-size: 13px;
        margin-bottom: 6px;
      }

      .info-value {
        display: block;
        color: #ffffff;
        font-size: 16px;
        font-weight: 500;
        font-family: 'Courier New', monospace;
      }
    }
  }
}

@media (max-width: 1200px) {
  .status-overview {
    grid-template-columns: repeat(2, 1fr);
  }

  .main-grid {
    .grid-row:first-child,
    .grid-row:nth-child(2) {
      grid-template-columns: 1fr;
    }

    .grid-col {
      &.col-1-3, &.col-2-3, &.col-1-2 { grid-column: span 1; }
    }
  }

  .info-card .ship-info-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
