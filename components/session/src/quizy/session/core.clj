(ns quizy.session.core
  (:require
   [com.rpl.rama :as r]
   [com.rpl.rama.path :as path]
   [com.rpl.rama.ops :as ops]
   [quizy.belt.interface :as belt]
   [clojure.string :as str])
  (:import
   (com.rpl.rama.helpers TopologyScheduler)))

(defn get-expiration-time
  [seconds]
  (-> (belt/now)
      (belt/plus-seconds seconds)
      (belt/->millis)))

(defrecord SessionRecord [id quiz-id users-id max-users])
(defrecord SessionUserRecord [session-id user-id action])
(defrecord SessionUserVoteRecord [session-id user-id question-id choice-id])

;; depots
(def *session-depot "*session-depot")
(def *session-users-depot "*session-users-depot")
(def *session-users-vote-depot "*session-users-vote-depot")
(def *session-tick-depot "$$session-tick-depot")
;; pstates
(def $$sessions "$$sessions")
(def $$session-users-vote "$$session-users-vote")
(def $$session-users-result "$$session-users-result")
(def $$quizzes "$$quizzes")
(def $$questions "$$questions")

(defn schedule-item [ts exp data]
  (.scheduleItem ts exp data))

(defn compute-questions-expiration [questions]
  (loop [[h & t] questions
         time 0
         res []]
    (if-not (nil? h)
      (let [exp (get-expiration-time time)]
        (recur t
               (+ time (:max-second-to-answer h))
               (conj res (assoc h :exp exp))))
      (conj res {:exp (get-expiration-time time)
                 :end true}))))

(defn compute-session-users-result
  [current-session-results old-question-users-vote old-question-right-answer old-question-points]
  (reduce-kv
   (fn [acc k {:keys [vote]}]
     (update acc k (fn [current-points]
                     (if (= vote old-question-right-answer)
                       (+ (or current-points 0) old-question-points)
                       current-points))))
   current-session-results
   old-question-users-vote))

#_:clj-kondo/ignore
(r/deframafn get-session-quiz-id [*session-id $$sessions]
  (r/local-select> (path/keypath *session-id :quiz-id) $$sessions :> *quiz-id)
  (:> *quiz-id))

#_:clj-kondo/ignore
(r/deframafn update-session-status [*session-id $$sessions *status]
  (r/local-transform> [(path/keypath *session-id :status) (path/termval *status)] $$sessions)
  (:>))

#_:clj-kondo/ignore
(r/deframafn init-session [*session-id *questions $$sessions]
  (first *questions :> {*question-id :id *second :max-second-to-answer})
  (rest *questions :> *rest-questions)
  (get-expiration-time *second :> *next-question-at)
  (r/local-transform> [(path/keypath *session-id)
                       (path/multi-path [:current-question (path/termval *question-id)]
                                        [:status (path/termval "started")]
                                        [:next-question-at (path/termval *next-question-at)])]
                      $$sessions)
  (:> *rest-questions))

#_:clj-kondo/ignore
(r/defmodule SessionModule [setup topo]
  (r/declare-depot setup *session-depot (r/hash-by :id))
  (r/declare-depot setup *session-users-depot (r/hash-by :session-id))
  (r/declare-depot setup *session-users-vote-depot (r/hash-by :user-id))
  (r/declare-tick-depot setup *session-tick-depot 1000)

  (r/mirror-pstate setup $$quizzes "quizy.quiz.core/QuizModule" "$$quizzes")
  (r/mirror-pstate setup $$questions "quizy.quiz.core/QuizModule" "$$questions")

  (let [s (r/stream-topology topo "session")
        ts-start-session (TopologyScheduler. "$$ts-start-session")
        ts-quiz-session (TopologyScheduler. "$$ts-quiz-session")]
    (.declarePStates ts-start-session s)
    (.declarePStates ts-quiz-session s)
    ;; Sessions
    (r/declare-pstate s $$sessions {String (r/fixed-keys-schema
                                            {:quiz-id String
                                             :max-users Long
                                             :users-id (r/set-schema String)
                                             :current-question String
                                             :next-question-at Long
                                             :status String
                                             :start-at Long
                                             :results {String Long}})})
    ;; Users vote
    (r/declare-pstate s $$session-users-vote {String {String {String (r/fixed-keys-schema {:vote String})}}})
    ;; Users result
    (r/declare-pstate s $$session-users-result {String {String (r/fixed-keys-schema {:points Long})}})

    (r/<<sources
     s
      ;; Process time based event
     (r/source> *session-tick-depot)
     (r/java-macro!
      (.handleExpirations
       ts-start-session "*id" "*currentTime"
       ;; Here handle the start of a session
       (r/java-block<-
        (r/local-select> (path/keypath *id :status) $$sessions :> *session-status)
        (r/<<if (not= "started" *session-status)
                (get-session-quiz-id *id $$sessions :> *quiz-id)
                (r/select> (path/keypath *quiz-id :questions) $$quizzes :> *questions)
                (compute-questions-expiration *questions :> *questions-with-exp)
                (init-session *id *questions-with-exp $$sessions :> *rest-questions)
                (ops/explode *rest-questions :> *question)
                (get *question :exp :> *expiration)
                (assoc *question :session-id *id :> *question-data)
                ;; Each question is scheduled for future processing
                (r/java-macro! (.scheduleItem ts-quiz-session "*expiration" "*question-data"))))))

     (r/java-macro!
      (.handleExpirations
       ts-quiz-session "*question-data" "*currentTime"
       ;; Here handle the process of question
       (r/java-block<-
        (identity *question-data :> {:keys [*id *session-id *max-second-to-answer *end *points]})
        (r/local-select> (path/keypath *session-id :current-question) $$sessions :> *old-question-id)
        (r/<<if (not *end)
                (get-expiration-time *max-second-to-answer :> *next-question-at)
                (r/local-transform> [(path/keypath *session-id)
                                     (path/multi-path [:current-question (path/termval *id)]
                                                      [:next-question-at (path/termval *next-question-at)])]
                                    $$sessions)
                (r/else>)
                (r/local-transform> [(path/keypath *session-id)
                                     (path/multi-path [:current-question (path/termval nil)]
                                                      [:next-question-at (path/termval nil)]
                                                      [:status (path/termval "result")])]
                                    $$sessions))
        (r/select> (path/keypath *old-question-id :right-answer) $$questions :> *old-question-right-answer)
        (r/local-select> (path/keypath *session-id *old-question-id) $$session-users-vote :> *old-question-users-vote)
        (r/local-select> (path/keypath *session-id :results) $$sessions :> *current-session-results)
        (compute-session-users-result *current-session-results
                                      *old-question-users-vote
                                      *old-question-right-answer
                                      *points :> *session-results)
        (r/local-transform> [(path/keypath *session-id :results)
                             (path/termval *session-results)]
                            $$sessions))))

     ;; Session
     (r/source> *session-depot :> {:keys [*id] :as *session})
     (r/local-transform> [(path/keypath *id)
                          (path/termval (into {} (dissoc *session :id)))]
                         $$sessions)
     ;; Attendee
     (r/source> *session-users-depot :> {:keys [*session-id *user-id *action]})
     (r/local-select> [(path/keypath *session-id :max-users)] $$sessions :> *max-users)
     (r/local-select> [(path/keypath *session-id :users-id) (path/view count)] $$sessions :> *total-user)
     (r/<<if (= *action "add")
             (r/local-transform> [(path/keypath *session-id :users-id)
                                  path/NIL->SET
                                  path/NONE-ELEM
                                  (path/termval *user-id)]
                                 $$sessions)
             ;; The first user start the waiting session counter
             (r/<<if (zero? *total-user)
                     (get-expiration-time 10 :> *start-at)
                     (r/java-macro! (.scheduleItem ts-start-session "*start-at" "*session-id"))
                     (r/local-transform> [(path/keypath *session-id)
                                          (path/multi-path [:start-at (path/termval *start-at)]
                                                           [:status (path/termval "waiting")])]
                                         $$sessions))
             ;; We start the session immediatly if we reach the max user
             (r/<<if (= (inc *total-user) *max-users)
                     (get-expiration-time 0 :> *start-at)
                     (r/java-macro! (.scheduleItem ts-start-session "*start-at" "*session-id"))
                     (r/local-transform> [(path/keypath *session-id)
                                          (path/multi-path [:start-at (path/termval *start-at)])]
                                         $$sessions)))
     (r/<<if (= *action "remove")
             (r/local-transform> [(path/keypath *session-id :users-id) (path/set-elem *user-id) r/NONE>]
                                 $$sessions)
             (r/<<if (= *total-user 1)
                     (r/local-transform> [(path/keypath *session-id)
                                          (path/multi-path [:start-at (path/termval nil)]
                                                           [:results (path/termval {})]
                                                           [:status (path/termval "empty")])]
                                         $$sessions)))
     ;; User vote
     (r/source> *session-users-vote-depot :> {:keys [*session-id *user-id *question-id *choice-id]})
     (r/local-transform> [(path/keypath *session-id *question-id *user-id :vote) (path/termval *choice-id)]
                         $$session-users-vote))))

(def session-module-name (r/get-module-name SessionModule))

(def depots-name
  [*session-depot *session-users-depot *session-users-vote-depot])

(def pstates-name
  [$$sessions $$session-users-vote])

(defn export-depots [cluster]
  (belt/make-depots-map cluster session-module-name depots-name))

(defn export-pstates [cluster]
  (belt/make-pstates-map cluster session-module-name pstates-name))

(defn send-session [session-depot session]
  (let [session-record (map->SessionRecord session)]
    (r/foreign-append! session-depot session-record)))

(defn send-user-session [session-users-depot session-id user-id]
  (let [session-user-record (map->SessionUserRecord {:session-id session-id
                                                     :user-id user-id
                                                     :action "add"})]
    (r/foreign-append! session-users-depot session-user-record)))

(defn send-user-session-vote [session-users-vote-depot payload]
  (let [session-user-vote-record (map->SessionUserVoteRecord payload)]
    (r/foreign-append! session-users-vote-depot session-user-vote-record)))

(defn remove-user-session [session-users-depot session-id user-id]
  (let [session-user-record (map->SessionUserRecord {:session-id session-id
                                                     :user-id user-id
                                                     :action "remove"})]
    (r/foreign-append! session-users-depot session-user-record)))

(defn get-session-module []
  SessionModule)

(defn get-session-by-id [session-pstate session-id]
  (r/foreign-select-one (path/keypath session-id) session-pstate))

(defn !lastest-users-in-session [session-pstate session-id]
  (belt/make-reactive-query (path/keypath session-id :users-id) session-pstate))

(defn !lastest-start-at-session [session-pstate session-id]
  (belt/make-reactive-query (path/keypath session-id :start-at) session-pstate))

(defn !latest-session-results [session-pstate session-id]
  (belt/make-reactive-query (path/keypath session-id :results) session-pstate))

(defn !latest-session-status [session-pstate session-id]
  (belt/make-reactive-query (path/keypath session-id :status) session-pstate))

(defn !latest-session-current-question [session-pstate session-id]
  (belt/make-reactive-query (path/keypath session-id :current-question) session-pstate))

(defn !latest-session-next-question-at [session-pstate session-id]
  (belt/make-reactive-query (path/keypath session-id :next-question-at) session-pstate))
