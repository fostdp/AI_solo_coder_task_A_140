import request from './request'

export function getShips() {
  return request.get('/ships')
}

export function getShipById(id) {
  return request.get(`/ships/${id}`)
}

export function createShip(data) {
  return request.post('/ships', data)
}

export function updateShip(id, data) {
  return request.put(`/ships/${id}`, data)
}

export function deleteShip(id) {
  return request.delete(`/ships/${id}`)
}
