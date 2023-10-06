(ns quizy.session.core
  (:require
   [clojure.walk :as walk]
   [quizy.clj-rama.interface.block :as block]
   [quizy.clj-rama.interface.helpers :as helpers]
   [quizy.clj-rama.interface.path :as path]
   [quizy.clj-rama.interface :as rama])
  (:import
   (com.rpl.rama
    Depot
    PState
    Block
    Path
    Expr
    RamaModule)
   (com.rpl.rama.ops Ops RamaFunction1)
   (java.util UUID)
   (java.time Instant)))

(deftype ExtractId []
  RamaFunction1
  (invoke [_ _]
    "id"))

(deftype ExtractSessionId []
  RamaFunction1
  (invoke [_ _]
    "session_id"))

(deftype ExtractUserId []
  RamaFunction1
  (invoke [_ _]
    "user_id"))

(deftype SetStartAt []
  RamaFunction1
  (invoke [_ _]
    (-> (Instant/now)
        (.plusSeconds (* 60 2))
        (.toEpochMilli))))

(def session-data-schema
  (PState/mapSchema
   UUID
   (PState/fixedKeysSchema
    (into-array Object ["quiz-id" UUID
                        "users-id" (PState/setSchema UUID)
                        "start-at" Long]))))

(def session-users-vote-schema
  (PState/mapSchema
   UUID
   (PState/mapSchema
    UUID
    (PState/mapSchema
     UUID
     (PState/fixedKeysSchema
      (into-array Object ["vote" UUID]))))))

(defrecord SessionRecord [id quiz-id users-id])
(defrecord SessionUserRecord [session-id user-id action])
(defrecord SessionUserVoteRecord [session-id user-id question-id choice-id])
(defrecord SessionNextQuestionRecord [user-id session-id next-question-index
                                      question-id right-choice points])

(deftype SessionModule []
  RamaModule
  (define [_ setup topo]
    (.declareDepot setup "*session-depot" (Depot/hashBy ExtractId))
    (.declareDepot setup "*session-users-depot" (Depot/hashBy ExtractSessionId))
    (.declareDepot setup "*session-users-vote-depot" (Depot/hashBy ExtractUserId))
    (.declareDepot setup "*session-next-question-depot" (Depot/hashBy ExtractUserId))

    (let [session (.stream topo "session")]
      (.pstate session "$$session" session-data-schema)
      (.pstate session "$$session-users-vote" session-users-vote-schema)

      ;; Session
      (-> (.source session "*session-depot") (block/out "*session")
          (helpers/bind-field "*session" "id" "*id")
          (helpers/bind-field "*session" "quiz_id" "*quiz-id")
          (helpers/bind-field "*session" "users_id" "*users-id")
          (.localTransform "$$session" (-> (path/key "*id")
                                           (.multiPath
                                            (into-array Path [(-> (path/key "quiz-id") (.termVal "*quiz-id"))
                                                              (-> (path/key "users-id") (.termVal "*users-id"))])))))
      ;; Attendee in session
      (-> (.source session "*session-users-depot") (block/out "*session-user")
          (helpers/bind-field "*session-user" "session_id" "*session-id")
          (helpers/bind-field "*session-user" "user_id" "*user-id")
          (helpers/bind-field "*session-user" "action" "*action")
          ;; add user in session
          (.ifTrue (Expr. Ops/EQUAL "*action" "add")
                   (-> (Block/localSelect "$$session" (path/key "*session-id" "users-id")) (block/out "*session-users")
                       (.localTransform "$$session" (-> (path/key "*session-id" "users-id")
                                                        (.voidSetElem)
                                                        (.termVal "*user-id")))
                       (.each Ops/SIZE "*session-users") (block/out "*total-users")
                       (.ifTrue (Expr. Ops/EQUAL "*total-users" 0)
                                (Block/localTransform "$$session" (-> (path/key "*session-id" "start-at")
                                                                      (.term (SetStartAt.)))))))
          ;; remove user in session
          (.ifTrue (Expr. Ops/EQUAL "*action" "remove")
                   (-> (Block/localSelect "$$session" (path/key "*session-id" "users-id")) (block/out "*session-users")
                       (.localTransform "$$session" (-> (path/key "*session-id" "users-id")
                                                        (.setElem "*user-id")
                                                        (.termVoid)))
                       (.each Ops/SIZE "*session-users") (block/out "*total-users")
                       (.ifTrue (Expr. Ops/EQUAL "*total-users" 1)
                                (Block/localTransform "$$session" (-> (path/key "*session-id" "start-at")
                                                                      (.termVal nil)))))))
      ;; User vote
      (-> (.source session "*session-users-vote-depot") (block/out "*session-user-vote")
          (helpers/bind-field "*session-user-vote" "session_id" "*session-id")
          (helpers/bind-field "*session-user-vote" "user_id" "*user-id")
          (helpers/bind-field "*session-user-vote" "question_id" "*question-id")
          (helpers/bind-field "*session-user-vote" "choice_id" "*choice-id")
          (.localTransform "$$session-users-vote" (-> (path/key "*user-id" "*session-id" "*question-id" "vote")
                                                      (.termVal "*choice-id"))))

      ;; Next question
      (-> (.source session "*session-next-question-depot") (block/out "*session-next-question")
          (helpers/bind-field "*session-next-question" "session_id" "*session-id")
          (helpers/bind-field "*session-next-question" "user_id" "*user-id")
          (helpers/bind-field "*session-next-question" "next_question_index" "*next-question-index")
          (helpers/bind-field "*session-next-question" "question_id" "*question-id")
          (helpers/bind-field "*session-next-question" "right_choice" "*right-choice")
          (helpers/bind-field "*session-next-question" "points" "*points")
          (.localSelect "$$session-users-vote" (path/key "*user-id" "*session-id" "*question-id" "vote")) (block/out "*vote")
          (.ifTrue (Expr. Ops/EQUAL "*vote" "*right-choice")
                   (Block/localTransform "$$session" (-> (path/key "*session-id" "results" "*user-id")
                                                         (.nullToVal 0)
                                                         (.term Ops/PLUS "*points"))))))))

(def session-module-name (.getName SessionModule))

(defn get-session-depot [cluster]
  (.clusterDepot cluster session-module-name "*session-depot"))

(defn get-session-users-depot [cluster]
  (.clusterDepot cluster session-module-name "*session-users-depot"))

(defn get-session-user-vote-depot [cluster]
  (.clusterDepot cluster session-module-name "*session-users-vote-depot"))

(defn get-session-next-question-depot [cluster]
  (.clusterDepot cluster session-module-name "*session-next-question-depot"))

(defn get-sessions-pstate [cluster]
  (.clusterPState cluster session-module-name "$$session"))

(defn get-session-users-vote-pstate [cluster]
  (.clusterPState cluster session-module-name "$$session-users-vote"))

(defn send-session [session-depot session]
  (let [session-record (map->SessionRecord (update-keys session keyword))]
    (.append session-depot session-record)))

(defn send-user-session [session-users-depot session-id user-id]
  (let [session-user-record (map->SessionUserRecord {:session-id session-id
                                                     :user-id user-id
                                                     :action "add"})]
    (.append session-users-depot session-user-record)))

(defn send-user-session-vote [session-users-vote-depot payload]
  (let [session-user-vote-record (map->SessionUserVoteRecord payload)]
    (.append session-users-vote-depot session-user-vote-record)))

(defn send-session-next-question [session-next-question-depot payload]
  (let [session-next-question-record (map->SessionNextQuestionRecord payload)]
    (.append session-next-question-depot session-next-question-record)))

(defn remove-user-session [session-users-depot session-id user-id]
  (let [session-user-record (map->SessionUserRecord {:session-id session-id
                                                     :user-id user-id
                                                     :action "remove"})]
    (.append session-users-depot session-user-record)))

(defn get-session-module []
  (->SessionModule))

(defn get-session-by-id [session-pstate session-id]
  (let [session (.selectOne session-pstate (path/key session-id))]
    (walk/keywordize-keys session)))

(defn get-user-vote [session-users-vote-pstate {:keys [user-id session-id question-id]}]
  (.selectOne session-users-vote-pstate (path/key user-id session-id question-id "vote")))

(defn !lastest-users-in-session [session-pstate session-id]
  (rama/make-reactive-pstate session-pstate (path/key session-id "users-id")))

(defn !lastest-start-at-session [session-pstate session-id]
  (rama/make-reactive-pstate session-pstate (path/key session-id "start-at")))

(defn !latest-current-question-index [session-pstate session-id]
  (rama/make-reactive-pstate session-pstate (path/key session-id "current-question-index")))

(defn !latest-session-results [session-pstate session-id]
  (rama/make-reactive-pstate session-pstate (path/key session-id "results")))

(comment

  (import '[java.time Instant])

  (.toEpochMilli (Instant/now))

  nil)
