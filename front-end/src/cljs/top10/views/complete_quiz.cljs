(ns top10.views.complete-quiz
  (:require
   [reagent-material-ui.core.button :refer [button]]
   [reagent-material-ui.core.grid :refer [grid]]
   [re-frame.core :as rf]
   [top10.events :as events]
   [top10.subs :as subs]
   [top10.views.base :refer [back-to-overview-button]]))

(defn complete-quiz-page [{:keys [name deadline deadline-has-passed? externalId]}]
  [:div
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
                                :on-click #(rf/dispatch [::events/complete-quiz externalId])
                                :variant "contained"} "End quiz"]]
    [grid {:item true} [back-to-overview-button]]]])

(defn complete-quiz-page-container []
  [complete-quiz-page @(rf/subscribe [::subs/quiz])])
