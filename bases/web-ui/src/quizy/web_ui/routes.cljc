(ns quizy.web-ui.routes
  (:require
   [hyperfiddle.electric :as e]
   [missionary.core :as m]
   [reitit.core :as r]
   [reitit.frontend.easy :as rfe]))

(e/def route-match)
(e/def route-name)

(def router
  (r/router
   [["/login" :login]
    ["/signup" :signup]
    ["/quizzes" :quizzes]
    ["/quizzes/:id" :quiz]
    ["/sessions/:id" :session]]))

(e/def re-router
  (->> (m/observe
        (fn [!]
          (rfe/start!
           router
           !
           {:use-fragment false})))
       (m/relieve {})
       new))

(defn encode-uri [_router]
  (fn [{:keys [path]}]
    path))

(defn decode-uri [router]
  (fn [uri-str]
    (r/match-by-path router uri-str)))

(e/defn Navigate
  [to params]
  (rfe/navigate to params))
