(ns quizy.quiz.interface
  (:require [quizy.quiz.core :as core]))

(defn get-question-depot [cluster]
  (core/get-question-depot cluster))

(defn get-questions-pstate [cluster]
  (core/get-questions-pstate cluster))

(defn get-quiz-depot [cluster]
  (core/get-quiz-depot cluster))

(defn get-quizzes-pstate [cluster]
  (core/get-quizzes-pstate cluster))

(defn get-quiz-sessions-pstate [cluster]
  (core/get-quiz-sessions-pstate cluster))

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
