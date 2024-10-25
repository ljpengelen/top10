(ns top10.views.please-log-in
  (:require [re-frame.core :as rf]
            [reagent-mui.components :refer [button grid]]
            [top10.events :as events]))

(defn please-log-in-page []
  [:<>
   [:h1 "Please log in"]
   [grid {:container true :direction "column"}
    [grid {:item true}
     [:p
      "You need to be logged in to view this page."]]
    [grid {:container true :spacing 2}
     [grid {:item true}
      [button {:color "primary"
               :on-click #(rf/dispatch [::events/navigate-to-log-in-form :google])
               :variant "contained"}
       "Log in with Google"]]
     [grid {:item true}
      [button {:color "primary"
               :on-click #(rf/dispatch [::events/navigate-to-log-in-form :microsoft])
               :variant "contained"}
       "Log in with Microsoft"]]]]])
