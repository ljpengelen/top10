(ns top10.views.quiz
  (:require
   [reagent-material-ui.core.button :refer [button]]
   [reagent-material-ui.core.grid :refer [grid]]
   [reagent-material-ui.core.link :refer [link]]
   [reagent-material-ui.core.table :refer [table]]
   [reagent-material-ui.core.table-body :refer [table-body]]
   [reagent-material-ui.core.table-cell :refer [table-cell]]
   [reagent-material-ui.core.table-container :refer [table-container]]
   [reagent-material-ui.core.table-head :refer [table-head]]
   [reagent-material-ui.core.table-row :refer [table-row]]
   [re-frame.core :as rf]
   [top10.config :refer [front-end-base-url]]
   [top10.events :as events]
   [top10.subs :as subs]
   [top10.views.base :refer [back-to-overview-button]]))

(defn quiz-page [{:keys [name deadline deadline-has-passed? externalId isActive personalListId personalListHasDraftStatus]} number-of-participants lists]
  [:<>
   [:h1 name]
   (cond
     (not isActive)
     [:<>
      [:p (str "This quiz has ended")]
      [back-to-overview-button]]
     (and deadline-has-passed? personalListHasDraftStatus)
     [:<>
      [:p (str
           "This quiz has reached the final round, "
           "but you haven't submitted a top 10. "
           "That means you can't participate in the final round. "
           "Better luck next time!")]
      [back-to-overview-button]]
     (and deadline-has-passed? personalListId (false? personalListHasDraftStatus))
     [:<>
      [:p (str
           "This quiz has reached the final round. "
           "It's time to assign top 10's to participants.")]
      [grid {:container true :direction "column" :spacing 2}
       [grid {:item true}
        [table-container
         [table
          [table-head
           [table-row
            [table-cell "Assigned to"]
            [table-cell "Action"]]]
          [table-body
           (for [{:keys [id assigneeName]} lists]
             ^{:key id}
             [table-row
              [table-cell (or assigneeName "Not assigned yet")]
              [table-cell [link {:href (str "#/quiz/" externalId "/list/" id "/assign") :color "primary"} "Assign"]]])]]]]
       [grid {:item true}
        [back-to-overview-button]]]]
     (not deadline-has-passed?)
     [:<>
      [:p
       (str
        "At the moment, this quiz has " number-of-participants " " (if (= number-of-participants 1) "participant" "participants") ". "
        "Anyone who wants to join has until " deadline " to submit their personal top 10. "
        "If you know anyone who might also want to join, just share the following URL: ")]
      [:pre (str front-end-base-url "/#/quiz/" externalId "/join")]
      (case personalListHasDraftStatus
        (true) [:p (str
                    "Remember, you still have to submit your personal top 10 for this quiz! "
                    "You can only take part in the final round after you've submitted a top 10.")]
        (false) [:p (str "You've already submitted your personal top 10 for this quiz.")]
        (nil) [button {:color "primary"
                       :on-click #(rf/dispatch [::events/participate-in-quiz externalId])
                       :variant "contained"}
               "Participate in quiz"])
      (when (some? personalListHasDraftStatus)
        [grid {:container true :spacing 2}
         [grid {:item true}
          [button {:href (str "#/list/" personalListId "/personal") :color "primary" :variant "contained"}
           (if personalListHasDraftStatus "Submit top 10" "View top 10")]]
         [grid {:item true}
          [back-to-overview-button]]])])])

(defn quiz-page-container []
  [quiz-page @(rf/subscribe [::subs/quiz]) @(rf/subscribe [::subs/number-of-participants]) @(rf/subscribe [::subs/quiz-lists])])
