(ns top10.db (:require [cljs.spec.alpha :as s]))

(s/def ::loading-lists? boolean?)
(s/def ::loading-quiz-lists? boolean?)

(s/def :quiz-list/id string?)
(s/def :quiz-list/isActiveQuiz boolean?)
(s/def :quiz-list/isOwnList boolean?)
(s/def ::quiz-list (s/keys :req-un [:quiz-list/id
                                    :quiz-list/isActiveQuiz
                                    :quiz-list/isOwnList]))
(s/def ::quiz-lists (s/coll-of ::quiz-list))

(s/def ::loading-quiz? boolean?)
(s/def ::active-list? string?)

(s/def :participant/id string?)
(s/def :participant/name string?)
(s/def :participant/listHasDraftStatus boolean?)
(s/def :participant/assignedLists (s/coll-of string?))
(s/def :participant/isOwnAccount boolean?)
(s/def ::quiz-participant (s/keys :req-un [:participant/id
                                           :participant/name
                                           :participant/listHasDraftStatus
                                           :participant/assignedLists
                                           :participant/isOwnAccount]))
(s/def ::quiz-participants (s/coll-of ::quiz-participant))

(s/def ::logged-in? boolean?)
(s/def ::account-id string?)
(s/def ::active-page #{:blank
                       :personal-results-page
                       :quizzes-page
                       :quiz-page
                       :join-quiz-page
                       :quiz-results-page
                       :complete-quiz-page
                       :create-quiz-page
                       :list-page
                       :personal-list-page
                       :assign-list-page
                       :home-page})

(s/def :list/quizId string?)
(s/def :list/hasDraftStatus (s/nilable boolean?))
(s/def :list/assigneeId string?)
(s/def :list/videos (s/coll-of string?))
(s/def :list/isActiveQuiz boolean?)
(s/def :list/creatorName string?)
(s/def :list/isOwnList boolean?)
(s/def :list/id string?)
(s/def :list/creatorId string?)
(s/def :list/assigneeName string?)
(s/def list (s/keys :req-un [:list/quizId
                             :list/hasDraftStatus
                             :list/assigneeId
                             :list/videos
                             :list/isActiveQuiz
                             :list/creatorName
                             :list/isOwnList
                             :list/id
                             :list/creatorId
                             :list/assigneeName]))

(s/def ::active-quiz string?)

(s/def :quiz/id string?)
(s/def :quiz/name string?)
(s/def :quiz/isActive boolean?)
(s/def :quiz/creatorId string?)
(s/def :quiz/isCreator boolean?)
(s/def :quiz/deadline string?)
(s/def :quiz/personalListId string?)
(s/def :quiz/personalListHasDraftStatus boolean?)
(s/def ::quiz (s/keys :req-un [:quiz/id
                               :quiz/name
                               :quiz/isActive
                               :quiz/creatorId
                               :quiz/isCreator
                               :quiz/deadline]
                      :opt-un [:quiz/personalListId
                               :quiz/personalListHasDraftStatus]))
(s/def ::quizzes (s/coll-of ::quiz))

(s/def :dialog/show? boolean?)
(s/def :dialog/title string?)
(s/def :dialog/text string?)
(s/def ::dialog (s/keys :req-un [:dialog/show?]
                        :opt-un [:dialog/title
                                 :dialog/text]))

(s/def :quiz-results/quizId string?)
(s/def :personal-results/accountId string?)
(s/def :personal-results/name string?)
(s/def :assignment/listId string?)
(s/def :assignment/assigneeId (s/nilable string?))
(s/def :assignment/assigneeName (s/nilable string?))
(s/def :assignment/creatorId string?)
(s/def :assignment/creatorName string?)
(s/def ::assignment (s/keys :req-un [:assignment/listId
                                     :assignment/assigneeId
                                     :assignment/assigneeName
                                     :assignment/creatorId
                                     :assignment/creatorName]))
(s/def :personal-results/correctAssignments (s/coll-of ::assignment))
(s/def :personal-results/incorrectAssignments (s/coll-of ::assignment))
(s/def ::personalResults (s/keys :req-un [:personal-results/accountId
                                          :personal-results/name
                                          :personal-results/correctAssignments
                                          :personal-results/incorrectAssignments]))
(s/def :quiz-results/personalResults (s/map-of keyword? ::personalResults))
(s/def :ranking/rank pos?)
(s/def :ranking/accountId string?)
(s/def :ranking/name string?)
(s/def :ranking/numberOfCorrectAssignments nat-int?)
(s/def ::ranking (s/keys :req-un [:ranking/rank
                                  :ranking/accountId
                                  :ranking/name
                                  :ranking/numberOfCorrectAssignments]))
(s/def :quiz-results/rankings (s/coll-of ::ranking))
(s/def ::quiz-results (s/keys :req-un [:quiz-results/quizId
                                       :quiz-results/personalResults
                                       :quiz-results/ranking]))

(s/def ::loading-quiz-participants? boolean?)

(s/def ::db (s/keys :req-un [::loading-list?
                             ::loading-quiz-lists?
                             ::quiz-lists
                             ::loading-quiz?
                             ::quiz-participants
                             ::logged-in?
                             ::active-page
                             ::dialog
                             ::loading-quiz-participants?]
                    :opt-un [::active-list
                             ::account-id
                             ::list
                             ::active-quiz
                             ::quizzes
                             ::quiz
                             ::quiz-results]))

(def default-db
  {:loading-list? false
   :loading-quiz-lists? false
   :quiz-lists []
   :loading-quiz? false
   :quiz-participants []
   :logged-in? false
   :active-page :blank
   :dialog {:show? false}
   :loading-quiz-participants? false})
