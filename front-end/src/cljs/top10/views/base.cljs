(ns top10.views.base
  (:require [clojure.string :as string]
            [re-frame.core :as rf]
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

(defn iframe [url]
  [:iframe {:allow "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
            :allowFullScreen true
            :frameBorder "0"
            :src url}])

(def video-url-prefix "https://www.youtube-nocookie.com/embed/")

(defn embedded-video
  ([video]
   (iframe (str video-url-prefix (:referenceId video))))
  ([first-video videos]
   (let [first-video-id (:referenceId first-video)
         ids (map :referenceId videos)
         joined-ids (string/join "," ids)]
     (iframe (str video-url-prefix first-video-id "?playlist=" joined-ids)))))

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
