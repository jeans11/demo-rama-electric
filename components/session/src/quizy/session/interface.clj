(ns quizy.session.interface
  (:require [quizy.session.core :as core]))

(defn get-session-depot [cluster]
  (core/get-session-depot cluster))

(defn get-session-users-depot [cluster]
  (core/get-session-users-depot cluster))

(defn get-session-user-vote-depot [cluster]
  (core/get-session-user-vote-depot cluster))

(defn get-session-next-question-depot [cluster]
  (core/get-session-next-question-depot cluster))

(defn get-sessions-pstate [cluster]
  (core/get-sessions-pstate cluster))

(defn get-session-users-vote-pstate [cluster]
  (core/get-session-users-vote-pstate cluster))

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

(defn send-session-next-question [depot payload]
  (core/send-session-next-question depot payload))

(defn get-session-by-id [pstate session-id]
  (core/get-session-by-id pstate session-id))

(defn !latest-users-in-session [pstate session-id]
  (core/!lastest-users-in-session pstate session-id))

(defn !latest-start-at-session [pstate session-id]
  (core/!lastest-start-at-session pstate session-id))

(defn !latest-session-results [pstate session-id]
  (core/!latest-session-results pstate session-id))
