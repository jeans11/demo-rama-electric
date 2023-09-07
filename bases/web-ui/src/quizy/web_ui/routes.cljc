(ns quizy.web-ui.routes
  (:require
   [hyperfiddle.electric :as e]
   [hyperfiddle.history :as history]
   [reitit.core :as r]))

(e/def router)

(defn create-router []
  (r/router
    [["/login" :login]
     ["/signup" :signup]
     ["/board" :board]]))

(defn encode-uri [_router]
  (fn [{:keys [path]}]
    path))

(defn decode-uri [router]
  (fn [uri-str]
    (r/match-by-path router uri-str)))

(e/defn Navigate
  [to params]
  (history/navigate! history/!history (r/match-by-name router to params)))
