(ns top10.views
  (:import (goog.i18n DateTimeSymbols_en_US))
  (:require
   [reagent.core :as r]
   [reagent-material-ui.cljs-time-utils :refer [cljs-time-utils]]
   [reagent-material-ui.core.button :refer [button]]
   [reagent-material-ui.pickers.date-picker :refer [date-picker]]
   [reagent-material-ui.core.grid :refer [grid]]
   [reagent-material-ui.core.link :refer [link]]
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
      [:li [:a {:href "#/quizzes"} "all quizzes page"]]
      [:li [:a {:href "#/quiz/1234"} "single quiz page"]]]]))

(defn quizzes-page [quizzes]
  [:div
   [:h1 "All quizzes"]
   [:ul
    (for [quiz quizzes]
      ^{:key (:id quiz)} [:li (:name quiz)])]
   [:ul
    [:li [:a {:href "#/"} "Go home"]]
    [:li [:a {:href "#/create-quiz"} "Create quiz"]]]])

(defn quizzes-page-container []
  [quizzes-page @(rf/subscribe [::subs/quizzes])])

(defn quiz-page []
  [:div
   [:h1 "Single quiz"]
   [:div
    [:a {:href "#/"} "go to Home Page"]]])

(defn submit-quiz [event] (js/console.log event)(.preventDefault event))

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
        "After the deadline has passed, all participants must have submitted their top 10."]
       [:p "After everyone has submitted their top 10, it's time to start matching people and top 10s."]
       [:div
        [mui-pickers-utils-provider {:utils cljs-time-utils :locale DateTimeSymbols_en_US}
         [:form {:on-submit (fn [event]
                              (.preventDefault event)
                              (if-not @deadline
                                (reset! deadline-error true)
                                (do
                                  (rf/dispatch [::events/create-quiz {:name @name :deadline @deadline}])
                                  (rf/dispatch [::events/redirect "/quizzes"]))))}
          [grid {:container true :direction "column" :spacing 3}
           [grid {:item true}
            [text-field {:label "Name" :value @name :required true :on-change #(reset! name (event-value %))}]]
           [grid {:item true}
            [date-picker {:error @deadline-error
                          :disablePast true
                          :format "MMMM d, yyyy"
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

(defn- pages [page-name]
  (case page-name
    :home-page [home-page]
    :quizzes-page [quizzes-page-container]
    :quiz-page [quiz-page]
    :create-quiz-page [create-quiz-page]
    [:div]))

(defn- show-page [page-name] [pages page-name])

(defn main-panel []
  (let [active-page (rf/subscribe [::subs/active-page])]
    [show-page @active-page]))
