(ns ^:figwheel-hooks top10.core
  (:require-macros [secretary.core :refer [defroute]])
  (:import goog.history.Html5History)
  (:require
   [top10.about :refer [about]]
   [top10.home :refer [home]]
   [top10.quiz :refer [quiz]]
   [secretary.core :as secretary]
   [goog.events :as events]
   [goog.history.EventType :as EventType]
   [reagent.core :as r]
   [reagent.dom :as rdom]))

(defn hook-browser-navigation! []
  (doto (Html5History.)
    (events/listen EventType/NAVIGATE (fn [event] (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

(defonce current-page (r/atom home))

(defn app-routes []
  (secretary/set-config! :prefix "#")
  (defroute "/" [] (reset! current-page home))
  (defroute "/about" [] (reset! current-page about))
  (defroute "/quiz/:id" [id] (reset! current-page (quiz id)))
  (hook-browser-navigation!))

(defn page [] [@current-page])

(defn mount-root []
  (app-routes)
  (rdom/render [page] (js/document.getElementById "app")))

(mount-root)

(defn ^:after-load on-reload [] (mount-root))
