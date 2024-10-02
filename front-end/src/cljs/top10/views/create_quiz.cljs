(ns top10.views.create-quiz
  (:require
   [reagent.core :as r] 
   ["@mui/x-date-pickers/AdapterDayjs" :refer [AdapterDayjs]]
   [reagent-mui.components :refer [button grid text-field]]
   [reagent-mui.x.date-time-picker :refer [date-time-picker]]
   [reagent-mui.x.localization-provider :refer [localization-provider]]
   [re-frame.core :as rf]
   [top10.events :as events]
   [top10.views.base :refer [back-to-overview-button event-value]]))

(defn create-quiz-page []
  (let [name (r/atom "")
        deadline (r/atom nil)
        deadline-error (r/atom false)]
    (fn []
      [:div
       [:h1 "New quiz"]
       [:p
        "Pick a name and a participation deadline for your new quiz. "
        "Everyone who wants to participate must have submitted their top 10 before the deadline has passed."]
       [:p
        "After everyone has submitted their top 10, it's time to start matching people and top 10s. "
        "You, the creator of this quiz, can end the quiz at any time. "
        "Once you do that, the end results will be available for all participants."]
       [:div
        [localization-provider {:dateAdapter AdapterDayjs}
         [:form {:on-submit (fn [event]
                              (.preventDefault event)
                              (if-not @deadline
                                (reset! deadline-error true)
                                (rf/dispatch [::events/create-quiz {:name @name :deadline @deadline}])))}
          [grid {:container true :direction "column" :spacing 3}
           [grid {:item true :xs 6}
            [text-field {:fullWidth true
                         :label "Name"
                         :on-change #(reset! name (event-value %))
                         :required true
                         :value @name}]]
           [grid {:item true :xs 6}
            [date-time-picker {:autoOk true
                               :ampm false
                               :disablePast true
                               :error @deadline-error
                               :format "MMMM DD, YYYY HH:mm"
                               :label "Deadline"
                               :on-change (fn [new-deadline]
                                            (reset! deadline-error false)
                                            (reset! deadline new-deadline))
                               :required true
                               :slotProps #js {:textField #js {:error @deadline-error
                                                               :fullWidth true}}
                               :value @deadline}]]
           [grid {:item true}
            [grid {:container true :direction "row" :spacing 2}
             [grid {:item true}
              [button {:type "submit" :color "primary" :variant "contained"} "Create quiz"]]
             [grid {:item true}
              [back-to-overview-button]]]]]]]]])))
