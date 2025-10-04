#!/bin/sh

docker build -t daviestobialex/shovel:nexis .
docker tag daviestobialex/shovel:nexis daviestobialex/shovel:nexis
docker push daviestobialex/shovel:nexis
