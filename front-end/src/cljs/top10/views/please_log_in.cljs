(ns top10.views.please-log-in
  (:require [re-frame.core :as rf]
            [reagent-material-ui.core.button :refer [button]]
            [reagent-material-ui.core.grid :refer [grid]]
            [top10.events :as events]))

(defn please-log-in-page []
  [:<>
   [:h1 "Please log in"]
   [grid {:container true :direction "column"}
    [grid {:item true}
     [:p
      "You need to be logged in to view this page."]]
    [grid {:item true}
     [button {:color "primary"
              :variant "contained"
              :on-click #(rf/dispatch [::events/log-in])}
      "Log in"]]]])
