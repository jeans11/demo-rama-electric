(ns quizy.quiz.core
  (:require
   [com.rpl.rama :as r]
   [com.rpl.rama.path :as path]
   [quizy.session.interface :as session]
   [quizy.belt.interface :as belt])
  (:import
   (java.util UUID)))

(defrecord QuestionRecord [id title right-answer choices])
(defrecord QuizRecord [id title level session-id description max-player-per-session questions])

;; depots
(def *quiz-depot "*quiz-depot")
(def *question-depot "*question-depot")
;; pstates
(def $$quizzes "$$quizzes")
(def $$questions "$$questions")
(def $$quiz-sessions "$$quiz-sessions")

#_:clj-kondo/ignore
(r/defmodule QuizModule [setup topo]
  (r/declare-depot setup *quiz-depot (r/hash-by :id))
  (r/declare-depot setup *question-depot (r/hash-by :id))
  (let [s (r/stream-topology topo "quiz")]
    ;; Quizzes schema
    (r/declare-pstate s $$quizzes {String (r/fixed-keys-schema
                                           {:title String
                                            :description String
                                            :level String
                                            :max-player-per-session Long
                                            :total-question Integer
                                            :questions (r/vector-schema
                                                        (r/fixed-keys-schema
                                                         {:id String
                                                          :max-second-to-answer Long
                                                          :points Long}))})})
    ;; Questions schema
    (r/declare-pstate s $$questions {String (r/fixed-keys-schema
                                             {:title String
                                              :right-answer String
                                              :choices (r/vector-schema
                                                        (r/fixed-keys-schema
                                                         {:id String
                                                          :value String}))})})
    ;; Quiz sessions schema
    (r/declare-pstate s $$quiz-sessions {String (r/vector-schema String)})

    (r/<<sources s
                 ;; Question
                 (r/source> *question-depot :> {:keys [*id] :as *question})
                 (r/local-transform> [(path/keypath *id)
                                      (path/termval (into {} (dissoc *question :id)))]
                                     $$questions)
                 ;; Quiz
                 (r/source> *quiz-depot :> {:keys [*id *session-id *questions] :as *quiz})
                 (count *questions :> *total-question)
                 (r/|hash *id)
                 (r/local-transform> [(path/keypath *id)
                                      path/NIL->LIST
                                      path/AFTER-ELEM
                                      (path/termval *session-id)]
                                     $$quiz-sessions)
                 (r/|hash *id)
                 (r/local-transform> [(path/keypath *id)
                                      (path/termval (into {:total-question *total-question}
                                                          (dissoc *quiz :id :session-id)))]
                                     $$quizzes))))

(def quiz-module-name (r/get-module-name QuizModule))

(def depots
  [*quiz-depot *question-depot])

(def pstates
  [$$quizzes $$questions $$quiz-sessions])

(defn export-depots [cluster]
  (belt/make-depots-map cluster quiz-module-name depots))

(defn export-pstates [cluster]
  (belt/make-pstates-map cluster quiz-module-name pstates))

(defn send-question [question-depot question]
  (let [question-record (map->QuestionRecord (update-keys question keyword))]
    (r/foreign-append! question-depot question-record)))

(defn send-quiz [depots quiz]
  (let [session-id (str (random-uuid))
        quiz-record (map->QuizRecord (assoc quiz :session-id session-id))]
    (session/send-session (:session depots) {:id session-id
                                             :quiz-id (:id quiz-record)
                                             :users-id #{}
                                             :max-users 2})
    (r/foreign-append! (:quiz depots) quiz-record)))

(defn get-quiz-module [] QuizModule)

(defn get-quizzes [quizzes-pstate]
  (into []
        (map (fn [[k v]] (assoc v :id k)))
        (r/foreign-select path/ALL quizzes-pstate)))

(defn get-quiz-by-id [quizzes-pstate quiz-id]
  (r/foreign-select-one (path/keypath quiz-id) quizzes-pstate))

(defn get-question-by-id [questions-pstate question-id]
  (r/foreign-select-one (path/keypath question-id) questions-pstate))

(defn get-quiz-sessions [quiz-session-pstate quiz-id]
  (r/foreign-select-one (path/keypath quiz-id) quiz-session-pstate))
