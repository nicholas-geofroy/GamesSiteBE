#!/bin/bash

set -e

echo "setup env vars"
params_json=$(aws ssm get-parameters-by-path --path / --region us-east-2 --with-decryption --no-paginate --query "Parameters[*].{Name:Name,Value:Value}")

IFS=$'\n'
for encoded_row in $(echo "$params_json" | jq -r '.[] | @base64'); do
  row=$(base64 --decode <<< $encoded_row)
  env_var_name=$(jq -r '.Name' <<< $row)
  env_var_val=$(jq -r '.Value' <<< $row)
  export $env_var_name="$env_var_val"
done