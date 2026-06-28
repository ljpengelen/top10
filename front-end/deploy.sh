#! /bin/bash

set -euo pipefail

export GIT_COMMIT_HASH="$(git rev-parse --short HEAD)"
export GIT_COMMIT_COUNT="$(git rev-list HEAD --count)"
export VERSION="v0.1.$GIT_COMMIT_COUNT.$GIT_COMMIT_HASH"

export API_BASE_URL=https://top10-api.cofx.nl
export FRONT_END_BASE_URL=https://top10.cofx.nl

rm -rf dist
git clone https://github.com/ljpengelen/top10.git -b deploy dist
cd dist && rm -rf * && cd ..

npm install 

lein clean
lein garden once
lein release
lein hash-assets

cd dist && git add . && git commit -am "Deploy" && git push && cd ..
