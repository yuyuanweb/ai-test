// Package handler 测试HTTP处理器
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package handler

import (
	"ai-test-go/internal/model/vo"
	"ai-test-go/internal/service"
	"ai-test-go/pkg/common"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
)

type TestHandler struct {
	testService *service.TestService
}

func NewTestHandler(testService *service.TestService) *TestHandler {
	return &TestHandler{
		testService: testService,
	}
}

// TestAISimple 测试AI调用（非流式）
// @Summary      测试AI调用（非流式）
// @Description  测试调用OpenRouter模型
// @Tags         测试接口
// @Accept       json
// @Produce      json
// @Param        prompt  query     string  true   "提示词"
// @Param        model   query     string  false  "模型名称" default(qwen/qwen-plus)
// @Success      200     {object}  common.BaseResponse{data=string}  "成功"
// @Router       /test/ai/simple [post]
func (h *TestHandler) TestAISimple(c *gin.Context) {
	prompt := c.Query("prompt")
	if prompt == "" {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "prompt参数不能为空"))
		return
	}

	model := c.Query("model")
	if model == "" {
		model = "qwen/qwen-plus"
	}

	log.Printf("Testing simple AI call with prompt: %s, model: %s", prompt, model)

	response, err := h.testService.TestAISimple(prompt, model)
	if err != nil {
		log.Printf("AI调用失败: %v", err)
		c.JSON(http.StatusOK, common.Error(common.SYSTEM_ERROR, "AI调用失败: "+err.Error()))
		return
	}

	c.JSON(http.StatusOK, common.Success(response))
}

// TestAIStream 测试AI流式调用
// @Summary      测试AI流式调用
// @Description  测试调用OpenRouter模型（流式）
// @Tags         测试接口
// @Accept       json
// @Produce      text/event-stream
// @Param        prompt  query     string  true   "提示词"
// @Param        model   query     string  false  "模型名称" default(qwen/qwen-plus)
// @Success      200     {object}  vo.StreamChunkVO  "成功"
// @Router       /test/ai/stream [post]
func (h *TestHandler) TestAIStream(c *gin.Context) {
	prompt := c.Query("prompt")
	if prompt == "" {
		c.JSON(http.StatusOK, common.Error(common.PARAMS_ERROR, "prompt参数不能为空"))
		return
	}

	model := c.Query("model")
	if model == "" {
		model = "qwen/qwen-plus"
	}

	log.Printf("Testing stream AI call with prompt: %s, model: %s", prompt, model)

	c.Header("Content-Type", "text/event-stream")
	c.Header("Cache-Control", "no-cache")
	c.Header("Connection", "keep-alive")
	c.Header("X-Accel-Buffering", "no")

	startTime := time.Now()
	fullContent := ""
	outputTokens := 0

	err := h.testService.TestAIStream(prompt, model, func(chunk vo.StreamChunkVO) error {
		fullContent += chunk.Content
		outputTokens += len(chunk.Content) / 4
		chunk.FullContent = fullContent
		chunk.OutputTokens = outputTokens
		chunk.ElapsedMs = time.Since(startTime).Milliseconds()

		data, err := json.Marshal(chunk)
		if err != nil {
			return err
		}

		_, err = fmt.Fprintf(c.Writer, "data: %s\n\n", data)
		if err != nil {
			return err
		}
		c.Writer.Flush()
		return nil
	})

	if err != nil {
		log.Printf("Stream error for model %s: %v", model, err)
		errorChunk := vo.StreamChunkVO{
			ModelName: model,
			Error:     err.Error(),
			HasError:  true,
			Done:      true,
		}
		data, _ := json.Marshal(errorChunk)
		fmt.Fprintf(c.Writer, "data: %s\n\n", data)
		c.Writer.Flush()
		return
	}

	doneChunk := vo.StreamChunkVO{
		ModelName:      model,
		FullContent:    fullContent,
		OutputTokens:   outputTokens,
		ResponseTimeMs: int(time.Since(startTime).Milliseconds()),
		Done:           true,
		HasError:       false,
	}
	data, _ := json.Marshal(doneChunk)
	fmt.Fprintf(c.Writer, "data: %s\n\n", data)
	c.Writer.Flush()

	log.Printf("Stream completed for model: %s", model)
}
