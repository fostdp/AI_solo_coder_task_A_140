import request from './request'

export function getLatestSensorData(shipId) {
  return request.get(`/sensor-data/ship/${shipId}/latest`)
}

export function getSensorData(shipId, page = 0, size = 50) {
  return request.get(`/sensor-data/ship/${shipId}?page=${page}&size=${size}`)
}

export function getSensorDataHistory(shipId, startTime, endTime) {
  return request.get(`/sensor-data/ship/${shipId}/history`, {
    params: { startTime, endTime }
  })
}

export function getRecentSensorData(shipId, limit = 100) {
  return request.get(`/sensor-data/ship/${shipId}/recent?limit=${limit}`)
}

export function createSensorData(data) {
  return request.post('/sensor-data/manual', data)
}
