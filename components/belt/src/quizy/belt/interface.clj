(ns quizy.belt.interface
  (:require [quizy.belt.rama :as belt.rama]))

(defn make-reactive-query [path pstate]
  (belt.rama/make-reactive-query path pstate))
