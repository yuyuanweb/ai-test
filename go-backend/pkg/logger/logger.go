// Package logger 日志工具类
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package logger

import (
	"io"
	"os"
	"path/filepath"
	"time"

	rotatelogs "github.com/lestrrat-go/file-rotatelogs"
	"github.com/sirupsen/logrus"
)

var (
	Log *logrus.Logger
)

type Config struct {
	LogDir     string
	MaxSize    int
	MaxBackups int
	MaxAge     int
	Compress   bool
	Level      string
}

func Init(config Config) error {
	Log = logrus.New()

	if config.LogDir == "" {
		config.LogDir = "logs"
	}
	if config.MaxSize == 0 {
		config.MaxSize = 30
	}
	if config.MaxBackups == 0 {
		config.MaxBackups = 30
	}
	if config.MaxAge == 0 {
		config.MaxAge = 30
	}

	if err := os.MkdirAll(config.LogDir, 0755); err != nil {
		return err
	}

	level, err := logrus.ParseLevel(config.Level)
	if err != nil {
		level = logrus.InfoLevel
	}
	Log.SetLevel(level)

	Log.SetFormatter(&logrus.TextFormatter{
		FullTimestamp:   true,
		TimestampFormat: "2006-01-02 15:04:05.000",
	})

	infoLogPath := filepath.Join(config.LogDir, "project-info-%Y%m%d.log")
	infoLogger, err := rotatelogs.New(
		infoLogPath,
		rotatelogs.WithMaxAge(time.Duration(config.MaxAge)*24*time.Hour),
		rotatelogs.WithRotationTime(24*time.Hour),
	)
	if err != nil {
		return err
	}

	errorLogPath := filepath.Join(config.LogDir, "project-error-%Y%m%d.log")
	errorLogger, err := rotatelogs.New(
		errorLogPath,
		rotatelogs.WithMaxAge(time.Duration(config.MaxAge)*24*time.Hour),
		rotatelogs.WithRotationTime(24*time.Hour),
	)
	if err != nil {
		return err
	}

	multiWriter := io.MultiWriter(os.Stdout, infoLogger)
	Log.SetOutput(multiWriter)

	Log.AddHook(&ErrorHook{
		writer: errorLogger,
	})

	return nil
}

type ErrorHook struct {
	writer io.Writer
}

func (hook *ErrorHook) Fire(entry *logrus.Entry) error {
	line, err := entry.String()
	if err != nil {
		return err
	}
	_, err = hook.writer.Write([]byte(line))
	return err
}

func (hook *ErrorHook) Levels() []logrus.Level {
	return []logrus.Level{
		logrus.ErrorLevel,
		logrus.FatalLevel,
		logrus.PanicLevel,
	}
}
