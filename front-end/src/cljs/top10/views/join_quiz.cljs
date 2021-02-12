(ns top10.views.join-quiz
  (:require
   [reagent-material-ui.core.button :refer [button]]
   [re-frame.core :as rf]
   [top10.events :as events]
   [top10.subs :as subs]
   [top10.views.base :refer [back-to-overview-button]]))

(defn join-quiz-page [logged-in? {:keys [name externalId deadline-has-passed? isActive personalListHasDraftStatus]}]
   (if-not logged-in?
     [:<>
      [:h1 "Please log in"]
      [:p
       (str "Someone invited you to join the quiz named \"" name "\". ")
       "So far, so good! "
       "However, you need to log in before you can join."]
      [button {:color "primary"
               :variant "contained"
               :on-click #(rf/dispatch [::events/log-in])}
       "Log in"]]
     [:<>
      [:h1 name]
      (cond
        (or deadline-has-passed? (not isActive))
        [:<>
         [:p "Too bad, it's too late to join this quiz. Better luck next time! Maybe you want to create a new quiz yourself?"]
         [back-to-overview-button]]
        (nil? personalListHasDraftStatus)
        [:<>
         [:p
          (str "Someone invited you to join the quiz named \"" name "\". ")
          "Do you want to join?"]
         [button {:color "primary"
                  :on-click #(rf/dispatch [::events/participate-in-quiz externalId])
                  :variant "contained"}
          "Participate in quiz"]]
        :else
        [:<>
         [:p "It looks like you've already joined this quiz."]
         [button {:href (str "#/quiz/" externalId) :color "primary" :variant "contained"} "View quiz"]])]))

(defn join-quiz-page-container []
  [join-quiz-page @(rf/subscribe [::subs/logged-in?]) @(rf/subscribe [::subs/quiz])])
