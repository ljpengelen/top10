FROM ubuntu:focal

RUN apt-get update \
  && DEBIAN_FRONTEND=noninteractive apt-get install -y \
    default-jre \
    leiningen \
    npm \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* /var/cache/apt/*

WORKDIR /app

COPY front-end/project.clj .
COPY front-end/shadow-cljs.edn .
COPY front-end/package* ./

RUN npm install

RUN npx shadow-cljs npm-deps app
