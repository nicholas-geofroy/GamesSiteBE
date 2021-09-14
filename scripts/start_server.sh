#!/bin/bash

set -e

echo "start scala server"
current_dir=$(pwd)
echo "$current_dir"

archive_dir="$current_dir/deployment-root/$DEPLOYMENT_GROUP_ID/$DEPLOYMENT_ID/deployment-archive"
echo "Archive Dir:"
echo "$archive_dir"

artifacts_dir="/var/cardsite"
echo "Cardsite Dir"
echo "$artifacts_dir"
pushd $artifacts_dir

play_secret=$(aws ssm get-parameters --region us-east-2 --names PLAY_HTTP_SECRET_KEY --with-decryption --query Parameters[0].Value --output text)
unzip -o "target/universal/cardsite-1.0-SNAPSHOT.zip"
chmod +x cardsite-1.0-SNAPSHOT/bin/cardsite

echo "Play Secret: $play_secret"
cardsite-1.0-SNAPSHOT/bin/cardsite -Dplay.http.secret.key=$play_secret &
GAMESITE_PID=$!

echo "Spawned gamesite process, PID: $GAMESITE_PID"

popd