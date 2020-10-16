(ns top10.quiz)

(defn quiz [id]
  [:div
   [:h1 (str "Quiz Page " id)]
   [:a {:href "#/"} "home page"]])
