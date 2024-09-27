#! /bin/sh

if [ -z "$1" ] || [ -z "$2" ]; then
    echo "Usage: $0 USERNAME HOST"
    exit;
fi

VERSION="v0.1.$(git rev-list HEAD --count)"
USERNAME=$1
HOST=$2
IMAGE="dokku/top10-api:$VERSION"
DESTINATION="$USERNAME@$HOST"

docker build \
    -t $IMAGE \
    --build-arg VERSION="$VERSION" \
    --platform linux/amd64 \
    -f dockerfiles/deploy/Dockerfile \
    .

docker save $IMAGE | \
    bzip2 | \
    ssh $DESTINATION "bunzip2 | docker load"

ssh $DESTINATION "dokku git:from-image top10-api $IMAGE"
