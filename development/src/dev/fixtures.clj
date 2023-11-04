(ns dev.fixtures
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.walk :as walk]
   [quizy.quiz.interface :as quiz]))

(defn -unqualify [m]
  (walk/postwalk
   (fn [n] (if (keyword? n) (-> n (name) (keyword)) n))
   m))

(defn create-fixtures [{:keys [depots] :as _system}]
  (let [questions (-> (io/resource "dev/fixtures/questions.edn")
                      (slurp)
                      (edn/read-string))
        quizzes (-> (io/resource "dev/fixtures/quizzes.edn")
                    (slurp)
                    (edn/read-string))
        question-depot (:*question-depot depots)
        quiz-depot (:*quiz-depot depots)
        session-depot (:*session-depot depots)]

    (doseq [question questions]
      (quiz/send-question question-depot (-unqualify question)))

    (doseq [quiz quizzes]
      (quiz/send-quiz {:quiz quiz-depot :session session-depot}
                      (-unqualify quiz)))))
