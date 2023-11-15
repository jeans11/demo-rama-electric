(ns ^:dev/always dre.web-ui.app
  (:require
   [hyperfiddle.electric :as e]
   [dre.web-ui.core :as core]))

(def electric-main
  (e/boot (core/App.)))

(defonce reactor nil)

(defn ^:dev/after-load ^:export start! []
  (assert (nil? reactor) "reactor already running")
  (set! reactor (electric-main
                 #(js/console.log "Reactor success:" %)
                 #(js/console.error "Reactor failure:" %))))

(defn ^:dev/before-load stop! []
  (when reactor (reactor)) ; teardown
  (set! reactor nil))
