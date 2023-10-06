(ns quizy.clj-rama.interface
  (:require [quizy.clj-rama.core :as core]))

(defmacro expr [ops & args]
  `(core/expr ~ops ~args))

(defn make-reactive-pstate [pstate key]
  (core/make-reactive-pstate pstate key))
