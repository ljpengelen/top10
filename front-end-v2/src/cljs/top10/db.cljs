(ns top10.db)

(def default-db
  {:active-page :home-page
   :session {:checking-status true
             :logged-in false}
   :quizzes []
   :quiz-lists []})
