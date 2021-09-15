#!/bin/bash

set -e
echo "Stop App"
server_pid_file="/var/cardsite/cardsite-1.0-SNAPSHOT/RUNNING_PID"
server_pid=$(cat $server_pid_file)
kill server_pid
rm $server_pid_file