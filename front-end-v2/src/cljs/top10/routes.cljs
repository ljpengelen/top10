(ns top10.routes
  (:require-macros [secretary.core :refer [defroute]])
  (:import [goog.history EventType Html5History])
  (:require
   [secretary.core :as secretary]
   [goog.events :as gevents]
   [re-frame.core :as rf]
   [top10.events :as events]))

(def history (Html5History.))

(defn nav! [token] (.setToken history token))

(defn set-up-browser-navigation! []
  (gevents/listen history EventType/NAVIGATE (fn [event] (secretary/dispatch! (.-token ^js event)))))

(defn enable-browser-navigation [] (.setEnabled history true))

(defn app-routes []
  (secretary/set-config! :prefix "#")
  (defroute "/" [] (rf/dispatch [::events/set-active-page {:page :home-page}]))
  (defroute "/quizzes" [] (rf/dispatch [::events/set-active-page {:page :quizzes-page}]))
  (defroute "/quiz/:id" [id] (rf/dispatch [::events/set-active-page {:page :quiz-page :quiz-id id}]))
  (defroute "/create-quiz" [] (rf/dispatch [::events/set-active-page {:page :create-quiz-page}]))
  (defroute "/list/:id" [id] (rf/dispatch [::events/set-active-page {:page :create-list-page :list-id id}]))
  (set-up-browser-navigation!))
