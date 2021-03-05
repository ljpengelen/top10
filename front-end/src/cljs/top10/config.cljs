(ns top10.config (:require-macros [top10.config :refer [api-base-url-from-env
                                                        front-end-base-url-from-env
                                                        google-oauth2-client-id-from-env
                                                        google-oauth2-redirect-uri-from-env
                                                        microsoft-oauth2-client-id-from-env
                                                        microsoft-oauth2-redirect-uri-from-env]]))

(def debug? ^boolean goog.DEBUG)

(declare api-base-url-from-env)
(def api-base-url (or (api-base-url-from-env)  "http://localhost:8080"))

(declare front-end-base-url-from-env)
(def front-end-base-url (or (front-end-base-url-from-env) "http://localhost:9500"))

(declare google-oauth2-client-id-from-env)
(declare google-oauth2-redirect-uri-from-env)
(declare microsoft-oauth2-client-id-from-env)
(declare microsoft-oauth2-redirect-uri-from-env)

(def oauth2
  {:google {:endpoint "https://accounts.google.com/o/oauth2/v2/auth"
            :client-id (or (google-oauth2-client-id-from-env) "442497309318-72n7detrn1ne7bprs59fv8lsm6hsfivh.apps.googleusercontent.com")
            :redirect-uri (or (google-oauth2-redirect-uri-from-env) "http://localhost:9500/oauth2/google")}
   :microsoft {:endpoint "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
               :client-id (or (microsoft-oauth2-client-id-from-env) "1861cf5d-8a7f-4c90-88ec-b4bdbb408b61")
               :redirect-uri (or (microsoft-oauth2-redirect-uri-from-env) "http://localhost:9500/oauth2/microsoft")}})


(def google-oauth2-redirect-uri )

(def csrf-token-header "x-csrf-token")
