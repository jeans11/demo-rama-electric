(ns quizy.clj-rama.path.core
  (:refer-clojure :exclude [key])
  (:import (com.rpl.rama Path)))

(defmacro key [args]
  (let [args (vec args)]
    `(Path/key (into-array Object ~args))))
