(ns dre.belt.interface
  (:require [dre.belt.rama :as belt.rama]
            [dre.belt.date :as belt.date]))

(defn make-reactive-query
  "Take a rama `path` and a rama `pstate` and
   create a missionary flow to observe `path`.
   Use `foreign-proxy-async` under the hood"
  [path pstate]
  (belt.rama/make-reactive-query path pstate))

(defn make-pstates-map
  ""
  [cluster module-name pstates-name]
  (belt.rama/make-pstates-map cluster module-name pstates-name))

(defn make-depots-map
  ""
  [cluster module-name depots-name]
  (belt.rama/make-depots-map cluster module-name depots-name))

(defn now
  "Return the current time `java.time.Instant`"
  []
  (belt.date/now))

(defn plus-seconds
  "Return a new `inst` with the amount of `seconds` added"
  [inst seconds]
  (belt.date/plus-seconds inst seconds))

(defn ->millis
  "Return the milliseconds of the `inst`"
  [inst]
  (belt.date/->millis inst))
