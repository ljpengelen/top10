(ns top10.css
  (:require [garden.def :refer [defstyles]]))

(defstyles screen
  [[:h1 {:color "#000"
         :font-size "2.5em"
         :line-height "1"}]
   [:body {:color "#333"
           :font-family "'Helvetica Neue', Verdana, Helvetica, Arial, sans-serif"
           :font-size "1.125rem"
           :line-height "1.75rem"
           :margin "0 auto"
           :max-width "40rem"
           :padding "4rem 2rem"}]
   [:.ytEmbeddedContainer {:height "0"
                           :max-width "100%"
                           :overflow "hidden"
                           :padding-bottom "56.25% !important"
                           :position "relative"}]
   [:.ytEmbedded {:height "100%"
                  :left "0"
                  :position "absolute"
                  :top "0"
                  :width "100%"}]])
