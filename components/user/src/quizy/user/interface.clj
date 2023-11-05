(ns quizy.user.interface
  (:require [quizy.user.core :as core]))

(defn export-depots [cluster]
  (core/export-depots cluster))

(defn export-pstates [cluster]
  (core/export-pstates cluster))

(defn get-user-by-id [pstate user-id]
  (core/get-user-by-id pstate user-id))

(defn send-signup [depot payload]
  (core/send-signup depot payload))

(defn check-signup [pstate id]
  (core/check-signup pstate id))

(defn login [pstates payload]
  (core/login pstates payload))

(defn get-user-module []
  (core/get-user-module))
