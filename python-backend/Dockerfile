# Python后端 Dockerfile
# @author <a href="https://codefather.cn">编程导航学习圈</a>

FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y \
    gcc \
    default-libmysqlclient-dev \
    pkg-config \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .

RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 9090

CMD ["python", "-m", "app.main"]
