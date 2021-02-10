(ns top10.views.list
  (:require
   [reagent-material-ui.core.button :refer [button]]
   [reagent-material-ui.core.grid :refer [grid]]
   [re-frame.core :as rf]
   [top10.subs :as subs]))

(defn list-page [quiz-id creator-name videos]
  [:<>
   [:h1 creator-name]
   [grid {:container true :direction "column" :spacing 2}
    (for [video videos]
      ^{:key (:id video)}
      [grid {:item true}
       [:iframe {:allow "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                 :allowFullScreen true
                 :frameBorder "0"
                 :src (:url video)}]])
    [grid {:item true}
     [button {:href (str "#/quiz/" quiz-id "/results")} "Back to quiz results"]]]])

(defn list-page-container []
  [list-page
   @(rf/subscribe [::subs/active-quiz])
   @(rf/subscribe [::subs/list-creator-name])
   @(rf/subscribe [::subs/videos])])
