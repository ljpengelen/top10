(ns top10.views.assign-list
  (:require ["@material-ui/core" :refer [TextField]]
            [re-frame.core :as rf]
            [reagent-material-ui.components :refer [button grid]]
            [reagent-material-ui.lab.autocomplete :refer [autocomplete]]
            [reagent.core :as r]
            [top10.events :as events]
            [top10.subs :as subs]
            [top10.views.base :refer [embedded-video]]))

(defn assign-list-page []
  (let [assignee (r/atom nil)]
    (fn [quiz-id list-id current-assignee videos participants]
      [:<>
       [:h1 "Assign list"]
       [:p
        "The playlist below contains someone's 10 favorite songs. "
        "Do you know who's top 10 this is? "
        "Until the quiz has ended, you can come back and change your assignment anytime."]
       [:div {:class "ytEmbeddedContainer"}
        [embedded-video (first videos) videos]]
       [:form {:on-submit (fn [event]
                            (.preventDefault event)
                            (when @assignee
                              (rf/dispatch [::events/assign-list quiz-id list-id (.-id @assignee)])))}
        [grid {:container true :direction "column" :spacing 2}
         [grid {:item true :xs 6}
          [autocomplete {:get-option-label (fn [^js option] (.-name option))
                         :get-option-selected (fn [option value] (= (.-id option) (.-id value)))
                         :on-change (fn [_ value] (reset! assignee value))
                         :options participants
                         :render-input (fn [^js params] (r/create-element TextField params))
                         :render-option (fn [^js option] (str (when (seq (.-assignedLists option)) "✓ ") (.-name option)))
                         :required true
                         :value (or @assignee current-assignee)}]]
         [grid {:item true}
          [grid {:container true :spacing 2}
           [grid {:item true}
            [button {:color "primary"
                     :disabled (nil? @assignee)
                     :type "submit"
                     :variant "contained"} "Assign"]]
           [grid {:item true}
            [button {:href (str "/quiz/" quiz-id)} "Back to quiz"]]]]]]])))

(defn assign-list-page-container []
  [assign-list-page
   @(rf/subscribe [::subs/active-quiz])
   @(rf/subscribe [::subs/active-list])
   @(rf/subscribe [::subs/assignee])
   @(rf/subscribe [::subs/videos])
   @(rf/subscribe [::subs/participants-with-lists])])
