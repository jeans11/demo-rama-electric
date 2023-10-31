(ns quizy.belt.interface
  (:require [quizy.belt.rama :as belt.rama]))

(defn make-reactive-query
  "Take a rama `path` and a rama `pstate` and
   create a missionary flow to observe `path`.
   Use `foreign-proxy-async` under the hood"
  [path pstate]
  (belt.rama/make-reactive-query path pstate))
