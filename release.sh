#!/bin/bash -xe
rm -rf dist/
mkdir dist

mvn clean package
cp target/gocd-build-badge-notifier*.jar dist/
