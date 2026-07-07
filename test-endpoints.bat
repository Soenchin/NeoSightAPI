@echo off
echo ===================================================
echo  NeoSightAPI endpoint verification (port 8345)
echo ===================================================
echo.

echo [1/10] GET /api/server
curl -s http://localhost:8345/api/server
echo.

echo [2/10] GET /api/server/mods
curl -s http://localhost:8345/api/server/mods
echo.

echo [3/10] GET /api/players
curl -s http://localhost:8345/api/players
echo.

echo [4/10] GET /api/worlds
curl -s http://localhost:8345/api/worlds
echo.

echo [5/10] GET /api/worlds/minecraft:overworld
curl -s http://localhost:8345/api/worlds/minecraft:overworld
echo.

echo [6/10] GET /api/worlds/minecraft:overworld/entities
curl -s http://localhost:8345/api/worlds/minecraft:overworld/entities
echo.

echo [7/10] POST /api/command (say hello)
curl -s -X POST http://localhost:8345/api/command -H "Content-Type: application/json" -d "{\"command\":\"say SightAPI is alive!\"}"
echo.

echo [8/10] GET /api/chat
curl -s http://localhost:8345/api/chat
echo.

echo [9/10] GET /api/metrics
curl -s http://localhost:8345/api/metrics
echo.

echo [10/10] GET /api/players/Steve/inventory
curl -s http://localhost:8345/api/players/Steve/inventory
echo.

echo ===================================================
echo  Done. 10 of 13 endpoints tested.
echo  Skipped: /players/{name} (no players online)
echo           /events SSE (requires long-lived connection)
echo           /worlds/{dim}/time (POST with time value)
echo           /regions CRUD (requires test data)
echo ===================================================
pause