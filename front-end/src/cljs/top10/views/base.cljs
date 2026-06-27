(ns top10.views.base
  (:require [clojure.string :as string]
            [re-frame.core :as rf]
            [reagent-mui.components :refer [button dialog grid
                                            dialog-actions dialog-content
                                            dialog-content-text dialog-title]]
            [top10.events :as events]
            [top10.subs :as subs]))

(defn back-to-overview-button []
  [button {:href "/quizzes"} "Show quiz overview"])

(defn event-value [^js/Event e] (.. e -target -value))

(defn iframe [url]
  [:iframe {:allow "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
            :allowFullScreen true
            :frameBorder "0"
            :src url}])

(def video-url-prefix "https://www.youtube-nocookie.com/embed/")

(defn embedded-video
  ([video]
   (iframe (str video-url-prefix (:referenceId video))))
  ([first-video videos]
   (let [first-video-id (:referenceId first-video)
         ids (map :referenceId videos)
         joined-ids (string/join "," ids)]
     (iframe (str video-url-prefix first-video-id "?playlist=" joined-ids)))))

(defn embedded-videos [videos]
  [grid {:container true :direction "column" :spacing 2}
   (for [video videos]
     ^{:key (:id video)}
     [:<>
      [grid {:class "ytEmbeddedContainer" :item true}
       [embedded-video video]]])])

(defn base-page [content]
  (let [show-dialog? @(rf/subscribe [::subs/show-dialog?])
        text @(rf/subscribe [::subs/dialog-text])
        title @(rf/subscribe [::subs/dialog-title])]
    [:<>
     content
     [dialog {:open show-dialog?}
      [dialog-title title]
      [dialog-content
       [dialog-content-text text]]
      [dialog-actions
       [button {:on-click #(rf/dispatch [::events/dismiss-dialog])}"OK"]]]]))
