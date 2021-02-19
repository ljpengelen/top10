(ns top10.views.home
  (:require [re-frame.core :as rf]
            [reagent-material-ui.components :refer [button grid]]
            [top10.events :as events]
            [top10.subs :as subs]
            [top10.views.base :refer [log-in-url]]))

(defn home-page []
  (let [logged-in? @(rf/subscribe [::subs/logged-in?])]
    [:<>
     [:h1 "Top 10"]
     [grid {:container true :direction "column"}
      [grid {:item true}
       [:p
        "Top 10 is an app for organizing musical quizzes. "
        "It's meant as a fun way to get to know other people better based on their musical taste. "
        "You could try it with your colleagues, your team mates, your family members, you name it. "
        "Here's how it works:"]]
      [grid {:item true}
       [:ul
        [:li "Start by creating a new quiz. You'll need to think of a name and a deadline."]
        [:li "Share the link for your quiz with anyone who wants to participate."]
        [:li "Anyone who joins has up until your chosen deadline to submit Youtube videos of their 10 favorite songs."]
        [:li "Once the deadline has passed, it's time to guess which top 10 belongs to whom."]
        [:li "As the creator of the quiz, you can end it at any time."]
        [:li "After you've ended your quiz, all participants can see the results."]]]
      [grid {:item true}
       [:p
        "You'll need a Google account to join. "
        "Have fun!"]]
      [grid {:container true :direction "row" :spacing 2}
       (if logged-in?
         [:<>
          [grid {:item true}
           [button {:color "primary"
                    :variant "contained"
                    :on-click #(rf/dispatch [::events/log-out])}
            "Log out"]]
          [grid {:item true}
           [button {:color "primary"
                    :variant "contained"
                    :href "/quizzes"}
            "Go to quiz overview"]]]
         [grid {:item true}
          [button {:color "primary"
                   :variant "contained"
                   :href (log-in-url "/quizzes")}
           "Log in with Google"]])]]]))
