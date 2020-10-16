(ns top10.rest
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<!]]
            [cljs-http.client :as http]
            [reagent.core :as r]
            [top10.config :refer [base-url]]))

(def access-token (r/atom nil))

(defn get-quiz [quiz-id cb]
  (go
    (let
     [quiz-response (<! (http/get (str base-url "/private/quiz/" quiz-id) {:with-credentials? false :oauth-token @access-token}))
      quiz (:body quiz-response)]
      (js/console.log "Yo quiz")
      (cb quiz))))

(defn get-quizzes [cb]
  (go
    (let
     [quizzes-response (<! (http/get (str base-url "/private/quiz") {:with-credentials? false :oauth-token @access-token}))
      quizzes (:body quizzes-response)]
      (js/console.log "Yo quizzes")
      (cb quizzes))))
