// Package config 配置管理
// @author <a href="https://codefather.cn">编程导航学习圈</a>
package config

import (
	"log"

	"github.com/spf13/viper"
)

type Config struct {
	Server     ServerConfig     `mapstructure:"server"`
	Database   DatabaseConfig   `mapstructure:"database"`
	Redis      RedisConfig      `mapstructure:"redis"`
	Session    SessionConfig    `mapstructure:"session"`
	OpenRouter OpenRouterConfig `mapstructure:"openrouter"`
	Log        LogConfig        `mapstructure:"log"`
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

	AppConfig = &Config{}
	if err := viper.Unmarshal(AppConfig); err != nil {
		return err
	}

	log.Println("配置文件加载成功")
	return nil
}
