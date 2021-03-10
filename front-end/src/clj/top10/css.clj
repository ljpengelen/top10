(ns top10.css
  (:require [garden.def :refer [defstyles]]
            [garden.selectors :refer [&]]))

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
   [:.join-url {:overflow-wrap "break-word"
                :white-space "normal"}]
   [:.ytEmbeddedContainer {:margin-bottom "8px"
                           :overflow "hidden"
                           :padding-bottom "56.25% !important"
                           :position "relative"
                           :width "100%"}
    [(& :.MuiGrid-item) {:margin "8px 8px 0"}]
    [:iframe {:height "100%"
              :left "0"
              :position "absolute"
              :top "0"
              :width "100%"}]]])
