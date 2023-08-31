(ns quizy.web-ui.core
  (:require
   [hyperfiddle.electric-dom2 :as dom]
   [hyperfiddle.electric :as e]))

(e/defn App []
  (dom/h1 "Hello"))
