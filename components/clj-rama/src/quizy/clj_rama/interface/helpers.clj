(ns quizy.clj-rama.interface.helpers
  (:require [quizy.clj-rama.helpers.topology-utils :as topo-utils]))

(defmacro extract-field [^String field]
  `(topo-utils/extract-field ~field))

(defmacro bind-field [source ^String data ^String field ^String field-var]
  `(topo-utils/bind-field ~source ~data ~field ~field-var))
