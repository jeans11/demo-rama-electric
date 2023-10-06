(ns quizy.clj-rama.core
  (:require
   [missionary.core :as m])
  (:import
   (com.rpl.rama Expr ProxyState$Callback Path)
   (com.rpl.rama.diffs ResyncDiff)))

(defmacro expr [ops args]
  (let [args (vec args)]
    `(Expr. ~ops (into-array Object ~args))))

(deftype ProxyCallback [f]
  ProxyState$Callback
  (change [_ new diff _old]
    (when (and new (not (instance? ResyncDiff diff)))
      (f new))))

(defn make-reactive-pstate [pstate path]
  (->> (m/observe
        (fn [!]
          ;; Send the current value of the path as init value
          (! (.selectOne pstate path))
          (let [p (.proxy pstate path (->ProxyCallback !))]
            #(.close p))))
       (m/latest (fn [i] i))))
