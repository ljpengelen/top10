(ns top10.subs
  (:require ["dayjs" :as dayjs]
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
     (->> quizzes
          (sort-by (fn [quiz] (-> quiz :deadline dayjs .unix)) >)
          (map extend-quiz)))))

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
 ::list-creator-name
 :<- [::list]
 (fn [list _]
   (:creatorName list)))

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
 ::participants-with-lists
 :<- [::quiz-participants]
 (fn [quiz-participants _]
   (filter #(not (:listHasDraftStatus %)) quiz-participants)))

(rf/reg-sub
 ::quiz-lists
 (fn [db _]
   (:quiz-lists db)))

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
 ::logged-in?
 :<- [::session]
 (fn [session _]
   (:logged-in session)))

(rf/reg-sub
 ::quiz-results
 (fn [db _]
   (:quiz-results db)))

(rf/reg-sub
 ::external-account-id
 (fn [db _]
   (:external-account-id db)))

(rf/reg-sub
 ::personal-results
 :<- [::external-account-id]
 :<- [::quiz-results]
 (fn [[external-account-id quiz-results]]
   (get-in quiz-results [:personalResults (keyword external-account-id)])))

(rf/reg-sub
 ::show-dialog?
 (fn [db _]
   (get-in db [:dialog :show?])))

(rf/reg-sub
 ::dialog-text
 (fn [db _]
   (get-in db [:dialog :text])))

(rf/reg-sub
 ::dialog-title
 (fn [db _]
   (get-in db [:dialog :title])))
