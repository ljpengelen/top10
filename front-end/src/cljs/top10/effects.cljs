(ns top10.effects
  (:require [re-frame.core :as rf]
            [top10.routes :as routes]))

(rf/reg-fx
 :enable-browser-navigation
 routes/enable-browser-navigation)

(rf/reg-fx
 :google-init
 (fn [{:keys [on-success on-failure]}]
   (js/gapi.load "auth2" (fn []
                           (.then
                            (js/gapi.auth2.init #js {:ux_mode "redirect"})
                            #(rf/dispatch on-success)
                            #(rf/dispatch on-failure))))))

(rf/reg-fx
 :google-status-check
 (fn [{:keys [logged-in logged-out]}]
   (let [google-auth (js/gapi.auth2.getAuthInstance)]
     (if (.. google-auth -isSignedIn get)
       (rf/dispatch [logged-in  (.. google-auth -currentUser get getAuthResponse -id-token)])
       (rf/dispatch [logged-out])))))

(rf/reg-fx
 :log-in-with-google
 (fn [{:keys [on-success on-failure]}]
   (let [google-auth (js/gapi.auth2.getAuthInstance)
         google-user (.. google-auth signIn then)]
     #(rf/dispatch (conj on-success (.. google-user getAuthResponse id-token)))
     #(rf/dispatch on-failure))))

(rf/reg-fx
 :log-out-with-google
 (fn []
   (.signOut (js/gapi.auth2.getAuthInstance))))

(defonce access-token (atom nil))

(rf/reg-fx
 :set-access-token
 (fn [new-token]
   (reset! access-token new-token)))

(rf/reg-cofx
 :access-token
 (fn [cofx]
   (assoc cofx :access-token @access-token)))

(defonce csrf-token (atom nil))

(rf/reg-fx
 :set-csrf-token
 (fn [new-token]
   (reset! csrf-token new-token)))

(rf/reg-cofx
 :csrf-token
 (fn [cofx]
   (assoc cofx :csrf-token @csrf-token)))

(rf/reg-fx
 :redirect
 (fn [url]
   (routes/nav! url)))
