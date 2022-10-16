(ns top10.css
  (:require [garden.def :refer [defstyles]]
            [garden.selectors :as s]))

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
   [:.icon-button {:cursor "pointer"}]
   [:.icon-button-container {:margin-top "0.25rem"}
    [:.icon-button {:margin-right "0.25rem"}]]
   [:.join-url {:font-family "monospace"
                :margin-right "0.5rem"
                :max-width "100%"
                :overflow-wrap "break-word"
                :white-space "normal"}]
   [:.join-url-container {:align-items "center"
                          :display "flex"
                          :flex-wrap "wrap"}]
   [:.ytEmbeddedContainer {:margin-bottom "8px"
                           :overflow "hidden"
                           :padding-bottom "56.25% !important"
                           :position "relative"
                           :width "100%"}
    [(s/& :.MuiGrid-item) {:margin "8px 8px 0"}]
    [:iframe {:height "100%"
              :left "0"
              :position "absolute"
              :top "0"
              :width "100%"}]]])
