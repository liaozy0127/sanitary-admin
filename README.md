# 卫浴管理系统

基于 Spring Boot 3 + Vue3 的卫浴加工厂商后台管理系统。

## 技术栈

- **后端**: Spring Boot 3 + MyBatis-Plus + Spring Security + JWT
- **前端**: Vue3 + Vite + Element Plus + Pinia
- **数据库**: MySQL 8.0

## 模块

- `backend/` - Spring Boot 后端服务
- `frontend/` - Vue3 前端应用

## 快速开始

详见各子模块 README。

## 快速启动

### Mac / Linux
```bash
bash scripts/start.sh
```

### Windows
双击运行 `scripts/start.bat`

### 手动 Docker Compose
```bash
docker compose up -d
```

### 要求
- Docker Desktop（Mac/Windows/Linux 均支持）
- 端口：80（前端）、8080（后端）、3307（MySQL）、6379（Redis）
