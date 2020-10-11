(ns top10.navigation
  (:import goog.history.Html5History)
  (:require
   [secretary.core :as secretary]
   [goog.events :as events]
   [goog.history.EventType :as EventType]))

(def history (Html5History.))

(defn nav! [token]
  (.setToken history token))

(defn hook-browser-navigation! []
  (doto history
    (events/listen EventType/NAVIGATE (fn [event] (secretary/dispatch! (.-token event))))
    (.setEnabled true)))
