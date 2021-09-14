#!/bin/bash

echo "Stop App"
server_pid=$(cat /var/cardsite/cardsite-1.0-SNAPSHOT/RUNNING_PID)
kill server_pid