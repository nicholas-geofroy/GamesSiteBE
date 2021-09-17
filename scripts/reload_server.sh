#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
echo "reload server with secret keys"
source "$SCRIPT_DIR/stop_app.sh"
source "$SCRIPT_DIR/update_java_keystore.sh"
source "$SCRIPT_DIR/start_server.sh"