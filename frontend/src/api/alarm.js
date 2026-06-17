import request from './request'

export function getActiveAlarms(shipId) {
  return request.get(`/alarms/ship/${shipId}/active`)
}

export function getUnacknowledgedCount(shipId) {
  return request.get(`/alarms/ship/${shipId}/count`)
}

export function getAlarmHistory(shipId, page = 0, size = 20) {
  return request.get(`/alarms/ship/${shipId}?page=${page}&size=${size}`)
}

export function getAlarmsByLevel(shipId, level) {
  return request.get(`/alarms/ship/${shipId}/level/${level}`)
}

export function acknowledgeAlarm(id) {
  return request.post(`/alarms/${id}/acknowledge`)
}

export function acknowledgeAllAlarms(shipId) {
  return request.post(`/alarms/ship/${shipId}/acknowledge-all`)
}

export function getAlarmById(id) {
  return request.get(`/alarms/${id}`)
}
