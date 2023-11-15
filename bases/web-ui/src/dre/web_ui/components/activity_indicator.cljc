(ns dre.web-ui.components.activity-indicator
  {:doc "Port of https://github.com/necolas/react-native-web/blob/master/packages/react-native-web/src/exports/ActivityIndicator/index.js"
   :shadow.css/include ["dre/web_ui/components/activity_indicator.css"]}
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-svg :as svg]
            [shadow.css :refer [css]]))

(def styles
  {::small (css {:width "20px" :height "20px"})
   ::large (css {:width "36px" :height "36px"})})

(e/defn CreateSvgCircle [style]
  (svg/circle (dom/props {:cx "16"
                          :cy "16"
                          :fill "none"
                          :r "14"
                          :stroke-width "4"
                          :style style})))

(e/defn CreateSvg [color]
  (svg/svg
   (dom/props {:height "100%" :width "100%" :viewBox "0 0 32 32"})
   (CreateSvgCircle. {"stroke" color
                      "opacity" 0.2})
   (CreateSvgCircle. {"stroke" color
                      ;; CHECK THIS
                      "stroke-dasharray" 80
                      "stroke-dashoffset" 60})))

(e/defn ActivityIndicator [{:keys [color size] :or {size "small"}}]
  (dom/div
    (dom/props {:class ["ActivityIndicator"
                        (case size
                          "small" (styles ::small)
                          "large" (styles ::large))]})
    (CreateSvg. color)))
