(ns dev.fixtures
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.walk :as walk]
   [quizy.quiz.interface :as quiz]
   [quizy.session.interface :as session]))

(defn -unqualify [m]
  (walk/postwalk
   (fn [n] (if (keyword? n) (-> n (name) (keyword)) n))
   m))

(defn create-fixtures [{:keys [cluster] :as _system}]
  (let [questions (-> (io/resource "dev/fixtures/questions.edn")
                      (slurp)
                      (edn/read-string))
        quizzes (-> (io/resource "dev/fixtures/quizzes.edn")
                    (slurp)
                    (edn/read-string))
        question-depot (quiz/get-question-depot cluster)
        quiz-depot (quiz/get-quiz-depot cluster)
        session-depot (session/get-session-depot cluster)]

    (doseq [question questions]
      (quiz/send-question question-depot (-unqualify question)))

    (doseq [quiz quizzes]
      (quiz/send-quiz {:quiz quiz-depot :session session-depot}
                      (-unqualify quiz)))))
