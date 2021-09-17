#!/bin/bash

# only needed to be ran once
sudo snap install core; sudo snap refresh core
sudo snap install --classic certbot
sudo certbot certonly --standalone --preferred-challenges http -d api.geofroy.ca