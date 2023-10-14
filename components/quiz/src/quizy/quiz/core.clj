(ns quizy.quiz.core
  (:require
   [com.rpl.rama :as r]
   [com.rpl.rama.path :as path]
   [quizy.session.interface :as session])
  (:import
   (java.util UUID)))

(def quiz-data-schema
  {UUID (r/fixed-keys-schema
         {:title String
          :description String
          :level String
          :max-player-per-session Long
          :total-question Integer
          :questions (r/vector-schema
                      (r/fixed-keys-schema
                       {:id UUID
                        :max-second-to-answer Long
                        :points Long}))})})

(def question-data-schema
  {UUID (r/fixed-keys-schema {:title String
                              :right-answer UUID
                              :choices (r/vector-schema (r/fixed-keys-schema {:id UUID
                                                                              :value String}))})})
(def quiz-sessions-schema
  {UUID (r/vector-schema UUID)})

(defrecord QuestionRecord [id title right-answer choices])
(defrecord QuizRecord [id title level session-id description max-player-per-session questions])

;; depots
(def *quiz-depot "*quiz-depot")
(def *question-depot "*question-depot")
;; pstates
(def $$quiz "$$quiz")
(def $$question "$$question")
(def $$quiz-sessions "$$quiz-sessions")

#_:clj-kondo/ignore
(r/defmodule QuizModule [setup topo]
  (r/declare-depot setup *quiz-depot (r/hash-by :id))
  (r/declare-depot setup *question-depot (r/hash-by :id))
  (let [s (r/stream-topology topo "quiz")]
    (r/declare-pstate s $$quiz quiz-data-schema)
    (r/declare-pstate s $$question question-data-schema)
    (r/declare-pstate s $$quiz-sessions quiz-sessions-schema)
    (r/<<sources s
                 ;; Question
                 (r/source> *question-depot :> {:keys [*id *title *right-answer *choices]})
                 (r/local-transform> [(path/keypath *id)
                                      (path/multi-path [:title (path/termval *title)]
                                                       [:right-answer (path/termval *right-answer)]
                                                       [:choices (path/termval *choices)])]
                                     $$question)
                 ;; Quiz
                 (r/source> *quiz-depot :> {:keys [*id *title *description *level
                                                   *max-player-per-session *session-id
                                                   *questions]})
                 (count *questions :> *total-question)
                 (r/local-transform> [(path/keypath *id)
                                      path/NIL->LIST
                                      path/AFTER-ELEM
                                      (path/termval *session-id)]
                                     $$quiz-sessions)
                 (r/local-transform> [(path/keypath *id)
                                      (path/multi-path [:title (path/termval *title)]
                                                       [:description (path/termval *description)]
                                                       [:max-player-per-session (path/termval *max-player-per-session)]
                                                       [:level (path/termval *level)]
                                                       [:total-question (path/termval *total-question)]
                                                       [:questions (path/termval *questions)])]
                                     $$quiz))))

(def quiz-module-name (r/get-module-name QuizModule))

(defn get-question-depot [cluster]
  (r/foreign-depot cluster quiz-module-name *question-depot))

(defn get-questions-pstate [cluster]
  (r/foreign-pstate cluster quiz-module-name $$question))

(defn get-quiz-depot [cluster]
  (r/foreign-depot cluster quiz-module-name *quiz-depot))

(defn get-quizzes-pstate [cluster]
  (r/foreign-pstate cluster quiz-module-name $$quiz))

(defn get-quiz-sessions-pstate [cluster]
  (r/foreign-pstate cluster quiz-module-name $$quiz-sessions))

(defn send-question [question-depot question]
  (let [question-record (map->QuestionRecord (update-keys question keyword))]
    (r/foreign-append! question-depot question-record)))

(defn send-quiz [depots quiz]
  (let [session-id (random-uuid)
        quiz-record (map->QuizRecord (assoc quiz :session-id session-id))]
    (session/send-session (:session depots) {:id session-id
                                             :quiz-id (:id quiz-record)
                                             :users-id #{}})
    (tap> quiz-record)
    (tap> (type (:max-player-per-session quiz-record)))
    (r/foreign-append! (:quiz depots) quiz-record)))

(defn get-quiz-module []
  QuizModule)

(defn get-quizzes [quizzes-pstate]
  (let [all-quizzes (r/foreign-select path/ALL quizzes-pstate)]
    (into []
          (map (fn [[id quiz]]
                 (assoc quiz :id id)))
          all-quizzes)))

(defn get-quiz-by-id [quizzes-pstate quiz-id]
  (r/foreign-select-one (path/keypath quiz-id) quizzes-pstate))

(defn get-question-by-id [questions-pstate question-id]
  (r/foreign-select-one (path/keypath question-id) questions-pstate))

(defn get-quiz-sessions [quiz-session-pstate quiz-id]
  (r/foreign-select-one (path/keypath quiz-id) quiz-session-pstate))
