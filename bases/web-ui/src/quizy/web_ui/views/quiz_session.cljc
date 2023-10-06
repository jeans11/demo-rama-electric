(ns quizy.web-ui.views.quiz-session
  (:require
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [shadow.css :refer [css]]
   #?(:clj [quizy.server.rama :as rama])))

(def styles
  {:session/container (css :flex :flex-1 :justify-center)
   :waiting/main-title (css :text-gray-700 :text-2xl)
   :waiting/second-title (css :text-gray-700 :text-xl)
   :waiting/container (css :flex :flex-1 :flex-col :items-center)
   :question/title (css :text-gray-700 :text-3xl)
   :question/choices-container (css :mt-4)
   :question/choice (css :mb-3 :text-xl :p-3 :rounded-md
                         :cursor-pointer)
   :question/choice-blank (css :bg-white :text-gray-700)
   :question/choice-clicked (css :bg-blue-400 :text-white)
   :question/next-question-container (css :flex :flex-1 :justify-between)
   :question/point-container (css :flex :flex-1 :justify-end)
   :quiz-engine/container (css :flex :flex-1 :flex :justify-center)
   :results/title (css :text-gray-700 :text-xl)
   :results/spacer (css :ml-5 :mr-5 :bg-gray-200 {:width "1px"})})

(defn format-time-before-start [time-ms]
  #?(:cljs
     (if (neg? time-ms)
       "--:--"
       (let [total-seconds (js/Math.floor (/ time-ms 1000))
             minutes (js/Math.floor (/ total-seconds 60))
             seconds (mod total-seconds 60)]
         (str "0" minutes ":" seconds)))))

(e/defn WaitingRoom [time-before-start nb-missing-user]
  (let [formated-time (format-time-before-start time-before-start)]
    (dom/div
      (dom/props {:class (styles :waiting/container)})
      (dom/h1
        (dom/props {:class (styles :waiting/main-title)})
        (dom/text (str "The quiz will start in")))
      (dom/h2
        (dom/props {:class (styles :waiting/second-title)})
        (dom/text formated-time))
      (dom/h3
        (dom/props {:class (styles :waiting/main-title)})
        (dom/text "or"))
      (dom/h2
        (dom/props {:class (styles :waiting/second-title)})
        (dom/text (str "when " nb-missing-user " attendees will join the session"))))))

(defn get-time-ms [_]
  #?(:cljs (js/Date.now)))

(e/defn compute-time-before-next-question [second-to-answer]
  (-> (+ (get-time-ms second-to-answer) (* second-to-answer 1000))
      (- e/system-time-ms)
      (/ 1000)
      (js/Math.floor)))

(e/defn QuizEngine [quiz session user-id]
  (let [session-id (:id session)
        !choice-clicked (atom nil)
        choice-clicked (e/watch !choice-clicked)
        questions (:questions quiz)
        !question-index (atom 0)
        question-index (e/watch !question-index)]

    (dom/div
      (dom/props {:class (styles :quiz-engine/container)})
      (if (<= (inc question-index) (count questions))
        (let [quiz-question (nth questions (or question-index 0))
              points (:points quiz-question)
              second-to-answer (inc (:max-second-to-answer quiz-question))
              question (e/server (rama/retrieve-question-by-id (:id quiz-question)))
              time-before-next-question (compute-time-before-next-question. second-to-answer)]

          (when (zero? time-before-next-question)
            (swap! !question-index inc)
            (e/server (rama/process-next-question {:user-id user-id
                                                   :session-id session-id
                                                   :question-id (:id quiz-question)
                                                   :right-choice (:right-answer question)
                                                   :points points})))
          (dom/div
            (dom/h1
              (dom/props {:class (styles :question/title)})
              (dom/text (:title question)))

            (dom/div
              (dom/props {:class (styles :question/point-container)})
              (dom/p (dom/text (str points (str " point" (when (> points 1) "s"))))))

            (dom/ul
              (dom/props {:class (styles :question/choices-container)})
              (e/for-by :id [choice (:choices question)]
                (dom/li
                  (dom/props {:class [(styles :question/choice)
                                      (styles (if (= choice-clicked (:id choice))
                                                :question/choice-clicked
                                                :question/choice-blank))]})
                  (dom/on "click" (e/fn [_]
                                    (reset! !choice-clicked (:id choice))
                                    (e/server (rama/process-user-vote
                                               {:session-id session-id
                                                :user-id user-id
                                                :question-id (:id quiz-question)
                                                :choice-id (:id choice)})
                                              nil)))
                  (dom/text (:value choice)))))

            (when (some? question)
              (dom/div
                (dom/props {:class (styles :question/next-question-container)})
                (dom/p (dom/text (str (inc question-index) "/" (count questions))))
                (dom/p (dom/text (str "Next question in " time-before-next-question "s")))))))
        (dom/div
          (dom/props {:class (styles :question-answer/container)})
          (dom/h1 (dom/text "Responses"))))
      (dom/div (dom/props {:class (styles :results/spacer)}))
      (let [leaderboard (e/server
                          (let [results (new (rama/!latest-session-results session-id))]
                            (rama/retrieve-current-leaderboard (:users-id session) results)))]
        (prn leaderboard)
        (dom/div
          (dom/props {:class (styles :results/container)})
          (dom/h1
            (dom/props {:class (styles :results/title)})
            (dom/text "Leaderboard"))
          (dom/ul
            (e/for-by first [[idx user] leaderboard]
                      (dom/li (dom/text (:display-name user))))))))))

(e/defn QuizSession [session-id user-id]
  ;; Handle add/remove user into the session
  (e/server
    (rama/add-user-to-session session-id user-id)
    (e/on-unmount #(rama/remove-user-to-session session-id user-id)))

  (let [session (e/server
                  (let [session (rama/retrieve-session-by-id session-id)]
                    (assoc session
                           :id session-id
                           :users-id (new (rama/!latest-users-in-session session-id)))))
        quiz (e/server (rama/retrieve-quiz-by-id (:quiz-id session)))
        nb-current-users (count (:users-id session))
        max-player-per-session (:max-player-per-session quiz)
        start-at (e/server (new (rama/!latest-start-at-session session-id)))
        time-before-start (- start-at e/system-time-ms)
        nb-missing-user (- max-player-per-session nb-current-users)
        can-start? (or (zero? time-before-start) (= nb-current-users max-player-per-session))]
    (dom/div
      (dom/props {:class (styles :session/container)})
      (if can-start?
        (QuizEngine. quiz session user-id)
        (WaitingRoom. time-before-start nb-missing-user)))))
