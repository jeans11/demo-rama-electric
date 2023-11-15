(ns dre.belt.rama
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

(defn make-pstates-map [cluster module-name pstates-name]
  (into {}
        (map (fn [pstate-name]
               [(keyword pstate-name)
                (r/foreign-pstate cluster module-name pstate-name)]))
        pstates-name))

(defn make-depots-map [cluster module-name depots-name]
  (into {}
        (map (fn [depot-name]
               [(keyword depot-name)
                (r/foreign-depot cluster module-name depot-name)]))
        depots-name))
