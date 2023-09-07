(ns quizy.web-ui.views.board
  (:require
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [shadow.css :refer [css]]))

(def styles
  {:board/container (css :flex-1)})

(e/defn Board []
  (dom/div
    (dom/props {:class (styles :board/container)})
    (dom/h1 (dom/text "Board"))))
