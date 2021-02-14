(ns top10.config (:require-macros [top10.config :refer [api-base-url-from-env front-end-base-url-from-env]]))

(def debug? ^boolean goog.DEBUG)

(def api-base-url (or (api-base-url-from-env)  "http://localhost:8080"))

(def front-end-base-url (or (front-end-base-url-from-env) "http://localhost:9500"))

(def csrf-token-header "x-csrf-token")
