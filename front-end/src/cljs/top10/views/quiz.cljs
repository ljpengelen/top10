(ns top10.views.quiz
  (:require [re-frame.core :as rf]
            [reagent-mui.components :refer [button grid link table table-body table-cell table-container table-head table-row]]
            [reagent.core :as r]
            [top10.config :refer [front-end-base-url]]
            [top10.events :as events]
            [top10.subs :as subs]
            [top10.views.base :refer [back-to-overview-button]]))

(defn quiz-has-ended []
  [:<>
   [:p (str "This quiz has ended")]
   [back-to-overview-button]])

(defn no-list-submitted []
  [:<>
   [:p (str
        "This quiz has reached the final round, "
        "but you haven't submitted a top 10. "
        "That means you can't participate in the final round. "
        "Better luck next time!")]
   [back-to-overview-button]])

(defn assign-lists [quiz-id lists] 
  [:<>
   [:p
    "This quiz has reached the final round. "
    "It's time to assign top 10's to participants."]
   [grid {:container true :direction "column" :spacing 2}
    [grid {:item true}
     [table-container
      [table
       [table-head
        [table-row
         [table-cell "Assigned to"]
         [table-cell "Action"]]]
       [table-body
        (for [{list-id :id assigneeName :assigneeName} lists]
          ^{:key list-id}
          [table-row
           [table-cell (or assigneeName "Not assigned yet")]
           [table-cell [link {:href (str "/quiz/" quiz-id "/list/" list-id "/assign") :color "primary"} "Assign"]]])]]]]
    [grid {:item true}
     [back-to-overview-button]]]])

(defn share-icon []
  [:svg {:xmlns "http://www.w3.org/2000/svg" :height "24" :viewBox "0 0 24 24" :width "24" :fill "#000"}
   [:path {:d "M0 0h24v24H0V0z" :fill "none"}]
   [:path {:d "M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92c0-1.61-1.31-2.92-2.92-2.92zM18 4c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zM6 13c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1zm12 7.02c-.55 0-1-.45-1-1s.45-1 1-1 1 .45 1 1-.45 1-1 1z"}]])

(defn copy-icon [checked?]
  [:svg {:xmlns "http://www.w3.org/2000/svg" :height "24" :viewBox "0 0 24 24" :width "24" :fill "#000"}
   [:path {:d "M0 0h24v24H0V0z", :fill "none"}]
   [:path {:d "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"}]
   [:g
    {:style {:display (when-not checked? "none")} :transform "translate(8, 10)"}
    [:path {:d "M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2z" :stroke "#000" :stroke-width "1.5" :transform "scale(0.45, 0.45)"}]]])

(defn copy-button []
  (let [pressed? (r/atom false)]
    (fn [content]
      [:span {:class "icon-button"
              :on-click (fn [] (reset! pressed? true) (js/navigator.clipboard.writeText content))
              :title "Copy to clipboard"}
       [copy-icon @pressed?]])))

(defn share-button [url]
  (when js/navigator.share
    [:span {:class "icon-button"
            :on-click (fn [] (.catch (js/navigator.share #js {:url url}) #()))
            :title "Share"}
     [share-icon]]))

(defn first-round-in-progress [number-of-participants deadline quiz-id personal-list-has-draft-status? personal-list-id]
  [:<>
   [:p
    (str
     "At the moment, this quiz has " number-of-participants " " (if (= number-of-participants 1) "participant" "participants") ". "
     "Anyone who wants to join has until " deadline " to submit their personal top 10. "
     "If you know anyone who might also want to join, just share the following URL: ")]
   (let [join-url (str front-end-base-url "/quiz/" quiz-id "/join")]
     [:div {:class "join-url-container"}
      [:span {:class "join-url"} join-url]
      [:span {:class "icon-button-container"}
       [copy-button join-url]
       [share-button join-url]]])
   (case personal-list-has-draft-status?
     (true) [:p (str
                 "Remember, you still have to submit your personal top 10 for this quiz! "
                 "You can only take part in the final round after you've submitted a top 10.")]
     (false) [:p (str "You've already submitted your personal top 10 for this quiz.")]
     (nil) [button {:color "primary"
                    :on-click #(rf/dispatch [::events/participate-in-quiz quiz-id])
                    :variant "contained"}
            "Participate in quiz"])
   (when (some? personal-list-has-draft-status?)
     [grid {:container true :spacing 2}
      [grid {:item true}
       [button {:href (str "/quiz/" quiz-id "/list/" personal-list-id "/personal") :color "primary" :variant "contained"}
        (if personal-list-has-draft-status? "Submit top 10" "View top 10")]]
      [grid {:item true}
       [back-to-overview-button]]])])

(defn too-few-participants []
  [:<>
   [:p (str
        "This quiz has reached the final round, but there are only one or two participants. "
        "That means that there's nothing to do in the final round. "
        "Better luck next time!")]
   [back-to-overview-button]])

(defn quiz-page [loading-quiz? loading-quiz-lists? loading-quiz-participants? {:keys [name deadline deadline-has-passed? id isActive personalListId personalListHasDraftStatus]} number-of-participants lists]
  (when-not (or loading-quiz? loading-quiz-lists? loading-quiz-participants?)
    [:<>
     [:h1 name]
     (cond
       (not isActive) [quiz-has-ended]
       (not deadline-has-passed?) [first-round-in-progress number-of-participants deadline id personalListHasDraftStatus personalListId]
       (or (nil? personalListHasDraftStatus) personalListHasDraftStatus) [no-list-submitted]
       (and personalListId (false? personalListHasDraftStatus) (> number-of-participants 2)) [assign-lists id lists]
       :else [too-few-participants])]))

(defn quiz-page-container []
  [quiz-page
   @(rf/subscribe [::subs/loading-quiz?])
   @(rf/subscribe [::subs/loading-quiz-lists?])
   @(rf/subscribe [::subs/loading-quiz-participants?])
   @(rf/subscribe [::subs/quiz])
   @(rf/subscribe [::subs/number-of-participants])
   @(rf/subscribe [::subs/other-quiz-lists])])
