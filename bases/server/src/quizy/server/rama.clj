(ns quizy.server.rama
  (:require
   [com.rpl.rama :as r]
   [com.rpl.rama.path :as path]
   [com.rpl.rama.test :as rtest]
   [quizy.quiz.interface :as quiz]
   [quizy.session.interface :as session]
   [quizy.user.interface :as user]))

(defonce system nil)

(defn -run-module [cluster module]
  (rtest/launch-module! cluster module {:tasks 1 :threads 1}))

(defn -make-system [cluster]
  (fn [_]
    {:cluster cluster
     :depots {:signup (user/get-signup-depot cluster)
              :quiz (quiz/get-quiz-depot cluster)
              :question (quiz/get-question-depot cluster)
              :session-users (session/get-session-users-depot cluster)
              :session-user-votes (session/get-session-user-vote-depot cluster)
              :session-next-question (session/get-session-next-question-depot cluster)}
     :pstates {:users (user/get-users-pstate cluster)
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
  (let [cluster (rtest/create-ipc)]
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

(defn stop-the-engine! []
  (println "Stop rama cluster...")
  (r/close! (:cluster system))
  (alter-var-root #'system (constantly nil))
  (println "Rama cluster stopped")
  :done)

(defn process-signup [payload]
  (let [id (user/send-signup (-get-depot :signup) payload)]
    (if (user/check-signup (-get-pstate :users) id)
      {:status :idle :user-id id}
      {:status :error})))

(defn process-login [payload]
  (if-some [user-id (user/login (-get-pstates :users :emails) payload)]
    {:status :idle :user-id user-id}
    {:status :error}))

(defn retrieve-logged-user [raw-user-id]
  (user/get-user-by-id (-get-pstate :users) raw-user-id))

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
  (let [user-pstate (-get-pstate :users)]
    (->> (into [] (comp
                   (map #(hash-map :id %
                                   :display-name (r/foreign-select-one (path/keypath % :display-name) user-pstate)))
                   (map #(assoc % :score (get results (:id %) 0))))
               users-id)
         (sort-by :score >)
         (map-indexed vector))))

(defn retrieve-questions [questions]
  (let [paths (mapv (fn [q] (path/keypath (:id q))) questions)]
    (r/foreign-select (apply path/multi-path paths)
                      (-get-pstate :questions))))

(comment

  system

  (stop-the-engine!)
  (start-the-engine!)

  (process-signup {:email "j@b.com"
                   :password "secret"
                   :display-name "J"})

  (def users-pstate (-get-pstate :users))
  (def questions-pstate (-get-pstate :questions))
  (def quizzes-pstate (-get-pstate :quizzes))
  (def sessions-pstate (-get-pstate :sessions))

  (r/foreign-select path/ALL users-pstate)
  (r/foreign-select path/ALL questions-pstate)
  (r/foreign-select path/ALL quizzes-pstate)
  (r/foreign-select path/ALL sessions-pstate)
  (r/foreign-select-one (path/keypath #uuid"10e5c34f-4efc-4de4-885a-1f88b1b39b5a") users-pstate)

  (retrieve-questions [{:id #uuid "5da5069f-046f-49c3-8038-91507274dc34"}
                       {:id #uuid "6fe0cfe4-c41a-4486-ac52-f5f7790e4238"}])

  (random-uuid)

  nil)
