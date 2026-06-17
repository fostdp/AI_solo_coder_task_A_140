<template>
  <div class="loading-optimization">
    <div class="page-header">
      <h2 class="page-title">
        <el-icon><Box /></el-icon>
        装载优化
      </h2>
      <div class="header-desc">
        基于整数规划算法，优化散货（粮、盐）的舱位分配，最大化有效载重并保持稳性安全
      </div>
    </div>

    <div class="optimization-content">
      <div class="config-section">
        <div class="card config-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><Setting /></el-icon>
              优化参数配置
            </h3>
          </div>
          <div class="card-body">
            <el-form :model="optimizationConfig" label-width="120px">
              <el-form-item label="粮优先系数">
                <el-slider
                  v-model="optimizationConfig.grainPriority"
                  :min="0.5"
                  :max="2.0"
                  :step="0.1"
                  show-stops
                  :marks="priorityMarks"
                />
                <div class="slider-desc">
                  系数越高，算法越优先装载粮食
                </div>
              </el-form-item>

              <el-form-item label="盐优先系数">
                <el-slider
                  v-model="optimizationConfig.saltPriority"
                  :min="0.5"
                  :max="2.0"
                  :step="0.1"
                  show-stops
                  :marks="priorityMarks"
                />
                <div class="slider-desc">
                  系数越高，算法越优先装载盐
                </div>
              </el-form-item>

              <el-form-item label="最小GM要求">
                <el-input-number
                  v-model="optimizationConfig.minGm"
                  :min="0.2"
                  :max="1.5"
                  :step="0.05"
                  :precision="2"
                  controls-position="right"
                />
                <span class="unit">m</span>
              </el-form-item>

              <el-form-item label="最大横倾角">
                <el-input-number
                  v-model="optimizationConfig.maxRollAngle"
                  :min="5"
                  :max="25"
                  :step="1"
                  controls-position="right"
                />
                <span class="unit">°</span>
              </el-form-item>

              <el-form-item label="允许纵倾差">
                <el-input-number
                  v-model="optimizationConfig.maxTrim"
                  :min="0.1"
                  :max="1.0"
                  :step="0.1"
                  :precision="1"
                  controls-position="right"
                />
                <span class="unit">m</span>
              </el-form-item>

              <el-form-item label="算法选项">
                <div class="algorithm-options">
                  <el-checkbox v-model="optimizationConfig.useHeuristic">
                    启用启发式算法
                    <el-tooltip content="货物种类多时可显著加速求解" placement="top">
                      <el-icon class="info-icon"><QuestionFilled /></el-icon>
                    </el-tooltip>
                  </el-checkbox>
                  <el-checkbox v-model="optimizationConfig.refineWithMip">
                    MIP精化
                    <el-tooltip content="启发式求解后用精确算法进一步优化" placement="top">
                      <el-icon class="info-icon"><QuestionFilled /></el-icon>
                    </el-tooltip>
                  </el-checkbox>
                </div>
              </el-form-item>

              <el-form-item label="货物信息">
                <div class="cargo-info-grid">
                  <div class="cargo-info-item grain">
                    <div class="cargo-icon">
                      <el-icon><Wheat /></el-icon>
                    </div>
                    <div class="cargo-details">
                      <span class="cargo-name">粮</span>
                      <span class="cargo-spec">密度: 0.8 t/m³</span>
                      <span class="cargo-spec">单位重: 0.5 t/单位</span>
                    </div>
                  </div>
                  <div class="cargo-info-item salt">
                    <div class="cargo-icon">
                      <el-icon><Grid /></el-icon>
                    </div>
                    <div class="cargo-details">
                      <span class="cargo-name">盐</span>
                      <span class="cargo-spec">密度: 1.2 t/m³</span>
                      <span class="cargo-spec">单位重: 0.8 t/单位</span>
                    </div>
                  </div>
                </div>
              </el-form-item>

              <el-form-item>
                <el-button
                  type="primary"
                  size="large"
                  @click="runOptimization"
                  :loading="optimizing"
                  class="optimize-btn"
                >
                  <el-icon><Operation /></el-icon>
                  执行装载优化
                </el-button>
                <el-button
                  size="large"
                  @click="resetConfig"
                >
                  <el-icon><Refresh /></el-icon>
                  重置参数
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>

        <div class="card holds-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><Warehouse /></el-icon>
              货舱信息
            </h3>
          </div>
          <div class="card-body">
            <div class="holds-list">
              <div
                v-for="hold in cargoHolds"
                :key="hold.id"
                class="hold-item"
              >
                <div class="hold-header">
                  <span class="hold-name">{{ hold.holdName }}</span>
                  <el-tag size="small" type="info">
                    {{ hold.position }}
                  </el-tag>
                </div>
                <div class="hold-capacity">
                  <div class="capacity-bar">
                    <div
                      class="capacity-fill"
                      :style="{ width: getHoldCapacityPercent(hold) + '%' }"
                    ></div>
                  </div>
                  <div class="capacity-labels">
                    <span>0</span>
                    <span class="capacity-value">
                      {{ getHoldCurrentWeight(hold)?.toFixed(1) || 0 }} / {{ hold.maxWeight }} t
                    </span>
                    <span>{{ hold.maxWeight }}</span>
                  </div>
                </div>
                <div class="hold-specs">
                  <span>容积: {{ hold.capacityVolume }} m³</span>
                  <span>CG: ({{ hold.cgX?.toFixed(1) }}, {{ hold.cgY?.toFixed(1) }}, {{ hold.cgZ?.toFixed(1) }}) m</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="result-section">
        <div v-if="!optimizationResult" class="empty-result">
          <el-icon class="empty-icon"><DataLine /></el-icon>
          <p class="empty-text">配置参数后点击"执行装载优化"查看结果</p>
        </div>

        <div v-else class="result-content">
          <div class="result-summary">
            <div class="summary-card success">
              <div class="summary-icon">
                <el-icon><CircleCheck /></el-icon>
              </div>
              <div class="summary-info">
                <span class="summary-label">优化状态</span>
                <span class="summary-value">{{ optimizationResult.status === 'OPTIMAL' ? '最优解' : '可行解' }}</span>
              </div>
            </div>

            <div class="summary-card total">
              <div class="summary-icon">
                <el-icon><TrendCharts /></el-icon>
              </div>
              <div class="summary-info">
                <span class="summary-label">总有效载重</span>
                <span class="summary-value">{{ optimizationResult.totalWeight?.toFixed(1) || 0 }} t</span>
              </div>
            </div>

            <div class="summary-card grain">
              <div class="summary-icon">
                <el-icon><Wheat /></el-icon>
              </div>
              <div class="summary-info">
                <span class="summary-label">粮总重</span>
                <span class="summary-value">{{ optimizationResult.totalGrainWeight?.toFixed(1) || 0 }} t</span>
              </div>
            </div>

            <div class="summary-card salt">
              <div class="summary-icon">
                <el-icon><Grid /></el-icon>
              </div>
              <div class="summary-info">
                <span class="summary-label">盐总重</span>
                <span class="summary-value">{{ optimizationResult.totalSaltWeight?.toFixed(1) || 0 }} t</span>
              </div>
            </div>
          </div>

          <div class="result-details">
            <div class="card allocation-card">
              <div class="card-header">
                <h3 class="card-title">
                  <el-icon><Histogram /></el-icon>
                  舱位分配结果
                </h3>
                <div class="header-actions">
                  <el-button type="success" size="small" @click="applyOptimization" :loading="applying">
                    <el-icon><Check /></el-icon>
                    应用装载方案
                  </el-button>
                </div>
              </div>
              <div class="card-body">
                <div class="allocation-table-container">
                  <table class="allocation-table">
                    <thead>
                      <tr>
                        <th>货舱</th>
                        <th>位置</th>
                        <th>粮 (单位)</th>
                        <th>粮 (重量)</th>
                        <th>盐 (单位)</th>
                        <th>盐 (重量)</th>
                        <th>合计 (t)</th>
                        <th>利用率</th>
                        <th>状态</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(alloc, index) in optimizationResult.allocations" :key="index">
                        <td class="hold-name">{{ alloc.holdName }}</td>
                        <td class="hold-position">{{ alloc.position }}</td>
                        <td class="grain-units">{{ alloc.grainUnits || 0 }}</td>
                        <td class="grain-weight">{{ (alloc.grainUnits * 0.5)?.toFixed(1) || 0 }} t</td>
                        <td class="salt-units">{{ alloc.saltUnits || 0 }}</td>
                        <td class="salt-weight">{{ (alloc.saltUnits * 0.8)?.toFixed(1) || 0 }} t</td>
                        <td class="total-weight">{{ alloc.totalWeight?.toFixed(1) || 0 }} t</td>
                        <td>
                          <div class="utilization-bar">
                            <div
                              class="utilization-fill"
                              :style="{
                                width: (alloc.utilizationPercent || 0) + '%',
                                backgroundColor: getUtilizationColor(alloc.utilizationPercent)
                              }"
                            ></div>
                          </div>
                          <span class="utilization-text">{{ alloc.utilizationPercent?.toFixed(1) || 0 }}%</span>
                        </td>
                        <td>
                          <el-tag
                            :type="alloc.utilizationPercent > 90 ? 'success' : alloc.utilizationPercent > 60 ? 'primary' : 'info'"
                            size="small"
                          >
                            {{ alloc.utilizationPercent > 90 ? '满载' : alloc.utilizationPercent > 60 ? '良好' : '轻载' }}
                          </el-tag>
                        </td>
                      </tr>
                    </tbody>
                    <tfoot>
                      <tr class="total-row">
                        <td colspan="2">合计</td>
                        <td class="grain-units">{{ totalGrainUnits }}</td>
                        <td class="grain-weight">{{ optimizationResult.totalGrainWeight?.toFixed(1) || 0 }} t</td>
                        <td class="salt-units">{{ totalSaltUnits }}</td>
                        <td class="salt-weight">{{ optimizationResult.totalSaltWeight?.toFixed(1) || 0 }} t</td>
                        <td class="total-weight">{{ optimizationResult.totalWeight?.toFixed(1) || 0 }} t</td>
                        <td colspan="2">
                          <el-tag type="success" effect="dark" size="small">
                            载重利用率: {{ optimizationResult.utilizationPercent?.toFixed(1) || 0 }}%
                          </el-tag>
                        </td>
                      </tr>
                    </tfoot>
                  </table>
                </div>
              </div>
            </div>

            <div class="card analysis-card">
              <div class="card-header">
                <h3 class="card-title">
                  <el-icon><DataAnalysis /></el-icon>
                  稳性校验结果
                </h3>
              </div>
              <div class="card-body">
                <div class="stability-checks">
                  <div class="check-item">
                    <div class="check-header">
                      <span class="check-label">初稳性高 GM</span>
                      <el-tag
                        :type="optimizationResult.resultGm >= optimizationConfig.minGm ? 'success' : 'danger'"
                        effect="dark"
                        size="large"
                      >
                        {{ optimizationResult.resultGm?.toFixed(3) || '--' }} m
                      </el-tag>
                    </div>
                    <div class="check-bar">
                      <div class="check-track">
                        <div
                          class="check-fill success"
                          :style="{ width: Math.min((optimizationResult.resultGm / 1.5) * 100, 100) + '%' }"
                        ></div>
                        <div
                          class="threshold-marker"
                          :style="{ left: (optimizationConfig.minGm / 1.5) * 100 + '%' }"
                        >
                          <span class="threshold-label">最小要求: {{ optimizationConfig.minGm }}m</span>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div class="check-item">
                    <div class="check-header">
                      <span class="check-label">纵倾差</span>
                      <el-tag
                        :type="Math.abs(optimizationResult.trim || 0) <= optimizationConfig.maxTrim ? 'success' : 'warning'"
                        effect="dark"
                        size="large"
                      >
                        {{ optimizationResult.trim?.toFixed(3) || '--' }} m
                      </el-tag>
                    </div>
                    <div class="check-bar">
                      <div class="check-track">
                        <div
                          class="check-fill"
                          :class="Math.abs(optimizationResult.trim || 0) <= optimizationConfig.maxTrim ? 'success' : 'warning'"
                          :style="{ width: Math.min((Math.abs(optimizationResult.trim || 0) / optimizationConfig.maxTrim) * 50 + 50, 100) + '%' }"
                        ></div>
                      </div>
                    </div>
                  </div>

                  <div class="check-item">
                    <div class="check-header">
                      <span class="check-label">重心高度 KG</span>
                      <el-tag type="info" effect="dark" size="large">
                        {{ optimizationResult.resultKg?.toFixed(3) || '--' }} m
                      </el-tag>
                    </div>
                  </div>

                  <div class="check-item">
                    <div class="check-header">
                      <span class="check-label">浮心高度 KB</span>
                      <el-tag type="info" effect="dark" size="large">
                        {{ optimizationResult.resultKb?.toFixed(3) || '--' }} m
                      </el-tag>
                    </div>
                  </div>

                  <div class="check-item">
                    <div class="check-header">
                      <span class="check-label">预计横摇周期</span>
                      <el-tag type="primary" effect="dark" size="large">
                        {{ optimizationResult.resultRollPeriod?.toFixed(2) || '--' }} s
                      </el-tag>
                    </div>
                  </div>

                  <div v-if="optimizationResult.algorithmUsed || optimizationResult.solveTimeMs" class="algorithm-info">
                    <h4 class="algorithm-title">
                      <el-icon><MagicStick /></el-icon>
                      求解信息
                    </h4>
                    <div class="algorithm-grid">
                      <div class="algorithm-item">
                        <el-icon class="alg-icon"><Cpu /></el-icon>
                        <div class="alg-content">
                          <span class="alg-label">使用算法</span>
                          <span class="alg-value">{{ getAlgorithmName(optimizationResult.algorithmUsed) }}</span>
                        </div>
                      </div>
                      <div class="algorithm-item">
                        <el-icon class="alg-icon"><Timer /></el-icon>
                        <div class="alg-content">
                          <span class="alg-label">求解时间</span>
                          <span class="alg-value">{{ optimizationResult.solveTimeMs?.toFixed(0) || '--' }} ms</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <div class="constraints-info">
                  <h4 class="constraints-title">约束条件满足情况</h4>
                  <div class="constraint-list">
                    <div class="constraint-item success">
                      <el-icon><CircleCheck /></el-icon>
                      <span>货舱重量限制: 所有舱位装载量未超过最大载重</span>
                    </div>
                    <div class="constraint-item success">
                      <el-icon><CircleCheck /></el-icon>
                      <span>货舱容积限制: 所有舱位装载体积未超过容积</span>
                    </div>
                    <div class="constraint-item" :class="optimizationResult.resultGm >= optimizationConfig.minGm ? 'success' : 'warning'">
                      <el-icon>
                        <component :is="optimizationResult.resultGm >= optimizationConfig.minGm ? 'CircleCheck' : 'Warning'" />
                      </el-icon>
                      <span>稳性约束: GM ≥ {{ optimizationConfig.minGm }}m</span>
                    </div>
                    <div class="constraint-item" :class="Math.abs(optimizationResult.trim || 0) <= optimizationConfig.maxTrim ? 'success' : 'warning'">
                      <el-icon>
                        <component :is="Math.abs(optimizationResult.trim || 0) <= optimizationConfig.maxTrim ? 'CircleCheck' : 'Warning'" />
                      </el-icon>
                      <span>纵倾约束: |纵倾| ≤ {{ optimizationConfig.maxTrim }}m</span>
                    </div>
                    <div class="constraint-item success">
                      <el-icon><CircleCheck /></el-icon>
                      <span>总载重限制: 总重量未超过船舶载重量</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="card chart-card">
            <div class="card-header">
              <h3 class="card-title">
                <el-icon><PieChart /></el-icon>
                装载分布可视化
              </h3>
            </div>
            <div class="card-body">
              <div class="chart-row">
                <div class="chart-container pie">
                  <canvas ref="pieChartRef"></canvas>
                </div>
                <div class="chart-container bar">
                  <canvas ref="barChartRef"></canvas>
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
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import {
  Box, Setting, Wheat, Grid, Operation, Refresh, Warehouse,
  DataLine, CircleCheck, TrendCharts, Histogram, Check,
  DataAnalysis, PieChart, Warning, QuestionFilled, Timer, MagicStick, Cpu
} from '@element-plus/icons-vue'
import { Chart, registerables } from 'chart.js'
import { useShipData } from '@/composables/useShipData'
import { optimizeLoading, getLatestOptimization, addCargoLoading, clearNonOptimizedLoadings } from '@/api/loading'
import { ElMessage } from 'element-plus'

Chart.register(...registerables)

const props = defineProps({
  shipId: {
    type: String,
    default: null
  }
})

const shipIdRef = computed(() => props.shipId)
const { cargoHolds, cargoLoadings, fetchAllData } = useShipData(shipIdRef)

const optimizing = ref(false)
const applying = ref(false)
const optimizationResult = ref(null)
const pieChartRef = ref(null)
const barChartRef = ref(null)
let pieChart = null
let barChart = null

const optimizationConfig = ref({
  grainPriority: 1.0,
  saltPriority: 1.0,
  minGm: 0.3,
  maxRollAngle: 15,
  maxTrim: 0.5,
  useHeuristic: false,
  refineWithMip: false
})

const priorityMarks = {
  0.5: '0.5',
  1.0: '1.0',
  1.5: '1.5',
  2.0: '2.0'
}

const totalGrainUnits = computed(() => {
  return optimizationResult.value?.allocations?.reduce((sum, a) => sum + (a.grainUnits || 0), 0) || 0
})

const totalSaltUnits = computed(() => {
  return optimizationResult.value?.allocations?.reduce((sum, a) => sum + (a.saltUnits || 0), 0) || 0
})

const getHoldCapacityPercent = (hold) => {
  const current = getHoldCurrentWeight(hold)
  if (!hold.maxWeight || hold.maxWeight <= 0) return 0
  return Math.min((current / hold.maxWeight) * 100, 100)
}

const getHoldCurrentWeight = (hold) => {
  return cargoLoadings.value
    .filter(cl => cl.cargoHoldId === hold.id)
    .reduce((sum, cl) => sum + (cl.weight || 0), 0)
}

const getUtilizationColor = (percent) => {
  if (percent >= 90) return '#67C23A'
  if (percent >= 60) return '#409EFF'
  return '#909399'
}

const getAlgorithmName = (code) => {
  const names = {
    'MIP_EXACT': '精确整数规划',
    'MIP_REFINED': '启发式+MIP精化',
    'HEURISTIC_GREEDY': '贪心启发式',
    'HEURISTIC_GM_ADJUSTED': '启发式+GM调整'
  }
  return names[code] || code || '未知算法'
}

const runOptimization = async () => {
  if (!props.shipId) {
    ElMessage.warning('请先选择船舶')
    return
  }

  optimizing.value = true
  try {
    const request = {
      shipId: props.shipId,
      grainPriority: optimizationConfig.value.grainPriority,
      saltPriority: optimizationConfig.value.saltPriority,
      minGm: optimizationConfig.value.minGm,
      maxRollAngle: optimizationConfig.value.maxRollAngle,
      maxTrim: optimizationConfig.value.maxTrim,
      useHeuristic: optimizationConfig.value.useHeuristic,
      refineWithMip: optimizationConfig.value.refineWithMip
    }

    const res = await optimizeLoading(request)
    optimizationResult.value = res.data
    ElMessage.success('装载优化计算完成')

    setTimeout(() => {
      initCharts()
    }, 100)
  } catch (e) {
    ElMessage.error('优化失败: ' + (e.message || '未知错误'))
  } finally {
    optimizing.value = false
  }
}

const applyOptimization = async () => {
  if (!optimizationResult.value?.allocations) return

  applying.value = true
  try {
    await clearNonOptimizedLoadings(props.shipId)

    const grainTypeId = 'GRAIN'
    const saltTypeId = 'SALT'

    for (const alloc of optimizationResult.value.allocations) {
      if (alloc.grainUnits > 0) {
        await addCargoLoading({
          shipId: props.shipId,
          cargoHoldId: alloc.holdId,
          cargoTypeId: grainTypeId,
          weight: alloc.grainUnits * 0.5,
          volume: (alloc.grainUnits * 0.5) / 0.8,
          isOptimized: true
        })
      }

      if (alloc.saltUnits > 0) {
        await addCargoLoading({
          shipId: props.shipId,
          cargoHoldId: alloc.holdId,
          cargoTypeId: saltTypeId,
          weight: alloc.saltUnits * 0.8,
          volume: (alloc.saltUnits * 0.8) / 1.2,
          isOptimized: true
        })
      }
    }

    ElMessage.success('装载方案已应用')
    fetchAllData()
  } catch (e) {
    ElMessage.error('应用失败: ' + (e.message || '未知错误'))
  } finally {
    applying.value = false
  }
}

const resetConfig = () => {
  optimizationConfig.value = {
    grainPriority: 1.0,
    saltPriority: 1.0,
    minGm: 0.3,
    maxRollAngle: 15,
    maxTrim: 0.5,
    useHeuristic: false,
    refineWithMip: false
  }
}

const initCharts = () => {
  if (!optimizationResult.value?.allocations) return

  if (pieChartRef.value) {
    if (pieChart) pieChart.destroy()

    const ctx = pieChartRef.value.getContext('2d')
    pieChart = new Chart(ctx, {
      type: 'doughnut',
      data: {
        labels: ['粮', '盐', '可用空间'],
        datasets: [{
          data: [
            optimizationResult.value.totalGrainWeight || 0,
            optimizationResult.value.totalSaltWeight || 0,
            (optimizationResult.value.maxTotalWeight || 0) - (optimizationResult.value.totalWeight || 0)
          ],
          backgroundColor: ['#E6A23C', '#909399', 'rgba(255,255,255,0.1)'],
          borderColor: ['#E6A23C', '#909399', 'rgba(255,255,255,0.2)'],
          borderWidth: 2,
          hoverOffset: 10
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'bottom',
            labels: { color: '#a0aec0', padding: 20, usePointStyle: true }
          },
          title: {
            display: true,
            text: '载重构成',
            color: '#ffffff',
            font: { size: 14 }
          }
        }
      }
    })
  }

  if (barChartRef.value) {
    if (barChart) barChart.destroy()

    const labels = optimizationResult.value.allocations.map(a => a.holdName)
    const grainData = optimizationResult.value.allocations.map(a => (a.grainUnits * 0.5) || 0)
    const saltData = optimizationResult.value.allocations.map(a => (a.saltUnits * 0.8) || 0)
    const maxData = optimizationResult.value.allocations.map(a => a.maxWeight || 0)

    const ctx = barChartRef.value.getContext('2d')
    barChart = new Chart(ctx, {
      type: 'bar',
      data: {
        labels,
        datasets: [
          {
            label: '粮 (t)',
            data: grainData,
            backgroundColor: 'rgba(230, 162, 60, 0.8)',
            borderColor: '#E6A23C',
            borderWidth: 1,
            stack: 'total'
          },
          {
            label: '盐 (t)',
            data: saltData,
            backgroundColor: 'rgba(144, 147, 153, 0.8)',
            borderColor: '#909399',
            borderWidth: 1,
            stack: 'total'
          },
          {
            label: '最大载重 (t)',
            data: maxData,
            type: 'line',
            borderColor: '#F56C6C',
            borderWidth: 2,
            borderDash: [5, 5],
            pointRadius: 0,
            fill: false
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'bottom',
            labels: { color: '#a0aec0', padding: 20, usePointStyle: true }
          },
          title: {
            display: true,
            text: '各舱装载分布',
            color: '#ffffff',
            font: { size: 14 }
          }
        },
        scales: {
          x: {
            stacked: true,
            ticks: { color: '#a0aec0' },
            grid: { color: 'rgba(255,255,255,0.05)' }
          },
          y: {
            stacked: true,
            ticks: { color: '#a0aec0' },
            grid: { color: 'rgba(255,255,255,0.05)' },
            title: {
              display: true,
              text: '重量 (t)',
              color: '#a0aec0'
            }
          }
        }
      }
    })
  }
}

const loadLatestOptimization = async () => {
  if (!props.shipId) return
  try {
    const res = await getLatestOptimization(props.shipId)
    if (res.data) {
      optimizationResult.value = res.data
      setTimeout(() => initCharts(), 100)
    }
  } catch (e) {
    console.error('加载最新优化结果失败', e)
  }
}

watch(() => props.shipId, () => {
  optimizationResult.value = null
  loadLatestOptimization()
}, { immediate: true })

onUnmounted(() => {
  if (pieChart) pieChart.destroy()
  if (barChart) barChart.destroy()
})
</script>

<style scoped lang="scss">
.loading-optimization {
  width: 100%;
  padding: 0 4px;
}

.page-header {
  margin-bottom: 20px;

  .page-title {
    display: flex;
    align-items: center;
    gap: 10px;
    color: #ffffff;
    font-size: 22px;
    margin: 0 0 8px 0;

    .el-icon {
      color: #409EFF;
    }
  }

  .header-desc {
    color: #a0aec0;
    font-size: 14px;
  }
}

.optimization-content {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 16px;
}

.config-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card {
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

    .header-actions {
      display: flex;
      gap: 8px;
    }
  }

  .card-body {
    padding: 20px;
  }
}

.slider-desc {
  color: #a0aec0;
  font-size: 12px;
  margin-top: 6px;
}

.unit {
  color: #a0aec0;
  margin-left: 8px;
  font-size: 13px;
}

.optimize-btn {
  width: 100%;
  margin-right: 12px;
}

.cargo-info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  width: 100%;

  .cargo-info-item {
    display: flex;
    gap: 12px;
    padding: 12px;
    border-radius: 8px;
    background: rgba(0, 0, 0, 0.2);

    &.grain {
      border: 1px solid rgba(230, 162, 60, 0.3);

      .cargo-icon {
        background: rgba(230, 162, 60, 0.2);
        color: #E6A23C;
      }
    }

    &.salt {
      border: 1px solid rgba(144, 147, 153, 0.3);

      .cargo-icon {
        background: rgba(144, 147, 153, 0.2);
        color: #909399;
      }
    }

    .cargo-icon {
      width: 40px;
      height: 40px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
      flex-shrink: 0;
    }

    .cargo-details {
      display: flex;
      flex-direction: column;
      gap: 2px;

      .cargo-name {
        color: #ffffff;
        font-size: 14px;
        font-weight: 500;
      }

      .cargo-spec {
        color: #a0aec0;
        font-size: 11px;
      }
    }
  }
}

.holds-list {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .hold-item {
    background: rgba(0, 0, 0, 0.2);
    padding: 12px;
    border-radius: 8px;
    border: 1px solid rgba(64, 158, 255, 0.1);

    .hold-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;

      .hold-name {
        color: #ffffff;
        font-size: 14px;
        font-weight: 500;
      }
    }

    .hold-capacity {
      margin-bottom: 8px;

      .capacity-bar {
        height: 8px;
        background: rgba(0, 0, 0, 0.3);
        border-radius: 4px;
        overflow: hidden;

        .capacity-fill {
          height: 100%;
          background: linear-gradient(90deg, #409EFF, #67C23A);
          border-radius: 4px;
          transition: width 0.5s ease;
        }
      }

      .capacity-labels {
        display: flex;
        justify-content: space-between;
        margin-top: 4px;
        font-size: 11px;
        color: #a0aec0;

        .capacity-value {
          color: #409EFF;
          font-weight: 500;
        }
      }
    }

    .hold-specs {
      display: flex;
      justify-content: space-between;
      font-size: 11px;
      color: #a0aec0;
      font-family: 'Courier New', monospace;
    }
  }
}

.result-section {
  .empty-result {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 400px;
    background: rgba(12, 25, 41, 0.6);
    border: 1px solid rgba(64, 158, 255, 0.2);
    border-radius: 12px;
    color: #a0aec0;

    .empty-icon {
      font-size: 64px;
      color: rgba(64, 158, 255, 0.3);
      margin-bottom: 16px;
    }

    .empty-text {
      font-size: 16px;
    }
  }
}

.result-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;

  .summary-card {
    background: rgba(12, 25, 41, 0.6);
    border: 1px solid rgba(64, 158, 255, 0.2);
    border-radius: 12px;
    padding: 16px;
    display: flex;
    gap: 12px;
    align-items: center;

    &.success { border-left: 4px solid #67C23A; }
    &.total { border-left: 4px solid #409EFF; }
    &.grain { border-left: 4px solid #E6A23C; }
    &.salt { border-left: 4px solid #909399; }

    .summary-icon {
      width: 48px;
      height: 48px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      flex-shrink: 0;

      &.success {
        background: rgba(103, 194, 58, 0.2);
        color: #67C23A;
      }

      &.total {
        background: rgba(64, 158, 255, 0.2);
        color: #409EFF;
      }

      &.grain {
        background: rgba(230, 162, 60, 0.2);
        color: #E6A23C;
      }

      &.salt {
        background: rgba(144, 147, 153, 0.2);
        color: #909399;
      }
    }

    .summary-info {
      flex: 1;
      min-width: 0;

      .summary-label {
        display: block;
        color: #a0aec0;
        font-size: 12px;
        margin-bottom: 4px;
      }

      .summary-value {
        display: block;
        color: #ffffff;
        font-size: 22px;
        font-weight: bold;
        font-family: 'Courier New', monospace;
      }
    }
  }
}

.result-details {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.allocation-table-container {
  overflow-x: auto;

  .allocation-table {
    width: 100%;
    border-collapse: collapse;

    th, td {
      padding: 12px;
      text-align: center;
      border-bottom: 1px solid rgba(64, 158, 255, 0.1);
    }

    th {
      background: rgba(0, 0, 0, 0.3);
      color: #a0aec0;
      font-weight: 500;
      font-size: 13px;
    }

    td {
      color: #ffffff;
      font-size: 13px;
    }

    .hold-name {
      font-weight: 500;
      color: #409EFF;
    }

    .grain-units, .grain-weight {
      color: #E6A23C;
      font-family: 'Courier New', monospace;
    }

    .salt-units, .salt-weight {
      color: #909399;
      font-family: 'Courier New', monospace;
    }

    .total-weight {
      color: #409EFF;
      font-weight: bold;
      font-family: 'Courier New', monospace;
    }

    .utilization-bar {
      width: 80px;
      height: 8px;
      background: rgba(0, 0, 0, 0.3);
      border-radius: 4px;
      overflow: hidden;
      display: inline-block;
      margin-right: 8px;
      vertical-align: middle;

      .utilization-fill {
        height: 100%;
        border-radius: 4px;
        transition: width 0.5s ease;
      }
    }

    .utilization-text {
      color: #a0aec0;
      font-size: 12px;
      font-family: 'Courier New', monospace;
      vertical-align: middle;
    }

    tbody tr:hover {
      background: rgba(64, 158, 255, 0.05);
    }

    .total-row {
      background: rgba(0, 0, 0, 0.3);
      font-weight: bold;

      td {
        border-bottom: none;
      }
    }
  }
}

.stability-checks {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 20px;

  .check-item {
    .check-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;

      .check-label {
        color: #ffffff;
        font-size: 14px;
      }
    }

    .check-bar {
      .check-track {
        position: relative;
        height: 12px;
        background: rgba(0, 0, 0, 0.3);
        border-radius: 6px;
        overflow: visible;

        .check-fill {
          height: 100%;
          border-radius: 6px;
          transition: width 0.5s ease;

          &.success {
            background: linear-gradient(90deg, #67C23A, #409EFF);
          }

          &.warning {
            background: linear-gradient(90deg, #E6A23C, #F56C6C);
          }
        }

        .threshold-marker {
          position: absolute;
          top: -4px;
          transform: translateX(-50%);

          &::before {
            content: '';
            width: 2px;
            height: 20px;
            background: #F56C6C;
            display: block;
            margin: 0 auto;
          }

          .threshold-label {
            position: absolute;
            top: 20px;
            left: 50%;
            transform: translateX(-50%);
            white-space: nowrap;
            font-size: 10px;
            color: #F56C6C;
          }
        }
      }
    }
  }
}

.algorithm-options {
  display: flex;
  flex-direction: column;
  gap: 10px;

  .el-checkbox {
    color: #ffffff;

    :deep(.el-checkbox__label) {
      color: #ffffff;
    }
  }

  .info-icon {
    color: #409EFF;
    margin-left: 4px;
    cursor: help;
  }
}

.algorithm-info {
  padding: 16px;
  background: rgba(64, 158, 255, 0.05);
  border: 1px solid rgba(64, 158, 255, 0.2);
  border-radius: 8px;
  margin-bottom: 16px;

  .algorithm-title {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #409EFF;
    font-size: 14px;
    margin: 0 0 12px 0;

    .el-icon {
      font-size: 18px;
    }
  }

  .algorithm-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;

    .algorithm-item {
      display: flex;
      align-items: center;
      gap: 10px;

      .alg-icon {
        font-size: 24px;
        color: #67C23A;
      }

      .alg-content {
        display: flex;
        flex-direction: column;
        gap: 2px;

        .alg-label {
          font-size: 11px;
          color: #a0aec0;
        }

        .alg-value {
          font-size: 16px;
          font-weight: bold;
          color: #ffffff;
          font-family: 'Courier New', monospace;
        }
      }
    }
  }
}

.constraints-info {
  padding-top: 16px;
  border-top: 1px solid rgba(64, 158, 255, 0.15);

  .constraints-title {
    color: #ffffff;
    font-size: 14px;
    margin: 0 0 12px 0;
  }

  .constraint-list {
    display: flex;
    flex-direction: column;
    gap: 8px;

    .constraint-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 12px;
      border-radius: 6px;
      font-size: 13px;

      &.success {
        background: rgba(103, 194, 58, 0.1);
        color: #67C23A;
      }

      &.warning {
        background: rgba(245, 108, 108, 0.1);
        color: #F56C6C;
      }

      .el-icon {
        flex-shrink: 0;
      }
    }
  }
}

.chart-row {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 20px;

  .chart-container {
    height: 300px;

    &.pie {
      max-width: 350px;
    }
  }
}

@media (max-width: 1400px) {
  .optimization-content {
    grid-template-columns: 1fr;
  }

  .result-summary {
    grid-template-columns: repeat(2, 1fr);
  }

  .result-details {
    grid-template-columns: 1fr;
  }

  .chart-row {
    grid-template-columns: 1fr;

    .chart-container.pie {
      max-width: 100%;
    }
  }
}
</style>
