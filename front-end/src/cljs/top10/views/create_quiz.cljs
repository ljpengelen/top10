(ns top10.views.create-quiz
  (:require
   [reagent.core :as r]
   ["@date-io/dayjs" :as dayjs-utils]
   [reagent-material-ui.core.button :refer [button]]
   [reagent-material-ui.pickers.date-time-picker :refer [date-time-picker]]
   [reagent-material-ui.core.grid :refer [grid]]
   [reagent-material-ui.core.text-field :refer [text-field]]
   [reagent-material-ui.pickers.mui-pickers-utils-provider :refer [mui-pickers-utils-provider]]
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
       [:p "After everyone has submitted their top 10, it's time to start matching people and top 10s."]
       [:div
        [mui-pickers-utils-provider {:utils dayjs-utils}
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
                               :fullWidth true
                               :format "MMMM D, YYYY HH:mm"
                               :label "Deadline"
                               :on-change (fn [new-deadline]
                                            (reset! deadline-error false)
                                            (reset! deadline new-deadline))
                               :required true
                               :value @deadline}]]
           [grid {:item true}
            [grid {:container true :direction "row" :spacing 2}
             [grid {:item true}
              [button {:type "submit" :color "primary" :variant "contained"} "Create quiz"]]
             [grid {:item true}
              [back-to-overview-button]]]]]]]]])))
