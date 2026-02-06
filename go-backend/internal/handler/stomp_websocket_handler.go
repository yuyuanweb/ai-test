// Package handler 原生 WebSocket + STOMP 处理器
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
	"github.com/sirupsen/logrus"
)

type StompWebSocketHandler struct {
	upgrader     websocket.Upgrader
	clients      map[*websocket.Conn]*StompWSClient
	clientsMutex sync.RWMutex
	logger       *logrus.Logger
}

type StompWSClient struct {
	conn          *websocket.Conn
	subscriptions map[string]bool
	subsMutex     sync.RWMutex
	send          chan []byte
	handler       *StompWebSocketHandler
	sessionID     string
}

func NewStompWebSocketHandler(logger *logrus.Logger) *StompWebSocketHandler {
	return &StompWebSocketHandler{
		upgrader: websocket.Upgrader{
			CheckOrigin: func(r *http.Request) bool {
				return true
			},
		},
		clients: make(map[*websocket.Conn]*StompWSClient),
		logger:  logger,
	}
}

// HandleWebSocket 处理 WebSocket 连接
func (h *StompWebSocketHandler) HandleWebSocket(c *gin.Context) {
	conn, err := h.upgrader.Upgrade(c.Writer, c.Request, nil)
	if err != nil {
		h.logger.WithError(err).Error("升级 WebSocket 失败")
		return
	}

	client := &StompWSClient{
		conn:          conn,
		subscriptions: make(map[string]bool),
		send:          make(chan []byte, 256),
		handler:       h,
		sessionID:     fmt.Sprintf("sess-%d", time.Now().UnixNano()),
	}

	h.clientsMutex.Lock()
	h.clients[conn] = client
	h.clientsMutex.Unlock()

	h.logger.WithField("sessionID", client.sessionID).Info("WebSocket 连接已建立")

	go client.writePump()
	go client.readPump()
}

// HandleInfo 处理 info 请求
func (h *StompWebSocketHandler) HandleInfo(c *gin.Context) {
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

func (c *StompWSClient) readPump() {
	defer func() {
		c.handler.clientsMutex.Lock()
		delete(c.handler.clients, c.conn)
		c.handler.clientsMutex.Unlock()
		c.conn.Close()
	}()

	for {
		_, message, err := c.conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
				c.handler.logger.WithError(err).Error("WebSocket 读取错误")
			}
			break
		}

		c.handleStompFrame(string(message))
	}
}

func (c *StompWSClient) writePump() {
	ticker := time.NewTicker(54 * time.Second)
	defer func() {
		ticker.Stop()
		c.conn.Close()
	}()

	for {
		select {
		case message, ok := <-c.send:
			if !ok {
				c.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}

			if err := c.conn.WriteMessage(websocket.TextMessage, message); err != nil {
				return
			}

		case <-ticker.C:
			// 心跳
			if err := c.conn.WriteMessage(websocket.TextMessage, []byte("\n")); err != nil {
				return
			}
		}
	}
}

func (c *StompWSClient) handleStompFrame(frame string) {
	scanner := bufio.NewScanner(strings.NewReader(frame))
	var command string
	headers := make(map[string]string)

	if scanner.Scan() {
		command = scanner.Text()
	}

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

	c.handler.logger.WithFields(logrus.Fields{
		"command": command,
		"headers": headers,
	}).Debug("收到 STOMP 帧")

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

func (c *StompWSClient) handleConnect(headers map[string]string) {
	response := fmt.Sprintf("CONNECTED\nversion:1.2\nheart-beat:0,0\nsession:%s\n\n\x00", c.sessionID)
	c.send <- []byte(response)
	c.handler.logger.WithField("sessionID", c.sessionID).Info("STOMP 客户端已连接")
}

func (c *StompWSClient) handleSubscribe(headers map[string]string) {
	destination := headers["destination"]
	id := headers["id"]

	c.subsMutex.Lock()
	c.subscriptions[destination] = true
	c.subsMutex.Unlock()

	c.handler.logger.WithFields(logrus.Fields{
		"destination": destination,
		"id":          id,
		"sessionID":   c.sessionID,
	}).Info("客户端订阅主题")
}

func (c *StompWSClient) handleUnsubscribe(headers map[string]string) {
	destination := headers["destination"]

	c.subsMutex.Lock()
	delete(c.subscriptions, destination)
	c.subsMutex.Unlock()

	c.handler.logger.WithField("destination", destination).Info("客户端取消订阅")
}

func (c *StompWSClient) handleDisconnect() {
	c.conn.Close()
	c.handler.logger.Info("客户端主动断开连接")
}

// SendToTopic 向指定主题发送消息
func (h *StompWebSocketHandler) SendToTopic(topic string, message interface{}) error {
	messageJSON, err := json.Marshal(message)
	if err != nil {
		return fmt.Errorf("序列化消息失败: %w", err)
	}

	frame := fmt.Sprintf("MESSAGE\ndestination:%s\ncontent-type:application/json\n\n%s\x00", topic, string(messageJSON))

	h.clientsMutex.RLock()
	defer h.clientsMutex.RUnlock()

	sentCount := 0
	for conn, client := range h.clients {
		client.subsMutex.RLock()
		subscribed := client.subscriptions[topic]
		client.subsMutex.RUnlock()

		if subscribed {
			select {
			case client.send <- []byte(frame):
				sentCount++
			default:
				h.logger.Warn("客户端发送缓冲区已满，跳过")
				conn.Close()
				delete(h.clients, conn)
			}
		}
	}

	h.logger.WithFields(logrus.Fields{
		"topic":     topic,
		"sentCount": sentCount,
	}).Info("消息已推送")

	return nil
}
