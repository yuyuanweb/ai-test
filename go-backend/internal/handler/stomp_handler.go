// Package handler STOMP over SockJS处理器
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package handler

import (
	"bufio"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/gorilla/websocket"
	"github.com/igm/sockjs-go/v3/sockjs"
	"github.com/sirupsen/logrus"
)

type StompHandler struct {
	sockjsHandler http.Handler
	clients       map[sockjs.Session]*StompClient
	clientsMutex  sync.RWMutex
	logger        *logrus.Logger
}

type StompClient struct {
	session       sockjs.Session
	subscriptions map[string]string // destination -> subscription ID
	subsMutex     sync.RWMutex
	handler       *StompHandler
	sessionID     string
	messageID     int
}

func NewStompHandler(logger *logrus.Logger) *StompHandler {
	handler := &StompHandler{
		clients: make(map[sockjs.Session]*StompClient),
		logger:  logger,
	}

	// 创建 SockJS handler
	options := sockjs.DefaultOptions
	options.Websocket = true
	options.SockJSURL = "https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"
	options.ResponseLimit = 128 * 1024
	options.HeartbeatDelay = 25 * time.Second
	options.DisconnectDelay = 5 * time.Second
	
	// 允许所有 Origin（类似 Java 版本的 setAllowedOriginPatterns("*")）
	options.CheckOrigin = func(r *http.Request) bool {
		return true // 允许所有来源（XHR 请求）
	}
	
	// 配置 WebSocket Upgrader，允许所有 Origin
	options.WebsocketUpgrader = &websocket.Upgrader{
		ReadBufferSize:  1024,
		WriteBufferSize: 1024,
		CheckOrigin: func(r *http.Request) bool {
			return true // 允许所有来源（WebSocket 升级）
		},
	}
	
	sockjsHandler := sockjs.NewHandler("/api/ws", options, handler.handleSockJSSession)
	handler.sockjsHandler = sockjsHandler

	return handler
}

// HandleSockJS 处理 SockJS 请求（包括 WebSocket 升级、xhr、eventsource 等所有传输方式）
func (h *StompHandler) HandleSockJS(c *gin.Context) {
	h.sockjsHandler.ServeHTTP(c.Writer, c.Request)
}

// GetSockJSHandler 获取 SockJS HTTP handler
func (h *StompHandler) GetSockJSHandler() http.Handler {
	return h.sockjsHandler
}

// HandleInfo 处理 SockJS info 请求
func (h *StompHandler) HandleInfo(c *gin.Context) {
	c.Header("Access-Control-Allow-Origin", "*")
	c.Header("Access-Control-Allow-Credentials", "true")
	c.Header("Cache-Control", "no-store, no-cache, no-transform, must-revalidate, max-age=0")
	
	c.JSON(http.StatusOK, gin.H{
		"websocket":     true,
		"origins":       []string{"*:*"},
		"cookie_needed": false,
		"entropy":       time.Now().UnixNano(),
	})
}

func (h *StompHandler) handleSockJSSession(session sockjs.Session) {
	sessionID := fmt.Sprintf("sess-%d", time.Now().UnixNano())
	h.logger.WithFields(logrus.Fields{
		"sessionID": sessionID,
	}).Info("SockJS 会话已建立")
	
	client := &StompClient{
		session:       session,
		subscriptions: make(map[string]string),
		handler:       h,
		sessionID:     sessionID,
		messageID:     1,
	}

	h.clientsMutex.Lock()
	h.clients[session] = client
	h.clientsMutex.Unlock()

	h.logger.WithField("sessionID", client.sessionID).Info("SockJS 会话已建立")

	// 读取消息循环
	for {
		msg, err := session.Recv()
		if err != nil {
			break
		}
		client.handleStompFrame(msg)
	}

	// 清理连接
	h.clientsMutex.Lock()
	delete(h.clients, session)
	h.clientsMutex.Unlock()
	h.logger.WithField("sessionID", client.sessionID).Info("SockJS 会话已关闭")
}

func (c *StompClient) handleStompFrame(frame string) {
	scanner := bufio.NewScanner(strings.NewReader(frame))
	var command string
	headers := make(map[string]string)
	var body strings.Builder

	// 解析命令
	if scanner.Scan() {
		command = scanner.Text()
	}

	// 解析头部
	for scanner.Scan() {
		line := scanner.Text()
		if line == "" {
			break
		}
		parts := strings.SplitN(line, ":", 2)
		if len(parts) == 2 {
			headers[parts[0]] = parts[1]
		}
	}

	// 解析消息体
	for scanner.Scan() {
		line := scanner.Text()
		if line == "\x00" {
			break
		}
		body.WriteString(line)
		body.WriteString("\n")
	}

	c.handler.logger.WithFields(logrus.Fields{
		"command": command,
		"headers": headers,
	}).Debug("收到STOMP帧")

	switch command {
	case "CONNECT", "STOMP":
		c.handleConnect(headers)
	case "SUBSCRIBE":
		c.handleSubscribe(headers)
	case "UNSUBSCRIBE":
		c.handleUnsubscribe(headers)
	case "DISCONNECT":
		c.handleDisconnect()
	}
}

func (c *StompClient) handleConnect(headers map[string]string) {
	response := fmt.Sprintf("CONNECTED\nversion:1.2\nheart-beat:0,0\nsession:%s\n\n\x00", c.sessionID)
	c.session.Send(response)
	c.handler.logger.WithField("sessionID", c.sessionID).Info("STOMP客户端已连接")
}

func (c *StompClient) handleSubscribe(headers map[string]string) {
	destination := headers["destination"]
	id := headers["id"]

	c.subsMutex.Lock()
	c.subscriptions[destination] = id // 保存订阅 ID
	c.subsMutex.Unlock()

	c.handler.logger.WithFields(logrus.Fields{
		"destination": destination,
		"id":          id,
		"sessionID":   c.sessionID,
	}).Info("客户端订阅主题")
}

func (c *StompClient) handleUnsubscribe(headers map[string]string) {
	destination := headers["destination"]

	c.subsMutex.Lock()
	delete(c.subscriptions, destination)
	c.subsMutex.Unlock()

	c.handler.logger.WithField("destination", destination).Info("客户端取消订阅")
}

func (c *StompClient) handleDisconnect() {
	c.session.Close(0, "client disconnect")
	c.handler.logger.Info("客户端主动断开连接")
}

// SendToTopic 向指定主题发送消息
func (h *StompHandler) SendToTopic(topic string, message interface{}) error {
	messageJSON, err := json.Marshal(message)
	if err != nil {
		return fmt.Errorf("序列化消息失败: %w", err)
	}

	h.clientsMutex.RLock()
	defer h.clientsMutex.RUnlock()

	sentCount := 0
	
	for session, client := range h.clients {
		client.subsMutex.RLock()
		subscriptionID, subscribed := client.subscriptions[topic]
		client.subsMutex.RUnlock()

		if subscribed {
			// 构建完整的 STOMP MESSAGE 帧（包含 subscription、message-id、content-length）
			frame := fmt.Sprintf("MESSAGE\ndestination:%s\ncontent-type:application/json\nsubscription:%s\nmessage-id:%s-%d\ncontent-length:%d\n\n%s\x00",
				topic,
				subscriptionID,
				client.sessionID,
				client.messageID,
				len(messageJSON),
				string(messageJSON))
			
			client.messageID++ // 递增消息 ID
			
			if err := session.Send(frame); err != nil {
				h.logger.WithError(err).Warn("发送消息到客户端失败")
			} else {
				sentCount++
			}
		}
	}

	h.logger.WithFields(logrus.Fields{
		"topic":     topic,
		"sentCount": sentCount,
	}).Info("消息已推送")

	return nil
}
