import request from './request'

export function getLatestStability(shipId) {
  return request.get(`/stability/ship/${shipId}/latest`)
}

export function getStabilityHistory(shipId, page = 0, size = 20) {
  return request.get(`/stability/ship/${shipId}?page=${page}&size=${size}`)
}

export function getStabilityByTimeRange(shipId, startTime, endTime) {
  return request.get(`/stability/ship/${shipId}/range`, {
    params: { startTime, endTime }
  })
}

export function getStabilityWarnings(shipId) {
  return request.get(`/stability/ship/${shipId}/warnings`)
}

export function calculateStability(shipId) {
  return request.post(`/stability/calculate/${shipId}`)
}

export function getStabilityById(id) {
  return request.get(`/stability/${id}`)
}
