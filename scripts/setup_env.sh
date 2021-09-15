#!/bin/bash

set -e

function setupEnvVar() {
  key=$1
  val=$2
  export $key=$val
}

echo "setup env vars"
params_json=$(aws ssm get-parameters-by-path --path / --region us-east-2 --with-decryption --no-paginate --query "Parameters[*].{Name:Name,Value:Value}")

IFS=$'\n'
for row in $(echo "$params_json" | jq -c '. | map([.Name, .Value])' | jq @sh)
do
  # Run the row through the shell interpreter to remove enclosing double-quotes
  stripped=$(echo $row | xargs echo)
  setupEnvVar $stripped

done