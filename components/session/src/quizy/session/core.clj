(ns quizy.session.core
  (:require
   [com.rpl.rama :as r]
   [com.rpl.rama.path :as path]
   [quizy.belt.interface :as belt])
  (:import
   (java.time Instant)
   (java.util UUID)))

(defn get-start-at [_]
  (-> (Instant/now)
      (.plusSeconds (* 60 2))
      (.toEpochMilli)))

(defn compute-score [points v]
  (+ v points))

(defrecord SessionRecord [id quiz-id users-id])
(defrecord SessionUserRecord [session-id user-id action])
(defrecord SessionUserVoteRecord [session-id user-id question-id choice-id])
(defrecord SessionNextQuestionRecord [user-id session-id next-question-index
                                      question-id right-choice points])

;; depots
(def *session-depot "*session-depot")
(def *session-users-depot "*session-users-depot")
(def *session-users-vote-depot "*session-users-vote-depot")
(def *session-next-question-depot "*session-next-question-depot")
;; pstates
(def $$sessions "$$sessions")
(def $$session-users-vote "$$session-users-vote")

#_:clj-kondo/ignore
(r/defmodule SessionModule [setup topo]
  (r/declare-depot setup *session-depot (r/hash-by :id))
  (r/declare-depot setup *session-users-depot (r/hash-by :session-id))
  (r/declare-depot setup *session-users-vote-depot (r/hash-by :user-id))
  (r/declare-depot setup *session-next-question-depot (r/hash-by :user-id))

  (let [s (r/stream-topology topo "session")]
    ;; Sessions
    (r/declare-pstate s $$sessions {String (r/fixed-keys-schema
                                            {:quiz-id String
                                             :users-id (r/set-schema String)
                                             :start-at Long
                                             :results {String Long}})})
    ;; Users vote
    (r/declare-pstate s $$session-users-vote {String {String {String (r/fixed-keys-schema {:vote String})}}})

    (r/<<sources s
                 ;; Session
                 (r/source> *session-depot :> {:keys [*id] :as *session})
                 (r/local-transform> [(path/keypath *id)
                                      (path/termval (into {} (dissoc *session :id)))]
                                     $$sessions)
                 ;; Attendee
                 (r/source> *session-users-depot :> {:keys [*session-id *user-id *action]})
                 (r/<<if (= *action "add")
                         (r/local-select> (path/keypath *session-id :users-id) $$sessions :> *session-users)
                         (r/local-transform> [(path/keypath *session-id :users-id)
                                              path/NIL->SET
                                              path/NONE-ELEM
                                              (path/termval *user-id)]
                                             $$sessions)
                         (count *session-users :> *total-user)
                         (r/<<if (zero? *total-user)
                                 (r/local-transform> [(path/keypath *session-id :start-at)
                                                      (path/term get-start-at)]
                                                     $$sessions)))
                 (r/<<if (= *action "remove")
                         (r/local-select> (path/keypath *session-id :users-id) $$sessions :> *session-users)
                         (r/local-transform> [(path/keypath *session-id :users-id)
                                              (path/set-elem *user-id)
                                              r/NONE>]
                                             $$sessions)
                         (count *session-users :> *total-user)
                         (r/<<if (= *total-user 1)
                                 (r/local-transform> [(path/keypath *session-id :start-at)
                                                      (path/termval nil)]
                                                     $$sessions)))
                 ;; User vote
                 (r/source> *session-users-vote-depot :> {:keys [*session-id *user-id *question-id *choice-id]})
                 (r/local-transform> [(path/keypath *user-id *session-id *question-id :vote)
                                      (path/termval *choice-id)]
                                     $$session-users-vote)
                 ;; Next question
                 (r/source> *session-next-question-depot :> {:keys [*session-id *user-id *question-id
                                                                    *right-choice *next-question-index
                                                                    *points]})
                 (r/local-select> [(path/keypath *user-id *session-id *question-id :vote)]
                                  $$session-users-vote
                                  :> *vote)
                 (r/<<if (= *vote *right-choice)
                         (r/local-transform> [(path/keypath *session-id :results *user-id)
                                              (path/nil->val 0)
                                              (path/term (partial compute-score *points))]
                                             $$sessions)))))

(def session-module-name (r/get-module-name SessionModule))

(defn get-session-depot [cluster]
  (r/foreign-depot cluster session-module-name *session-depot))

(defn get-session-users-depot [cluster]
  (r/foreign-depot cluster session-module-name *session-users-depot))

(defn get-session-user-vote-depot [cluster]
  (r/foreign-depot cluster session-module-name *session-users-vote-depot))

(defn get-session-next-question-depot [cluster]
  (r/foreign-depot cluster session-module-name *session-next-question-depot))

(defn get-sessions-pstate [cluster]
  (r/foreign-pstate cluster session-module-name $$sessions))

(defn get-session-users-vote-pstate [cluster]
  (r/foreign-pstate cluster session-module-name $$session-users-vote))

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

(defn send-session-next-question [session-next-question-depot payload]
  (let [session-next-question-record (map->SessionNextQuestionRecord payload)]
    (r/foreign-append! session-next-question-depot session-next-question-record)))

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
