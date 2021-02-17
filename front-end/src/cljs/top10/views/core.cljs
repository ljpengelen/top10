(ns top10.views.core
  (:require [re-frame.core :as rf]
            [top10.subs :as subs]
            [top10.views.assign-list :refer [assign-list-page-container]]
            [top10.views.base :refer [base-page]]
            [top10.views.complete-quiz :refer [complete-quiz-page-container]]
            [top10.views.create-quiz :refer [create-quiz-page]]
            [top10.views.home :refer [home-page]]
            [top10.views.join-quiz :refer [join-quiz-page-container]]
            [top10.views.list :refer [list-page-container]]
            [top10.views.personal-list :refer [personal-list-page-container]]
            [top10.views.personal-results :refer [personal-results-page-container]]
            [top10.views.please-log-in :refer [please-log-in-page]]
            [top10.views.quiz :refer [quiz-page-container]]
            [top10.views.quiz-results :refer [quiz-results-page-container]]
            [top10.views.quizzes :refer [quizzes-page-container]]))

(defn- secure-content [logged-in? content]
  (if logged-in? [content] [please-log-in-page]))

(defn- content [page-name logged-in?]
  (case page-name
    :blank nil
    :personal-results-page (secure-content logged-in? personal-results-page-container)
    :quizzes-page (secure-content logged-in? quizzes-page-container)
    :quiz-page (secure-content logged-in? quiz-page-container)
    :join-quiz-page [join-quiz-page-container]
    :quiz-results-page (secure-content logged-in? quiz-results-page-container)
    :complete-quiz-page (secure-content logged-in? complete-quiz-page-container)
    :create-quiz-page (secure-content logged-in? create-quiz-page)
    :list-page (secure-content logged-in? list-page-container)
    :personal-list-page (secure-content logged-in? personal-list-page-container)
    :assign-list-page (secure-content logged-in? assign-list-page-container)
    [home-page]))

(defn main-panel []
  (let [active-page @(rf/subscribe [::subs/active-page])
        logged-in? @(rf/subscribe [::subs/logged-in?])]
    [base-page [content active-page logged-in?]]))
