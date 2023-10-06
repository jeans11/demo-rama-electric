(ns quizy.quiz.core
  (:require
   [quizy.clj-rama.interface.block :as block]
   [quizy.clj-rama.interface.helpers :as helpers]
   [quizy.clj-rama.interface.path :as path]
   [clojure.walk :as walk]
   [quizy.session.interface :as session])
  (:import
   (com.rpl.rama
    Block
    Depot
    Expr
    PState
    Path
    RamaModule)
   (com.rpl.rama.ops Ops RamaFunction1 RamaFunction2)
   (java.util UUID)))

(deftype ExtractId []
  RamaFunction1
  (invoke [_ _]
    "id"))

(def quiz-data-schema
  (PState/mapSchema
   UUID
   (PState/fixedKeysSchema
    (into-array Object ["title" String
                        "description" String
                        "level" String
                        "max-player-per-session" Integer
                        "total-question" Integer
                        "questions" (PState/listSchema
                                     (PState/fixedKeysSchema
                                      (into-array Object ["id" UUID
                                                          "max-second-to-answer" Integer
                                                          "points" Integer])))]))))

(def question-data-schema
  (PState/mapSchema
   UUID
   (PState/fixedKeysSchema
    (into-array Object ["title" String
                        "right-answer" UUID
                        "choices" (PState/listSchema
                                   (PState/fixedKeysSchema
                                    (into-array Object ["id" UUID
                                                        "value" String])))]))))

(def quiz-sessions-schema
  (PState/mapSchema
   UUID
   (PState/listSchema UUID)))

(defrecord QuestionRecord [id title right-answer choices])
(defrecord ChoicesQuestionRecord [id value right-answer])
(defrecord QuizRecord [id title level session-id description max-player-per-session questions])

(deftype QuizModule []
  RamaModule
  (define [_ setup topo]
    (.declareDepot setup "*quiz-depot" (Depot/hashBy ExtractId))
    (.declareDepot setup "*question-depot" (Depot/hashBy ExtractId))
    (let [quiz (.stream topo "quiz")
          question (.stream topo "question")]
      (.pstate quiz "$$quiz" quiz-data-schema)
      (.pstate question "$$question" question-data-schema)
      (.pstate quiz "$$quiz-sessions" quiz-sessions-schema)

      ;; Question
      (-> (.source question "*question-depot") (block/out "*question")
          (helpers/bind-field "*question" "id" "*id")
          (helpers/bind-field "*question" "title" "*title")
          (helpers/bind-field "*question" "right_answer" "*right-answer")
          (helpers/bind-field "*question" "choices" "*choices")
          (.localTransform "$$question" (-> (path/key "*id")
                                            (.multiPath
                                             (into-array Path [(-> (path/key "title") (.termVal "*title"))
                                                               (-> (path/key "right-answer") (.termVal "*right-answer"))
                                                               (-> (path/key "choices") (.termVal "*choices"))])))))
      ;; Quiz
      (-> (.source quiz "*quiz-depot") (block/out "*quiz")
          (helpers/bind-field "*quiz" "id" "*id")
          (helpers/bind-field "*quiz" "title" "*title")
          (helpers/bind-field "*quiz" "description" "*desc")
          (helpers/bind-field "*quiz" "max_player_per_session" "*mpps")
          (helpers/bind-field "*quiz" "level" "*level")
          (helpers/bind-field "*quiz" "questions" "*questions")
          (helpers/bind-field "*quiz" "session_id" "*session-id")
          (.each Ops/SIZE "*questions") (block/out "*total-question")
          (.localTransform "$$quiz-sessions" (-> (path/key "*id")
                                                 (.nullToList)
                                                 (.afterElem)
                                                 (.termVal "*session-id")))
          (.localTransform "$$quiz" (-> (path/key "*id")
                                        (.multiPath
                                         (into-array Path [(-> (path/key "title") (.termVal "*title"))
                                                           (-> (path/key "description") (.termVal "*desc"))
                                                           (-> (path/key "max-player-per-session") (.termVal "*mpps"))
                                                           (-> (path/key "level") (.termVal "*level"))
                                                           (-> (path/key "total-question") (.termVal "*total-question"))
                                                           (-> (path/key "questions") (.termVal "*questions"))]))))))))

(def quiz-module-name (.getName QuizModule))

(defn get-question-depot [cluster]
  (.clusterDepot cluster quiz-module-name "*question-depot"))

(defn get-questions-pstate [cluster]
  (.clusterPState cluster quiz-module-name "$$question"))

(defn get-quiz-depot [cluster]
  (.clusterDepot cluster quiz-module-name "*quiz-depot"))

(defn get-quizzes-pstate [cluster]
  (.clusterPState cluster quiz-module-name "$$quiz"))

(defn get-quiz-sessions-pstate [cluster]
  (.clusterPState cluster quiz-module-name "$$quiz-sessions"))

(defn send-question [question-depot question]
  (let [question-record (map->QuestionRecord (update-keys question keyword))]
    (.append question-depot question-record)))

(defn send-quiz [depots quiz]
  (let [session-id (random-uuid)
        quiz-record (map->QuizRecord
                     (-> (update-keys quiz keyword)
                         (assoc :session-id session-id)))]
    (session/send-session (:session depots) {:id session-id
                                             :quiz-id (:id quiz-record)
                                             :users-id #{}})
    (.append (:quiz depots) quiz-record)))

(defn get-quiz-module []
  (->QuizModule))

(defn get-quizzes [quizzes-pstate]
  (let [all-quizzes (.select quizzes-pstate (Path/all))]
    (into []
          (map (fn [[id quiz]]
                 (-> (walk/keywordize-keys quiz)
                     (assoc :id id))))
          all-quizzes)))

(defn get-quiz-by-id [quizzes-pstate quiz-id]
  (let [quiz (.selectOne quizzes-pstate (path/key quiz-id))]
    (walk/keywordize-keys quiz)))

(defn get-question-by-id [questions-pstate question-id]
  (let [question (.selectOne questions-pstate (path/key question-id))]
    (walk/keywordize-keys question)))

(defn get-quiz-sessions [quiz-session-pstate quiz-id]
  (.selectOne quiz-session-pstate (path/key quiz-id)))
