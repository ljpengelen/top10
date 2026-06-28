(ns top10.config (:require-macros [top10.config :refer [version-from-env
                                                        api-base-url-from-env
                                                        front-end-base-url-from-env]]))

(def debug? ^boolean goog.DEBUG)

(declare version-from-env)
(def version (or (version-from-env) "unknown"))

(declare api-base-url-from-env)
(def api-base-url (or (api-base-url-from-env)  "http://localhost:8080"))

(declare front-end-base-url-from-env)
(def front-end-base-url (or (front-end-base-url-from-env) "http://localhost:9500"))

(def oauth2-authorize-endpoint (str api-base-url "/oauth/authorize"))

(def redirect-url (str front-end-base-url "/oauth"))
