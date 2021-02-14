(ns top10.config)

(defmacro api-base-url-from-env [] (System/getenv "API_BASE_URL"))

(defmacro front-end-base-url-from-env [] (System/getenv "FRONT_END_BASE_URL"))
