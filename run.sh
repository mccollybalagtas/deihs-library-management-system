#!/usr/bin/env bash
# Launch the Balagtas DEIHS Library Management System
#
# Usage: ./run.sh   (from any directory; the script finds the project itself)
# Needs: JDK and Maven on PATH, plus the MySQL server on 127.0.0.1:3307.
# The app opens a desktop window, so run it from a graphical session.

# Stop at the first failing command. Without this, a failed `cd` would leave
# Maven running in whatever directory the caller happened to be in.
set -e

# Move into the folder that contains this script, so `mvn` finds pom.xml even
# when run.sh is started from somewhere else (for example a desktop shortcut).
cd "$(dirname "$0")"

# Hand the process over to Maven and run the JavaFX goal. `exec` replaces this
# shell, so Ctrl+C and the app's exit code reach the caller directly.
# -q hides Maven's own progress noise and leaves only the app's output.
exec mvn -q javafx:run
