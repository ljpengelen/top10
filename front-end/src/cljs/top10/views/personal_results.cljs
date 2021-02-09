(ns top10.views.personal-results
  (:require
   [reagent-material-ui.core.button :refer [button]]
   [reagent-material-ui.core.grid :refer [grid]]
   [reagent-material-ui.core.table :refer [table]]
   [reagent-material-ui.core.table-body :refer [table-body]]
   [reagent-material-ui.core.table-cell :refer [table-cell]]
   [reagent-material-ui.core.table-container :refer [table-container]]
   [reagent-material-ui.core.table-head :refer [table-head]]
   [reagent-material-ui.core.table-row :refer [table-row]]
   [re-frame.core :as rf]
   [top10.events :as events]
   [top10.subs :as subs]))

(defn personal-results-page [{:keys [name correctAssignments incorrectAssignments]}]
  [:div
   [:h1 (str "Personal results for " name)]
   [grid {:container true :direction "column" :spacing 2}
    [grid {:item true}
     [table-container
      [table
       [table-head
        [table-row
         [table-cell]
         [table-cell "Your guess"]
         [table-cell "Actual name"]
         [table-cell "Top 10"]]]
       [table-body
        (for [{:keys [listId assigneeName]} correctAssignments]
          ^{:key listId}
          [table-row
           [table-cell "✅"]
           [table-cell assigneeName]
           [table-cell assigneeName]
           [table-cell listId]])
        (for [{:keys [listId assigneeName creatorName]} incorrectAssignments]
          ^{:key listId}
          [table-row
           [table-cell "❌"]
           [table-cell assigneeName]
           [table-cell creatorName]
           [table-cell listId]])]]]]
    [grid {:item true}
     [button {:on-click #(rf/dispatch [::events/show-quiz-results])} "Back to quiz results"]]]])

(defn personal-results-page-container []
  [personal-results-page @(rf/subscribe [::subs/personal-results])])
