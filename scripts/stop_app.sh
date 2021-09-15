#!/bin/bash

set -e
echo "Stop App"
server_pid_file="/var/cardsite/cardsite-1.0-SNAPSHOT/RUNNING_PID"

if [ -f "$server_pid_fil" ]; then
    echo "$server_pid_fil exists. killing and deleting"
    server_pid=$(cat $server_pid_file)
    kill server_pid
    rm $server_pid_file
else 
    echo "$server_pid_file does not exist."
fi
