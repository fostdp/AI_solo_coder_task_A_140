/**
 * stability_panel.js
 * 稳性分析面板模块
 * 功能：稳心曲线(GZ曲线)Chart.js渲染、稳性参数展示、物理示意图、公式说明
 */

export class StabilityPanel {
  constructor(canvasElement, options = {}) {
    this.canvas = canvasElement
    this.chart = null
    this.curveData = null
    this.stabilityData = null

    this.options = {
      curveColor: '#409EFF',
      curveFillColor: 'rgba(64, 158, 255, 0.1)',
      momentColor: '#67C23A',
      zeroLineColor: '#F56C6C',
      fontSize: 12,
      fontFamily: 'system-ui, -apple-system, sans-serif',
      ...options
    }

    this._chartLoaded = false
    this._initPromise = null
  }

  async _ensureChartJS() {
    if (this._chartLoaded) return true
    if (this._initPromise) return this._initPromise

    this._initPromise = (async () => {
      try {
        const {
          Chart, LineController, LineElement, PointElement,
          LinearScale, CategoryScale, Tooltip, Legend,
          Filler, Decimation
        } = await import('chart.js')

        Chart.register(
          LineController, LineElement, PointElement,
          LinearScale, CategoryScale, Tooltip, Legend,
          Filler, Decimation
        )
        this._chartLoaded = true
        return true
      } catch (e) {
        console.error('[StabilityPanel] Chart.js加载失败:', e)
        throw e
      }
    })()

    return this._initPromise
  }

  async init() {
    await this._ensureChartJS()
    this._createChart()
  }

  _createChart() {
    if (!this.canvas) {
      throw new Error('Canvas元素不能为空')
    }

    const ctx = this.canvas.getContext('2d')

    this.chart = new Chart(ctx, {
      type: 'line',
      data: {
        labels: [],
        datasets: [
          {
            label: '复原力臂 GZ (m)',
            data: [],
            borderColor: this.options.curveColor,
            backgroundColor: this.options.curveFillColor,
            yAxisID: 'y',
            fill: true,
            tension: 0.35,
            pointRadius: 2,
            pointHoverRadius: 6,
            borderWidth: 2.5
          },
          {
            label: '复原力矩 MR (kN·m)',
            data: [],
            borderColor: this.options.momentColor,
            yAxisID: 'y1',
            fill: false,
            tension: 0.35,
            pointRadius: 0,
            borderWidth: 2,
            borderDash: [5, 5]
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
            position: 'top',
            labels: {
              color: '#ffffff',
              font: {
                family: this.options.fontFamily,
                size: this.options.fontSize
              },
              usePointStyle: true,
              padding: 15
            }
          },
          tooltip: {
            backgroundColor: 'rgba(15, 23, 42, 0.95)',
            titleColor: '#ffffff',
            bodyColor: '#e2e8f0',
            borderColor: 'rgba(64, 158, 255, 0.5)',
            borderWidth: 1,
            padding: 12,
            titleFont: { family: this.options.fontFamily, size: 13, weight: 'bold' },
            bodyFont: { family: this.options.fontFamily, size: 12 },
            callbacks: {
              title: (items) => `横摇角: ${items[0].label}°`,
              label: (ctx) => {
                const label = ctx.dataset.label || ''
                const value = ctx.parsed.y
                return `${label}: ${typeof value === 'number' ? value.toFixed(3) : value}`
              }
            }
          }
        },
        scales: {
          x: {
            type: 'category',
            display: true,
            title: {
              display: true,
              text: '横摇角 φ (°)',
              color: '#a0aec0',
              font: { family: this.options.fontFamily, size: 12, weight: 'bold' }
            },
            ticks: {
              color: '#a0aec0',
              font: { family: this.options.fontFamily, size: 11 },
              maxTicksLimit: 12
            },
            grid: {
              color: 'rgba(160, 174, 192, 0.1)'
            }
          },
          y: {
            type: 'linear',
            display: true,
            position: 'left',
            title: {
              display: true,
              text: '复原力臂 GZ (m)',
              color: this.options.curveColor,
              font: { family: this.options.fontFamily, size: 12, weight: 'bold' }
            },
            ticks: {
              color: this.options.curveColor,
              font: { family: this.options.fontFamily, size: 11 }
            },
            grid: {
              color: 'rgba(160, 174, 192, 0.1)'
            }
          },
          y1: {
            type: 'linear',
            display: true,
            position: 'right',
            title: {
              display: true,
              text: '复原力矩 MR (kN·m)',
              color: this.options.momentColor,
              font: { family: this.options.fontFamily, size: 12, weight: 'bold' }
            },
            ticks: {
              color: this.options.momentColor,
              font: { family: this.options.fontFamily, size: 11 }
            },
            grid: {
              drawOnChartArea: false
            }
          }
        }
      }
    })
  }

  updateCurve(curvePoints) {
    if (!this.chart || !curvePoints || !curvePoints.length) return

    this.curveData = curvePoints

    const labels = curvePoints.map(p =>
      typeof p.angle === 'number' ? p.angle.toFixed(0) : String(p.angle)
    )
    const gzData = curvePoints.map(p =>
      typeof p.rightingArm === 'number' ? +p.rightingArm.toFixed(4) : null
    )
    const momentData = curvePoints.map(p =>
      typeof p.rightingMoment === 'number' ? +p.rightingMoment.toFixed(2) : null
    )

    this.chart.data.labels = labels
    this.chart.data.datasets[0].data = gzData
    this.chart.data.datasets[1].data = momentData
    this.chart.update('none')
  }

  setStabilityData(data) {
    this.stabilityData = data
  }

  getDerivedMetrics() {
    if (!this.stabilityData) return null

    const gm = this.stabilityData.gmValue
    const result = {
      gm: gm != null ? +gm : 0,
      gmRange: null,
      maximumGZ: null,
      angleOfMaximumGZ: null,
      angleOfVanishingStability: null,
      areaUnderGZ: 0,
      stabilityGrade: '未知'
    }

    if (this.curveData && this.curveData.length > 0) {
      let maxGZ = -Infinity
      let maxGZAngle = 0
      let vanishAngle = 0
      let prevGZ = 0
      let area = 0
      const gmRadians = result.gm

      for (let i = 0; i < this.curveData.length; i++) {
        const point = this.curveData[i]
        const angle = typeof point.angle === 'number' ? point.angle : 0
        const gz = typeof point.rightingArm === 'number' ? point.rightingArm : 0

        if (gz > maxGZ) {
          maxGZ = gz
          maxGZAngle = angle
        }

        if (i > 0 && prevGZ > 0 && gz <= 0 && vanishAngle === 0) {
          vanishAngle = angle
        }

        if (i > 0) {
          const prevAngle = typeof this.curveData[i - 1].angle === 'number'
            ? this.curveData[i - 1].angle : 0
          const dAngle = (angle - prevAngle) * Math.PI / 180
          area += (prevGZ + gz) / 2 * dAngle
        }

        prevGZ = gz
      }

      result.maximumGZ = +maxGZ.toFixed(4)
      result.angleOfMaximumGZ = maxGZAngle
      result.angleOfVanishingStability = vanishAngle
      result.areaUnderGZ = +area.toFixed(4)

      if (gmRadians > 0.5) {
        result.stabilityGrade = '优良'
      } else if (gmRadians > 0.3) {
        result.stabilityGrade = '合格'
      } else if (gmRadians > 0.15) {
        result.stabilityGrade = '偏低'
      } else {
        result.stabilityGrade = '危险'
      }
    }

    return result
  }

  static getPhysicsDiagramSVG(options = {}) {
    const {
      hullColor = '#8B4513',
      waterLineColor = '#0077be',
      gravityColor = '#F56C6C',
      buoyancyColor = '#67C23A',
      metacenterColor = '#409EFF',
      dimensionColor = '#a0aec0'
    } = options

    return `
      <svg viewBox="0 0 200 260" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <marker id="arrow" viewBox="0 0 10 10" refX="8" refY="5"
                  markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="${dimensionColor}"/>
          </marker>
        </defs>

        <line x1="10" y1="85" x2="190" y2="85"
              stroke="${waterLineColor}" stroke-width="2" stroke-dasharray="4 2"/>
        <text x="192" y="88" fill="${waterLineColor}" font-size="9" font-family="sans-serif">WL</text>

        <path d="M30,90 Q25,140 40,180 L160,180 Q175,140 170,90 Z"
              fill="${hullColor}" stroke="#5a3510" stroke-width="1.5" opacity="0.85"/>

        <line x1="20" y1="180" x2="180" y2="180" stroke="${dimensionColor}"
              stroke-width="1" marker-start="url(#arrow)" marker-end="url(#arrow)"/>
        <text x="95" y="193" text-anchor="middle" fill="${dimensionColor}" font-size="9" font-family="sans-serif">B</text>

        <circle cx="100" cy="130" r="5" fill="${gravityColor}" stroke="#ffffff" stroke-width="1.5"/>
        <line x1="100" y1="130" x2="115" y2="130" stroke="${gravityColor}" stroke-width="1"/>
        <text x="117" y="133" fill="${gravityColor}" font-size="10" font-weight="bold" font-family="sans-serif">G</text>

        <circle cx="100" cy="98" r="5" fill="${buoyancyColor}" stroke="#ffffff" stroke-width="1.5"/>
        <line x1="100" y1="98" x2="78" y2="98" stroke="${buoyancyColor}" stroke-width="1"/>
        <text x="60" y="101" fill="${buoyancyColor}" font-size="10" font-weight="bold" font-family="sans-serif">B</text>

        <circle cx="100" cy="40" r="5" fill="${metacenterColor}" stroke="#ffffff" stroke-width="1.5"/>
        <line x1="100" y1="40" x2="115" y2="40" stroke="${metacenterColor}" stroke-width="1"/>
        <text x="117" y="43" fill="${metacenterColor}" font-size="10" font-weight="bold" font-family="sans-serif">M</text>

        <line x1="100" y1="40" x2="100" y2="130" stroke="${metacenterColor}"
              stroke-width="2" stroke-dasharray="3 3"/>
        <line x1="94" y1="85" x2="106" y2="85" stroke="${metacenterColor}" stroke-width="1.5"/>

        <line x1="140" y1="42" x2="140" y2="128" stroke="${dimensionColor}"
              stroke-width="1" marker-start="url(#arrow)" marker-end="url(#arrow)"/>
        <text x="143" y="88" fill="${metacenterColor}" font-size="10" font-weight="bold"
              font-family="sans-serif" writing-mode="tb">GM</text>

        <line x1="60" y1="75" x2="60" y2="100" stroke="${dimensionColor}"
              stroke-width="1" marker-start="url(#arrow)" marker-end="url(#arrow)"/>
        <text x="42" y="90" fill="${dimensionColor}" font-size="9" font-family="sans-serif">d</text>

        <line x1="30" y1="95" x2="100" y2="130" stroke="#ffaa00" stroke-width="1.5"
              stroke-dasharray="2 2" opacity="0.7"/>
        <path d="M 78,112 L 84,110 L 86,116" fill="none" stroke="#ffaa00" stroke-width="1"/>
        <text x="62" y="108" fill="#ffaa00" font-size="9" font-family="sans-serif">φ</text>

        <line x1="100" y1="130" x2="105" y2="180" stroke="#ffaa00" stroke-width="1.5"/>
      </svg>
    `
  }

  static getFormulas() {
    return [
      {
        name: '初稳心高',
        symbol: 'GM',
        formula: 'GM = KM − KG',
        description: '初稳心M到重心G的垂向距离，衡量初始稳性储备',
        unit: 'm',
        safetyValue: '≥ 0.3m'
      },
      {
        name: '复原力臂',
        symbol: 'GZ',
        formula: 'GZ = GM · sin(φ)',
        description: '横倾φ角后浮力作用线到重心的垂直距离',
        unit: 'm',
        safetyValue: 'max ≥ 0.2m'
      },
      {
        name: '复原力矩',
        symbol: 'MR',
        formula: 'MR = Δ · g · GZ',
        description: '使船舶恢复平衡的力矩，与排水量成正比',
        unit: 'kN·m',
        safetyValue: '正值区间越大越好'
      },
      {
        name: '横摇周期',
        symbol: 'T',
        formula: 'T = 2πk / √(g·GM)',
        description: '船舶自由横摇的固有周期，与√GM成反比',
        unit: 's',
        safetyValue: '8~12s（舒适性）'
      }
    ]
  }

  resize() {
    if (this.chart) {
      this.chart.resize()
    }
  }

  destroy() {
    if (this.chart) {
      this.chart.destroy()
      this.chart = null
    }
    this.curveData = null
    this.stabilityData = null
  }
}

export default StabilityPanel
