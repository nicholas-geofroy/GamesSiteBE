#!/bin/bash

source /var/cardsite/scripts/setup_env.sh
# add .pem file to pkcs 12 archive
sudo openssl pkcs12 -export \
	 -in /etc/letsencrypt/live/api.geofroy.ca/cert.pem \
	 -inkey /etc/letsencrypt/live/api.geofroy.ca/privkey.pem \
	 -out /tmp/api.geofroy.ca.p12 \
	 -name api.geofroy.ca \
	 -CAfile /etc/letsencrypt/live/api.geofroy.ca/fullchain.pem \
	 -caname "Let's Encrypt Authority X3" \
	 -password pass:$PKCS12_STORE_PASSWORD

# import certificates to .keystore file
sudo keytool -importkeystore \
	-deststorepass $JAVA_KEYSTORE_PASS \
	-destkeypass $JAVA_KEYSTORE_PASS \
	-deststoretype pkcs12 \
	-srckeystore /tmp/api.geofroy.ca.p12 \
	-srcstoretype PKCS12 \
	-srcstorepass $PKCS12_STORE_PASSWORD \
	-destkeystore /tmp/api.geofroy.ca.keystore \
	-alias api.geofroy.ca

sudo mkdir -p /var/cardsite/ssl/
sudo cp /tmp/api.geofroy.ca.keystore /var/cardsite/ssl/api.geofroy.ca.keystore