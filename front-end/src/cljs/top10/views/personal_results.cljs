(ns top10.views.personal-results
  (:require [re-frame.core :as rf]
            [reagent-material-ui.components :refer [button grid link table table-body table-cell table-container table-head table-row]]
            [top10.subs :as subs]))

(defn personal-results-page [loading-quiz-results? quiz-id {:keys [name correctAssignments incorrectAssignments]}]
  (when-not loading-quiz-results?
    [:<>
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
           [table-cell "Action"]]]
         [table-body
          (for [{:keys [listId assigneeName]} correctAssignments]
            ^{:key listId}
            [table-row
             [table-cell "✅"]
             [table-cell assigneeName]
             [table-cell assigneeName]
             [table-cell [link {:href (str "/quiz/" quiz-id "/list/" listId) :color "primary"} "Show top 10"]]])
          (for [{:keys [listId assigneeName creatorName]} incorrectAssignments]
            ^{:key listId}
            [table-row
             [table-cell "❌"]
             [table-cell assigneeName]
             [table-cell creatorName]
             [table-cell [link {:href (str "/quiz/" quiz-id "/list/" listId) :color "primary"} "Show top 10"]]])]]]]
      [grid {:item true}
       [button {:href (str "/quiz/" quiz-id "/results")} "Back to quiz results"]]]]))

(defn personal-results-page-container []
  [personal-results-page
   @(rf/subscribe [::subs/loading-quiz-results?])
   @(rf/subscribe [::subs/active-quiz])
   @(rf/subscribe [::subs/personal-results])])
