(ns top10.effects
  (:refer-clojure :exclude [random-uuid])
  (:require [cljs.reader :as reader]
            [re-frame.core :as rf]
            [top10.events :as events]
            [top10.routes :as routes]))

(rf/reg-fx
 :enable-browser-navigation
 routes/enable-browser-navigation)

(def access-token-key "access-token")

(rf/reg-fx
 :set-access-token
 (fn [new-token] 
   (if new-token 
     (.setItem js/localStorage access-token-key new-token)
     (.removeItem js/localStorage access-token-key))))

(rf/reg-cofx
 :access-token
 (fn [cofx]
   (assoc cofx :access-token (.getItem js/localStorage access-token-key))))

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

(defn random-uuids [cofx keys]
  (reduce
   (fn [acc key]
     (assoc-in acc [:random-uuid key] (random-uuid)))
   cofx
   keys))

(comment (random-uuids {} [:one :two :three]))

(rf/reg-cofx :random-uuid random-uuids)

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

(defn string-to-uint8-array [value]
  (.encode (js/TextEncoder.) value))

(defn array-buffer-to-hex-string [array-buffer]
  (-> array-buffer
      js/Uint8Array. 
      js/Array.from
      (.map (fn [b] (.padStart (.toString b 16) 2 "0")))
      (.join "")))

(defn sha-256 [uint8-array callback]
  (.then (.digest js/window.crypto.subtle "SHA-256" uint8-array) callback))

(comment
  (string-to-uint8-array "input")
  (js/ArrayBuffer. 8)
  (array-buffer-to-hex-string (js/ArrayBuffer. 2))
  (sha-256 (string-to-uint8-array "input") (fn [array-buffer] (prn (array-buffer-to-hex-string array-buffer)))))

(rf/reg-fx
 :calculate-digest
 (fn [value]
   (sha-256
    (string-to-uint8-array value)
    (fn [array-buffer] (rf/dispatch [::events/digest-calculated value (array-buffer-to-hex-string array-buffer)])))))
