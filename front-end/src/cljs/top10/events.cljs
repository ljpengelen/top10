(ns top10.events
  (:require [ajax.core :as ajax]
            [re-frame.core :as rf]
            [top10.config :refer [api-base-url csrf-token-header]]
            [top10.db :as db]))

(rf/reg-event-fx
 ::enable-browser-navigation
 (fn [_ _]
   {:enable-browser-navigation nil}))

(rf/reg-event-fx
 ::initialize
 (fn [_ _]
   {:db db/default-db
    :async-flow {:first-dispatch [::check-session]
                 :rules [{:when :seen?
                          :events ::session-check-succeeded
                          :dispatch [::enable-browser-navigation]
                          :halt? true}
                         {:when :seen?
                          :events ::session-check-failed
                          :dispatch-n [[::enable-browser-navigation] [::request-failed]]
                          :halt? true}]}}))

(rf/reg-event-fx
 ::navigate
 (fn [{:keys [db]} [_ {:keys [page quiz-id list-id account-id]}]]
   (let [{:keys [logged-in?]} db
         events (case page
                  :quiz-page (when logged-in? [[::get-quiz quiz-id]
                                               [::get-quiz-lists quiz-id]
                                               [::get-quiz-participants quiz-id]])
                  :quiz-results-page (when logged-in? [[::get-quiz-results quiz-id]])
                  :personal-results-page (when logged-in? [[::get-quiz-results quiz-id]])
                  :complete-quiz-page (when logged-in? [[::get-quiz quiz-id]])
                  :join-quiz-page [[::get-quiz quiz-id]]
                  :quizzes-page (when logged-in? [[::get-quizzes]])
                  (:list-page :personal-list-page) (when logged-in? [[::get-list list-id]
                                                                     [::get-quiz quiz-id]])
                  :assign-list-page (when logged-in? [[::get-list list-id]
                                                      [::get-quiz-participants quiz-id]])
                  [])]
     {:dispatch-n (conj events [::switch-active-page page])
      :db (assoc db :active-quiz quiz-id :active-list list-id :account-id account-id)})))

(rf/reg-event-db
 ::switch-active-page
 (fn [db [_ page]]
   (assoc db :active-page page)))

(rf/reg-event-fx
 ::session-check-succeeded
 (fn [{:keys [db]} [_ response]]
   (let [status (get-in response [:body :status])
         new-access-token (get-in response [:body :token])
         new-csrf-token (get-in response [:headers csrf-token-header])]
     {:set-access-token new-access-token
      :set-csrf-token new-csrf-token
      :db (assoc db :logged-in? (= "VALID_SESSION" status))})))

(def ring-json-response-format (ajax/ring-response-format {:format (ajax/json-response-format {:keywords? true})}))

(rf/reg-event-fx
 ::session-check-failed
 (fn [_ _]))

(rf/reg-event-fx
 ::check-session
 (fn [_ _]
   {:http-xhrio {:method :get
                 :uri (str api-base-url "/session/status")
                 :response-format ring-json-response-format
                 :with-credentials true
                 :on-success [::session-check-succeeded]
                 :on-failure [::session-check-failed]}}))

(rf/reg-event-fx
 ::log-in-with-back-end-succeeded
 (fn [{:keys [db]} [_ response]]
   (let [status (get-in response [:body :status])
         new-access-token (get-in response [:body :token])
         new-csrf-token (get-in response [:headers csrf-token-header])]
     {:set-access-token new-access-token
      :set-csrf-token new-csrf-token
      :db (assoc db :logged-in? (= "SESSION_CREATED" status))})))

(rf/reg-event-fx
 ::log-in-with-back-end-failed
 (fn [_ _]))

(rf/reg-event-fx
 ::log-in-with-back-end
 [(rf/inject-cofx :csrf-token)]
 (fn [{:keys [csrf-token]} [_ provider code]]
   {:http-xhrio {:method :post
                 :uri (str api-base-url "/session/logIn")
                 :headers {csrf-token-header csrf-token}
                 :params {:code code :provider provider}
                 :format (ajax/json-request-format)
                 :response-format ring-json-response-format
                 :with-credentials true
                 :on-success [::log-in-with-back-end-succeeded]
                 :on-failure [::log-in-with-back-end-failed]}}))

(rf/reg-event-fx
 ::log-in
 (fn [_ [_ provider code redirect-url]]
   {:async-flow {:first-dispatch [::check-session]
                 :rules [{:when :seen?
                          :events ::session-check-succeeded
                          :dispatch [::log-in-with-back-end provider code]}
                         {:when :seen?
                          :events ::log-in-with-back-end-succeeded
                          :dispatch (when redirect-url [::redirect redirect-url])
                          :halt? true}
                         {:when :seen-any-of?
                          :events [::session-check-failed ::log-in-with-back-end-failed]
                          :dispatch-n [[::request-failed] [::redirect "/"]]
                          :halt? true}]}}))

(rf/reg-event-fx
 ::log-out-with-back-end-succeeded
 (fn [{:keys [db]} [_ response]]
   (let [new-csrf-token (get-in response [:headers csrf-token-header])]
     {:set-csrf-token new-csrf-token
      :db (assoc db :logged-in? false)})))

(rf/reg-event-fx
 ::log-out-with-back-end-failed
 (fn [_ _]))

(rf/reg-event-fx
 ::log-out-with-backend
 [(rf/inject-cofx :csrf-token)]
 (fn [{:keys [csrf-token]} _]
   {:set-access-token nil
    :http-xhrio {:method :post
                 :uri (str api-base-url "/session/logOut")
                 :headers {csrf-token-header csrf-token}
                 :format (ajax/json-request-format)
                 :response-format (ajax/ring-response-format)
                 :with-credentials true
                 :on-success [::log-out-with-back-end-succeeded]
                 :on-failure [::log-out-with-back-end-failed]}}))

(rf/reg-event-fx
 ::log-out
 (fn [_ _]
   {:async-flow {:first-dispatch [::check-session]
                 :rules [{:when :seen?
                          :events ::session-check-succeeded
                          :dispatch [::log-out-with-backend]}
                         {:when :seen-all-of?
                          :events [::session-check-succeeded ::log-out-with-back-end-succeeded]
                          :halt? true}
                         {:when :seen-any-of?
                          :events [::session-check-failed ::log-out-with-back-end-failed]
                          :dispatch [::request-failed]
                          :halt? true}]}}))

(rf/reg-event-db
 ::get-quiz-succeeded
 (fn [db [_ response]]
   (let [quiz (:body response)]
     (assoc db :quiz quiz :loading-quiz? false))))

(rf/reg-event-db
 ::get-quiz-lists-succeeded
 (fn [db [_ response]]
   (let [lists (:body response)]
     (assoc db :quiz-lists lists :loading-quiz-lists? false))))

(rf/reg-event-db
 ::get-quiz-participants-succeeded
 (fn [db [_ response]]
   (let [participants (:body response)]
     (assoc db :quiz-participants participants :loading-quiz-participants? false))))

(rf/reg-event-db
 ::request-failed
 (fn [db event]
   (js/console.log event)
   (assoc db :dialog {:show? true
                      :title "Oh no!"
                      :text (str "Something unexpected went wrong. "
                                 "Please try again if you're convinced this should work.")})))

(rf/reg-event-db
 ::dismiss-dialog
 (fn [db _]
   (assoc db :dialog {:show? false})))

(defn authorization-header [access-token] {"Authorization" (str "Bearer " access-token)})

(rf/reg-event-fx
 ::get-quiz
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token db]} [_ quiz-id]]
   {:db (assoc db :loading-quiz? true)
    :http-xhrio [{:method :get
                  :uri (str api-base-url "/public/quiz/" quiz-id)
                  :headers (authorization-header access-token)
                  :format (ajax/json-request-format)
                  :response-format ring-json-response-format
                  :on-success [::get-quiz-succeeded]
                  :on-failure [::request-failed]}]}))

(rf/reg-event-fx
 ::get-quiz-lists
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token db]} [_ quiz-id]]
   {:db (assoc db :loading-quiz-lists? true)
    :http-xhrio [{:method :get
                  :uri (str api-base-url "/private/quiz/" quiz-id "/list")
                  :headers (authorization-header access-token)
                  :format (ajax/json-request-format)
                  :response-format ring-json-response-format
                  :on-success [::get-quiz-lists-succeeded]
                  :on-failure [::request-failed]}]}))

(rf/reg-event-fx
 ::get-quiz-participants
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token db]} [_ quiz-id]]
   {:db (assoc db :loading-quiz-participants? true)
    :http-xhrio [{:method :get
                  :uri (str api-base-url "/private/quiz/" quiz-id "/participants")
                  :headers (authorization-header access-token)
                  :format (ajax/json-request-format)
                  :response-format ring-json-response-format
                  :on-success [::get-quiz-participants-succeeded]
                  :on-failure [::request-failed]}]}))

(rf/reg-event-db
 ::get-quizzes-succeeded
 (fn [db [_ response]]
   (let [quizzes (:body response)]
     (assoc db :quizzes quizzes))))

(rf/reg-event-fx
 ::get-quizzes
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token]} _]
   {:http-xhrio {:method :get
                 :uri (str api-base-url "/private/quiz/")
                 :headers (authorization-header access-token)
                 :format (ajax/json-request-format)
                 :response-format ring-json-response-format
                 :on-success [::get-quizzes-succeeded]
                 :on-failure [::request-failed]}}))

(rf/reg-event-fx
 ::redirect
 (fn [_ [_ url]]
   {:redirect url}))

(rf/reg-event-fx
 ::create-quiz-succeeded
 (fn [_ [_ response]]
   (let [quiz-id (get-in response [:body :id])]
     {:redirect (str "/quiz/" quiz-id)})))

(rf/reg-event-fx
 ::create-quiz
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token]} [_ {:keys [name deadline]}]]
   {:http-xhrio {:method :post
                 :uri (str api-base-url "/private/quiz")
                 :headers {"Authorization" (str "Bearer " access-token)}
                 :params {:name name :deadline deadline}
                 :format (ajax/json-request-format)
                 :response-format ring-json-response-format
                 :on-success [::create-quiz-succeeded]
                 :on-failure [::request-failed]}}))

(rf/reg-event-db
 ::add-video-succeeded
 (fn [db [_ response]]
   (let [video (:body response)]
     (update-in db [:list :videos] conj video))))

(rf/reg-event-fx
 ::add-video
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token]} [_ list-id url]]
   {:http-xhrio {:method :post
                 :uri (str api-base-url "/private/list/" list-id "/video")
                 :headers (authorization-header access-token)
                 :params {:url url}
                 :format (ajax/json-request-format)
                 :response-format ring-json-response-format
                 :on-success [::add-video-succeeded]
                 :on-failure [::request-failed]}}))

(rf/reg-event-db
 ::remove-video-succeeded
 (fn [db [_ video-id _]]
   (let [old-videos (get-in db [:list :videos])
         new-videos (remove (fn [video] (= video-id (:id video))) old-videos)]
     (assoc-in db [:list :videos] new-videos))))

(rf/reg-event-fx
 ::remove-video
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token]} [_ video-id]]
   {:http-xhrio {:method :delete
                 :uri (str api-base-url "/private/video/" video-id)
                 :headers (authorization-header access-token)
                 :params nil
                 :format (ajax/json-request-format)
                 :response-format (ajax/ring-response-format)
                 :on-success [::remove-video-succeeded video-id]
                 :on-failure [::request-failed]}}))

(rf/reg-event-db
 ::get-list-succeeded
 (fn [db [_ response]]
   (let [list (:body response)
         active-quiz (:quizId list)]
     (-> db
         (assoc :loading-list? false)
         (assoc :list list)
         (assoc :active-quiz active-quiz)))))

(rf/reg-event-fx
 ::get-list
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token db]} [_ list-id]]
   {:db (assoc db :loading-list? true)
    :http-xhrio {:method :get
                 :uri (str api-base-url "/private/list/" list-id)
                 :headers (authorization-header access-token)
                 :format (ajax/json-request-format)
                 :response-format ring-json-response-format
                 :on-success [::get-list-succeeded]
                 :on-failure [::request-failed]}}))

(rf/reg-event-fx
 ::finalize-list-succeeded
 (fn [_ [_ quiz-id list-id]]
   {:redirect (str "/quiz/" quiz-id "/list/" list-id "/personal")}))

(rf/reg-event-fx
 ::finalize-list
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token]} [_ quiz-id list-id]]
   {:http-xhrio {:method :put
                 :uri (str api-base-url "/private/list/" list-id "/finalize")
                 :headers (authorization-header access-token)
                 :format (ajax/json-request-format)
                 :response-format (ajax/ring-response-format)
                 :on-success [::finalize-list-succeeded quiz-id list-id]
                 :on-failure [::request-failed]}}))

(rf/reg-event-fx
 ::assign-list-succeeded
 (fn [_ [_ quiz-id]]
   {:redirect (str "/quiz/" quiz-id)}))

(rf/reg-event-fx
 ::assign-list
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token]} [_ quiz-id list-id assignee-id]]
   {:http-xhrio {:method :put
                 :uri (str api-base-url "/private/list/" list-id "/assign")
                 :headers (authorization-header access-token)
                 :params {:assigneeId assignee-id}
                 :format (ajax/json-request-format)
                 :response-format (ajax/ring-response-format)
                 :on-success [::assign-list-succeeded quiz-id]
                 :on-failure [::request-failed]}}))

(rf/reg-event-fx
 ::participate-in-quiz-succeeded
 (fn [{:keys [db]} [_ quiz-id response]]
   (let [personal-list-id (get-in response [:body :personalListId])]
     {:db (-> db
              (assoc-in [:quiz :personalListId] personal-list-id)
              (assoc-in [:quiz :personalListHasDraftStatus] true))
      :redirect (str "/quiz/" quiz-id)})))

(rf/reg-event-fx
 ::participate-in-quiz
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token]} [_ quiz-id]]
   {:http-xhrio {:method :post
                 :uri (str api-base-url "/private/quiz/" quiz-id "/participate")
                 :headers (authorization-header access-token)
                 :format (ajax/json-request-format)
                 :response-format ring-json-response-format
                 :on-success [::participate-in-quiz-succeeded quiz-id]
                 :on-failure [::request-failed]}}))

(rf/reg-event-fx
 ::complete-quiz-succeeded
 (fn [_ _]
   {:redirect "/quizzes"}))

(rf/reg-event-fx
 ::complete-quiz
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token]} [_ quiz-id]]
   {:http-xhrio {:method :put
                 :uri (str api-base-url "/private/quiz/" quiz-id "/complete")
                 :headers (authorization-header access-token)
                 :format (ajax/json-request-format)
                 :response-format (ajax/ring-response-format)
                 :on-success [::complete-quiz-succeeded]
                 :on-failure [::request-failed]}}))

(rf/reg-event-db
 ::get-quiz-results-succeeded
 (fn [db [_ response]]
   (let [quiz-results (:body response)]
     (assoc db :quiz-results quiz-results :loading-quiz-results? false))))

(rf/reg-event-fx
 ::get-quiz-results
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token db]} [_ quiz-id]]
   (let [current-quiz-id (get-in db [:quiz-results :quizId])]
     (when-not (= current-quiz-id quiz-id)
       {:db (assoc db :loading-quiz-results? true)
        :http-xhrio [{:method :get
                      :uri (str api-base-url "/private/quiz/" quiz-id "/result")
                      :headers (authorization-header access-token)
                      :format (ajax/json-request-format)
                      :response-format ring-json-response-format
                      :on-success [::get-quiz-results-succeeded]
                      :on-failure [::request-failed]}]}))))

(rf/reg-event-db
 ::show-quiz-results
 (fn [db _]
   (assoc db :active-page :quiz-results-page)))
