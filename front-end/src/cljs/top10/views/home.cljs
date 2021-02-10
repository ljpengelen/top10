(ns top10.views.home
  (:require
   [re-frame.core :as rf]
   [top10.events :as events]
   [top10.subs :as subs]))

(defn home-page []
  (let [checking-status @(rf/subscribe [::subs/checking-status])
        logged-in? @(rf/subscribe [::subs/logged-in?])]
    [:div
     [:h1 "Greatest Hits"]
     [:ul
      (when-not checking-status
       (if logged-in? 
         [:li [:a {:href "#" :onClick #(rf/dispatch [::events/log-out])} "Log out"]]
         [:li [:a {:href "#" :onClick #(rf/dispatch [::events/log-in])} "Log in"]]))
      [:li [:a {:href "#/quizzes"} "all quizzes page"]]]]))
