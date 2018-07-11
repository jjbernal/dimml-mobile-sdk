#!/bin/sh
bash gradlew :sdk:build # build new aar
cp sdk/build/outputs/aar/* app/libs/ # copy all flavors to app/libs/
