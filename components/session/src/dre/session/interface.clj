(ns dre.session.interface
  (:require [dre.session.core :as core]))

(defn export-depots [cluster]
  (core/export-depots cluster))

(defn export-pstates [cluster]
  (core/export-pstates cluster))

(defn get-session-module []
  (core/get-session-module))

(defn send-session [depot session]
  (core/send-session depot session))

(defn send-user-session [depot session-id user-id]
  (core/send-user-session depot session-id user-id))

(defn remove-user-session [depot session-id user-id]
  (core/remove-user-session depot session-id user-id))

(defn send-user-session-vote [depot payload]
  (core/send-user-session-vote depot payload))

(defn get-session-by-id [pstate session-id]
  (core/get-session-by-id pstate session-id))

(defn !latest-users-in-session [pstate session-id]
  (core/!lastest-users-in-session pstate session-id))

(defn !latest-start-at-session [pstate session-id]
  (core/!lastest-start-at-session pstate session-id))

(defn !latest-session-results [pstate session-id]
  (core/!latest-session-results pstate session-id))

(defn !latest-session-status [pstate session-id]
  (core/!latest-session-status pstate session-id))

(defn !latest-session-current-question [pstate session-id]
  (core/!latest-session-current-question pstate session-id))

(defn !latest-session-next-question-at [pstate session-id]
  (core/!latest-session-next-question-at pstate session-id))
