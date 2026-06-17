# 古代沙船（方艄）稳性仿真与装载优化系统

## 项目概述

本系统为某船舶史团队研究宋代沙船复原而开发，实现沙船稳性仿真、装载优化、实时监控与三维可视化等功能。

## 技术栈

### 后端
- Java 17 + Spring Boot 3.2.0
- PostgreSQL 数据库
- MQTT 协议（传感器数据接收）
- WebSocket（实时数据推送）
- Google OR-Tools（整数规划求解）
- Apache Commons Math3（数值计算）

### 前端
- Vue 3 + Vite
- Three.js（三维可视化）
- GLSL Shaders（动态水面）
- Chart.js（图表展示）
- Element Plus（UI组件库）

### 模拟器
- Python 3.x
- paho-mqtt（MQTT客户端）

## 项目结构

```
AI_solo_coder_task_A_140/
├── backend/                    # Java后端
│   ├── src/main/java/...
│   ├── src/main/resources/
│   └── pom.xml
├── frontend/                   # Vue前端
│   ├── src/
│   ├── package.json
│   └── vite.config.js
├── database/                   # 数据库脚本
│   └── init.sql
└── simulator/                  # 传感器模拟器
    ├── sand_ship_simulator.py
    └── requirements.txt
```

## 快速开始

### 1. 数据库初始化

```bash
# 连接PostgreSQL并执行初始化脚本
psql -U postgres -f database/init.sql
```

### 2. 启动MQTT服务器

需要安装Mosquitto或其他MQTT服务器：

```bash
# Windows (使用Chocolatey)
choco install mosquitto

# 或使用Docker
docker run -d -p 1883:1883 eclipse-mosquitto
```

### 3. 启动后端服务

```bash
cd backend

# 修改application.yml中的数据库连接配置
# 确保PostgreSQL和MQTT服务器已启动

# 编译并运行
mvn clean compile
mvn spring-boot:run
```

后端服务将在 `http://localhost:8080/api` 启动

API文档地址：`http://localhost:8080/api/swagger-ui.html`

### 4. 启动前端服务

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端服务将在 `http://localhost:3000` 启动

### 5. 启动传感器模拟器

```bash
cd simulator

# 安装依赖
pip install -r requirements.txt

# 启动模拟器（默认2艘船，每分钟上报一次）
python sand_ship_simulator.py

# 或指定参数
python sand_ship_simulator.py --broker localhost --port 1883 --interval 60 --ships 2
```

## 核心功能

### 1. 稳性仿真模型
- 基于船舶静力学原理，计算GM（稳心高度）值
- 计算复原力矩和横摇周期
- 生成0°-60°稳心曲线
- 实时显示船舶稳性状态

### 2. 装载优化
- 基于整数规划算法
- 优化散货（粮、盐）的舱位分配
- 最大化有效载重
- 约束条件：重量、容积、纵倾、GM值

### 3. 告警系统
- GM值低于0.3m触发预警
- 横摇角超过15°触发预警
- 舱底水位过高触发预警
- 通过WebSocket实时推送告警

### 4. 三维可视化
- Three.js绘制宋代沙船三维模型
- GLSL Shader实现动态水面波浪
- 色块标注不同类型货物
- 支持多视角切换（透视、正前、侧视、俯视）
- 支持线框/实体模式切换

## 主要API接口

### 船舶管理
- `GET /api/ships` - 获取船舶列表
- `GET /api/ships/{id}` - 获取船舶详情

### 传感器数据
- `GET /api/sensor-data/ship/{shipId}` - 查询传感器历史数据
- `POST /api/sensor-data` - 手动录入传感器数据

### 稳性计算
- `GET /api/stability/ship/{shipId}/latest` - 获取最新稳性结果
- `GET /api/stability/ship/{shipId}/history` - 获取稳性历史数据
- `POST /api/stability/calculate/{shipId}` - 手动触发稳性计算

### 装载优化
- `POST /api/loading/optimize` - 执行装载优化
- `GET /api/loading/optimizations/ship/{shipId}` - 获取优化历史

### 告警管理
- `GET /api/alarms/ship/{shipId}` - 获取船舶告警
- `PUT /api/alarms/{id}/acknowledge` - 确认告警

## 模拟船舶ID

数据库初始化时已预置2艘宋代沙船：

| 船名 | ID |
|------|-----|
| 元丰号 | 550e8400-e29b-41d4-a716-446655440000 |
| 元祐号 | 550e8400-e29b-41d4-a716-446655440001 |

## 稳性计算原理

### GM值计算
```
GM = KM - KG
其中:
- KM: 横稳心距基线高度
- KG: 重心距基线高度
```

### 复原力矩
```
MR = Δ × g × GZ
其中:
- Δ: 排水量
- g: 重力加速度
- GZ: 复原力臂 = GM × sin(θ)
```

### 横摇周期
```
Tφ = 2π × k / √(g × GM)
其中:
- k: 横摇惯性半径 (约为型宽的0.35倍)
- GM: 横稳心高度
```

## 装载优化模型

### 决策变量
- x[i][0]: 第i舱装载的粮食单位数
- x[i][1]: 第i舱装载的食盐单位数

### 目标函数
最大化总载重：Σ(x[i][0] × W_grain + x[i][1] × W_salt)

### 约束条件
1. 货舱重量限制
2. 货舱容积限制
3. 总重量不超过载重吨
4. 纵倾平衡约束
5. GM值不低于安全阈值

## 告警阈值

| 告警类型 | 阈值 | 级别 |
|---------|------|------|
| GM过低 | < 0.3m | WARNING |
| 横摇过大 | > 15° | WARNING |
| 舱底水过高 | > 0.5m | WARNING |

## 配置说明

### 后端配置 (application.yml)
```yaml
stability:
  min-gm-threshold: 0.3      # GM安全阈值
  max-roll-angle: 15.0       # 横摇角安全阈值
  max-bilge-water: 0.5       # 舱底水安全阈值

loading:
  default-min-gm: 0.3        # 优化默认GM要求
  optimization-timeout: 30000 # 优化超时时间
```

## 开发说明

### 后端代码结构
- `entity/` - JPA实体类
- `repository/` - Spring Data JPA接口
- `service/` - 业务逻辑服务
- `controller/` - REST API控制器
- `config/` - 配置类
- `dto/` - 数据传输对象
- `websocket/` - WebSocket处理器

### 前端代码结构
- `views/` - 页面视图
- `components/` - 可复用组件
- `api/` - API请求封装
- `composables/` - Vue组合式函数
- `router/` - 路由配置
- `styles/` - 全局样式

## 系统要求

- JDK 17+
- Node.js 18+
- PostgreSQL 12+
- MQTT Broker (Mosquitto等)
- Python 3.8+ (模拟器)

## 许可证

本项目为船舶史研究用途开发。
