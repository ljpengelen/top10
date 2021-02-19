(ns top10.config (:require-macros [top10.config :refer [api-base-url-from-env
                                                        front-end-base-url-from-env
                                                        oauth2-client-id-from-env
                                                        oauth2-redirect-uri-from-env]]))

(def debug? ^boolean goog.DEBUG)

(declare api-base-url-from-env)
(def api-base-url (or (api-base-url-from-env)  "http://localhost:8080"))

(declare front-end-base-url-from-env)
(def front-end-base-url (or (front-end-base-url-from-env) "http://localhost:9500"))

(declare oauth2-client-id-from-env)
(def oauth2-client-id (or (oauth2-client-id-from-env) "442497309318-72n7detrn1ne7bprs59fv8lsm6hsfivh.apps.googleusercontent.com"))

(declare oauth2-redirect-uri-from-env)
(def oauth2-redirect-uri (or (oauth2-redirect-uri-from-env) "http://localhost:9500/oauth2"))

(def csrf-token-header "x-csrf-token")
