(ns quizy.belt.rama
  (:require
   [com.rpl.rama :as r]
   [missionary.core :as m]))

(defn proxy-callback [f]
  (fn [new _diff _old]
    (f new)))

(defn make-reactive-query [path pstate]
  (->> (m/observe
        (fn [!]
          ;; Init
          (! nil)
          (let [cp (r/foreign-proxy-async path pstate {:callback-fn (proxy-callback !)})]
            #(.close @cp))))
       (m/relieve {})))
