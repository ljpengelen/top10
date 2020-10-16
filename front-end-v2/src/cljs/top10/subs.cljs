(ns top10.subs
  (:require
   [re-frame.core :as rf]))

(rf/reg-sub
 ::active-panel
 (fn [db _]
   (:active-panel db)))

(rf/reg-sub
 ::session
 (fn [db _]
   (:session db)))

(rf/reg-sub
 ::access-token
 
 :<- [::session]
 
 (fn [session _]
   (:access-token session)))

(rf/reg-sub
 ::checking-status

 :<- [::session]

 (fn [session _]
   (:checking-status session)))

(rf/reg-sub
 ::csrf-token

 :<- [::session]

 (fn [session _]
   (:csrf-token session)))

(rf/reg-sub
 ::logged-in

 :<- [::session]

 (fn [session _]
   (:logged-in session)))
