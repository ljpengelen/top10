(ns top10.effects
  (:refer-clojure :exclude [random-uuid])
  (:require [cljs.reader :as reader]
            [re-frame.core :as rf]
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
 :relative-redirect
 (fn [url] (routes/nav! url)))

(rf/reg-fx
 :absolute-redirect
 (fn [url] (.replace js/window.location url)))

(rf/reg-fx
 :scroll-to
 (fn [{:keys [x y]}]
   (js/window.scrollTo x y)))

(defn relative-path []
  (.replace js/window.location.href js/window.location.origin ""))

(comment (relative-path))

(rf/reg-cofx
 :relative-path
 (fn [cofx]
   (assoc cofx :relative-path (relative-path))))

(defn random-uuid []
  (.randomUUID js/crypto))

(comment (random-uuid))

(rf/reg-cofx
 :random-uuid
 (fn [cofx]
   (assoc cofx :random-uuid (random-uuid))))

(def oauth-state-key "oauth-state")

(defn auth-state []
  (reader/read-string (.getItem js/sessionStorage oauth-state-key)))

(comment (auth-state))

(rf/reg-cofx
 :oauth-state
 (fn [cofx]
   (assoc cofx :oauth-state (auth-state))))

(rf/reg-fx
 :store-oauth-state
 (fn [state]
   (.setItem js/sessionStorage oauth-state-key (pr-str state))))
