(ns quizy.web-ui.views.board
  (:require
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [shadow.css :refer [css]]
   [quizy.web-ui.views.quiz-list :as quiz-list]
   [quizy.web-ui.views.quiz-item :as quiz-item]
   [quizy.web-ui.views.quiz-session :as quiz-session]
   #?(:clj [quizy.server.rama :as rama])))

(e/def current-user nil)

(def default-avatar-svg
  "<svg width=\"32\" height=\"32\" viewBox=\"0 0 32 32\"><defs><path id=\"a\" d=\"M0 0h32v32H0z\"/></defs><g fill=\"none\" fill-rule=\"evenodd\"><mask id=\"b\" fill=\"#fff\"><use xlink:href=\"#a\"/></mask><use fill=\"#C2CEDE\" xlink:href=\"#a\"/><path fill=\"#90A7C3\" d=\"M8.053 28c.46-3.94 3.843-7 7.947-7s7.487 3.06 7.947 7H24v4H8v-4h.053zM16 19a6 6 0 1 1 0-12 6 6 0 0 1 0 12z\" mask=\"url(#b)\"/></g></svg>")

(def styles
  {:avatar/container (css :flex :justify-center :items-center)
   :avatar/image (css :mr-2 :rounded-full :overflow-hidden)
   :board/container (css :flex-1)
   :board/inner-container (css :p-5 :mt-3 :flex :flex-1 :justify-center)
   :header/container (css :flex :p-3 :bg-white {:height "55px"})})

(e/defn Avatar []
  (dom/div
    (dom/props {:class (styles :avatar/container)})
    (dom/div
      (dom/props {:class (styles :avatar/image)})
      (set! (.-innerHTML dom/node) default-avatar-svg))
    (dom/div (dom/text (:display-name current-user)))))

(e/defn Header []
  (dom/header
    (dom/props {:class (styles :header/container)})
    (Avatar.)))

(e/defn Board [route-name route-match]
  (e/server
    (let [raw-user-id (get-in e/*http-request* [:cookies "quizy-id" :value])
          user (rama/retrieve-logged-user raw-user-id)]
      (e/client
        (binding [current-user user]
          (dom/div
            (dom/props {:class (styles :board/container)})
            (Header.)
            (dom/div
              (dom/props {:class (styles :board/inner-container)})
              (case route-name
                :quizzes (quiz-list/QuizList.)
                :session (quiz-session/QuizSession. (uuid (-> route-match :path-params :id))
                                                    (uuid raw-user-id))))))))))
                :quiz (quiz-item/QuizItem. (-> route-match :path-params :id))
