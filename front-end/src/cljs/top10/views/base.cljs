(ns top10.views.base
  (:require [re-frame.core :as rf]
            [reagent-material-ui.components :refer [button dialog dialog-actions dialog-content dialog-content-text dialog-title]]
            [top10.events :as events]
            [top10.subs :as subs]))

(defn back-to-overview-button []
  [button {:href "#/quizzes"} "Show quiz overview"])

(defn event-value [^js/Event e] (.. e -target -value))

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
