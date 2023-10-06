(ns quizy.server.core
  (:require [quizy.server.impl :as impl]
            [quizy.server.rama :as rama])
  (:gen-class))

(defn start-server!
  []
  (let [config {:host "0.0.0.0"
                :port 8080
                :resources-path "web-ui/public"
                :manifest-path "web-ui/public/js/manifest.edn"}]
    (rama/start-the-engine!)
    (impl/start-server! config)))

(defn -main [& _args]
  (start-server!))
