(ns top10.views
  (:require
   [re-frame.core :as rf]
   [top10.events :as events]
   [top10.subs :as subs]))

(defn home-page []
  (let [checking-status (rf/subscribe [::subs/checking-status])
        logged-in (rf/subscribe [::subs/logged-in])]
    [:div
     [:h1 "Greatest Hits"]
     [:ul
      (when (and (not @checking-status) @logged-in)
        [:li [:a {:href "#" :onClick #(rf/dispatch [::events/log-out])} "Log out"]])
      (when (and (not @checking-status) (not @logged-in))
        [:li [:a {:href "#" :onClick #(rf/dispatch [::events/log-in])} "Log in"]])
      [:li [:a {:href "#/quizzes"} "all quizzes page"]]
      [:li [:a {:href "#/quiz/1234"} "single quiz page"]]]]))

(defn quizzes-page []
  [:div
   [:h1 "All quizzes"]
   [:div
    [:a {:href "#/"} "go to Home Page"]]])

(defn quiz-page []
  [:div
   [:h1 "Single quiz"]
   [:div
    [:a {:href "#/"} "go to Home Page"]]])

(defn- pages [page-name]
  (case page-name
    :home-page [home-page]
    :quizzes-page [quizzes-page]
    :quiz-page [quiz-page]
    [:div]))

(defn- show-page [page-name] [pages page-name])

(defn main-panel []
  (let [active-page (rf/subscribe [::subs/active-page])]
    [show-page @active-page]))
