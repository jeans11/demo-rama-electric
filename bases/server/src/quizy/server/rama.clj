(ns quizy.server.rama
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.walk :as walk]
   [quizy.quiz.interface :as quiz]
   [quizy.session.interface :as session]
   [quizy.user.interface :as user]
   [quizy.clj-rama.interface.path :as path])
  (:import
   (com.rpl.rama.test InProcessCluster LaunchConfig)
   (java.util UUID)))

(defonce system nil)

(defn -run-module [cluster module]
  (.launchModule cluster module (LaunchConfig. 1 1)))

(defn -make-system [cluster]
  (fn [_]
    {:cluster cluster
     :depots {:signup (user/get-signup-depot cluster)
              :quiz (quiz/get-quiz-depot cluster)
              :question (quiz/get-question-depot cluster)
              :session-users (session/get-session-users-depot cluster)
              :session-user-votes (session/get-session-user-vote-depot cluster)
              :session-next-question (session/get-session-next-question-depot cluster)}
     :pstates {:accounts (user/get-accounts-pstate cluster)
               :emails (user/get-emails-pstate cluster)
               :quizzes (quiz/get-quizzes-pstate cluster)
               :questions (quiz/get-questions-pstate cluster)
               :sessions (session/get-sessions-pstate cluster)
               :quiz-sessions (quiz/get-quiz-sessions-pstate cluster)
               :session-users-vote (session/get-session-users-vote-pstate cluster)}}))

(defn -get-depot [key]
  (get-in system [:depots key]))

(defn -get-pstate [key]
  (get-in system [:pstates key]))

(defn -get-pstates [& keys]
  (select-keys (:pstates system) keys))

(defn start-the-engine! []
  (println "Start rama cluster...")
  (let [cluster (InProcessCluster/create)]
    (try
      (-run-module cluster (user/get-signup-module))
      (-run-module cluster (quiz/get-quiz-module))
      (-run-module cluster (session/get-session-module))
      (alter-var-root #'system (-make-system cluster))
      (println "Rama cluster started")
      :done
      (catch Exception ex
        (.printStackTrace ex)
        (.close cluster)))))

(defn process-signup [payload]
  (let [id (user/send-signup (-get-depot :signup) payload)]
    (if (user/check-signup (-get-pstate :accounts) id)
      {:status :idle :user-id id}
      {:status :error})))

(defn process-login [payload]
  (if-some [user-id (user/login (-get-pstates :accounts :emails) payload)]
    {:status :idle :user-id user-id}
    {:status :error}))

(defn retrieve-logged-user [raw-user-id]
  (user/get-user-by-id (-get-pstate :accounts) (UUID/fromString raw-user-id)))

(defn retrieve-quizzes []
  (quiz/get-quizzes (-get-pstate :quizzes)))

(defn retrieve-quiz-by-id [quiz-id]
  (quiz/get-quiz-by-id (-get-pstate :quizzes) quiz-id))

(defn retrieve-quiz-sessions [quiz-id]
  (quiz/get-quiz-sessions (-get-pstate :quiz-sessions) quiz-id))

(defn !latest-users-in-session [session-id]
  (session/!latest-users-in-session (-get-pstate :sessions) session-id))

(defn !latest-start-at-session [session-id]
  (session/!latest-start-at-session (-get-pstate :sessions) session-id))

(defn !latest-session-results [session-id]
  (session/!latest-session-results (-get-pstate :sessions) session-id))

(defn add-user-to-session [session-id user-id]
  (session/send-user-session (-get-depot :session-users) session-id user-id))

(defn remove-user-to-session [session-id user-id]
  (session/remove-user-session (-get-depot :session-users) session-id user-id))

(defn retrieve-session-by-id [session-id]
  (session/get-session-by-id (-get-pstate :sessions) session-id))

(defn retrieve-question-by-id [question-id]
  (quiz/get-question-by-id (-get-pstate :questions) question-id))

(defn process-user-vote [payload]
  (session/send-user-session-vote (-get-depot :session-user-votes) payload))

(defn process-next-question [{:keys [session-id user-id current-question-index question-id right-choice points]}]
  (session/send-session-next-question (-get-depot :session-next-question)
                                      {:user-id user-id
                                       :session-id session-id
                                       :question-id question-id
                                       :right-choice right-choice
                                       :points points
                                       :next-question-index (inc (or current-question-index 0))}))

(defn retrieve-current-leaderboard [users-id results]
  (let [user-pstate (-get-pstate :accounts)]
    (->> (into [] (comp
                   (map #(hash-map :id % :display-name (.selectOne user-pstate (path/key % "display-name"))))
                   (map #(assoc % :score (get results (:id %) 0))))
               users-id)
         (sort-by :score >)
         (map-indexed vector))))

(comment

  (import '(com.rpl.rama Path))

  system

  (.select (-get-pstate :accounts) (Path/all))
  (.select (-get-pstate :emails) (Path/all))

  (require '[clojure.java.io :as io])
  (require '[clojure.edn :as edn])
  (require '[clojure.walk :as walk])
  (def questions (-> (io/resource "server/fixtures/questions.edn")
                     (slurp)
                     (edn/read-string)))

  (def quizzes (-> (io/resource "server/fixtures/quizzes.edn")
                   (slurp)
                   (edn/read-string)))

  quizzes
  questions

  (def question-depot (quiz/get-question-depot (:cluster system)))
  (def quiz-depot (quiz/get-quiz-depot (:cluster system)))
  (def session-depot (session/get-session-depot (:cluster system)))
  (def session-user-depot (session/get-session-users-depot (:cluster system)))

  (doseq [question questions]
    (quiz/send-question question-depot (walk/stringify-keys question)))

  (doseq [quiz quizzes]
    (quiz/send-quiz {:quiz quiz-depot :session session-depot} (walk/stringify-keys quiz)))

  (def user-id (random-uuid))
  (def session-id #uuid"ec56376d-27e1-41cb-b3e9-424f37618bd2")

  (session/send-user-session session-user-depot
                             session-id
                             user-id)

  (session/remove-user-session session-user-depot
                               session-id
                               user-id)

  (def question-pstate (quiz/get-questions-pstate (:cluster system)))
  (def quiz-pstate (quiz/get-quizzes-pstate (:cluster system)))
  (def session-pstate (session/get-sessions-pstate (:cluster system)))
  (def session-users-vote (-get-pstate :session-users-vote))

  (require '[quizy.clj-rama.interface.path :as path])

  (.selectOne question-pstate (path/key #uuid"5da5069f-046f-49c3-8038-91507274dc34"))
  (.select quiz-pstate (Path/all))
  (.select session-pstate (Path/all))
  (.select question-pstate (Path/all))
  (.select session-users-vote (Path/all))

  (.close (:cluster system))
  (start-the-engine!)

  (retrieve-quizzes)

  (import '(java.time Instant))
  (Instant/ofEpochMilli 1695224836384)

  (-> (Instant/now)
      (.plusSeconds (* 60 2))
      (.toEpochMilli)
      (Instant/ofEpochMilli))

  nil)
