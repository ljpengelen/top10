(ns top10.effects
  (:require
   [re-frame.core :as rf]))

(rf/reg-fx
 :log-in
 (fn [{:keys [on-success on-failure]}]
   (js/window.signIn 
    (fn [id-token] (rf/dispatch [on-success id-token])) 
    #(rf/dispatch [on-failure]))))

(rf/reg-fx
 :log-out
 (fn [{:keys [on-success on-failure]}]
   (js/window.signOut
    #(rf/dispatch [on-success])
    #(rf/dispatch [on-failure]))))
