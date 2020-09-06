FROM ubuntu:18.04

RUN apt-get update \
  && apt-get -y install \
    awscli \
    gnupg \
    leiningen \
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

COPY project.clj .

RUN lein install
