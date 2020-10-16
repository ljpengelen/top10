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

(rf/reg-event-db
 ::session-check-succeeded
 (fn [db [_ { :keys [status]}]]
   (-> db
       (assoc-in [:session :logged-in] (= "VALID_SESSION" status))
       (assoc-in [:session :checking-status] false))))

(rf/reg-event-fx
 ::check-status
 (fn [_ _]
   {:http-xhrio {:method :get
                 :uri (str config/base-url "/session/status")
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [::session-check-succeeded]
                 :on-failure [::session-check-failed]}}))

(rf/reg-event-db
 ::log-in-succeeded
 (fn [db [_ id-token]]
   (js/console.log id-token)
   (assoc-in db [:session :logged-in] true)))

(rf/reg-event-fx
 ::log-in
 (fn [_ _]
   {:log-in {:on-success ::log-in-succeeded
             :on-failure ::log-in-failed}}))

(rf/reg-event-db
 ::log-out-succeeded
 (fn [db _]
   (assoc-in db [:session :logged-in] false)))

(rf/reg-event-fx
 ::log-out
 (fn [_ _]
   {:log-out {:on-success ::log-out-succeeded
             :on-failure ::log-out-failed}}))
