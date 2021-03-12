(ns top10.views.quiz-results
  (:require [re-frame.core :as rf]
            [reagent-material-ui.components :refer [grid link table table-body table-cell table-container table-head table-row]]
            [top10.subs :as subs]
            [top10.views.base :refer [back-to-overview-button]]))

(defn quiz-results-page [loading-quiz-results? {:keys [quizId ranking]}]
  [:<>
   [:h1 "Final results"]
   (when-not loading-quiz-results?
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
          (for [{:keys [accountId rank name numberOfCorrectAssignments]} ranking]
            ^{:key accountId}
            [table-row
             [table-cell rank]
             [table-cell name]
             [table-cell numberOfCorrectAssignments]
             [table-cell [link {:href (str "/quiz/" quizId "/account/" accountId "/results") :color "primary"} "Show details"]]])]]]]
      [grid {:item true}
       [back-to-overview-button]]])])

(defn quiz-results-page-container []
  [quiz-results-page @(rf/subscribe [::subs/loading-quiz-results?]) @(rf/subscribe [::subs/quiz-results])])
