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
  (defroute "/" [] (rf/dispatch [::events/navigate {:page :home-page}]))
  (defroute "/oauth2/:provider" {provider :provider
                                 {:keys [code state]} :query-params} (rf/dispatch [::events/log-in provider code state]))
  (defroute "/quizzes" [] (rf/dispatch [::events/navigate {:page :quizzes-page}]))
  (defroute "/quiz/:id" [id] (rf/dispatch [::events/navigate {:page :quiz-page :quiz-id id}]))
  (defroute "/quiz/:id/complete" [id] (rf/dispatch [::events/navigate {:page :complete-quiz-page :quiz-id id}]))
  (defroute #"^(/#)?/quiz/([^/]+)/join$" [_ id] (rf/dispatch [::events/navigate {:page :join-quiz-page :quiz-id id}]))
  (defroute "/quiz/:id/results" [id] (rf/dispatch [::events/navigate {:page :quiz-results-page :quiz-id id}]))
  (defroute "/quiz/:quiz-id/account/:account-id/results" [quiz-id account-id]
    (rf/dispatch [::events/navigate {:page :personal-results-page :quiz-id quiz-id :account-id account-id}]))
  (defroute "/create-quiz" [] (rf/dispatch [::events/navigate {:page :create-quiz-page}]))
  (defroute "/quiz/:quiz-id/list/:list-id" [quiz-id list-id] (rf/dispatch [::events/navigate {:page :list-page :quiz-id quiz-id :list-id list-id}]))
  (defroute "/quiz/:quiz-id/list/:list-id/personal" [quiz-id list-id]
    (rf/dispatch [::events/navigate {:page :personal-list-page :quiz-id quiz-id :list-id list-id}]))
  (defroute "/quiz/:quiz-id/list/:list-id/assign" [quiz-id list-id]
    (rf/dispatch [::events/navigate {:page :assign-list-page :quiz-id quiz-id :list-id list-id}])))
