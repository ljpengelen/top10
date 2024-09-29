#! /bin/bash

set -euo pipefail

export API_BASE_URL=https://top10-api.cofx.nl
export FRONT_END_BASE_URL=https://top10.cofx.nl
export GOOGLE_OAUTH2_CLIENT_ID=442497309318-72n7detrn1ne7bprs59fv8lsm6hsfivh.apps.googleusercontent.com
export GOOGLE_OAUTH2_REDIRECT_URI=https://top10.cofx.nl/oauth2/google
export MICROSOFT_OAUTH2_CLIENT_ID=1861cf5d-8a7f-4c90-88ec-b4bdbb408b61
export MICROSOFT_OAUTH2_REDIRECT_URI=https://top10.cofx.nl/oauth2/microsoft

rm -rf dist
git clone https://github.com/ljpengelen/top10.git -b deploy dist
cd dist && rm -rf * && cd ..

lein clean
lein garden once
lein release
lein hash-assets

cd dist && git add . && git commit -am "Deploy" && git push && cd ..
