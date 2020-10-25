(ns top10.subs
  (:require
   ["dayjs" :as dayjs]
   [re-frame.core :as rf]))

(rf/reg-sub
 ::active-page
 (fn [db _]
   (:active-page db)))

(rf/reg-sub
 ::active-quiz
 (fn [db _]
   (:active-quiz db)))

(defn extend-quiz [quiz]
  (let [deadline (:deadline quiz)
        dayjs-deadline (dayjs deadline)
        formatted-deadline (.format dayjs-deadline "MMMM D, YYYY HH:mm")
        deadline-has-passed? (.isAfter (dayjs) dayjs-deadline)]
    (-> quiz
        (assoc :deadline formatted-deadline)
        (assoc :deadline-has-passed? deadline-has-passed?))))

(rf/reg-sub
 ::quizzes
 (fn [db _]
   (let [quizzes (:quizzes db)]
     (map extend-quiz quizzes))))

(rf/reg-sub
 ::quiz
 (fn [db _]
   (if-let [quiz (:quiz db)]
     (extend-quiz quiz))))

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
 ::quiz-participants
 (fn [db _]
   (:quiz-participants db)))

(rf/reg-sub
 ::number-of-participants
 :<- [::quiz-participants]
 (fn [quiz-participants _]
   (count quiz-participants)))

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
