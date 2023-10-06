(ns quizy.web-ui.views.login
  (:require
   #?(:cljs [reagent.cookies :as cookies])
   #?(:clj [quizy.server.rama :as rama])
   [clojure.string :as str]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   #?(:cljs [quizy.web-ui.routes :as routes])
   [shadow.css :refer [css]]))

(defn set-login-cookie [user-id]
  #?(:cljs
     (do
       (cookies/set! :quizy-id user-id
                     {:secure? false
                      :raw? true
                      :same-site :strict})
       (prn "foo")
       :idle)))

(def styles
  {:login/container (css :flex :flex-1 :flex-col :justify-center :items-center)
   :login/inner-container (css {:min-width "360px"})
   :login/title (css :text-2xl :mb-4)
   :login/actions (css :flex :items-center :justify-between)
   :login/login-button (css :bg-blue-500 :text-white :font-bold :py-2 :px-4 :rounded)
   :login/signup-button (css :inline-block :align-baseline :font-bold :text-sm :text-blue-500)
   :form/container (css :bg-white :shadow-md :rounded :px-8 :pt-6 :pb-8 :mb-4)
   :form/field-container (css :mb-6)
   :form/label (css :block :text-gray-700 :text-sm :font-bold :mb-2)
   :form/input (css :shadow :appearance-none :border :rounded :w-full :py-2 :px-3 :text-gray-700 :leading-tight)})

(e/defn EmailField [on-change]
  (dom/div
    (dom/props {:class (styles :form/field-container)})
    (dom/label
      (dom/props {:class (styles :form/label)
                  :for "email"})
      (dom/text "Email"))
    (dom/input
      (dom/on "change" (e/fn [e]
                         (let [value (-> e .-target .-value (str/trim))]
                           (on-change. value))))
      (dom/props {:class (styles :form/input)
                  :id "email"
                  :type "text"
                  :placeholder "example@foo.com"}))))

(e/defn PasswordField [on-change]
  (dom/div
    (dom/props {:class (styles :form/field-container)})
    (dom/label
      (dom/props {:class (styles :form/label)
                  :for "password"})
      (dom/text "Password"))
    (dom/input
      (dom/on "change" (e/fn [e]
                         (let [value (-> e .-target .-value (str/trim))]
                           (on-change. value))))
      (dom/props {:class (styles :form/input)
                  :id "email"
                  :type "password"
                  :placeholder "******************"}))))

(e/defn DisplayNameField [on-change]
  (dom/div
    (dom/props {:class (styles :form/field-container)})
    (dom/label
      (dom/props {:class (styles :form/label)
                  :for "display-name"})
      (dom/text "Display name"))
    (dom/input
      (dom/on "change" (e/fn [e]
                         (let [value (-> e .-target .-value (str/trim))]
                           (on-change. value))))
      (dom/props {:class (styles :form/input)
                  :id "display-name"
                  :type "text"
                  :placeholder "John"}))))

(e/defn Login [mode]
  (let [!state (atom {})
        signup? (= mode "signup")]
    (dom/div
      (dom/props {:class (styles :login/container)})
      #_(dom/h1 (dom/props {:class (styles :login/title)}) (dom/text "Quizy"))
      (dom/div
        (dom/props {:class (styles :login/inner-container)})
        (dom/form
          (dom/props {:class (styles :form/container)})
          (EmailField. (e/fn [value] (swap! !state assoc :email value)))
          (PasswordField. (e/fn [value] (swap! !state assoc :password value)))
          (when signup?
            (DisplayNameField. (e/fn [value] (swap! !state assoc :display-name value))))
          (dom/div
            (dom/props {:class (styles :login/actions)})
            (dom/button
              (dom/props {:class (styles :login/login-button)})
              (dom/on "click" (e/fn [e]
                                (.preventDefault e)
                                (let [state @!state
                                      {:keys [status] :as res} (e/server
                                                                 (if signup?
                                                                   (rama/process-signup state)
                                                                   (rama/process-login state)))]
                                  (if (= status :idle)
                                    (when (= :idle (set-login-cookie (:user-id res)))
                                      (set! (.-href js/window.location) "/quizzes"))
                                    nil))))
              (dom/text (if signup? "Sign Up" "Sign In")))
            (dom/button
              (dom/props {:class (styles :login/signup-button)})
              (dom/on "click" (e/fn [_]
                                (routes/Navigate. (if signup? :login :signup) {})))
              (dom/text (if signup?
                          "You have an account"
                          "Don't have an account?")))))))))
