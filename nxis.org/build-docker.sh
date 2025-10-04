#!/bin/sh

docker build -t daviestobialex/shovel:nexis-react .
docker tag daviestobialex/shovel:nexis-react daviestobialex/shovel:nexis-react
docker push daviestobialex/shovel:nexis-react
