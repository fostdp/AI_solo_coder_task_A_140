<template>
  <div class="stability-analysis">
    <div class="analysis-header">
      <h2 class="page-title">
        <el-icon><TrendCharts /></el-icon>
        稳性分析
      </h2>
      <div class="header-actions">
        <el-button type="primary" @click="recalculateStability" :loading="calculating">
          <el-icon><Refresh /></el-icon>
          重新计算
        </el-button>
        <el-button type="success" @click="exportReport">
          <el-icon><Download /></el-icon>
          导出报告
        </el-button>
      </div>
    </div>

    <div class="analysis-content">
      <div class="metrics-row">
        <div class="metric-card critical">
          <div class="metric-icon">
            <el-icon><Warning /></el-icon>
          </div>
          <div class="metric-info">
            <span class="metric-label">GM值 (初稳性高)</span>
            <span class="metric-value" :class="getGmClass()">
              {{ stabilityData?.gmValue?.toFixed(4) || '--' }}
              <small>m</small>
            </span>
            <span class="metric-status">
              {{ getGmStatus() }}
            </span>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon period">
            <el-icon><Timer /></el-icon>
          </div>
          <div class="metric-info">
            <span class="metric-label">横摇周期</span>
            <span class="metric-value">
              {{ stabilityData?.rollPeriod?.toFixed(3) || '--' }}
              <small>s</small>
            </span>
            <span class="metric-desc">T = 2πk/√(g·GM)</span>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon moment">
            <el-icon><Rotate /></el-icon>
          </div>
          <div class="metric-info">
            <span class="metric-label">最大复原力矩</span>
            <span class="metric-value">
              {{ stabilityData?.maxRightingMoment?.toFixed(1) || '--' }}
              <small>kN·m</small>
            </span>
            <span class="metric-desc">@ {{ stabilityData?.maxMomentAngle || '--' }}°</span>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon range">
            <el-icon><Compass /></el-icon>
          </div>
          <div class="metric-info">
            <span class="metric-label">稳性范围</span>
            <span class="metric-value">
              0 ~ {{ stabilityData?.stabilityRange || '--' }}
              <small>°</small>
            </span>
            <span class="metric-desc">正复原力臂角度范围</span>
          </div>
        </div>
      </div>

      <div class="charts-row">
        <div class="chart-card large">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><DataLine /></el-icon>
              静稳性曲线 (GZ曲线)
            </h3>
            <div class="legend-info">
              <span class="legend-item">
                <span class="legend-color gz"></span>
                复原力臂 (m)
              </span>
              <span class="legend-item">
                <span class="legend-color moment"></span>
                复原力矩 (kN·m)
              </span>
            </div>
          </div>
          <div class="chart-container">
            <canvas ref="stabilityCurveRef"></canvas>
          </div>
          <div class="curve-annotations">
            <div class="annotation point-a">
              <span class="label">A</span>
              <span class="desc">最大复原力臂点</span>
            </div>
            <div class="annotation point-b">
              <span class="label">B</span>
              <span class="desc">稳性消失角</span>
            </div>
            <div class="annotation threshold">
              <span class="label">GM ≥ 0.3m</span>
              <span class="desc">安全阈值</span>
            </div>
          </div>
        </div>

        <div class="chart-card small">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><PieChart /></el-icon>
              重心与浮心位置
            </h3>
          </div>
          <div class="diagram-container">
            <svg viewBox="0 0 300 400" class="ship-diagram">
              <defs>
                <linearGradient id="waterGrad" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" style="stop-color:#0077be;stop-opacity:0.8" />
                  <stop offset="100%" style="stop-color:#001a33;stop-opacity:0.9" />
                </linearGradient>
                <linearGradient id="shipGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                  <stop offset="0%" style="stop-color:#8B4513" />
                  <stop offset="100%" style="stop-color:#654321" />
                </linearGradient>
              </defs>

              <rect x="0" y="200" width="300" height="200" fill="url(#waterGrad)" />
              
              <path d="M 50 200 Q 70 260 150 280 Q 230 260 250 200 L 250 180 L 50 180 Z" 
                    fill="url(#shipGrad)" stroke="#3d2817" stroke-width="2" />
              <rect x="70" y="160" width="160" height="20" fill="#654321" stroke="#3d2817" stroke-width="1" />
              <rect x="110" y="130" width="80" height="30" fill="#4a3728" stroke="#3d2817" stroke-width="1" />

              <line x1="150" y1="50" x2="150" y2="350" stroke="rgba(255,255,255,0.2)" stroke-width="1" stroke-dasharray="5,5" />

              <circle cx="150" :cy="kgY" r="6" fill="#F56C6C" />
              <line x1="150" :y1="kgY" x2="180" :y2="kgY" stroke="#F56C6C" stroke-width="2" />
              <text x="185" :y="kgY + 4" fill="#F56C6C" font-size="12">G (重心)</text>

              <circle cx="150" :cy="kbY" r="6" fill="#67C23A" />
              <line x1="150" :y1="kbY" x2="120" :y2="kbY" stroke="#67C23A" stroke-width="2" />
              <text x="60" :y="kbY + 4" fill="#67C23A" font-size="12">B (浮心)</text>

              <circle cx="150" :cy="kmY" r="6" fill="#409EFF" />
              <line x1="150" :y1="kmY" x2="180" :y2="kmY" stroke="#409EFF" stroke-width="2" />
              <text x="185" :y="kmY + 4" fill="#409EFF" font-size="12">M (稳心)</text>

              <line x1="145" :y1="kgY" x2="145" :y2="kmY" stroke="#E6A23C" stroke-width="3" />
              <text x="130" :y="(kgY + kmY) / 2 + 4" fill="#E6A23C" font-size="12" font-weight="bold">GM</text>

              <line x1="30" y1="200" x2="30" y2="280" stroke="#ffffff" stroke-width="2" />
              <text x="10" y="245" fill="#ffffff" font-size="11" transform="rotate(-90 20 240)">吃水 d</text>

              <text x="10" y="205" fill="#a0aec0" font-size="11">WL</text>
            </svg>
          </div>
          <div class="position-stats">
            <div class="stat-row">
              <span class="stat-label">重心高度 KG</span>
              <span class="stat-value">{{ stabilityData?.cgZ?.toFixed(3) || '--' }} m</span>
            </div>
            <div class="stat-row">
              <span class="stat-label">浮心高度 KB</span>
              <span class="stat-value">{{ stabilityData?.cbZ?.toFixed(3) || '--' }} m</span>
            </div>
            <div class="stat-row">
              <span class="stat-label">稳心高度 KM</span>
              <span class="stat-value">{{ stabilityData?.kmValue?.toFixed(3) || '--' }} m</span>
            </div>
            <div class="stat-row highlight">
              <span class="stat-label">初稳性高 GM = KM - KG</span>
              <span class="stat-value" :class="getGmClass()">{{ stabilityData?.gmValue?.toFixed(3) || '--' }} m</span>
            </div>
          </div>
        </div>
      </div>

      <div class="history-section">
        <div class="card-header">
          <h3 class="card-title">
            <el-icon><Histogram /></el-icon>
            稳性历史趋势
          </h3>
          <el-radio-group v-model="historyRange" size="small" @change="loadHistoryData">
            <el-radio-button value="24h">24小时</el-radio-button>
            <el-radio-button value="7d">7天</el-radio-button>
            <el-radio-button value="30d">30天</el-radio-button>
          </el-radio-group>
        </div>
        <div class="history-chart-container">
          <canvas ref="historyChartRef"></canvas>
        </div>
      </div>

      <div class="formula-section">
        <div class="card-header">
          <h3 class="card-title">
            <el-icon><Document /></el-icon>
            稳性计算理论
          </h3>
        </div>
        <div class="formula-grid">
          <div class="formula-card">
            <h4>初稳性高 (GM)</h4>
            <div class="formula">GM = KM - KG</div>
            <p class="formula-desc">
              KM为横稳心距基线高度，KG为重心距基线高度。
              GM > 0表示船舶具有正稳性，GM越大，船舶回复能力越强，但横摇越剧烈。
            </p>
          </div>
          <div class="formula-card">
            <h4>复原力臂 (GZ)</h4>
            <div class="formula">GZ = GM · sin(φ)</div>
            <p class="formula-desc">
              φ为横倾角。复原力臂是重心G到浮力作用线的垂直距离，
              表示船舶在倾斜后产生回复力矩的能力。
            </p>
          </div>
          <div class="formula-card">
            <h4>复原力矩 (MR)</h4>
            <div class="formula">MR = Δ · g · GZ</div>
            <p class="formula-desc">
              Δ为排水量(t)，g为重力加速度(9.81m/s²)。
              复原力矩使船舶回复到平衡位置，是衡量稳性的重要指标。
            </p>
          </div>
          <div class="formula-card">
            <h4>横摇周期 (T)</h4>
            <div class="formula">T = 2πk / √(g · GM)</div>
            <p class="formula-desc">
              k为横摇惯性半径(约为0.35·B，B为船宽)。
              横摇周期与GM的平方根成反比，GM越大，横摇周期越短。
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { 
  TrendCharts, Refresh, Download, Warning, Timer, Rotate, 
  Compass, DataLine, PieChart, Histogram, Document 
} from '@element-plus/icons-vue'
import { Chart, registerables } from 'chart.js'
import { useShipData } from '@/composables/useShipData'
import { calculateStability, getStabilityHistory } from '@/api/stability'
import { ElMessage } from 'element-plus'

Chart.register(...registerables)

const props = defineProps({
  shipId: {
    type: String,
    default: null
  }
})

const shipIdRef = computed(() => props.shipId)
const { stabilityData, loading } = useShipData(shipIdRef)

const calculating = ref(false)
const historyRange = ref('24h')
const stabilityCurveRef = ref(null)
const historyChartRef = ref(null)
let stabilityChart = null
let historyChart = null

const kgY = computed(() => {
  const kg = stabilityData.value?.cgZ || 0
  return 180 - kg * 20
})

const kbY = computed(() => {
  const kb = stabilityData.value?.cbZ || 0
  return 200 + (2.5 - kb) * 20
})

const kmY = computed(() => {
  const km = stabilityData.value?.kmValue || 0
  return 180 - km * 20
})

const getGmClass = () => {
  const gm = stabilityData.value?.gmValue
  if (gm == null) return ''
  if (gm < 0.3) return 'danger'
  if (gm < 0.5) return 'warning'
  return 'success'
}

const getGmStatus = () => {
  const gm = stabilityData.value?.gmValue
  if (gm == null) return '数据不足'
  if (gm < 0.3) return '不安全'
  if (gm < 0.5) return '需注意'
  if (gm < 1.0) return '良好'
  return '优秀'
}

const recalculateStability = async () => {
  if (!props.shipId) return
  calculating.value = true
  try {
    await calculateStability(props.shipId)
    ElMessage.success('稳性计算完成')
    initStabilityChart()
  } catch (e) {
    ElMessage.error('计算失败: ' + (e.message || '未知错误'))
  } finally {
    calculating.value = false
  }
}

const exportReport = () => {
  ElMessage.info('报告导出功能开发中...')
}

const initStabilityChart = () => {
  if (!stabilityCurveRef.value) return

  if (stabilityChart) {
    stabilityChart.destroy()
  }

  const curvePoints = stabilityData.value?.curvePoints || []
  const labels = curvePoints.map(p => p.angle)
  const gzData = curvePoints.map(p => p.gz)
  const momentData = curvePoints.map(p => p.moment)

  const ctx = stabilityCurveRef.value.getContext('2d')
  stabilityChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels,
      datasets: [
        {
          label: '复原力臂 (m)',
          data: gzData,
          borderColor: '#409EFF',
          backgroundColor: 'rgba(64, 158, 255, 0.2)',
          fill: true,
          tension: 0.3,
          pointRadius: 0,
          pointHoverRadius: 6,
          yAxisID: 'y'
        },
        {
          label: '复原力矩 (kN·m)',
          data: momentData,
          borderColor: '#E6A23C',
          backgroundColor: 'rgba(230, 162, 60, 0.1)',
          fill: false,
          tension: 0.3,
          pointRadius: 0,
          pointHoverRadius: 6,
          yAxisID: 'y1'
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        mode: 'index',
        intersect: false
      },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: 'rgba(12, 25, 41, 0.95)',
          titleColor: '#ffffff',
          bodyColor: '#a0aec0',
          borderColor: 'rgba(64, 158, 255, 0.3)',
          borderWidth: 1,
          padding: 12,
          callbacks: {
            title: (items) => `横倾角: ${items[0].label}°`,
            label: (item) => {
              if (item.datasetIndex === 0) {
                return `复原力臂: ${item.raw?.toFixed(4)} m`
              } else {
                return `复原力矩: ${item.raw?.toFixed(1)} kN·m`
              }
            }
          }
        }
      },
      scales: {
        x: {
          title: {
            display: true,
            text: '横倾角 φ (°)',
            color: '#a0aec0'
          },
          ticks: { color: '#a0aec0', stepSize: 5 },
          grid: { color: 'rgba(255,255,255,0.05)' }
        },
        y: {
          type: 'linear',
          display: true,
          position: 'left',
          title: {
            display: true,
            text: '复原力臂 GZ (m)',
            color: '#409EFF'
          },
          ticks: { color: '#409EFF' },
          grid: { color: 'rgba(255,255,255,0.05)' }
        },
        y1: {
          type: 'linear',
          display: true,
          position: 'right',
          title: {
            display: true,
            text: '复原力矩 MR (kN·m)',
            color: '#E6A23C'
          },
          ticks: { color: '#E6A23C' },
          grid: { drawOnChartArea: false }
        }
      }
    }
  })
}

const loadHistoryData = async () => {
  if (!props.shipId) return

  const pageSize = historyRange.value === '24h' ? 144 : historyRange.value === '7d' ? 168 : 720
  try {
    const res = await getStabilityHistory(props.shipId, 0, pageSize)
    const data = res.data?.content || []
    initHistoryChart(data.reverse())
  } catch (e) {
    console.error('加载历史数据失败', e)
  }
}

const initHistoryChart = (data) => {
  if (!historyChartRef.value) return

  if (historyChart) {
    historyChart.destroy()
  }

  const labels = data.map(d => {
    const date = new Date(d.calculationTime)
    return historyRange.value === '24h' 
      ? date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
      : date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
  })
  const gmData = data.map(d => d.gmValue)
  const rollPeriodData = data.map(d => d.rollPeriod)

  const ctx = historyChartRef.value.getContext('2d')
  historyChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels,
      datasets: [
        {
          label: 'GM值 (m)',
          data: gmData,
          borderColor: '#409EFF',
          backgroundColor: 'rgba(64, 158, 255, 0.15)',
          fill: true,
          tension: 0.4,
          pointRadius: 2,
          pointHoverRadius: 6,
          yAxisID: 'y'
        },
        {
          label: '横摇周期 (s)',
          data: rollPeriodData,
          borderColor: '#67C23A',
          backgroundColor: 'rgba(103, 194, 58, 0.1)',
          fill: false,
          tension: 0.4,
          pointRadius: 2,
          pointHoverRadius: 6,
          yAxisID: 'y1'
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        mode: 'index',
        intersect: false
      },
      plugins: {
        legend: {
          display: true,
          position: 'top',
          labels: { color: '#a0aec0', usePointStyle: true }
        }
      },
      scales: {
        x: {
          ticks: { color: '#a0aec0', maxTicksLimit: 12 },
          grid: { color: 'rgba(255,255,255,0.05)' }
        },
        y: {
          type: 'linear',
          display: true,
          position: 'left',
          title: {
            display: true,
            text: 'GM值 (m)',
            color: '#409EFF'
          },
          ticks: { color: '#409EFF' },
          grid: { color: 'rgba(255,255,255,0.05)' }
        },
        y1: {
          type: 'linear',
          display: true,
          position: 'right',
          title: {
            display: true,
            text: '横摇周期 (s)',
            color: '#67C23A'
          },
          ticks: { color: '#67C23A' },
          grid: { drawOnChartArea: false }
        }
      }
    }
  })
}

watch(stabilityData, () => {
  initStabilityChart()
}, { deep: true })

watch(() => props.shipId, () => {
  setTimeout(() => {
    initStabilityChart()
    loadHistoryData()
  }, 200)
})

onMounted(() => {
  setTimeout(() => {
    initStabilityChart()
    loadHistoryData()
  }, 300)
})

onUnmounted(() => {
  if (stabilityChart) stabilityChart.destroy()
  if (historyChart) historyChart.destroy()
})
</script>

<style scoped lang="scss">
.stability-analysis {
  width: 100%;
  padding: 0 4px;
}

.analysis-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

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

  .header-actions {
    display: flex;
    gap: 12px;
  }
}

.metrics-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;

  .metric-card {
    background: rgba(12, 25, 41, 0.6);
    border: 1px solid rgba(64, 158, 255, 0.2);
    border-radius: 12px;
    padding: 20px;
    display: flex;
    gap: 16px;
    backdrop-filter: blur(10px);
    transition: all 0.3s;

    &:hover {
      transform: translateY(-3px);
      border-color: rgba(64, 158, 255, 0.4);
    }

    &.critical {
      border-left: 4px solid #409EFF;
    }

    .metric-icon {
      width: 56px;
      height: 56px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      flex-shrink: 0;

      &.critical {
        background: linear-gradient(135deg, rgba(64, 158, 255, 0.3), rgba(64, 158, 255, 0.1));
        color: #409EFF;
      }

      &.period {
        background: linear-gradient(135deg, rgba(103, 194, 58, 0.3), rgba(103, 194, 58, 0.1));
        color: #67C23A;
      }

      &.moment {
        background: linear-gradient(135deg, rgba(230, 162, 60, 0.3), rgba(230, 162, 60, 0.1));
        color: #E6A23C;
      }

      &.range {
        background: linear-gradient(135deg, rgba(156, 39, 176, 0.3), rgba(156, 39, 176, 0.1));
        color: #9C27B0;
      }
    }

    .metric-info {
      flex: 1;
      min-width: 0;

      .metric-label {
        display: block;
        color: #a0aec0;
        font-size: 13px;
        margin-bottom: 6px;
      }

      .metric-value {
        display: block;
        color: #ffffff;
        font-size: 26px;
        font-weight: bold;
        font-family: 'Courier New', monospace;
        line-height: 1.2;

        small {
          font-size: 13px;
          color: #a0aec0;
          font-weight: normal;
        }

        &.danger { color: #F56C6C; }
        &.warning { color: #E6A23C; }
        &.success { color: #67C23A; }
      }

      .metric-status,
      .metric-desc {
        display: block;
        color: #a0aec0;
        font-size: 12px;
        margin-top: 4px;
      }

      .metric-status {
        font-weight: 500;
        color: #67C23A;
      }
    }
  }
}

.charts-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
  margin-bottom: 20px;

  .chart-card {
    background: rgba(12, 25, 41, 0.6);
    border: 1px solid rgba(64, 158, 255, 0.2);
    border-radius: 12px;
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

      .legend-info {
        display: flex;
        gap: 20px;

        .legend-item {
          display: flex;
          align-items: center;
          gap: 6px;
          font-size: 12px;
          color: #a0aec0;

          .legend-color {
            width: 12px;
            height: 3px;
            border-radius: 2px;

            &.gz { background: #409EFF; }
            &.moment { background: #E6A23C; }
          }
        }
      }
    }

    .chart-container {
      height: 350px;
      padding: 16px 20px;
      position: relative;
    }

    .curve-annotations {
      position: absolute;
      top: 60px;
      right: 30px;
      display: flex;
      flex-direction: column;
      gap: 12px;

      .annotation {
        display: flex;
        align-items: center;
        gap: 8px;
        background: rgba(12, 25, 41, 0.8);
        padding: 6px 12px;
        border-radius: 6px;
        font-size: 11px;

        .label {
          width: 20px;
          height: 20px;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          font-weight: bold;
          font-size: 10px;
        }

        &.point-a .label {
          background: #409EFF;
          color: #fff;
        }

        &.point-b .label {
          background: #F56C6C;
          color: #fff;
        }

        &.threshold .label {
          background: #67C23A;
          color: #fff;
          width: auto;
          padding: 0 8px;
          border-radius: 10px;
        }

        .desc {
          color: #a0aec0;
        }
      }
    }

    .diagram-container {
      padding: 20px;
      display: flex;
      justify-content: center;

      .ship-diagram {
        width: 100%;
        max-width: 300px;
        height: auto;
      }
    }

    .position-stats {
      padding: 0 20px 20px;

      .stat-row {
        display: flex;
        justify-content: space-between;
        padding: 10px 12px;
        background: rgba(0, 0, 0, 0.2);
        border-radius: 6px;
        margin-bottom: 8px;

        &:last-child {
          margin-bottom: 0;
        }

        &.highlight {
          background: rgba(64, 158, 255, 0.15);
          border: 1px solid rgba(64, 158, 255, 0.3);

          .stat-label {
            color: #409EFF;
            font-weight: 500;
          }
        }

        .stat-label {
          color: #a0aec0;
          font-size: 13px;
        }

        .stat-value {
          color: #ffffff;
          font-weight: 500;
          font-family: 'Courier New', monospace;

          &.danger { color: #F56C6C; }
          &.warning { color: #E6A23C; }
          &.success { color: #67C23A; }
        }
      }
    }
  }
}

.history-section,
.formula-section {
  background: rgba(12, 25, 41, 0.6);
  border: 1px solid rgba(64, 158, 255, 0.2);
  border-radius: 12px;
  margin-bottom: 20px;
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
  }

  .history-chart-container {
    height: 280px;
    padding: 16px 20px;
  }
}

.formula-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  padding: 20px;

  .formula-card {
    background: rgba(0, 0, 0, 0.2);
    border-radius: 10px;
    padding: 16px;

    h4 {
      color: #409EFF;
      font-size: 14px;
      margin: 0 0 12px 0;
    }

    .formula {
      background: rgba(64, 158, 255, 0.1);
      border: 1px solid rgba(64, 158, 255, 0.2);
      border-radius: 6px;
      padding: 12px;
      text-align: center;
      color: #ffffff;
      font-family: 'Courier New', monospace;
      font-size: 15px;
      font-weight: 500;
      margin-bottom: 12px;
    }

    .formula-desc {
      color: #a0aec0;
      font-size: 12px;
      line-height: 1.6;
      margin: 0;
    }
  }
}

@media (max-width: 1200px) {
  .metrics-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .charts-row {
    grid-template-columns: 1fr;
  }

  .formula-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
