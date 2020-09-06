(ns top10.home
  (:require [reagent.core :as r]))

(defonce logged-in (r/atom false))

(defonce error-message (r/atom nil))

(defn sign-in-successful [id-token]
  (js/console.log id-token)
  (reset! logged-in true)
  (reset! error-message nil))

(defn sign-in-failed []
  (reset! error-message "Log in failed"))

(defn sign-out-successful []
  (reset! logged-in false)
  (reset! error-message nil))

(defn sign-out-failed []
  (reset! error-message "Log out failed"))

(defn home []
  [:div [:h1 "Home Page"]
   (when-not @logged-in [:a {:href "#" :onClick #(js/window.signIn sign-in-successful sign-in-failed)} "Log in"])
   (when @logged-in [:a {:href "#" :onClick #(js/window.signOut sign-out-successful sign-out-failed)} "Log out"])
   [:a {:href "#/about"} "about page"]
   [:a {:href "#/quiz/1234"} "quiz page"]
   (when @error-message [:div @error-message])])
