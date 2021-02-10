(ns top10.views.personal-list
  (:require
   [reagent.core :as r]
   [reagent-material-ui.core.button :refer [button]]
   [reagent-material-ui.core.grid :refer [grid]]
   [reagent-material-ui.core.text-field :refer [text-field]]
   [re-frame.core :as rf]
   [top10.events :as events]
   [top10.subs :as subs]
   [top10.views.base :refer [back-to-overview-button event-value]]))

(defn personal-list-page []
  (let [new-url (r/atom nil)]
    (fn [list-id has-draft-status? videos]
      [:div
       [:h1 "Your top 10"]
       (if has-draft-status?
         [:<>
          [:p (str
               "Pick 10 songs that represent your taste in music. "
               "Just copy any YouTube URL from the address bar of your browser and click the button to add a video to your list. "
               "You can use the button below each video to remove it.")]
          [:p (str
               "Once you've added 10 videos, you can submit the list. "
               "You can't change your top 10 after submitting.")]]
         [:p (str
              "These are the 10 songs you picked for this quiz. "
              "Do you think anyone will know they're yours?")])
       [grid {:container true :direction "column" :spacing 2}
        (for [video videos]
          ^{:key (:id video)}
          [grid {:item true}
           [grid {:container true :direction "column"}
            [grid {:item true}
             [:iframe {:allow "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                       :allowFullScreen true
                       :frameBorder "0"
                       :src (:url video)}]]
            (when has-draft-status?
              [grid {:item true}
               [button {:on-click #(rf/dispatch [::events/remove-video (:id video)])} "Remove video"]])]])
        (when has-draft-status?
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
                 (when is-complete? [grid {:item true}
                                     [button {:color "primary"
                                              :on-click #(rf/dispatch [::events/finalize-list list-id])
                                              :variant "contained"} "Submit top 10"]])]]]])])
        [grid {:item true}
         [back-to-overview-button]]]])))

(defn personal-list-page-container []
  [personal-list-page @(rf/subscribe [::subs/active-list]) @(rf/subscribe [::subs/has-draft-status]) @(rf/subscribe [::subs/videos])])