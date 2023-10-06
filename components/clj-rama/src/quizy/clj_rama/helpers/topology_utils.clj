(ns quizy.clj-rama.helpers.topology-utils
  (:require [quizy.clj-rama.block.core :as block])
  (:import
   (com.rpl.rama.helpers TopologyUtils$ExtractJavaField)))

(defmacro extract-field [^String field]
  `(TopologyUtils$ExtractJavaField. ~field))

(defmacro bind-field [source data field field-var]
  `(-> (.each ~source (extract-field ~field) ~data)
       (block/out [~field-var])))

(comment

  nil)
