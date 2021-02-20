FROM ubuntu:focal

RUN apt-get update \
  && DEBIAN_FRONTEND=noninteractive apt-get install -y \
    default-jre \
    leiningen \
    npm \
    wget \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* /var/cache/apt/*

RUN wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | apt-key add - \
  && echo "deb https://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google-chrome.list \
  && apt-get update \
  && apt-get -y install google-chrome-stable \
  && rm /etc/apt/sources.list.d/google-chrome.list \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* /var/cache/apt/*

WORKDIR /app

COPY front-end/project.clj .
COPY front-end/shadow-cljs.edn .
COPY front-end/package* ./
COPY front-end/src/cljs/deps.cljs ./src/cljs/deps.cljs

RUN npm install

RUN npx shadow-cljs npm-deps app
