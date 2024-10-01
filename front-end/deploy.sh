#! /bin/bash

set -euo pipefail

GIT_COMMIT_HASH="$(git rev-parse --short HEAD)"
GIT_COMMIT_COUNT="$(git rev-list HEAD --count)"
VERSION="v0.1.$GIT_COMMIT_COUNT.$GIT_COMMIT_HASH"

API_BASE_URL=https://top10-api.cofx.nl
FRONT_END_BASE_URL=https://top10.cofx.nl
GOOGLE_OAUTH2_CLIENT_ID=442497309318-72n7detrn1ne7bprs59fv8lsm6hsfivh.apps.googleusercontent.com
GOOGLE_OAUTH2_REDIRECT_URI=https://top10.cofx.nl/oauth2/google
MICROSOFT_OAUTH2_CLIENT_ID=1861cf5d-8a7f-4c90-88ec-b4bdbb408b61
MICROSOFT_OAUTH2_REDIRECT_URI=https://top10.cofx.nl/oauth2/microsoft

rm -rf dist
git clone https://github.com/ljpengelen/top10.git -b deploy dist
cd dist && rm -rf * && cd ..

npm install -legacy-peer-deps

lein clean
lein garden once
lein release
lein hash-assets

cd dist && git add . && git commit -am "Deploy" && git push && cd ..
