#!/bin/bash

set -e

function setupEnvVar() {
  key=$1
  val=$2
  export $key=$val
}

echo "setup env vars"
params_json=$(aws ssm get-parameters-by-path --path / --region us-east-2 --with-decryption --no-paginate --query "Parameters[*].{Name:Name,Value:Value}")
echo "Params JSON:"
echo "$params_json"

IFS=$'\n'
for encoded_row in $(echo "$params_json" | jq -r '.[] | @base64'); do
  row=$(base64 --decode <<< $encoded_row)
  echo "row: $row"
  env_var_name=$(jq -r '.Name' <<< $row)
  env_var_val=$(jq -r '.Value' <<< $row)
  echo "set $env_var_name=$env_var_val"
  export $env_var_name=$env_var_val
done