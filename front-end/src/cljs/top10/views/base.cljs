(ns top10.views.base
  (:require [clojure.string :as string]
            [re-frame.core :as rf]
            [reagent-mui.components :refer [button dialog
                                            dialog-actions dialog-content
                                            dialog-content-text dialog-title]]
            [top10.config :as config]
            [top10.events :as events]
            [top10.subs :as subs]))

(defn back-to-overview-button []
  [button {:href "/quizzes"} "Show quiz overview"])

(defn event-value [^js/Event e] (.. e -target -value))

(defn log-in-url
  ([provider]
   (log-in-url provider (js/window.location.href.replace js/window.location.origin "")))
  ([provider landing-page]
   (let [{:keys [client-id endpoint redirect-uri scope]} (provider config/oauth2)]
     (str
      endpoint "?"
      "response_type=code&"
      "scope=" scope "&"
      "redirect_uri=" redirect-uri "&"
      "state=" (js/encodeURIComponent landing-page) "&"
      "client_id=" client-id))))

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
