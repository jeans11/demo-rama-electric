(ns dre.web-ui.views.quiz-item
  (:require
   #?(:clj [dre.server.rama :as rama])
   [clojure.string :as str]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [shadow.css :refer [css]]
   #?(:cljs [dre.web-ui.routes :as routes])))

(def styles
  {:quiz-item/container (css :flex :flex-1 :flex-col
                             {:max-width "700px"})
   :quiz-item/header-container (css :p-5 :rounded-md :flex :flex-1 :flex-col :bg-white)
   :quiz-item/title-container (css :flex :flex-1 :flex-row :justify-between :items-center)
   :quiz-item/title (css :text-gray-700 :text-2xl)
   :quiz-item/level (css :bg-green-100 :text-green-800 :text-xs :font-medium :mr-2 :px-2.5 :py-0.5 :rounded
                         :border :border-green-400)
   :quiz-item/session-container (css :mt-5)
   :quiz-item/session-title (css :text-gray-700 :text-xl)
   :session-item/container (css :flex :flex-1 :bg-white :rounded-md
                                :justify-between
                                :p-5 :mt-3
                                :items-center
                                :cursor-pointer)})

(e/defn QuizSessionItem [session-id max-player user-id]
  (let [current-users (e/server (new (rama/!latest-users-in-session session-id)))]
    (dom/div
      (dom/props {:class (styles :session-item/container)})
      (dom/on "click" (e/fn [_]
                        (e/server (rama/add-user-to-session session-id user-id))
                        (routes/Navigate. :session {:path-params {:id (str session-id)}})))
      (dom/p (dom/text (last (str/split (str session-id) #"-"))))
      (dom/p (dom/text (str (count current-users) " / " max-player "  attendees"))))))

(e/defn QuizItem [quiz-id user-id]
  (let [quiz (e/server (rama/retrieve-quiz-by-id quiz-id))]
    (dom/div
      (dom/props {:class (styles :quiz-item/container)})
      (dom/div
        (dom/props {:class (styles :quiz-item/header-container)})
        (dom/div
          (dom/props {:class (styles :quiz-item/title-container)})
          (dom/h1
            (dom/props {:class (styles :quiz-item/title)})
            (dom/text (:title quiz)))
          (dom/div
            (dom/props {:class (styles :quiz-item/level)})
            (dom/span (dom/text (str/upper-case (:level quiz))))))
        (dom/div
          (dom/p
            (dom/props {:class (styles :quiz-item/description)})
            (dom/text (:description quiz)))))
      (dom/div
        (dom/props {:class (styles :quiz-item/session-container)})
        (dom/h3
          (dom/props {:class (styles :quiz-item/session-title)})
          (dom/text "Join a session:"))
        (e/server
          (e/for [session-id (rama/retrieve-quiz-sessions quiz-id)]
            (e/client
              (QuizSessionItem. session-id (:max-player-per-session quiz) user-id))))))))
