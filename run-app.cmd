@echo off
setlocal
set "JAVA_HOME=C:\Program Files\Java\jdk-26.0.1"
set "PATH=C:\Users\User\tools\apache-maven-3.9.16\bin;%PATH%"

call "%~dp0start-database.cmd"
cd /d "%~dp0"

echo [DEIHS] Launching the library system...
call mvn javafx:run
if errorlevel 1 (
    echo.
    echo [DEIHS] The app exited with an error. See the message above.
    pause
)
