import { ref, watch, onMounted, onUnmounted } from 'vue'
import { getLatestSensorData, getRecentSensorData } from '@/api/sensor'
import { getLatestStability } from '@/api/stability'
import { getLoadingSummary, getCargoLoadings, getCargoHolds } from '@/api/loading'
import { useWebSocket } from './useWebSocket'

export function useShipData(shipId) {
  const sensorData = ref(null)
  const stabilityData = ref(null)
  const loadingSummary = ref(null)
  const cargoLoadings = ref([])
  const cargoHolds = ref([])
  const loading = ref(true)
  const error = ref(null)

  const { connected, onMessage } = useWebSocket()

  const fetchAllData = async () => {
    if (!shipId.value) return

    loading.value = true
    error.value = null

    try {
      const [sensorRes, stabilityRes, summaryRes, loadingsRes, holdsRes] = await Promise.all([
        getLatestSensorData(shipId.value).catch(() => ({ data: null })),
        getLatestStability(shipId.value).catch(() => ({ data: null })),
        getLoadingSummary(shipId.value).catch(() => ({ data: null })),
        getCargoLoadings(shipId.value).catch(() => ({ data: [] })),
        getCargoHolds(shipId.value).catch(() => ({ data: [] }))
      ])

      sensorData.value = sensorRes.data
      stabilityData.value = stabilityRes.data
      loadingSummary.value = summaryRes.data
      cargoLoadings.value = loadingsRes.data || []
      cargoHolds.value = holdsRes.data || []
    } catch (e) {
      error.value = e
      console.error('加载船舶数据失败', e)
    } finally {
      loading.value = false
    }
  }

  const fetchSensorHistory = async (limit = 50) => {
    if (!shipId.value) return []
    try {
      const res = await getRecentSensorData(shipId.value, limit)
      return res.data || []
    } catch (e) {
      return []
    }
  }

  const handleWsMessage = (message) => {
    if (!message || message.shipId !== shipId.value) return

    if (message.type === 'STABILITY_UPDATE') {
      stabilityData.value = message.payload
    } else if (message.type === 'SENSOR_DATA') {
      sensorData.value = message.payload
    }
  }

  let unsubscribe = null
  let dataInterval = null

  watch(
    () => shipId.value,
    () => {
      fetchAllData()
    },
    { immediate: true }
  )

  onMounted(() => {
    unsubscribe = onMessage(handleWsMessage)
    dataInterval = setInterval(fetchAllData, 60000)
  })

  onUnmounted(() => {
    if (unsubscribe) unsubscribe()
    if (dataInterval) clearInterval(dataInterval)
  })

  return {
    sensorData,
    stabilityData,
    loadingSummary,
    cargoLoadings,
    cargoHolds,
    loading,
    error,
    fetchAllData,
    fetchSensorHistory
  }
}
