# NeoSightAPI

> [English](README.md)

NeoForge 1.21.1 服务端专用 HTTP REST API 模组。零外部依赖 — 基于 JDK `HttpServer` 实现。

端口：**8345**

## 快速开始

```bash
# 构建
./gradlew build      # macOS / Linux
gradlew.bat build    # Windows（双击即可）

# 部署
# 将 build/libs/NeoSightAPI-*.jar 复制到服务器的 mods/ 目录
# 启动服务器 — API 自动运行在 8345 端口

# 测试连通性（Windows 双击 test-endpoints.bat）
# 或: bash test-endpoints.sh
```

## API 端点

所有响应均为 `application/json`，另有标注者除外。

### 服务器

| 方法 | 路径 | 说明 |
|--------|------|------|
| `GET` | `/api/server` | 服务器状态：版本、MOTD、TPS、MSPT、内存、难度、在线/最大人数 |

### 模组

| 方法 | 路径 | 说明 |
|--------|------|------|
| `GET` | `/api/server/mods` | 已加载模组列表，含 id、显示名、版本 |

### 玩家

| 方法 | 路径 | 说明 |
|--------|------|------|
| `GET` | `/api/players` | 在线玩家列表：名字、UUID、延迟、维度、游戏模式 |
| `GET` | `/api/players/{name}` | 玩家详情：坐标 (x/y/z)、生命、饱腹度、饱和度、经验、维度、游戏模式、延迟 |

### 世界

| 方法 | 路径 | 说明 |
|--------|------|------|
| `GET` | `/api/worlds` | 维度列表：ID、玩家人数、白天时间、下雨/打雷状态 |
| `GET` | `/api/worlds/{dim}` | 维度详情：白天/游戏时间、下雨/打雷、已加载区块、玩家、种子、难度 |
| `POST` | `/api/worlds/{dim}/time` | 设置世界时间。Body：`{"time": "day"}` 或 `{"time": "6000"}`。时间别名：day / noon / sunset / night / midnight / sunrise |

### 命令

| 方法 | 路径 | 说明 |
|--------|------|------|
| `POST` | `/api/command` | 执行控制台命令。Body：`{"command": "say Hello"}` |

### 事件

| 方法 | 路径 | 说明 |
|--------|------|------|
| `GET` | `/api/events` | SSE 事件流。事件类型：玩家加入/离开、死亡、聊天、成就。每 30 秒心跳 |

### 聊天

| 方法 | 路径 | 说明 |
|--------|------|------|
| `GET` | `/api/chat?limit=20` | 最近聊天记录（最多 100 条）。字段：player、message、timestamp |

## 请求示例

```bash
# 服务器状态
curl http://localhost:8345/api/server

# 在线玩家
curl http://localhost:8345/api/players

# 玩家详情
curl http://localhost:8345/api/players/Steve

# 设为白天
curl -X POST http://localhost:8345/api/worlds/minecraft:overworld/time \
  -H "Content-Type: application/json" \
  -d '{"time": "day"}'

# 执行命令
curl -X POST http://localhost:8345/api/command \
  -H "Content-Type: application/json" \
  -d '{"command": "give Steve minecraft:diamond 1"}'

# SSE 事件流
curl -N http://localhost:8345/api/events

# 聊天记录（最近 10 条）
curl http://localhost:8345/api/chat?limit=10
```

## 运行要求

- Minecraft 1.21.1
- NeoForge 21.1.202
- Java 21+

## 许可证

AGPL-3.0