(ns top10.events
  (:require
   [ajax.core :as ajax]
   [re-frame.core :as rf]
   [top10.config :as config]
   [top10.db :as db]))

(rf/reg-event-db
 ::initialize-db
 (fn [_ _]
   db/default-db))

(rf/reg-event-db
 ::set-active-panel
 (fn [db [_ active-panel]]
   (assoc db :active-panel active-panel)))

(rf/reg-event-fx
 ::session-check-succeeded
 (fn [{:keys [db]} [_ response]]
   (let [status (get-in response [:body :status])
         new-csrf-token (get-in response [:headers "x-csrf-token"])]
     {:set-csrf-token new-csrf-token
      :db (-> db
              (assoc-in [:session :logged-in] (= "VALID_SESSION" status))
              (assoc-in [:session :checking-status] false))})))

(def ring-json-response-format (ajax/ring-response-format {:format (ajax/json-response-format {:keywords? true})}))

(rf/reg-event-fx
 ::check-status
 (fn [_ _]
   {:http-xhrio {:method :get
                 :uri (str config/base-url "/session/status")
                 :response-format ring-json-response-format
                 :with-credentials true
                 :on-success [::session-check-succeeded]
                 :on-failure [::session-check-failed]}}))

(rf/reg-event-fx
 ::log-in-with-back-end-succeeded
 (fn [{:keys [db]} [_ response]]
   (let [status (get-in response [:body :status])
         new-access-token (get-in response [:body :token])
         new-csrf-token (get-in response [:headers "x-csrf-token"])]
     {:set-access-token new-access-token
      :set-csrf-token new-csrf-token
      :db (assoc-in db [:session :logged-in] (= "SESSION_CREATED" status))})))

(rf/reg-event-fx
 ::log-in-with-back-end
 [(rf/inject-cofx :csrf-token)]
 (fn [{:keys [csrf-token]} [_ id-token]]
   {:http-xhrio {:method :post
                 :uri (str config/base-url "/session/logIn")
                 :headers {"X-CSRF-Token" csrf-token}
                 :params {:token id-token :type "GOOGLE"}
                 :format (ajax/json-request-format)
                 :response-format ring-json-response-format
                 :with-credentials true
                 :on-success [::log-in-with-back-end-succeeded]
                 :on-failure [::log-in-failed]}}))

(rf/reg-event-fx
 ::log-in
 (fn [_ _]
   {:log-in-with-google {:on-success ::log-in-with-back-end
                         :on-failure ::log-in-failed}}))

(rf/reg-event-db
 ::log-out-succeeded
 (fn [db _]
   (assoc-in db [:session :logged-in] false)))


(rf/reg-event-fx
 ::log-out
 (fn [_ _]
   {:log-out-with-google {:on-success ::log-out-succeeded
                          :on-failure ::log-out-failed}
    :set-access-token nil}))
