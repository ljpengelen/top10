(ns ^:figwheel-hooks top10.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require
   [top10.home :refer [check-status home logged-in]]
   [top10.navigation :refer [hook-browser-navigation!]]
   [top10.quiz :refer [quiz]]
   [top10.quizzes :refer [quizzes-page]]
   [top10.rest :refer [get-quiz get-quizzes]]
   [secretary.core :as secretary]
   [reagent.core :as r]
   [reagent.dom :as rdom]))

(defonce state
  (r/atom {:page :home
           :quizzes []
           :quiz {}}))

(defn app-routes []
  (secretary/set-config! :prefix "#")
  (defroute "/" [] (swap! state assoc :page :home))
  (defroute "/quizzes" [] (get-quizzes (fn [quizzes] (state assoc :page :quizzes :quizzes quizzes))))
  (defroute "/quiz/:id" [id] (get-quiz id (fn [quiz] (state assoc :page :quiz :quiz quiz))))
  (hook-browser-navigation!))

(defmulti routed-page #(:page @state))
(defmethod routed-page :quiz [] [quiz (:quiz @state)])
(defmethod routed-page :quizzes [] [quizzes-page (:quizzes @state)])
(defmethod routed-page :default [] [home])

(defn page [] (if @logged-in (do (js/console.log "logged in") routed-page) (do (js/console.log "not logged in") home)))

(defn mount-root []
  (check-status 
   #(do 
      (js/console.log "status checked") 
      (app-routes) 
      (rdom/render [page] (js/document.getElementById "app")))))

(mount-root)

(defn ^:after-load on-reload [] (mount-root))
