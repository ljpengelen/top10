(ns top10.views.assign-list
  (:require ["@material-ui/core" :refer [TextField]]
            [re-frame.core :as rf]
            [reagent-material-ui.core.button :refer [button]]
            [reagent-material-ui.core.grid :refer [grid]]
            [reagent-material-ui.lab.autocomplete :refer [autocomplete]]
            [reagent.core :as r]
            [top10.events :as events]
            [top10.subs :as subs]))

(defn assign-list-page []
  (let [assignee (r/atom nil)]
    (fn [quiz-id list-id videos participants]
      [:<>
       [:h1 "Assign list"]
       [grid {:container true :direction "column" :spacing 2}
        (for [video videos]
          ^{:key (:id video)}
          [grid {:item true}
           [:iframe {:allow "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                     :allowFullScreen true
                     :frameBorder "0"
                     :src (:url video)}]])
        [grid {:item true}
         [:form {:on-submit (fn [event]
                              (.preventDefault event)
                              (when @assignee
                                (rf/dispatch [::events/assign-list quiz-id list-id (.-id @assignee)])))}
          [grid {:container true :direction "column" :spacing 2}
           [grid {:item true :xs 6}
            [autocomplete {:get-option-label (fn [option] (.-name option))
                           :get-option-selected (fn [option value] (= (.-id option) (.-id value)))
                           :on-change (fn [_ value] (reset! assignee value))
                           :options participants
                           :render-input (fn [^js params] (r/create-element TextField params))
                           :required true}]]
           [grid {:item true}
            [grid {:container true :spacing 2}
             [grid {:item true}
              [button {:color "primary"
                       :disabled (nil? @assignee)
                       :type "submit"
                       :variant "contained"} "Assign"]]
             [grid {:item true}
              [button {:href (str "#/quiz/" quiz-id)} "Back to quiz"]]]]]]]]])))

(defn assign-list-page-container []
  [assign-list-page
   @(rf/subscribe [::subs/active-quiz])
   @(rf/subscribe [::subs/active-list])
   @(rf/subscribe [::subs/videos])
   @(rf/subscribe [::subs/participants-with-lists])])
