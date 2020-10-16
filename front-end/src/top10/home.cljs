(ns top10.home
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [top10.config :refer [base-url]]
            [top10.navigation :refer [nav!]]
            [top10.rest :refer [access-token]]
            [reagent.core :as r]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

(def checking-status (r/atom true))
(def logged-in (r/atom false))
(def csrf-token (r/atom nil))

(def error-message (r/atom nil))

(defn sign-in-successful [id-token]
  (reset! error-message nil)
  (go
    (let
     [headers {"X-CSRF-Token" @csrf-token}
      request {:token id-token :type "GOOGLE"}
      log-in-response (<! (http/post (str base-url "/session/logIn") {:json-params request :headers headers}))
      new-csrf-token (get-in log-in-response [:headers, "x-csrf-token"])
      new-access-token (get-in log-in-response [:body, :token])]
      (reset! logged-in true)
      (reset! csrf-token new-csrf-token)
      (reset! access-token new-access-token)
      (nav! "/quizzes"))))

(defn sign-in-failed []
  (reset! error-message "Log in failed"))

(defn sign-out-successful []
  (reset! logged-in false)
  (reset! access-token nil)
  (reset! error-message nil))

(defn sign-out-failed []
  (reset! error-message "Log out failed"))

(defn home []
  [:div [:h1 "Greatest Hits"]
   [:ul
    (when (and (not @checking-status) @logged-in)
      [:li [:a {:href "#" :onClick #(js/window.signOut sign-out-successful sign-out-failed)} "Log out"]])
    (when (and (not @checking-status) (not @logged-in))
      [:li [:a {:href "#" :onClick #(js/window.signIn sign-in-successful sign-in-failed)} "Log in"]])
    [:li [:a {:href "#/quiz/1234"} "quiz page"]]]
   (when @error-message [:div @error-message])])

(defn check-status [cb]
  (go
    (let
      [status-response (<! (http/get "http://localhost:8080/session/status"))
       session-status (get-in status-response [:body, :status])
       has-session (= "VALID_SESSION" session-status)
       new-csrf-token (get-in status-response [:headers, "x-csrf-token"])]
      (reset! logged-in has-session)
      (reset! checking-status false)
      (reset! csrf-token new-csrf-token)
      (cb))))
