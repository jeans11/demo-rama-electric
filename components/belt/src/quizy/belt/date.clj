(ns quizy.belt.date
  (:import (java.time Instant)))

(defn now []
  (Instant/now))

(defn plus-seconds [inst seconds]
  (.plusSeconds inst seconds))

(defn ->millis [inst]
  (.toEpochMilli inst))
