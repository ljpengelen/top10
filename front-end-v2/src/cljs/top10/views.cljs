(ns top10.views
  (:require
   [re-frame.core :as rf]
   [top10.events :as events]
   [top10.subs :as subs]))

(defn home-panel []
  (let [checking-status (rf/subscribe [::subs/checking-status])
        logged-in (rf/subscribe [::subs/logged-in])]
    [:div
     [:h1 "Greatest Hits"]
     [:ul
      (when (and (not @checking-status) @logged-in)
        [:li [:a {:href "#" :onClick #(rf/dispatch [::events/log-out])} "Log out"]])
      (when (and (not @checking-status) (not @logged-in))
        [:li [:a {:href "#" :onClick #(rf/dispatch [::events/log-in])} "Log in"]])
      [:li [:a {:href "#/quiz/1234"} "quiz page"]]]]))

(defn about-panel []
  [:div
   [:h1 "This is the About Page."]
   [:div
    [:a {:href "#/"}
     "go to Home Page"]]])

(defn- panels [panel-name]
  (case panel-name
    :home-panel [home-panel]
    :about-panel [about-panel]
    [:div]))

(defn- show-panel [panel-name] [panels panel-name])

(defn main-panel []
  (let [active-panel (rf/subscribe [::subs/active-panel])]
    [show-panel @active-panel]))
