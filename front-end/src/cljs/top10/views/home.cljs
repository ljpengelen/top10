(ns top10.views.home
  (:require
   [reagent-material-ui.core.button :refer [button]]
   [re-frame.core :as rf]
   [top10.events :as events]
   [top10.subs :as subs]))

(defn home-page []
  (let [checking-status @(rf/subscribe [::subs/checking-status])
        logged-in? @(rf/subscribe [::subs/logged-in?])]
    [:<>
     [:h1 "Top 10 quiz"]
     (when-not checking-status
       (if logged-in?
         [button {:color "primary"
                  :variant "contained"
                  :on-click #(rf/dispatch [::events/log-out])}
          "Log out"]
         [button {:color "primary"
                  :variant "contained"
                  :on-click #(rf/dispatch [::events/log-in "/quizzes"])}
          "Log in"]))]))
