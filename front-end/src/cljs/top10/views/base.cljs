(ns top10.views.base
  (:require [re-frame.core :as rf]
            [reagent-material-ui.components :refer [button dialog dialog-actions dialog-content dialog-content-text dialog-title]]
            [top10.config :as config]
            [top10.events :as events]
            [top10.subs :as subs]))

(defn back-to-overview-button []
  [button {:href "/quizzes"} "Show quiz overview"])

(defn event-value [^js/Event e] (.. e -target -value))

(defn log-in-url
  ([]
   (log-in-url (js/window.location.href.replace js/window.location.origin "")))
  ([landing-page]
   (str
    "https://accounts.google.com/o/oauth2/v2/auth?"
    "response_type=code&"
    "scope=openid email profile&"
    "redirect_uri=" config/oauth2-redirect-uri "&"
    "state=" (js/encodeURIComponent landing-page) "&"
    "client_id=" config/oauth2-client-id)))

(defn base-page [content]
  (let [show-dialog? @(rf/subscribe [::subs/show-dialog?])
        text @(rf/subscribe [::subs/dialog-text])
        title @(rf/subscribe [::subs/dialog-title])]
    [:<>
     content
     [dialog {:open show-dialog?}
      [dialog-title title]
      [dialog-content
       [dialog-content-text text]]
      [dialog-actions
       [button {:on-click #(rf/dispatch [::events/dismiss-dialog])}"OK"]]]]))
