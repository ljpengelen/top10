(ns top10.views.list
  (:require [re-frame.core :as rf]
            [reagent-material-ui.components :refer [button grid]]
            [top10.subs :as subs]))

(defn list-page [quiz-id creator-name has-draft-status? videos]
  [:<>
   [:h1 creator-name]
   [grid {:container true :direction "column" :spacing 2}
    (if has-draft-status?
      [:p "This person did not submit a top 10 for this quiz."]
      (for [video videos]
        ^{:key (:id video)}
        [grid {:item true}
         [:iframe {:allow "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                   :allowFullScreen true
                   :frameBorder "0"
                   :src (:url video)}]]))
    [grid {:item true}
     [button {:href (str "/quiz/" quiz-id "/results")} "Back to quiz results"]]]])

(defn list-page-container []
  [list-page
   @(rf/subscribe [::subs/active-quiz])
   @(rf/subscribe [::subs/list-creator-name])
   @(rf/subscribe [::subs/has-draft-status])
   @(rf/subscribe [::subs/videos])])
