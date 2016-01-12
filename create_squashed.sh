
export IMAGE=zmon-metric-cache
docker build -t registry-write.opensource.zalan.do/stups/$IMAGE:$1-unsquashed .
docker save registry-write.opensource.zalan.do/stups/$IMAGE:$1-unsquashed | docker-squash -verbose -t registry-write.opensource.zalan.do/stups/$IMAGE:$1 | docker load
docker push registry-write.opensource.stups.zalan.do/stups/$IMAGE:$1
