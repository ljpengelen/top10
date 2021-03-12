(ns top10.views.quiz
  (:require [re-frame.core :as rf]
            [reagent-material-ui.components :refer [button grid link table table-body table-cell table-container table-head table-row]]
            [top10.config :refer [front-end-base-url]]
            [top10.events :as events]
            [top10.subs :as subs]
            [top10.views.base :refer [back-to-overview-button]]))

(defn quiz-has-ended []
  [:<>
   [:p (str "This quiz has ended")]
   [back-to-overview-button]])

(defn no-list-submitted []
  [:<>
   [:p (str
        "This quiz has reached the final round, "
        "but you haven't submitted a top 10. "
        "That means you can't participate in the final round. "
        "Better luck next time!")]
   [back-to-overview-button]])

(defn assign-lists [quiz-id lists] 
  [:<>
   [:p
    "This quiz has reached the final round. "
    "It's time to assign top 10's to participants."]
   [grid {:container true :direction "column" :spacing 2}
    [grid {:item true}
     [table-container
      [table
       [table-head
        [table-row
         [table-cell "Assigned to"]
         [table-cell "Action"]]]
       [table-body
        (for [{list-id :id assigneeName :assigneeName} lists]
          ^{:key list-id}
          [table-row
           [table-cell (or assigneeName "Not assigned yet")]
           [table-cell [link {:href (str "/quiz/" quiz-id "/list/" list-id "/assign") :color "primary"} "Assign"]]])]]]]
    [grid {:item true}
     [back-to-overview-button]]]])

(defn first-round-in-progress [number-of-participants deadline quiz-id personal-list-has-draft-status? personal-list-id]
  [:<>
   [:p
    (str
     "At the moment, this quiz has " number-of-participants " " (if (= number-of-participants 1) "participant" "participants") ". "
     "Anyone who wants to join has until " deadline " to submit their personal top 10. "
     "If you know anyone who might also want to join, just share the following URL: ")]
   [:pre {:class "join-url"} (str front-end-base-url "/quiz/" quiz-id "/join")]
   (case personal-list-has-draft-status?
     (true) [:p (str
                 "Remember, you still have to submit your personal top 10 for this quiz! "
                 "You can only take part in the final round after you've submitted a top 10.")]
     (false) [:p (str "You've already submitted your personal top 10 for this quiz.")]
     (nil) [button {:color "primary"
                    :on-click #(rf/dispatch [::events/participate-in-quiz quiz-id])
                    :variant "contained"}
            "Participate in quiz"])
   (when (some? personal-list-has-draft-status?)
     [grid {:container true :spacing 2}
      [grid {:item true}
       [button {:href (str "/quiz/" quiz-id "/list/" personal-list-id "/personal") :color "primary" :variant "contained"}
        (if personal-list-has-draft-status? "Submit top 10" "View top 10")]]
      [grid {:item true}
       [back-to-overview-button]]])])

(defn single-participant []
  [:<>
   [:p (str
        "This quiz has reached the final round, "
        "but you're the only participant. "
        "Better luck next time!")]
   [back-to-overview-button]])

(defn quiz-page [loading-quiz? loading-quiz-lists? loading-quiz-participants? {:keys [name deadline deadline-has-passed? id isActive personalListId personalListHasDraftStatus]} number-of-participants lists]
  (when-not (or loading-quiz? loading-quiz-lists? loading-quiz-participants?)
    [:<>
     [:h1 name]
     (cond
       (not isActive) [quiz-has-ended]
       (not deadline-has-passed?) [first-round-in-progress number-of-participants deadline id personalListHasDraftStatus personalListId]
       (or (nil? personalListHasDraftStatus) personalListHasDraftStatus) [no-list-submitted]
       (and personalListId (false? personalListHasDraftStatus) (> number-of-participants 1)) [assign-lists id lists]
       :else [single-participant])]))

(defn quiz-page-container []
  [quiz-page
   @(rf/subscribe [::subs/loading-quiz?])
   @(rf/subscribe [::subs/loading-quiz-lists?])
   @(rf/subscribe [::subs/loading-quiz-participants?])
   @(rf/subscribe [::subs/quiz])
   @(rf/subscribe [::subs/number-of-participants])
   @(rf/subscribe [::subs/quiz-lists])])
