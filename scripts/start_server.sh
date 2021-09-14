#!/bin/bash

echo "start scala server"
current_dir=$(pwd)
echo "$current_dir"
files=$(ls -l)
echo "Files:"
echo "$files"
play_secret=$(aws ssm get-parameters --region us-east-2 --names PLAY_HTTP_SECRET_KEY --with-decryption --query Parameters[0].Value --output text)
unzip target/universal/cardsite-1.0-SNAPSHOT.zip
chmod +x cardsite-1.0-SNAPSHOT/bin/cardsite
cardsite-1.0-SNAPSHOT/bin/cardsite -Dplay.http.secret.key=$play_secret