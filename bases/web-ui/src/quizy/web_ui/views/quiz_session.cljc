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
   :results/title (css :text-gray-700 :text-xl :mb-2)
   :results/spacer (css :ml-5 :mr-5 :bg-gray-200 {:width "1px"})
   :responses/container (css :mt-3)
   :responses/title (css :text-gray-700 :text-xl)
   :responses/item (css :flex :flex-col :rounded-md :p-3 :mb-3 {:background-color "#fff"})
   :responses/item-question-title (css :text-gray-700 :text-lg)
   :responses/item-response-value (css :font-semibold {:text-align "end"})})

(defn format-time-before-start [time-ms]
  #?(:cljs
     (if (neg? time-ms)
       "--:--"
       (let [total-seconds (js/Math.floor (/ time-ms 1000))
             minutes (js/Math.floor (/ total-seconds 60))
             seconds (mod total-seconds 60)]
         (str "0" minutes ":" seconds)))))

(defn index-by-question-id [questions]
  (into {} (map (juxt :id identity))
        (map-indexed (fn [idx item]
                       (assoc item :idx (inc idx)))
                     questions)))

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

(e/defn compute-time-before-next-question [next-question-at]
  (-> next-question-at
      (- e/system-time-ms)
      (/ 1000)
      (js/Math.floor)))

(e/defn QuizLeaderboard [session-id users-id]
  (let [leaderboard (e/server
                      (let [results (new (rama/!latest-session-results session-id))]
                        (rama/retrieve-current-leaderboard users-id results)))]
    (dom/div
      (dom/props {:class (styles :results/container)})
      (dom/h1
        (dom/props {:class (styles :results/title)})
        (dom/text "Leaderboard"))
      (dom/ul
        (e/for-by first [[idx user] leaderboard]
          (dom/li
            (dom/span (dom/text (str (:display-name user) ": ")))
            (dom/span (dom/text (let [score (:score user)]
                                  (str score " point" (when (> score 1) "s")))))))))))

(e/defn QuizResult [questions]
  (dom/div
    (dom/props {:class (styles :question-answer/container)})
    (dom/h1
      (dom/props {:class (styles :responses/title)})
      (dom/text "Responses"))
    (dom/div (dom/props {:class (styles :responses/container)})
             (e/for-by :right-answer [question (e/server (rama/retrieve-questions questions))]
               (dom/div (dom/props {:class (styles :responses/item)})
                        (dom/h2
                          (dom/props {:class (styles :responses/item-question-title)})
                          (dom/text (:title question)))
                        (dom/span
                          (dom/props {:class (styles :responses/item-response-value)})
                          (dom/text (let [right-choice (first (filter #(= (:id %) (:right-answer question))
                                                                      (:choices question)))]
                                      (:value right-choice)))))))))

(e/defn QuizEngine [{:keys [session-id user-id users-id quiz session status]}]
  (let [!choice-clicked (atom nil)
        choice-clicked (e/watch !choice-clicked)
        questions-by-id (index-by-question-id (:questions quiz))
        current-question-id (e/server (new (rama/!latest-session-current-question session-id)))
        next-question-at (e/server (new (rama/!latest-session-next-question-at session-id)))]

    (dom/div
      (dom/props {:class (styles :quiz-engine/container)})
      (if (= "result" status)
        (QuizResult. (:questions quiz))
        (let [quiz-question (questions-by-id current-question-id)
              points (:points quiz-question)
              question (e/server (rama/retrieve-question-by-id current-question-id))
              time-before-next-question (compute-time-before-next-question. next-question-at)]

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
                (dom/p (dom/text (str (:idx quiz-question) "/" (count questions-by-id))))
                (dom/p (dom/text (str "Next question in " time-before-next-question "s"))))))))
      (dom/div (dom/props {:class (styles :results/spacer)}))
      (QuizLeaderboard. session-id users-id))))

(e/defn QuizSession [session-id user-id]
  (e/server
    ;; Add a user on mount
    (rama/add-user-to-session session-id user-id)
    ;; Remove user on unmount
    (e/on-unmount #(rama/remove-user-to-session session-id user-id)))

  (let [session (e/server (rama/retrieve-session-by-id session-id))
        quiz (e/server (rama/retrieve-quiz-by-id (:quiz-id session)))
        users-id (e/server (new (rama/!latest-users-in-session session-id)))
        start-at (e/server (new (rama/!latest-start-at-session session-id)))
        status (e/server (new (rama/!latest-session-status session-id)))
        nb-current-users (count users-id)
        max-users (:max-users session)
        time-before-start (- start-at e/system-time-ms)
        nb-missing-user (- max-users nb-current-users)]
    (dom/div
      (dom/props {:class (styles :session/container)})
      (case status
        "waiting" (WaitingRoom. time-before-start nb-missing-user)
        (QuizEngine. {:session-id session-id
                      :user-id user-id
                      :users-id users-id
                      :quiz quiz
                      :session session
                      :status status})))))
