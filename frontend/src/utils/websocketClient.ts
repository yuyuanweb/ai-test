/**
 * WebSocket客户端工具类（使用STOMP over SockJS）
 * 
 * 使用标准的 @stomp/stompjs 和 sockjs-client 库
 */

import { Client } from '@stomp/stompjs'
import type { IMessage, StompSubscription } from '@stomp/stompjs'
// @ts-ignore - sockjs-client 没有完整的类型定义
import SockJS from 'sockjs-client'

export interface WebSocketOptions {
  onConnect?: () => void
  onDisconnect?: () => void
  onError?: (error: Event | string) => void
  onMessage?: (topic: string, message: any) => void
}

export class WebSocketClient {
  private client: Client | null = null
  private baseUrl: string
  private options: WebSocketOptions
  private subscriptions: Map<string, StompSubscription> = new Map()
  private reconnectAttempts = 0
  private maxReconnectAttempts = 5
  private reconnectDelay = 3000

  constructor(baseUrl: string, options: WebSocketOptions = {}) {
    this.baseUrl = baseUrl
    this.options = options
  }

  connect(): void {
    if (this.client && this.client.connected) {
      console.log('⚠️ WebSocket已连接，跳过重复连接')
      return
    }

    try {
      // 构建 WebSocket URL
      // 后端 context-path 是 /api，所以 WebSocket 端点是 /api/ws
      let sockJsUrl: string
      
      if (this.baseUrl.startsWith('/')) {
        // 相对路径：使用 /api/ws（通过 Vite 代理）
        sockJsUrl = '/api/ws'
      } else {
        // 完整 URL：直接使用 baseUrl + /ws
        // baseUrl 已经是 http://localhost:8123/api，所以加上 /ws 变成 /api/ws
        const baseUrl = this.baseUrl.endsWith('/') ? this.baseUrl.slice(0, -1) : this.baseUrl
        sockJsUrl = `${baseUrl}/ws`
      }

      console.log('🔌 正在连接WebSocket:', sockJsUrl, '(baseUrl:', this.baseUrl, ')')

      this.client = new Client({
        webSocketFactory: () => new SockJS(sockJsUrl) as any,
        reconnectDelay: this.reconnectDelay,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        onConnect: (frame) => {
          console.log('✅ WebSocket连接成功:', frame)
          this.reconnectAttempts = 0
          this.options.onConnect?.()
        },
        onStompError: (frame) => {
          console.error('❌ STOMP错误:', frame)
          this.options.onError?.(frame.headers['message'] || 'STOMP连接错误')
        },
        onWebSocketError: (error) => {
          console.error('❌ WebSocket错误:', error)
          this.options.onError?.(error)
        },
        onDisconnect: () => {
          console.log('🔌 WebSocket断开连接')
          this.options.onDisconnect?.()
          this.attemptReconnect()
        }
      })

      this.client.activate()
    } catch (error) {
      console.error('❌ WebSocket连接失败:', error)
      this.options.onError?.(error as Event)
    }
  }

  subscribe(topic: string): void {
    if (!this.client) {
      console.warn('⚠️ WebSocket客户端未初始化，无法订阅')
      return
    }

    if (this.subscriptions.has(topic)) {
      console.warn('⚠️ 已订阅该主题，跳过:', topic)
      return
    }

    if (!this.client.connected) {
      console.warn('⚠️ WebSocket未连接，等待连接后订阅:', topic)
      const originalOnConnect = this.options.onConnect
      this.options.onConnect = () => {
        originalOnConnect?.()
        this.doSubscribe(topic)
      }
      return
    }

    this.doSubscribe(topic)
  }

  private doSubscribe(topic: string): void {
    if (!this.client || !this.client.connected) {
      console.warn('⚠️ WebSocket未连接，无法订阅:', topic)
      return
    }

    try {
      const subscription = this.client.subscribe(topic, (message: IMessage) => {
        try {
          const body = message.body
          console.log('📨 收到WebSocket消息:', topic, body)
          
          let parsedMessage: any
          try {
            parsedMessage = JSON.parse(body)
          } catch (e) {
            console.warn('⚠️ 消息不是JSON格式，使用原始内容:', body)
            parsedMessage = body
          }

          this.options.onMessage?.(topic, parsedMessage)
        } catch (error) {
          console.error('❌ 处理消息失败:', error)
        }
      })

      this.subscriptions.set(topic, subscription)
      console.log('📡 订阅主题成功:', topic)
    } catch (error) {
      console.error('❌ 订阅主题失败:', topic, error)
    }
  }

  unsubscribe(topic: string): void {
    const subscription = this.subscriptions.get(topic)
    if (subscription) {
      subscription.unsubscribe()
      this.subscriptions.delete(topic)
      console.log('📴 取消订阅主题:', topic)
    }
  }

  disconnect(): void {
    if (this.client) {
      this.subscriptions.forEach((sub) => sub.unsubscribe())
      this.subscriptions.clear()
      this.client.deactivate()
      this.client = null
      console.log('🔌 WebSocket已断开连接')
    }
  }

  private attemptReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('❌ 达到最大重连次数，停止重连')
      return
    }

    this.reconnectAttempts++
    console.log(`🔄 尝试重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`)

    setTimeout(() => {
      if (this.client) {
        this.client.activate()
      } else {
        this.connect()
      }
    }, this.reconnectDelay)
  }
}

/**
 * 创建WebSocket客户端
 */
export function createWebSocketClient(baseUrl: string, options: WebSocketOptions = {}): WebSocketClient {
  return new WebSocketClient(baseUrl, options)
}
