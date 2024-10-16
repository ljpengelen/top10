(ns top10.core
  (:require ["@sentry/browser" :as Sentry]
            [day8.re-frame.async-flow-fx]
            [day8.re-frame.http-fx]
            [re-frame.core :as rf]
            [reagent.dom.client :as rc]
            [top10.config :as config]
            #_{:clj-kondo/ignore [:unused-namespace]}
            [top10.effects :as effects]
            [top10.events :as events]
            [top10.routes :as routes]
            [top10.views.core :as views]))

(defn dev-setup []
  (when config/debug?
    (println "dev mode")))

(defn set-up-sentry []
  (.init Sentry #js {:dsn "https://af15232561f8401b941d26f709e51f17@o136594.ingest.sentry.io/5403220"
                     :integrations #js [(Sentry/captureConsoleIntegration #js {:levels #js ["error"]})]}))

(defonce root (rc/create-root (.getElementById js/document "app")))

(defn ^:dev/after-load mount-root []
  (rf/clear-subscription-cache!)
  (rc/render root [views/main-panel]))

(defn init []
  (println config/version)
  (dev-setup)
  (set-up-sentry)
  (routes/configure-routes)
  (rf/dispatch-sync [::events/initialize])
  (mount-root))
