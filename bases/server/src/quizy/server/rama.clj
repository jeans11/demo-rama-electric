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
     :depots (merge (user/export-depots cluster)
                    (quiz/export-depots cluster)
                    (session/export-depots cluster))
     :pstates (merge (user/export-pstates cluster)
                     (quiz/export-pstates cluster)
                     (session/export-pstates cluster))}))

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
  (let [id (user/send-signup (-get-depot :*signup-depot) payload)]
    (if (user/check-signup (-get-pstate :$$users) id)
      {:status :idle :user-id id}
      {:status :error})))

(defn process-login [payload]
  (if-some [user-id (user/login (-get-pstates :$$users :$$emails-to-signup) payload)]
    {:status :idle :user-id user-id}
    {:status :error}))

(defn retrieve-logged-user [raw-user-id]
  (user/get-user-by-id (-get-pstate :$$users) raw-user-id))

(defn retrieve-quizzes []
  (quiz/get-quizzes (-get-pstate :$$quizzes)))

(defn retrieve-quiz-by-id [quiz-id]
  (quiz/get-quiz-by-id (-get-pstate :$$quizzes) quiz-id))

(defn retrieve-quiz-sessions [quiz-id]
  (quiz/get-quiz-sessions (-get-pstate :$$quiz-sessions) quiz-id))

(defn !latest-users-in-session [session-id]
  (session/!latest-users-in-session (-get-pstate :$$sessions) session-id))

(defn !latest-start-at-session [session-id]
  (session/!latest-start-at-session (-get-pstate :$$sessions) session-id))

(defn !latest-session-results [session-id]
  (session/!latest-session-results (-get-pstate :$$sessions) session-id))

(defn !latest-session-status [session-id]
  (session/!latest-session-status (-get-pstate :$$sessions) session-id))

(defn !latest-session-current-question [session-id]
  (session/!latest-session-current-question (-get-pstate :$$sessions) session-id))

(defn !latest-session-next-question-at [session-id]
  (session/!latest-session-next-question-at (-get-pstate :$$sessions) session-id))

(defn add-user-to-session [session-id user-id]
  (session/send-user-session (-get-depot :*session-users-depot) session-id user-id))

(defn remove-user-to-session [session-id user-id]
  (session/remove-user-session (-get-depot :*session-users-depot) session-id user-id))

(defn retrieve-session-by-id [session-id]
  (session/get-session-by-id (-get-pstate :$$sessions) session-id))

(defn retrieve-question-by-id [question-id]
  (quiz/get-question-by-id (-get-pstate :$$questions) question-id))

(defn process-user-vote [payload]
  (session/send-user-session-vote (-get-depot :*session-users-vote-depot) payload))

(defn retrieve-current-leaderboard [users-id results]
  (let [user-pstate (-get-pstate :$$users)]
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
                      (-get-pstate :$$questions))))

(comment

  system

  (stop-the-engine!)
  (start-the-engine!)

  nil)
