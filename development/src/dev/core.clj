(ns dev.core
  (:require
   [dev.css :as css]
   [quizy.server.core :as server]
   [shadow.cljs.devtools.api :as shadow.api]
   [shadow.cljs.devtools.server :as shadow.server]))

(defonce server nil)

(defn start!
  [& _args]
  (println "Starting compiler...")
  (shadow.server/start!)
  (shadow.api/watch :app)
  (println "Start css watcher...")
  (css/go)
  (println "Starting server...")
  (alter-var-root #'server (constantly (server/start-server!)))
  :idle)

(comment
  ;; Launch the app
  (start!)
  (.stop server)
  (shadow.server/stop!)

  nil)
