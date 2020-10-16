(ns top10.routes
  (:require-macros [secretary.core :refer [defroute]])
  (:import [goog.history EventType Html5History])
  (:require
   [secretary.core :as secretary]
   [goog.events :as gevents]
   [re-frame.core :as re-frame]
   [top10.events :as events]))

(def history (Html5History.))

(defn nav! [token] (.setToken history token))

(defn hook-browser-navigation! []
  (doto history
    (gevents/listen EventType/NAVIGATE (fn [event] (secretary/dispatch! (.-token ^js event))))
    (.setEnabled true)))

(defn app-routes []
  (secretary/set-config! :prefix "#")
  (defroute "/" [] (re-frame/dispatch [::events/set-active-panel :home-panel]))
  (defroute "/about" [] (re-frame/dispatch [::events/set-active-panel :about-panel]))
  (hook-browser-navigation!))
