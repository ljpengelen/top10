(ns top10.css
  (:require [garden.def :refer [defstyles]]))

(defstyles screen
  [[:h1 {:color "#000"
         :font-size "2.5em"
         :line-height "1"}]
   [:body {:color "#333"
           :font-family "'Helvetica Neue', Verdana, Helvetica, Arial, sans-serif"
           :font-size "1.125rem"
           :margin "0 auto"
           :max-width "30rem"
           :padding "4rem 2rem"
           :line-height "1.75rem"}]])
