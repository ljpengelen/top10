(ns top10.views.core
  (:require
   [re-frame.core :as rf]
   [top10.subs :as subs]
   [top10.views.assign-list :refer [assign-list-page-container]]
   [top10.views.base :refer [base-page]]
   [top10.views.complete-quiz :refer [complete-quiz-page-container]]
   [top10.views.list :refer [list-page-container]]
   [top10.views.personal-list :refer [personal-list-page-container]]
   [top10.views.create-quiz :refer [create-quiz-page]]
   [top10.views.home :refer [home-page]]
   [top10.views.personal-results :refer [personal-results-page-container]]
   [top10.views.quiz :refer [quiz-page-container]]
   [top10.views.join-quiz :refer [join-quiz-page-container]]
   [top10.views.quiz-results :refer [quiz-results-page-container]]
   [top10.views.quizzes :refer [quizzes-page-container]]))

(defn- content [page-name]
  (case page-name
    :home-page [home-page]
    :personal-results-page [personal-results-page-container]
    :quizzes-page [quizzes-page-container]
    :quiz-page [quiz-page-container]
    :join-quiz-page [join-quiz-page-container]
    :quiz-results-page [quiz-results-page-container]
    :complete-quiz-page [complete-quiz-page-container]
    :create-quiz-page [create-quiz-page]
    :list-page [list-page-container]
    :personal-list-page [personal-list-page-container]
    :assign-list-page [assign-list-page-container]
    [:div]))

(defn main-panel []
  (let [active-page (rf/subscribe [::subs/active-page])]
    [base-page [content @active-page]]))
