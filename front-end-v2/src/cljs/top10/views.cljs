(ns top10.views
  (:require
   [reagent.core :as r]
   ["@date-io/dayjs" :as dayjs-utils]
   [reagent-material-ui.core.button :refer [button]]
   [reagent-material-ui.pickers.date-time-picker :refer [date-time-picker]]
   [reagent-material-ui.core.grid :refer [grid]]
   [reagent-material-ui.core.link :refer [link]]
   [reagent-material-ui.core.table :refer [table]]
   [reagent-material-ui.core.table-body :refer [table-body]]
   [reagent-material-ui.core.table-cell :refer [table-cell]]
   [reagent-material-ui.core.table-container :refer [table-container]]
   [reagent-material-ui.core.table-head :refer [table-head]]
   [reagent-material-ui.core.table-row :refer [table-row]]
   [reagent-material-ui.pickers.mui-pickers-utils-provider :refer [mui-pickers-utils-provider]]
   [reagent-material-ui.core.text-field :refer [text-field]]
   [re-frame.core :as rf]
   [top10.events :as events]
   [top10.subs :as subs]))

(defn home-page []
  (let [checking-status (rf/subscribe [::subs/checking-status])
        logged-in (rf/subscribe [::subs/logged-in])]
    [:div
     [:h1 "Greatest Hits"]
     [:ul
      (when (and (not @checking-status) @logged-in)
        [:li [:a {:href "#" :onClick #(rf/dispatch [::events/log-out])} "Log out"]])
      (when (and (not @checking-status) (not @logged-in))
        [:li [:a {:href "#" :onClick #(rf/dispatch [::events/log-in])} "Log in"]])
      [:li [:a {:href "#/quizzes"} "all quizzes page"]]]]))

(defn quizzes-page [quizzes]
  [:div
   [:h1 "All quizzes"]
   [grid {:container true :direction "column" :spacing 2}
    [grid {:item true}
     [table-container
      [table
       [table-head
        [table-row
         [table-cell "Name"]
         [table-cell "Deadline"]
         [table-cell "Personal list"]
         [table-cell "Action"]]]
       [table-body
        (for [{:keys [id name deadline deadline-has-passed? externalId personalListHasDraftStatus]} quizzes]
          ^{:key id}
          [table-row
           [table-cell name]
           [table-cell (if deadline-has-passed? "Closed for participation" deadline)]
           [table-cell (if personalListHasDraftStatus "No list submitted" "List submitted")]
           [table-cell [link {:href (str "#/quiz/" externalId) :color "primary"} "Show"]]])]]]]
    [grid {:item true}
     [button {:href "#/create-quiz" :color "primary" :variant "contained"} "Create quiz"]]]])

(defn quizzes-page-container []
  [quizzes-page @(rf/subscribe [::subs/quizzes])])

(defn quiz-page [{:keys [name deadline deadline-has-passed? personalListId personalListHasDraftStatus]} number-of-participants]
  [:div
   [:h1 name]
   (if deadline-has-passed?
     [:<>
      [:p "This quiz has reached the final round. "]]
     [:<>
      [:p
       (str 
        "At the moment, this quiz has " number-of-participants " " (if (> number-of-participants 1) "participants" "participant") ". "
        "Anyone who wants to join has until " deadline " to submit their personal top 10.") ]
      (if personalListHasDraftStatus
        [:p (str
             "Remember, you still have to submit your personal top 10 for this quiz! "
             "You can only join the final round when you've submitted a top 10.")]
        [:p (str "You've already submitted your personal top 10 for this quiz.")])
      [grid {:container true :spacing 2}
       [grid {:item true}
        [button {:href (str "#/list/" personalListId) :color "primary" :variant "contained"}
         (if personalListHasDraftStatus "Submit top 10" "View top 10")]]
       [grid {:item true}
        [button {:href "#/quizzes"} "Back to quiz overview"]]]])])

(defn quiz-page-container []
  [quiz-page @(rf/subscribe [::subs/quiz]) @(rf/subscribe [::subs/number-of-participants])])

(defn event-value [^js/Event e] (.. e -target -value))

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
              [button {:href "#/quizzes"} "Back to quiz overview"]]]]]]]]])))

(defn create-list-page []
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
         [button {:href "#/quizzes"} "Back to quiz overview"]]]])))

(defn create-list-page-container []
  [create-list-page @(rf/subscribe [::subs/active-list]) @(rf/subscribe [::subs/has-draft-status]) @(rf/subscribe [::subs/videos])])

(defn- pages [page-name]
  (case page-name
    :home-page [home-page]
    :quizzes-page [quizzes-page-container]
    :quiz-page [quiz-page-container]
    :create-quiz-page [create-quiz-page]
    :create-list-page [create-list-page-container]
    [:div]))

(defn- show-page [page-name] [pages page-name])

(defn main-panel []
  (let [active-page (rf/subscribe [::subs/active-page])]
    [show-page @active-page]))
