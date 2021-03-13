(ns top10.effects
  (:require [re-frame.core :as rf]
            [top10.routes :as routes]))

(rf/reg-fx
 :enable-browser-navigation
 routes/enable-browser-navigation)

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

(rf/reg-fx
 :scroll-to
 (fn [{:keys [x y]}]
   (js/window.scrollTo x y)))
