// Package config 配置管理
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package config

import (
	"log"
	"os"
	"strconv"
	"strings"

	"github.com/spf13/viper"
)

type Config struct {
	Server     ServerConfig     `mapstructure:"server"`
	Database   DatabaseConfig   `mapstructure:"database"`
	Redis      RedisConfig      `mapstructure:"redis"`
	Session    SessionConfig    `mapstructure:"session"`
	OpenRouter OpenRouterConfig `mapstructure:"openrouter"`
	RabbitMQ   RabbitMQConfig   `mapstructure:"rabbitmq"`
	Log        LogConfig        `mapstructure:"log"`
	Tencent    struct {
		Cos TencentCosConfig `mapstructure:"cos"`
	} `mapstructure:"tencent"`
}

type TencentCosConfig struct {
	AccessKey string `mapstructure:"accessKey"`
	SecretKey string `mapstructure:"secretKey"`
	Region    string `mapstructure:"region"`
	Bucket    string `mapstructure:"bucket"`
	Host      string `mapstructure:"host"`
}

type ServerConfig struct {
	Port int    `mapstructure:"port"`
	Mode string `mapstructure:"mode"`
}

type DatabaseConfig struct {
	Host         string `mapstructure:"host"`
	Port         int    `mapstructure:"port"`
	User         string `mapstructure:"user"`
	Password     string `mapstructure:"password"`
	DBName       string `mapstructure:"dbname"`
	Charset      string `mapstructure:"charset"`
	ParseTime    bool   `mapstructure:"parseTime"`
	Loc          string `mapstructure:"loc"`
	MaxIdleConns int    `mapstructure:"maxIdleConns"`
	MaxOpenConns int    `mapstructure:"maxOpenConns"`
}

type RedisConfig struct {
	Addr     string `mapstructure:"addr"`
	Password string `mapstructure:"password"`
	DB       int    `mapstructure:"db"`
	PoolSize int    `mapstructure:"poolSize"`
}

type SessionConfig struct {
	Secret     string `mapstructure:"secret"`
	MaxAge     int    `mapstructure:"maxAge"`
	CookieName string `mapstructure:"cookieName"`
}

type OpenRouterConfig struct {
	APIKey  string `mapstructure:"apiKey"`
	BaseURL string `mapstructure:"baseURL"`
}

type RabbitMQConfig struct {
	Host     string `mapstructure:"host"`
	Port     int    `mapstructure:"port"`
	Username string `mapstructure:"username"`
	Password string `mapstructure:"password"`
}

type LogConfig struct {
	Dir      string `mapstructure:"dir"`
	Level    string `mapstructure:"level"`
	MaxAge   int    `mapstructure:"maxAge"`
	Compress bool   `mapstructure:"compress"`
}

var AppConfig *Config

func LoadConfig() error {
	viper.SetConfigName("config")
	viper.SetConfigType("yaml")

	viper.AddConfigPath("./config")
	viper.AddConfigPath("../config")
	viper.AddConfigPath("../../config")
	viper.AddConfigPath("./go-backend/config")
	viper.AddConfigPath("../go-backend/config")
	viper.AddConfigPath("../../go-backend/config")
	viper.AddConfigPath("./ai-test/go-backend/config")

	if err := viper.ReadInConfig(); err != nil {
		return err
	}

	viper.SetEnvKeyReplacer(strings.NewReplacer(".", "_"))
	viper.AutomaticEnv()

	AppConfig = &Config{}
	if err := viper.Unmarshal(AppConfig); err != nil {
		return err
	}

	overrideFromEnv()

	log.Println("配置文件加载成功")
	return nil
}

func overrideFromEnv() {
	if v := os.Getenv("SERVER_MODE"); v != "" {
		AppConfig.Server.Mode = v
	}
	if v := os.Getenv("DATABASE_HOST"); v != "" {
		AppConfig.Database.Host = v
	}
	if v := os.Getenv("DATABASE_PORT"); v != "" {
		if port, err := strconv.Atoi(v); err == nil {
			AppConfig.Database.Port = port
		}
	}
	if v := os.Getenv("DATABASE_USER"); v != "" {
		AppConfig.Database.User = v
	}
	if v := os.Getenv("DATABASE_PASSWORD"); v != "" {
		AppConfig.Database.Password = v
	}
	if v := os.Getenv("DATABASE_DBNAME"); v != "" {
		AppConfig.Database.DBName = v
	}
	if v := os.Getenv("REDIS_ADDR"); v != "" {
		AppConfig.Redis.Addr = v
	}
	if v := os.Getenv("REDIS_PASSWORD"); v != "" {
		AppConfig.Redis.Password = v
	}
	if v := os.Getenv("RABBITMQ_HOST"); v != "" {
		AppConfig.RabbitMQ.Host = v
	}
	if v := os.Getenv("RABBITMQ_PORT"); v != "" {
		if port, err := strconv.Atoi(v); err == nil {
			AppConfig.RabbitMQ.Port = port
		}
	}
	if v := os.Getenv("RABBITMQ_USERNAME"); v != "" {
		AppConfig.RabbitMQ.Username = v
	}
	if v := os.Getenv("RABBITMQ_PASSWORD"); v != "" {
		AppConfig.RabbitMQ.Password = v
	}
	if v := os.Getenv("OPENROUTER_APIKEY"); v != "" {
		AppConfig.OpenRouter.APIKey = v
	}
	if v := os.Getenv("OPENROUTER_BASEURL"); v != "" {
		AppConfig.OpenRouter.BaseURL = v
	}
}
