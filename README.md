# NeoSightAPI

> [中文文档](README_CN.md)

Lightweight HTTP REST API mod for NeoForge 1.21.1 servers. Zero external dependencies — powered by JDK `HttpServer`.

Port: **8345**

## Quick Start

```bash
# Build
./gradlew build      # macOS / Linux
gradlew.bat build    # Windows (double-click works too)

# Deploy
# Copy build/libs/NeoSightAPI-*.jar to your server's mods/ folder
# Start the server — the API goes live on port 8345

# Test connectivity (double-click test-endpoints.bat on Windows)
# or: bash test-endpoints.sh
```

## API Endpoints

All responses are `application/json` unless noted otherwise.

### Server

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/server` | Server status: version, MOTD, TPS, MSPT, memory, difficulty, player/max count |

### Mods

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/server/mods` | List loaded mods with id, display name, version |

### Players

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/players` | Online player list: name, UUID, ping, dimension, gamemode |
| `GET` | `/api/players/{name}` | Player detail: position (x/y/z), health, food, saturation, XP, dimension, gamemode, ping |

### Worlds

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/worlds` | Dimension list: id, player count, day time, rain/thunder status |
| `GET` | `/api/worlds/{dim}` | Dimension detail: day/game time, rain/thunder, loaded chunks, players, seed, difficulty |
| `POST` | `/api/worlds/{dim}/time` | Set world time. Body: `{"time": "day"}` or `{"time": "6000"}`. Aliases: day, noon, sunset, night, midnight, sunrise |

### Command

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/command` | Execute a console command. Body: `{"command": "say Hello"}` |

### Events

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/events` | SSE stream. Events: player join/leave, death, chat, advancement. Heartbeat every 30s |

### Chat

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/chat?limit=20` | Recent chat history (max 100 entries). Fields: player, message, timestamp |

## Example Requests

```bash
# Server status
curl http://localhost:8345/api/server

# Player list
curl http://localhost:8345/api/players

# Player detail
curl http://localhost:8345/api/players/Steve

# Set time to day
curl -X POST http://localhost:8345/api/worlds/minecraft:overworld/time \
  -H "Content-Type: application/json" \
  -d '{"time": "day"}'

# Execute command
curl -X POST http://localhost:8345/api/command \
  -H "Content-Type: application/json" \
  -d '{"command": "give Steve minecraft:diamond 1"}'

# SSE event stream
curl -N http://localhost:8345/api/events

# Chat history (last 10)
curl http://localhost:8345/api/chat?limit=10
```

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.202
- Java 21+

## License

AGPL-3.0