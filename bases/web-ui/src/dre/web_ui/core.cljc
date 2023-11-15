(ns dre.web-ui.core
  (:require
   #?(:cljs [dre.web-ui.routes :as routes])
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [dre.web-ui.views.board :as board]
   [dre.web-ui.views.login :as login]))

(e/defn NotFound []
  (dom/div
    (dom/h1 (dom/text "Sorry but there is nothing to see here!"))))

;; ----------------------------------------
;; ENTRYPOINT
;; ----------------------------------------

(e/defn App []
  (let [match routes/re-router]
    (binding [dom/node js/document.body
              routes/route-match match
              routes/route-name (some-> match :data :name)]
      (case routes/route-name
        :login (login/Login. "login")
        :signup (login/Login. "signup")
        (:quizzes :quiz :session) (board/Board. routes/route-name routes/route-match)
        (NotFound.)))))
