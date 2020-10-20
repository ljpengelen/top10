(ns top10.events
  (:require
   [ajax.core :as ajax]
   [re-frame.core :as rf]
   [top10.config :refer [base-url csrf-token-header]]
   [top10.db :as db]))

(rf/reg-event-fx
 ::enable-browser-navigation
 (fn [_ _]
   {:enable-browser-navigation nil}))

(rf/reg-event-fx
 ::initialize
 (fn [_ _]
   {:db db/default-db
    :async-flow {:first-dispatch [::check-status]
                 :rules [{:when :seen? :events ::session-check-succeeded :dispatch [::enable-browser-navigation]}]}}))

(rf/reg-event-fx
 ::set-active-page
 (fn [{:keys [db]} [_ {:keys [page quiz-id]} ]]
   (let [db-with-page (assoc db :active-page page)
         logged-in? (get-in db [:session :logged-in])]
     (case page
       :quiz-page (cond-> {:db (assoc db-with-page :active-quiz quiz-id)}
                    logged-in? (assoc :dispatch [::get-quiz quiz-id]))
       :quizzes-page (cond-> {:db db-with-page}
                       logged-in? (assoc :dispatch [::get-quizzes]))
       (:home-page :create-quiz-page) {:db db-with-page}))))

(rf/reg-event-db
 ::set-query
 (fn [db [_ [query params]]]
   (-> db
       (assoc :query query)
       (assoc :query-params params))))

(rf/reg-event-fx
 ::session-check-succeeded
 (fn [{:keys [db]} [_ response]]
   (let [status (get-in response [:body :status])
         new-access-token (get-in response [:body :token])
         new-csrf-token (get-in response [:headers csrf-token-header])]
     {:set-access-token new-access-token
      :set-csrf-token new-csrf-token
      :db (-> db
              (assoc-in [:session :logged-in] (= "VALID_SESSION" status))
              (assoc-in [:session :checking-status] false))})))

(def ring-json-response-format (ajax/ring-response-format {:format (ajax/json-response-format {:keywords? true})}))

(rf/reg-event-fx
 ::check-status
 (fn [_ _]
   {:http-xhrio {:method :get
                 :uri (str base-url "/session/status")
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
     (js/console.log new-access-token)
     {:set-access-token new-access-token
      :set-csrf-token new-csrf-token
      :db (assoc-in db [:session :logged-in] (= "SESSION_CREATED" status))})))

(rf/reg-event-fx
 ::log-in-with-back-end
 [(rf/inject-cofx :csrf-token)]
 (fn [{:keys [csrf-token]} [_ id-token]]
   {:http-xhrio {:method :post
                 :uri (str base-url "/session/logIn")
                 :headers {csrf-token-header csrf-token}
                 :params {:token id-token :type "GOOGLE"}
                 :format (ajax/json-request-format)
                 :response-format ring-json-response-format
                 :with-credentials true
                 :on-success [::log-in-with-back-end-succeeded]
                 :on-failure [::log-in-failed]}}))

(rf/reg-event-fx
 ::log-in
 (fn [_ _]
   {:log-in-with-google {:on-success [::log-in-with-back-end]
                         :on-failure [::log-in-failed]}}))

(rf/reg-event-fx
 ::log-out-with-back-end-succeeded
 (fn [{:keys [db]} [_ response]]
   (let [new-csrf-token (get-in response [:headers csrf-token-header])]
     {:set-csrf-token new-csrf-token
      :db (assoc-in db [:session :logged-in] false)})))

(rf/reg-event-db
 ::log-out-failed
 (fn [_ event]
   (js/console.log event)))

(rf/reg-event-fx
 ::log-out
 [(rf/inject-cofx :csrf-token)]
 (fn [{:keys [csrf-token]} _]
   {:log-out-with-google {:on-failure [::log-out-failed]}
    :set-access-token nil
    :http-xhrio {:method :post
                 :uri (str base-url "/session/logOut")
                 :headers {csrf-token-header csrf-token}
                 :format (ajax/json-request-format)
                 :response-format (ajax/ring-response-format)
                 :with-credentials true
                 :on-success [::log-out-with-back-end-succeeded]
                 :on-failure [::log-out-failed]}}))

(rf/reg-event-db
 ::get-quiz-succeeded
 (fn [db event]
   (js/console.log db event)))

(rf/reg-event-db
 ::request-failed
 (fn [_ event]
   (js/console.log event)))

(rf/reg-event-fx
 ::get-quiz
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token]} [_ quiz-id]]
   {:http-xhrio {:method :get
                 :uri (str base-url "/private/quiz/" quiz-id)
                 :headers {"Authorization" (str "Bearer " access-token)}
                 :format (ajax/json-request-format)
                 :response-format ring-json-response-format
                 :on-success [::get-quiz-succeeded]
                 :on-failure [::request-failed]}}))

(rf/reg-event-db
 ::get-quizzes-succeeded
 (fn [db event]
   (js/console.log db event)))

(rf/reg-event-fx
 ::get-quizzes
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token]} _]
   {:http-xhrio {:method :get
                 :uri (str base-url "/private/quiz/")
                 :headers {"Authorization" (str "Bearer " access-token)}
                 :format (ajax/json-request-format)
                 :response-format ring-json-response-format
                 :on-success [::get-quizzes-succeeded]
                 :on-failure [::request-failed]}}))

(rf/reg-event-db
 ::create-quiz-succeeded
 (fn [db event]
   (js/console.log db event)))

(rf/reg-event-fx
 ::create-quiz
 [(rf/inject-cofx :access-token)]
 (fn [{:keys [access-token]} [_ {:keys [name deadline]}]]
   {:http-xhrio {:method :post
                 :uri (str base-url "/private/quiz")
                 :headers {"Authorization" (str "Bearer " access-token)}
                 :params {:name name :deadline (.-date deadline)}
                 :format (ajax/json-request-format)
                 :response-format (ajax/ring-response-format)
                 :on-success [::create-quiz-succeeded]
                 :on-failure [::request-failed]}}))

(rf/reg-event-fx
 ::redirect
 (fn [_ [_ url]]
   {:redirect url}))
