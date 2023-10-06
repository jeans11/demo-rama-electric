(ns quizy.user.interface
  (:require [quizy.user.core :as core]))

(defn get-signup-depot [cluster]
  (core/get-signup-depot cluster))

(defn get-accounts-pstate [cluster]
  (core/get-accounts-pstate cluster))

(defn get-emails-pstate [cluster]
  (core/get-emails-pstate cluster))

(defn get-user-by-id [pstate user-id]
  (core/get-user-by-id pstate user-id))

(defn send-signup [depot payload]
  (core/send-signup depot payload))

(defn check-signup [pstate id]
  (core/check-signup pstate id))

(defn login [pstates payload]
  (core/login pstates payload))

(defn get-signup-module []
  (core/get-signup-module))
