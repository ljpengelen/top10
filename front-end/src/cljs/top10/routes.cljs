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
  (defroute "/quiz/:id/complete" [id] (rf/dispatch [::events/set-active-page {:page :complete-quiz-page :quiz-id id}]))
  (defroute "/quiz/:id/join" [id] (rf/dispatch [::events/set-active-page {:page :join-quiz-page :quiz-id id}]))
  (defroute "/quiz/:id/results" [id] (rf/dispatch [::events/set-active-page {:page :quiz-results-page :quiz-id id}]))
  (defroute "/create-quiz" [] (rf/dispatch [::events/set-active-page {:page :create-quiz-page}]))
  (defroute "/list/:id" [id] (rf/dispatch [::events/set-active-page {:page :list-page :list-id id}]))
  (defroute "/list/:id/personal" [id] (rf/dispatch [::events/set-active-page {:page :personal-list-page :list-id id}]))
  (defroute "/quiz/:quiz-id/list/:list-id/assign" [quiz-id list-id]
    (rf/dispatch [::events/set-active-page {:page :assign-list-page :quiz-id quiz-id :list-id list-id}]))
  (set-up-browser-navigation!))
