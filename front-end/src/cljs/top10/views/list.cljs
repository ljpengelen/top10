(ns top10.views.list
  (:require [re-frame.core :as rf]
            [reagent-mui.components :refer [button]]
            [top10.subs :as subs]
            [top10.views.base :refer [embedded-video]]))

(defn list-page [loading-quiz? loading-list? quiz-id creator-name has-draft-status? videos]
  (when-not (or loading-quiz? loading-list?)
    [:<>
     [:h1 creator-name]
     (if has-draft-status?
       [:p (str creator-name " did not submit a top 10 for this quiz.")]
       [:<>
        [:p (str creator-name " submitted these 10 songs for this quiz.")]
        [:div {:class "ytEmbeddedContainer"}
         [embedded-video (first videos) videos]]])
     [button {:href (str "/quiz/" quiz-id "/results")} "Back to quiz results"]]))

(defn list-page-container []
  [list-page
   @(rf/subscribe [::subs/loading-quiz?])
   @(rf/subscribe [::subs/loading-list?])
   @(rf/subscribe [::subs/active-quiz])
   @(rf/subscribe [::subs/list-creator-name])
   @(rf/subscribe [::subs/has-draft-status])
   @(rf/subscribe [::subs/videos])])
