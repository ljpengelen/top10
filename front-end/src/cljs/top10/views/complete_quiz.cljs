(ns top10.views.complete-quiz
  (:require [re-frame.core :as rf]
            [reagent-material-ui.components :refer [button grid]]
            [top10.events :as events]
            [top10.subs :as subs]
            [top10.views.base :refer [back-to-overview-button]]))

(defn complete-quiz-page [loading-quiz? {:keys [name deadline deadline-has-passed? id]}]
  (when-not loading-quiz?
    [:<>
     [:h1 name]
     (when-not deadline-has-passed?
       [:p (str
            "The deadline for this quiz hasn't passed yet. "
            "Its deadline is " deadline ". "
            "Are you sure you want to end this quiz?")])
     [:p (str
          "After ending the quiz, the end results will become available to all participants.")]
     [grid {:container true :spacing 2}
      [grid {:item true} [button {:color "primary"
                                  :on-click #(rf/dispatch [::events/complete-quiz id])
                                  :variant "contained"} "End quiz"]]
      [grid {:item true} [back-to-overview-button]]]]))

(defn complete-quiz-page-container []
  [complete-quiz-page @(rf/subscribe [::subs/loading-quiz?]) @(rf/subscribe [::subs/quiz])])
