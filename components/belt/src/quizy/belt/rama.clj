(ns quizy.belt.rama
  (:require
   [com.rpl.rama :as r]
   [missionary.core :as m])
  (:import
   (com.rpl.rama.diffs ResyncDiff)))

(defn proxy-callback [f]
  (fn [new diff _old]
    (when (and new (not (instance? ResyncDiff diff)))
      (f new))))

(defn make-reactive-query [path pstate]
  (->> (m/observe
        (fn [!]
          ;; Send the current value of the path as init value
          (! (r/foreign-select-one path pstate))
          (let [p (r/foreign-proxy path pstate {:callback-fn (proxy-callback !)})]
            #(.close p))))
       (m/latest (fn [i] i))))
