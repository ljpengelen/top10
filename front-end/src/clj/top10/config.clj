(ns top10.config)

(defmacro api-base-url-from-env [] (System/getenv "API_BASE_URL"))

(defmacro front-end-base-url-from-env [] (System/getenv "FRONT_END_BASE_URL"))

(defmacro oauth2-client-id-from-env [] (System/getenv "OAUTH2_CLIENT_ID"))

(defmacro oauth2-redirect-uri-from-env [] (System/getenv "OAUTH2_REDIRECT_URI"))
