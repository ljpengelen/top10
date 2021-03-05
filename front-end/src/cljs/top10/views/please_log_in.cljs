(ns top10.views.please-log-in
  (:require [reagent-material-ui.components :refer [button grid]]
            [top10.views.base :refer [log-in-url]]))

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
               :variant "contained"
               :href (log-in-url :google)}
       "Log in with Google"]]
     [grid {:item true}
      [button {:color "primary"
               :variant "contained"
               :href (log-in-url :microsoft)}
       "Log in with Microsoft"]]]]])
