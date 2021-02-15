(ns top10.views.home
  (:require [re-frame.core :as rf]
            [reagent-material-ui.core.button :refer [button]]
            [reagent-material-ui.core.grid :refer [grid]]
            [top10.events :as events]
            [top10.subs :as subs]))

(defn home-page []
  (let [logged-in? @(rf/subscribe [::subs/logged-in?])]
    [:<>
     [:h1 "Top 10"]
     [grid {:container true :direction "row" :spacing 2}
      (if logged-in?
        [:<>
         [grid {:item true}
          [button {:color "primary"
                   :variant "contained"
                   :on-click #(rf/dispatch [::events/log-out])}
           "Log out"]]
         [grid {:item true}
          [button {:color "primary"
                   :variant "contained"
                   :href "#/quizzes"}
           "Go to quiz overview"]]]
        [grid {:item true}
         [button {:color "primary"
                  :variant "contained"
                  :on-click #(rf/dispatch [::events/log-in "/quizzes"])}
          "Log in"]])]]))
