(ns top10.views.quiz-results
  (:require
   [reagent-material-ui.core.grid :refer [grid]]
   [reagent-material-ui.core.table :refer [table]]
   [reagent-material-ui.core.table-body :refer [table-body]]
   [reagent-material-ui.core.table-cell :refer [table-cell]]
   [reagent-material-ui.core.table-container :refer [table-container]]
   [reagent-material-ui.core.table-head :refer [table-head]]
   [reagent-material-ui.core.table-row :refer [table-row]]
   [re-frame.core :as rf]
   [top10.subs :as subs]
   [top10.views.base :refer [back-to-overview-button]]))

(defn personal-results [{:keys [correctAssignments incorrectAssignments]}]
  [:div
   [:h2 "Your correct assignments"]
      [grid {:container true :direction "column" :spacing 2}
       [grid {:item true}
        [table-container
         [table
          [table-head
           [table-row
            [table-cell "Name"]
            [table-cell "Top 10"]]]
          [table-body
           (for [{:keys [listId assigneeName]} correctAssignments]
             ^{:key listId}
             [table-row
              [table-cell assigneeName]
              [table-cell listId]])]]]]
       [grid {:item true}
        [back-to-overview-button]]]
   [:h2 "Your incorrect assignments"]
      [grid {:container true :direction "column" :spacing 2}
       [grid {:item true}
        [table-container
         [table
          [table-head
           [table-row
            [table-cell "Your guess"]
            [table-cell "Actual name"]
            [table-cell "Top 10"]]]
          [table-body
           (for [{:keys [listId assigneeName creatorName]} incorrectAssignments]
             ^{:key listId}
             [table-row
              [table-cell assigneeName]
              [table-cell creatorName]
              [table-cell listId]])]]]]
       [grid {:item true}
        [back-to-overview-button]]]])

(defn quiz-results-page [{:keys [personalResults ranking]}]
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
         [table-cell "Number of correct assignments"]]]
       [table-body
        (for [{:keys [accountId rank name numberOfCorrectAssignments]} ranking]
          ^{:key accountId}
          [table-row
           [table-cell rank]
           [table-cell name]
           [table-cell numberOfCorrectAssignments]])]]]]
    [grid {:item true}
     [back-to-overview-button]]]])

(defn quiz-results-page-container []
  [quiz-results-page @(rf/subscribe [::subs/quiz-results])])
