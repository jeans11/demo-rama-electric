(ns quizy.clj-rama.block.core)

(defmacro out [source args]
  (let [args (vec args)]
    `(.out ~source (into-array String ~args))))
