#!/bin/bash

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
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

source "$SCRIPT_DIR/setup_env.sh"

echo "play http secret key $PLAY_HTTP_SECRET_KEY"

unzip -o -q "target/universal/cardsite-1.0-SNAPSHOT.zip"
chmod +x cardsite-1.0-SNAPSHOT/bin/cardsite

nohup cardsite-1.0-SNAPSHOT/bin/cardsite \
  -Dplay.http.secret.key=$PLAY_HTTP_SECRET_KEY \
  -Dconfig.resource=prod.conf \
  > server.log 2>&1 &
GAMESITE_PID=$!

echo "Spawned gamesite process, PID: $GAMESITE_PID"

popd