(ns ^:figwheel-hooks top10.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require
   [top10.about :refer [about]]
   [top10.home :refer [home, check-status]]
   [top10.navigation :refer [hook-browser-navigation!]]
   [top10.quiz :refer [quiz]]
   [top10.quizzes :refer [quizzes]]
   [secretary.core :as secretary]
   [reagent.core :as r]
   [reagent.dom :as rdom]))

(defonce current-page (r/atom home))

(defn app-routes []
  (secretary/set-config! :prefix "#")
  (defroute "/" [] (reset! current-page home))
  (defroute "/quizzes" [] (reset! current-page quizzes))
  (defroute "/about" [] (reset! current-page about))
  (defroute "/quiz/:id" [id] (reset! current-page (quiz id)))
  (hook-browser-navigation!))

(defn page [] [@current-page])

(defn mount-root []
  (app-routes)
  (rdom/render [page] (js/document.getElementById "app")))

(mount-root)

(check-status)

(defn ^:after-load on-reload [] (mount-root))
