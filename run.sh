#!/bin/bash

docker run -p 8080:8080 --rm -it $(docker build --build-arg BRANCH=master --no-cache -q .)
