#!/bin/bash

echo "Install Dependencies"

curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

 sudo apt-get update; sudo apt-get install -y java-11-amazon-corretto-jdk

 java -version