@echo off
echo ===================================================
echo  NeoSightAPI endpoint verification (port 8345)
echo ===================================================
echo.

echo [1/7] GET /api/server
curl -s http://localhost:8345/api/server
echo.

echo [2/7] GET /api/server/mods
curl -s http://localhost:8345/api/server/mods
echo.

echo [3/7] GET /api/players
curl -s http://localhost:8345/api/players
echo.

echo [4/7] GET /api/worlds
curl -s http://localhost:8345/api/worlds
echo.

echo [5/7] GET /api/worlds/minecraft:overworld
curl -s http://localhost:8345/api/worlds/minecraft:overworld
echo.

echo [6/7] POST /api/command (say hello)
curl -s -X POST http://localhost:8345/api/command -H "Content-Type: application/json" -d "{\"command\":\"say SightAPI is alive!\"}"
echo.

echo [7/7] GET /api/chat
curl -s http://localhost:8345/api/chat
echo.

echo ===================================================
echo  Done. 7 of 10 endpoints tested.
echo  Skipped: /players/{name} (no players online)
echo           /events SSE (requires long-lived connection)
echo           /worlds/{dim}/time (POST with time value)
echo ===================================================
pause