(ns top10.views.quizzes
  (:require
   [reagent-material-ui.core.button :refer [button]]
   [reagent-material-ui.core.grid :refer [grid]]
   [reagent-material-ui.core.link :refer [link]]
   [reagent-material-ui.core.table :refer [table]]
   [reagent-material-ui.core.table-body :refer [table-body]]
   [reagent-material-ui.core.table-cell :refer [table-cell]]
   [reagent-material-ui.core.table-container :refer [table-container]]
   [reagent-material-ui.core.table-head :refer [table-head]]
   [reagent-material-ui.core.table-row :refer [table-row]]
   [re-frame.core :as rf]
   [top10.subs :as subs]))

(defn quizzes-page [quizzes]
  [:div
   [:h1 "All quizzes"]
   [grid {:container true :direction "column" :spacing 2}
    [grid {:item true}
     (if (empty? quizzes)
       [:p "You're not participating in any quizzes right now."]
       [table-container
        [table
         [table-head
          [table-row
           [table-cell "Name"]
           [table-cell "Participation deadline"]
           [table-cell "Personal top 10"]
           [table-cell {:col-span 2} "Action"]]]
         [table-body
          (for [{:keys [id name deadline deadline-has-passed? externalId isActive isCreator personalListHasDraftStatus]} quizzes]
            ^{:key id}
            [table-row
             [table-cell name]
             [table-cell (cond
                           (not isActive) "Quiz has ended"
                           deadline-has-passed? "Closed for participation"
                           :else deadline)]
             [table-cell (if personalListHasDraftStatus "No top 10 submitted" "Top 10 submitted")]
             [table-cell (cond
                           (not isActive) [link {:href (str "#/quiz/" externalId "/results") :color "primary"} "Show results"]
                           (and (not deadline-has-passed?) personalListHasDraftStatus) [link {:href (str "#/quiz/" externalId) :color "primary"} "Submit top 10"]
                           (and deadline-has-passed? (not personalListHasDraftStatus)) [link {:href (str "#/quiz/" externalId) :color "primary"} "Assign top 10's"]
                           :else [link {:href (str "#/quiz/" externalId) :color "primary"} "Show status"])]
             [table-cell (when (and isActive isCreator) [link {:href (str "#/quiz/" externalId "/complete")} "End"])]])]]])]
    [grid {:item true}
     [button {:href "#/create-quiz" :color "primary" :variant "contained"} "Create quiz"]]]])

(defn quizzes-page-container []
  [quizzes-page @(rf/subscribe [::subs/quizzes])])
