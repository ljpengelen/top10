(ns top10.subs
  (:require
   [re-frame.core :as rf]))

(rf/reg-sub
 ::active-page
 (fn [db _]
   (:active-page db)))

(rf/reg-sub
 ::session
 (fn [db _]
   (:session db)))

(rf/reg-sub
 ::checking-status

 :<- [::session]

 (fn [session _]
   (:checking-status session)))

(rf/reg-sub
 ::logged-in

 :<- [::session]

 (fn [session _]
   (:logged-in session)))
