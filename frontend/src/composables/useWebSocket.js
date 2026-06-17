import { ref, onUnmounted, watch } from 'vue'

const connections = new Map()
const messageHandlers = new Set()

export function useWebSocket(shipId = null) {
  const connected = ref(false)
  const lastMessage = ref(null)
  const reconnectAttempts = ref(0)
  const maxReconnectAttempts = 10

  let ws = null
  let reconnectTimer = null
  let pingTimer = null

  const getWsUrl = () => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    let url = `${protocol}//${host}/api/ws/stability`
    if (shipId) {
      url += `?shipId=${shipId}`
    }
    return url
  }

  const connect = () => {
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
      return
    }

    try {
      ws = new WebSocket(getWsUrl())

      ws.onopen = () => {
        console.log('WebSocket连接已建立')
        connected.value = true
        reconnectAttempts.value = 0
        startPing()
      }

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)
          lastMessage.value = data

          messageHandlers.forEach(handler => {
            try {
              handler(data)
            } catch (e) {
              console.error('消息处理失败', e)
            }
          })
        } catch (e) {
          console.error('解析WebSocket消息失败', e)
        }
      }

      ws.onclose = (event) => {
        console.log('WebSocket连接已关闭', event.code, event.reason)
        connected.value = false
        stopPing()
        scheduleReconnect()
      }

      ws.onerror = (error) => {
        console.error('WebSocket错误', error)
        connected.value = false
      }
    } catch (e) {
      console.error('创建WebSocket连接失败', e)
      scheduleReconnect()
    }
  }

  const scheduleReconnect = () => {
    if (reconnectAttempts.value >= maxReconnectAttempts) {
      console.warn('达到最大重连次数，停止重连')
      return
    }

    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
    }

    const delay = Math.min(1000 * Math.pow(2, reconnectAttempts.value), 30000)
    reconnectAttempts.value++

    reconnectTimer = setTimeout(() => {
      console.log(`正在尝试第 ${reconnectAttempts.value} 次重连...`)
      connect()
    }, delay)
  }

  const startPing = () => {
    stopPing()
    pingTimer = setInterval(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'PING' }))
      }
    }, 30000)
  }

  const stopPing = () => {
    if (pingTimer) {
      clearInterval(pingTimer)
      pingTimer = null
    }
  }

  const disconnect = () => {
    stopPing()
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (ws) {
      ws.close()
      ws = null
    }
    connected.value = false
  }

  const reconnect = () => {
    reconnectAttempts.value = 0
    disconnect()
    connect()
  }

  const send = (data) => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(data))
    }
  }

  const subscribe = (shipIdToSubscribe) => {
    send({
      type: 'SUBSCRIBE',
      shipId: shipIdToSubscribe
    })
  }

  const unsubscribe = (shipIdToUnsubscribe) => {
    send({
      type: 'UNSUBSCRIBE',
      shipId: shipIdToUnsubscribe
    })
  }

  const onMessage = (handler) => {
    messageHandlers.add(handler)
    return () => messageHandlers.delete(handler)
  }

  watch(
    () => shipId,
    (newShipId, oldShipId) => {
      if (oldShipId) {
        unsubscribe(oldShipId)
      }
      if (newShipId && connected.value) {
        subscribe(newShipId)
      }
    }
  )

  onUnmounted(() => {
    disconnect()
  })

  if (!connections.has(shipId || 'global')) {
    connect()
    connections.set(shipId || 'global', { connected })
  }

  return {
    connected,
    lastMessage,
    reconnectAttempts,
    connect,
    disconnect,
    reconnect,
    send,
    subscribe,
    unsubscribe,
    onMessage
  }
}
