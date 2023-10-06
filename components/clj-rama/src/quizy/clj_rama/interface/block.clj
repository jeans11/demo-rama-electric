(ns quizy.clj-rama.interface.block
  (:require [quizy.clj-rama.block.core :as block]))

(defmacro out [source & args]
  `(block/out ~source ~args))
