(ns quizy.server.core
   (:gen-class))

(defn start-server!
  []
  (let [config {:host "0.0.0.0"
                :port 8080
                :resources-path "web-ui/public"
                :manifest-path "web-ui/public/js/manifest.edn"}]
    #_(impl/start-server! config)))

(defn -main [& _args]
  (start-server!))
