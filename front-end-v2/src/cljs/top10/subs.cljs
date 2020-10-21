(ns top10.subs
  (:require
   [cljs-time.format :as tf]
   [re-frame.core :as rf]))

(rf/reg-sub
 ::active-page
 (fn [db _]
   (:active-page db)))

(defn format-date [date-string]
  (->> date-string
      (tf/parse)
      (tf/unparse (tf/formatter "MMM d, yyyy"))))

(rf/reg-sub
 ::quizzes
 (fn [db _]
   (let [quizzes (:quizzes db)]
     (map #(update % :deadline format-date) quizzes))))

(rf/reg-sub
 ::quiz
 (fn [db _]
   (let [quiz (:quiz db)]
     (update quiz :deadline format-date))))

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
