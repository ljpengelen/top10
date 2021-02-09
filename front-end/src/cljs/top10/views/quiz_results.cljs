(ns top10.views.quiz-results
  (:require
   [reagent-material-ui.core.grid :refer [grid]]
   [reagent-material-ui.core.link :refer [link]]
   [reagent-material-ui.core.table :refer [table]]
   [reagent-material-ui.core.table-body :refer [table-body]]
   [reagent-material-ui.core.table-cell :refer [table-cell]]
   [reagent-material-ui.core.table-container :refer [table-container]]
   [reagent-material-ui.core.table-head :refer [table-head]]
   [reagent-material-ui.core.table-row :refer [table-row]]
   [re-frame.core :as rf]
   [top10.events :as events]
   [top10.subs :as subs]
   [top10.views.base :refer [back-to-overview-button]]))

(defn quiz-results-page [{:keys [ranking]}]
  [:div
   [:h1 "Final results"]
   [grid {:container true :direction "column" :spacing 2}
    [grid {:item true}
     [table-container
      [table
       [table-head
        [table-row
         [table-cell "Rank"]
         [table-cell "Name"]
         [table-cell "Number of correct assignments"]
         [table-cell "Action"]]]
       [table-body
        (for [{:keys [externalAccountId rank name numberOfCorrectAssignments]} ranking]
          ^{:key externalAccountId}
          [table-row
           [table-cell rank]
           [table-cell name]
           [table-cell numberOfCorrectAssignments]
           [table-cell [link {:href "#"
                              :color "primary"
                              :on-click (fn [event]
                                          (.preventDefault event)
                                          (rf/dispatch [::events/show-personal-results externalAccountId]))}
                        "Show details"]]])]]]]
    [grid {:item true}
     [back-to-overview-button]]]])

(defn quiz-results-page-container []
  [quiz-results-page @(rf/subscribe [::subs/quiz-results])])
