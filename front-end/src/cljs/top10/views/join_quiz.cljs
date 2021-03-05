(ns top10.views.join-quiz
  (:require [re-frame.core :as rf]
            [reagent-material-ui.components :refer [button grid]]
            [top10.events :as events]
            [top10.subs :as subs]
            [top10.views.base :refer [back-to-overview-button log-in-url]]))

(defn join-quiz-page [loading-quiz? logged-in? {:keys [name id deadline deadline-has-passed? isActive personalListHasDraftStatus]}]
   (when-not loading-quiz?
     (if-not logged-in?
       [:<>
        [:h1 "Please log in"]
        [:p
         (str "Someone invited you to join the quiz named \"" name "\". ")
         "So far, so good! "
         "However, you need to log in before you can join."]
        [grid {:container true :spacing 2}
         [grid {:item true}
          [button {:color "primary"
                   :variant "contained"
                   :href (log-in-url :google)}
           "Log in with Google"]]
         [grid {:item true}
          [button {:color "primary"
                   :variant "contained"
                   :href (log-in-url :microsoft)}
           "Log in with Microsoft"]]]]
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
            (str "Someone invited you to join the quiz named \"" name "\". ")]
           [:p
            "The goal of this quiz is trying to recognize people based on their taste in music. "
            "Everyone who joins is asked to submit Youtube videos of their 10 favorite songs. "
            "For this quiz, you have until " deadline " to submit your top 10. "]
           [:p
            "Once this is done, you're asked to find the right top 10 for each participant. "
            "The person who assigns the most top 10's to their creator wins the quiz."]
           [:p "Do you want to join?"]
           [button {:color "primary"
                    :on-click #(rf/dispatch [::events/participate-in-quiz id])
                    :variant "contained"}
            "Participate in quiz"]]
          :else
          [:<>
           [:p "It looks like you've already joined this quiz."]
           [button {:href (str "/quiz/" id) :color "primary" :variant "contained"} "View quiz"]])])))

(defn join-quiz-page-container []
  [join-quiz-page @(rf/subscribe [::subs/loading-quiz?]) @(rf/subscribe [::subs/logged-in?]) @(rf/subscribe [::subs/quiz])])
