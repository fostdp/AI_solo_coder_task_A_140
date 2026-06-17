<template>
  <div class="stability-card card">
    <div class="card-header">
      <h3 class="card-title">
        <el-icon><TrendCharts /></el-icon>
        稳性状态
      </h3>
      <el-tag :type="getStatusType()" effect="dark" size="large">
        {{ stabilityData?.stabilityStatus || '未知' }}
      </el-tag>
    </div>

    <div class="gm-display">
      <div class="gm-ring" :class="getGmClass()">
        <svg viewBox="0 0 120 120" class="ring-svg">
          <circle cx="60" cy="60" r="50" class="ring-bg" />
          <circle
            cx="60" cy="60" r="50"
            class="ring-progress"
            :stroke-dasharray="circumference"
            :stroke-dashoffset="getGmOffset()"
            transform="rotate(-90 60 60)"
          />
        </svg>
        <div class="gm-value">
          <span class="gm-number">{{ gmValue }}</span>
          <span class="gm-unit">m</span>
        </div>
      </div>
      <div class="gm-label">
        GM值 (初稳性高)
        <span class="gm-threshold">阈值: {{ minGm }}m</span>
      </div>
    </div>

    <div class="stability-metrics">
      <div class="metric-item">
        <span class="metric-label">横摇周期</span>
        <span class="metric-value">{{ stabilityData?.rollPeriod?.toFixed(2) || '--' }} s</span>
      </div>
      <div class="metric-item">
        <span class="metric-label">复原力矩</span>
        <span class="metric-value">{{ stabilityData?.rightingMoment?.toFixed(1) || '--' }} kN·m</span>
      </div>
      <div class="metric-item">
        <span class="metric-label">复原力臂</span>
        <span class="metric-value">{{ stabilityData?.rightingArm?.toFixed(4) || '--' }} m</span>
      </div>
      <div class="metric-item">
        <span class="metric-label">实际排水量</span>
        <span class="metric-value">{{ stabilityData?.displacementActual?.toFixed(1) || '--' }} t</span>
      </div>
    </div>

    <div v-if="stabilityData?.warningMessage" class="warning-box">
      <el-icon><Warning /></el-icon>
      <span>{{ stabilityData.warningMessage }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { TrendCharts, Warning } from '@element-plus/icons-vue'

const props = defineProps({
  stabilityData: {
    type: Object,
    default: null
  },
  minGm: {
    type: Number,
    default: 0.3
  }
})

const circumference = 2 * Math.PI * 50

const gmValue = computed(() => {
  return props.stabilityData?.gmValue?.toFixed(3) || '--'
})

const getStatusType = () => {
  const status = props.stabilityData?.stabilityStatus
  const types = {
    'NORMAL': 'success',
    'CAUTION': 'warning',
    'WARNING': 'warning',
    'CRITICAL': 'danger'
  }
  return types[status] || 'info'
}

const getGmClass = () => {
  const gm = props.stabilityData?.gmValue
  if (gm == null) return ''
  if (gm < props.minGm) return 'critical'
  if (gm < 0.5) return 'warning'
  return 'normal'
}

const getGmOffset = () => {
  const gm = props.stabilityData?.gmValue
  if (gm == null) return circumference
  const maxGm = 1.5
  const progress = Math.min(Math.max(gm / maxGm, 0), 1)
  return circumference * (1 - progress)
}
</script>

<style scoped lang="scss">
.stability-card {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;

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

.gm-display {
  text-align: center;
  margin-bottom: 20px;

  .gm-ring {
    position: relative;
    width: 120px;
    height: 120px;
    margin: 0 auto 12px;

    .ring-svg {
      width: 100%;
      height: 100%;

      .ring-bg {
        fill: none;
        stroke: rgba(64, 158, 255, 0.2);
        stroke-width: 8;
      }

      .ring-progress {
        fill: none;
        stroke: #67C23A;
        stroke-width: 8;
        stroke-linecap: round;
        transition: stroke-dashoffset 0.5s ease, stroke 0.3s;
      }
    }

    &.warning .ring-progress {
      stroke: #E6A23C;
    }

    &.critical .ring-progress {
      stroke: #F56C6C;
    }

    .gm-value {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      text-align: center;

      .gm-number {
        display: block;
        font-size: 24px;
        font-weight: bold;
        font-family: 'Courier New', monospace;
        color: #ffffff;
      }

      .gm-unit {
        font-size: 12px;
        color: #a0aec0;
      }
    }
  }

  .gm-label {
    color: #a0aec0;
    font-size: 14px;

    .gm-threshold {
      display: block;
      font-size: 12px;
      margin-top: 4px;
    }
  }
}

.stability-metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;

  .metric-item {
    background: rgba(0, 0, 0, 0.2);
    padding: 10px 12px;
    border-radius: 6px;
    text-align: center;

    .metric-label {
      display: block;
      color: #a0aec0;
      font-size: 12px;
      margin-bottom: 4px;
    }

    .metric-value {
      display: block;
      color: #409EFF;
      font-size: 16px;
      font-weight: bold;
      font-family: 'Courier New', monospace;
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
  border-radius: 6px;
  color: #F56C6C;
  font-size: 13px;

  .el-icon {
    flex-shrink: 0;
  }
}
</style>
