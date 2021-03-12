(ns top10.db)

(def default-db
  {:active-page :blank
   :logged-in? false
   :quizzes nil
   :quiz-lists []
   :quiz-participants []
   :dialog {:show? false}})
