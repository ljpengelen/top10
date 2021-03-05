(ns top10.config)

(defmacro api-base-url-from-env [] (System/getenv "API_BASE_URL"))

(defmacro front-end-base-url-from-env [] (System/getenv "FRONT_END_BASE_URL"))

(defmacro google-oauth2-client-id-from-env [] (System/getenv "GOOGLE_OAUTH2_CLIENT_ID"))

(defmacro google-oauth2-redirect-uri-from-env [] (System/getenv "GOOGLE_OAUTH2_REDIRECT_URI"))

(defmacro microsoft-oauth2-client-id-from-env [] (System/getenv "MICROSOFT_OAUTH2_CLIENT_ID"))

(defmacro microsoft-oauth2-redirect-uri-from-env [] (System/getenv "MICROSOFT_OAUTH2_REDIRECT_URI"))
