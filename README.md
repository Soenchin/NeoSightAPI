# NeoSightAPI

> [中文文档](README_CN.md)

Lightweight HTTP REST API mod for NeoForge 1.21.x servers. Zero external dependencies — powered by JDK `HttpServer`.

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
| `GET` | `/api/players/{name}/inventory` | Player inventory: all non-empty slots with item ID, display name, count |

### Worlds

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/worlds` | Dimension list: id, player count, day time, rain/thunder status |
| `GET` | `/api/worlds/{dim}` | Dimension detail: day/game time, rain/thunder, loaded chunks, players, seed, difficulty |
| `GET` | `/api/worlds/{dim}/entities` | Entity list for dimension. Optional query params: `x`, `z`, `radius` (default 100) for range filtering |
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

### Metrics *(v1.1.0+)*

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/metrics` | Historical TPS/MSPT/player count metrics. Sampled every 10s, retains last 2 hours (720 entries). Optional query param: `limit` |

### Regions *(v1.0.3+)*

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/regions` | Create a region. Body: `{"name":"...", "owner":"...", "dimension":"minecraft:overworld", "x1":0, "z1":0, "x2":50, "z2":50, "label":"..."}` |
| `GET` | `/api/regions` | List all regions |
| `GET` | `/api/regions?x=X&z=Z&dim=DIM` | Find regions containing the given XZ coordinate |
| `DELETE` | `/api/regions/{id}` | Delete a region (owner-verified). Body: `{"owner":"..."}` |

Region data is persisted to `config/neosight/regions.json`.

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

# Player inventory
curl http://localhost:8345/api/players/Steve/inventory

# All entities in dimension
curl http://localhost:8345/api/worlds/minecraft:overworld/entities

# Entities in range (centered at 100,64, radius 50)
curl "http://localhost:8345/api/worlds/minecraft:overworld/entities?x=100&z=64&radius=50"

# Historical metrics (last 60 samples)
curl http://localhost:8345/api/metrics?limit=60

# Create a region
curl -X POST http://localhost:8345/api/regions \
  -H "Content-Type: application/json" \
  -d '{"name":"MyBase","owner":"Steve","dimension":"minecraft:overworld","x1":100,"z1":50,"x2":150,"z2":100,"label":"Main base"}'

# Find regions at coordinates
curl "http://localhost:8345/api/regions?x=120&z=75&dim=minecraft:overworld"

# List all regions
curl http://localhost:8345/api/regions
```

## Requirements

- Minecraft 1.21.1+ (tested on 1.21.1 and 1.21.4)
- NeoForge 21.1+ (tested on 21.1.202 and 21.4.157)
- Java 21+

## License

AGPL-3.0