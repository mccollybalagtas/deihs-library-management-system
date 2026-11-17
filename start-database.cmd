@echo off
setlocal
set "MARIADB_DIR=C:\Users\User\tools\mariadb-11.4.8-winx64"
set "DATA_DIR=C:\Users\User\tools\mariadb-data"

netstat -ano | findstr /R /C:":3307 .*LISTENING" >nul 2>&1
if not errorlevel 1 (
    echo [DEIHS] Database already running on port 3307.
    exit /b 0
)

echo [DEIHS] Starting MariaDB on port 3307...
start "DEIHS Library Database" /min "%MARIADB_DIR%\bin\mysqld.exe" --datadir="%DATA_DIR%" --port=3307 --console
timeout /t 4 /nobreak >nul
echo [DEIHS] Database started.
