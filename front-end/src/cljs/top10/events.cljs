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
 ::google-init-finished
 (fn [_ _]))

(rf/reg-event-fx
 ::google-init
 (fn [_ _]
   {:google-init {:on-success [::google-init-finished]
                  :on-failure [::google-init-finished]}}))

(rf/reg-event-fx
 ::google-status-logged-in
 (fn [_ [_ _]]))

(rf/reg-event-fx
 ::google-status-logged-out
 (fn [_ _]))

(rf/reg-event-fx
 ::google-status-check
 (fn [_ _]
   {:google-status-check {:logged-in ::google-status-logged-in
                          :logged-out ::google-status-logged-out}}))

(rf/reg-event-fx
 ::initialize
 (fn [_ _]
   {:db db/default-db
    :async-flow {:first-dispatch [::check-session]
                 :rules [{:when :seen?
                          :events ::session-check-succeeded
                          :dispatch-n [[::google-init] [::enable-browser-navigation]]}
                         {:when :seen?
                          :events ::google-init-finished
                          :dispatch [::google-status-check]}
                         {:when :seen?
                          :events ::google-status-logged-out
                          :halt? true}
                         {:when :seen?
                          :events ::google-status-logged-in
                          :dispatch-fn (fn [[_ id-token]] [[::log-in-with-back-end id-token]])
                          :halt? true}]}}))

(rf/reg-event-fx
 ::set-active-page
 (fn [{:keys [db]} [_ {:keys [page quiz-id list-id]} ]]
   {:db (-> db
            (assoc :active-page page)
            (assoc :active-quiz quiz-id)
            (assoc :active-list list-id))
    :dispatch [::get-data-for-active-page]}))

(rf/reg-event-fx
 ::get-data-for-active-page
 (fn [{:keys [db]} _]
   (let [{:keys [active-page logged-in? active-list active-quiz]} db]
     (case active-page
       :quiz-page (when logged-in? {:dispatch-n [[::get-quiz active-quiz]
                                                 [::get-quiz-lists active-quiz]
                                                 [::get-quiz-participants active-quiz]]})
       :quiz-results-page (when logged-in? {:dispatch [::get-quiz-results active-quiz]})
       :complete-quiz-page (when logged-in? {:dispatch [::get-quiz active-quiz]})
       :join-quiz-page {:dispatch [::get-quiz active-quiz]}
       :quizzes-page (when logged-in? {:dispatch [::get-quizzes]})
       (:list-page :personal-list-page) (when logged-in? {:dispatch [::get-list active-list]})
       :assign-list-page (when logged-in? {:dispatch-n [[::get-list active-list]
                                                        [::get-quiz-participants active-quiz]]})
       {}))))

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
      :db (assoc db :logged-in? (= "SESSION_CREATED" status))
      :dispatch [::get-data-for-active-page]})))

(rf/reg-event-fx
 ::log-in-with-back-end-failed
 (fn [_ _]))

(rf/reg-event-fx
 ::log-in-with-back-end
 [(rf/inject-cofx :csrf-token)]
 (fn [{:keys [csrf-token]} [_ id-token]]
   {:http-xhrio {:method :post
                 :uri (str api-base-url "/session/logIn")
                 :headers {csrf-token-header csrf-token}
                 :params {:token id-token :type "GOOGLE"}
                 :format (ajax/json-request-format)
                 :response-format ring-json-response-format
                 :with-credentials true
                 :on-success [::log-in-with-back-end-succeeded]
                 :on-failure [::log-in-with-back-end-failed]}}))

(rf/reg-event-fx
 ::log-in-with-google-succeeded
 (fn [_ _]))

(rf/reg-event-fx
 ::log-in-with-google-failed
 (fn [_ _]))

(rf/reg-event-fx
 ::log-in-with-google
 (fn [_ _]
   {:log-in-with-google {:on-success [::log-in-with-google-succeeded]
                         :on-failure [::log-in-with-google-failed]}}))

(rf/reg-event-fx
 ::log-in
 (fn [_ [_ redirect-url]]
   {:async-flow {:first-dispatch [::check-session]
                 :rules [{:when :seen?
                          :events ::session-check-succeeded
                          :dispatch [::log-in-with-google]}
                         {:when :seen?
                          :events ::log-in-with-google-succeeded
                          :dispatch-fn (fn [[_ id-token]] [[::log-in-with-back-end id-token]])}
                         {:when :seen?
                          :events ::log-in-with-back-end-succeeded
                          :dispatch (when redirect-url [::redirect redirect-url])
                          :halt? true}
                         {:when :seen-any-of?
                          :events [::session-check-failed ::log-in-with-google-failed ::log-in-with-back-end-failed]
                          :dispatch [::request-failed]
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
 ::log-out-with-google-succeeded
 (fn [_ _]))

(rf/reg-event-fx
 ::log-out-with-google-failed
 (fn [_ _]))

(rf/reg-event-fx
 ::log-out-with-google
 (fn [_ _]
   {:log-out-with-google {:on-success [::log-out-with-google-succeeded]
                          :on-failure [::log-out-with-google-failed]}}))

(rf/reg-event-fx
 ::log-out
 (fn [_ _]
   {:async-flow {:first-dispatch [::check-session]
                 :rules [{:when :seen?
                          :events ::session-check-succeeded
                          :dispatch-n [[::log-out-with-google] [::log-out-with-backend]]}
                         {:when :seen-all-of?
                          :events [::session-check-succeeded ::log-out-with-google-succeeded ::log-out-with-back-end-succeeded]
                          :halt? true}
                         {:when :seen-any-of?
                          :events [::session-check-failed ::log-out-with-google-failed ::log-out-with-back-end-failed]
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
     (assoc db :quiz-lists lists))))

(rf/reg-event-db
 ::get-quiz-participants-succeeded
 (fn [db [_ response]]
   (let [participants (:body response)]
     (assoc db :quiz-participants participants))))

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
 (fn [{:keys [access-token]} [_ quiz-id]]
   {:http-xhrio [{:method :get
                  :uri (str api-base-url "/private/quiz/" quiz-id "/list")
                  :headers (authorization-header access-token)
                  :format (ajax/json-request-format)
                  :response-format ring-json-response-format
                  :on-success [::get-quiz-lists-succeeded]
                  :on-failure [::request-failed]}]}))

(rf/reg-event-fx
 ::get-quiz-participants
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token]} [_ quiz-id]]
   {:http-xhrio [{:method :get
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
     (assoc db :quizzes quizzes :loading-quizzes? false))))

(rf/reg-event-fx
 ::get-quizzes
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token db]} _]
   {:db (assoc db :loading-quizzes? true)
    :http-xhrio {:method :get
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
 ::create-quiz
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token]} [_ {:keys [name deadline]}]]
   {:http-xhrio {:method :post
                 :uri (str api-base-url "/private/quiz")
                 :headers {"Authorization" (str "Bearer " access-token)}
                 :params {:name name :deadline deadline}
                 :format (ajax/json-request-format)
                 :response-format (ajax/ring-response-format)
                 :on-success [::redirect "/quizzes"]
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
         active-quiz (:externalQuizId list)]
     (-> db
         (assoc :list list)
         (assoc :active-quiz active-quiz)))))

(rf/reg-event-fx
 ::get-list
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token]} [_ list-id]]
   {:http-xhrio {:method :get
                 :uri (str api-base-url "/private/list/" list-id)
                 :headers (authorization-header access-token)
                 :format (ajax/json-request-format)
                 :response-format ring-json-response-format
                 :on-success [::get-list-succeeded]
                 :on-failure [::request-failed]}}))

(rf/reg-event-fx
 ::finalize-list-succeeded
 (fn [_ _]
   {:redirect "/quizzes"}))

(rf/reg-event-fx
 ::finalize-list
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token]} [_ list-id]]
   {:http-xhrio {:method :put
                 :uri (str api-base-url "/private/list/" list-id "/finalize")
                 :headers (authorization-header access-token)
                 :format (ajax/json-request-format)
                 :response-format (ajax/ring-response-format)
                 :on-success [::finalize-list-succeeded]
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
   {:db (assoc db :loading-quiz-results? true)
    :http-xhrio [{:method :get
                  :uri (str api-base-url "/private/quiz/" quiz-id "/result")
                  :headers (authorization-header access-token)
                  :format (ajax/json-request-format)
                  :response-format ring-json-response-format
                  :on-success [::get-quiz-results-succeeded]
                  :on-failure [::request-failed]}]}))

(rf/reg-event-db
 ::show-personal-results
 (fn [db [_ external-account-id]]
   (-> db
       (assoc :active-page :personal-results-page)
       (assoc :external-account-id external-account-id))))

(rf/reg-event-db
 ::show-quiz-results
 (fn [db _]
   (assoc db :active-page :quiz-results-page)))
