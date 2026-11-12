#!/usr/bin/env bash
# Launch the Balagtas DEIHS Library Management System
set -e
cd "$(dirname "$0")"
exec mvn -q javafx:run
