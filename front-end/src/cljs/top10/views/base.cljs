(ns top10.views.base
  (:require
   [reagent-material-ui.core.button :refer [button]]))

(defn back-to-overview-button []
  [button {:href "#/quizzes"} "Back to quiz overview"])

(defn event-value [^js/Event e] (.. e -target -value))
