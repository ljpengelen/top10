(ns top10.views.personal-list
  (:require [re-frame.core :as rf]
            [reagent-mui.components :refer [button grid text-field]]
            [reagent.core :as r]
            [top10.events :as events]
            [top10.subs :as subs]
            [top10.views.base :refer [embedded-video event-value]]))

(defn back-to-quiz-button [quiz-id]
  [button {:href (str "/quiz/" quiz-id)} "Show quiz"])

(defn finalized-list [quiz-id videos]
  [:<>
   [:p
    "This is a playlist of the 10 songs you picked for this quiz. "
    "Do you think anyone will know they're yours?"]
   [:div {:class "ytEmbeddedContainer"}
    [embedded-video (first videos) videos]]
   [back-to-quiz-button quiz-id]])

(defn draft-list []
  (let [new-url (r/atom nil)]
    (fn [quiz-id list-id videos]
      [:<>
       [:p
        "Pick 10 songs that represent your taste in music. "
        "Just copy any YouTube URL from the address bar of your browser and click the button to add a video to your list. "
        "You can use the button below each video to remove it."]
       [:p
        "Once you've added 10 videos, you can submit the list. "
        "You can't change your top 10 after submitting."]
       [grid {:container true :direction "column" :spacing 2}
        (for [video videos]
          ^{:key (:id video)}
          [:<>
           [grid {:class "ytEmbeddedContainer" :item true}
            [embedded-video video]]
           [grid {:item true}
            [button {:on-click #(rf/dispatch [::events/remove-video (:id video)])} "Remove video"]]])
        [grid {:item true}
         (let [is-complete? (= 10 (count videos))]
           [:form {:on-submit (fn [event]
                                (.preventDefault event)
                                (when @new-url
                                  (rf/dispatch [::events/add-video list-id @new-url])
                                  (reset! new-url nil)))}
            [grid {:container true :direction "column" :spacing 2}
             [grid {:item true}
              [text-field {:disabled is-complete?
                           :fullWidth true
                           :label "YouTube URL"
                           :on-change #(reset! new-url (event-value %))
                           :required true
                           :value @new-url}]]
             [grid {:item true}
              [grid {:container true :spacing 2}
               [grid {:item true}
                [button {:color "primary"
                         :disabled is-complete?
                         :type "submit"
                         :variant "contained"} "Add video"]]
               [grid {:item true}
                [button {:color "primary"
                         :disabled (not is-complete?)
                         :on-click #(rf/dispatch [::events/finalize-list quiz-id list-id])
                         :variant "contained"} "Submit top 10"]]]]]])]
        [grid {:item true}
         [back-to-quiz-button quiz-id]]]])))

(defn deadline-has-passed [quiz-id]
    [:<>
     [:p
      "You can't submit a top 10 for this quiz anymore because its deadline has passed. "
      "Better luck next time!"]
     [back-to-quiz-button quiz-id]])

(defn personal-list-page [quiz-id list-id has-draft-status? videos {:keys [deadline-has-passed?]}]
  [:<>
   [:h1 "Your top 10"]
   (cond
     (not has-draft-status?) [finalized-list quiz-id videos]
     (not deadline-has-passed?) [draft-list quiz-id list-id videos]
     :else [deadline-has-passed quiz-id])])

(defn personal-list-page-container []
  [personal-list-page
   @(rf/subscribe [::subs/active-quiz])
   @(rf/subscribe [::subs/active-list])
   @(rf/subscribe [::subs/has-draft-status])
   @(rf/subscribe [::subs/videos])
   @(rf/subscribe [::subs/quiz])])
