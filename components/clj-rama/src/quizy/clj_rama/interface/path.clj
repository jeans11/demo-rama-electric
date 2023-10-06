(ns quizy.clj-rama.interface.path
  (:refer-clojure :exclude [key])
  (:require [quizy.clj-rama.path.core :as path]))

(defmacro key [& args]
  `(path/key ~args))
