(ns top10.effects
  (:require
   [re-frame.core :as rf]))

(rf/reg-fx
 :log-in-with-google
 (fn [{:keys [on-success on-failure]}]
   (js/window.signIn 
    (fn [id-token] (rf/dispatch (conj on-success id-token))) 
    #(rf/dispatch on-failure))))

(rf/reg-fx
 :log-out-with-google
 (fn [{:keys [on-success on-failure]}]
   (js/window.signOut
    (if on-success #(rf/dispatch on-success) #())
    #(rf/dispatch on-failure))))

(def access-token (atom nil))

(rf/reg-fx
 :set-access-token
 (fn [new-token]
   (reset! access-token new-token)))

(rf/reg-cofx
 :access-token
 (fn [cofx]
   (assoc cofx :access-token @access-token)))

(def csrf-token (atom nil))

(rf/reg-fx
 :set-csrf-token
 (fn [new-token]
   (reset! csrf-token new-token)))

(rf/reg-cofx
 :csrf-token
 (fn [cofx]
   (assoc cofx :csrf-token @csrf-token)))
