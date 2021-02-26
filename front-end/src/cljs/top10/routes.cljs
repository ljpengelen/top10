(ns top10.routes
  (:require-macros [secretary.core :refer [defroute]])
  (:require [accountant.core :as accountant]
            [re-frame.core :as rf]
            [secretary.core :as secretary]
            [top10.events :as events]))

(defn nav! [url] (accountant/navigate! url))

(defn enable-browser-navigation []
  (accountant/configure-navigation! {:nav-handler secretary/dispatch!
                                     :path-exists? secretary/locate-route})
  (accountant/dispatch-current!))

(defn configure-routes []
  (defroute "/" [] (rf/dispatch [::events/set-active-page {:page :home-page}]))
  (defroute "/oauth2" {{:keys [code state]} :query-params} (rf/dispatch [::events/log-in code state]))
  (defroute "/quizzes" [] (rf/dispatch [::events/set-active-page {:page :quizzes-page}]))
  (defroute "/quiz/:id" [id] (rf/dispatch [::events/set-active-page {:page :quiz-page :quiz-id id}]))
  (defroute "/quiz/:id/complete" [id] (rf/dispatch [::events/set-active-page {:page :complete-quiz-page :quiz-id id}]))
  (defroute #"^(/#)?/quiz/([^/]+)/join$" [_ id] (rf/dispatch [::events/set-active-page {:page :join-quiz-page :quiz-id id}]))
  (defroute "/quiz/:id/results" [id] (rf/dispatch [::events/set-active-page {:page :quiz-results-page :quiz-id id}]))
  (defroute "/create-quiz" [] (rf/dispatch [::events/set-active-page {:page :create-quiz-page}]))
  (defroute "/list/:id" [id] (rf/dispatch [::events/set-active-page {:page :list-page :list-id id}]))
  (defroute "/list/:id/personal" [id] (rf/dispatch [::events/set-active-page {:page :personal-list-page :list-id id}]))
  (defroute "/quiz/:quiz-id/list/:list-id/assign" [quiz-id list-id]
    (rf/dispatch [::events/set-active-page {:page :assign-list-page :quiz-id quiz-id :list-id list-id}])))
