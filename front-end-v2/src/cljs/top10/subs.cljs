(ns top10.subs
  (:require
   [cljs-time.format :as tf]
   [re-frame.core :as rf]))

(rf/reg-sub
 ::active-page
 (fn [db _]
   (:active-page db)))

(rf/reg-sub
 ::active-quiz
 (fn [db _]
   (:active-quiz db)))

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
     (if quiz (update quiz :deadline format-date) nil))))

(rf/reg-sub
 ::list
 (fn [db _]
   (:list db)))

(rf/reg-sub
 ::active-list
 (fn [db _]
   (:active-list db)))

(rf/reg-sub
 ::videos
 :<- [::list]
 (fn [list _]
   (:videos list)))

(rf/reg-sub
 ::has-draft-status
 :<- [::list]
 (fn [list _]
   (:hasDraftStatus list)))

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
