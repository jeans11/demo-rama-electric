(ns quizy.quiz.interface
  (:require [quizy.quiz.core :as core]))

(defn export-depots [cluster]
  (core/export-depots cluster))

(defn export-pstates [cluster]
  (core/export-pstates cluster))

(defn get-quiz-module []
  (core/get-quiz-module))

(defn send-question [depot question]
  (core/send-question depot question))

(defn send-quiz [{:keys [_quiz _session] :as depots} quiz]
  (core/send-quiz depots quiz))

(defn get-quizzes [pstate]
  (core/get-quizzes pstate))

(defn get-quiz-by-id [pstate quiz-id]
  (core/get-quiz-by-id pstate quiz-id))

(defn get-question-by-id [pstate question-id]
  (core/get-question-by-id pstate question-id))

(defn get-quiz-sessions [pstate quiz-id]
  (core/get-quiz-sessions pstate quiz-id))
