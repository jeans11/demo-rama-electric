(ns quizy.web-ui.core
  (:require
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [hyperfiddle.history :as history]
   [quizy.web-ui.routes :as routes]
   [quizy.web-ui.views.login :as login]
   [quizy.web-ui.views.board :as board]))

(e/defn NotFound []
  (dom/div
    (dom/h1 (dom/text "Sorry but there is nothing to see here!"))))

;; ----------------------------------------
;; ENTRYPOINT
;; ----------------------------------------

(e/defn App []
  (let [!router (routes/create-router)]
    (binding [routes/router !router
              history/encode (routes/encode-uri !router)
              history/decode (routes/decode-uri !router)]
      (history/router
        (history/HTML5-History.)
        (let [route-name (get-in history/route [:data :name] :login)]
          (case route-name
            :login (login/Login. "login")
            :signup (login/Login. "signup")
            :board (board/Board.)
            (NotFound.)))))))
