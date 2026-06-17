import request from './request'

export function optimizeLoading(data) {
  return request.post('/loading-optimization/optimize', data)
}

export function getLatestOptimization(shipId) {
  return request.get(`/loading-optimization/ship/${shipId}/latest`)
}

export function getOptimizationHistory(shipId, page = 0, size = 20) {
  return request.get(`/loading-optimization/ship/${shipId}?page=${page}&size=${size}`)
}

export function getOptimizationById(id) {
  return request.get(`/loading-optimization/${id}`)
}

export function getCargoTypes() {
  return request.get('/cargo/types')
}

export function getCargoHolds(shipId) {
  return request.get(`/cargo/holds/ship/${shipId}`)
}

export function getCargoLoadings(shipId) {
  return request.get(`/cargo/loading/ship/${shipId}`)
}

export function getLoadingSummary(shipId) {
  return request.get(`/cargo/loading/ship/${shipId}/summary`)
}

export function addCargoLoading(data) {
  return request.post('/cargo/loading', data)
}

export function deleteCargoLoading(id) {
  return request.delete(`/cargo/loading/${id}`)
}

export function clearNonOptimizedLoadings(shipId) {
  return request.post(`/cargo/loading/ship/${shipId}/clear`)
}
