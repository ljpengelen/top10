(ns top10.core
  (:require [day8.re-frame.async-flow-fx]
            [day8.re-frame.http-fx]
            [re-frame.core :as rf]
            [reagent.dom :as rdom]
            [top10.config :as config]
            [top10.effects :as effects]
            [top10.events :as events]
            [top10.routes :as routes]
            [top10.views.core :as views]))

(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn ^:dev/after-load mount-root []
  (rf/clear-subscription-cache!)
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/main-panel] root-el)))

(defn init []
  (dev-setup)
  (routes/configure-routes)
  (rf/dispatch-sync [::events/initialize])
  (mount-root))
