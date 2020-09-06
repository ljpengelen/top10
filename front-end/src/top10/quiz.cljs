(ns top10.quiz)

(defn quiz [id]
  (fn []
    [:div
     [:h1 (str "Quiz Page " id)]
     [:a {:href "#/"} "home page"]]))
