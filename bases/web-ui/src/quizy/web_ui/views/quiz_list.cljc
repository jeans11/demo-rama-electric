(ns quizy.web-ui.views.quiz-list
  (:require
   #?(:clj [quizy.server.rama :as rama])
   #?(:cljs [quizy.web-ui.routes :as routes])
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [shadow.css :refer [css]]
   [clojure.string :as str]))

(def styles
  {:quiz-list/container (css :flex :flex-1 :flex-col)
   :quiz-list/inner-container (css :mt-3 :flex :flex-1)
   :quiz-list/title (css :text-gray-700 :text-2xl)
   :quiz-item/container (css :p-2 :mb-3 :flex :flex-1 :justify-between
                             :cursor-pointer
                             :items-center :bg-white :rounded-md
                             {:height "55px"
                              :transition "all .2s ease-in-out"}
                             [:hover {:transform "scale(1.02)"}])
   :quiz-item/title (css :text-gray-700 :font-medium)
   :quiz-item/level (css :bg-green-100 :text-green-800 :text-xs :font-medium :mr-2 :px-2.5 :py-0.5 :rounded
                         :border :border-green-400)
   :quiz-item/total-question (css :text-gray-700)
   :quiz-item/attendees (css :text-gray-700)})

(e/defn QuizListItem [quiz]
  (dom/div
    (dom/on "click" (e/fn [_] (routes/Navigate. :quiz {:path-params {:id (:id quiz)}})))
    (dom/props {:class (styles :quiz-item/container)})
    (dom/h3
      (dom/props {:class (styles :quiz-item/title)})
      (dom/text (:title quiz)))
    (dom/div
      (dom/props {:class (styles :quiz-item/level)})
      (dom/span (dom/text (str/upper-case (:level quiz)))))
    (dom/div
      (dom/props {:class (styles :quiz-item/total-question)})
      (dom/span (dom/text (str (:total-question quiz) " questions"))))))

(e/defn QuizList []
  (dom/div
    (dom/props {:class (styles :quiz-list/container)})
    (dom/h1
      (dom/props {:class (styles :quiz-list/title)})
      (dom/text "Quizzes"))
    (dom/div
      (dom/props {:class (styles :quiz-list/inner-container)})
      (e/server
        (e/for-by :id [quiz (rama/retrieve-quizzes)]
          (e/client
            (QuizListItem. quiz)))))))
