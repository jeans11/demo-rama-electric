(ns dev.rama
  {:clj-kondo/config {:ignore true}}
  (:require [com.rpl.rama :as r]
            [com.rpl.rama.ops :as ops]))

(comment

  ;;-----------------
  ;; Basics
  ;; ----------------

  (r/?<- (println "Hello"))

  (r/deframaop foo [*a]
    (:> (inc *a))
    (:> (dec *a))
    (:> (* *a *a)))

  (r/?<-
   (foo 1 :> *v)
   (println *v))

  (r/?<-
    (count [1 2 3] :> *v)
    (println *v))

  ;;-----------------
  ;; Branching
  ;; ----------------

  (def team
    [{:name "Brasegali" :type #{:fire :fight}}
     {:name "Nostenfer" :type #{:poison :fly}}
     {:name "Leviator" :type #{:water :fly}}
     {:name "Dracolosse" :type #{:dragon :fly}}])

  (r/deframaop pokemon-categorize [*pokemon]
    (identity *pokemon :> {:keys [*name *type]})
    (r/<<if (contains? *type :fire)
          (:fire> *name))
    (r/<<if (contains? *type :fly)
          (:fly> *name))
    (r/<<if (contains? *type :dragon)
          (:dragon> *name)))

  (r/?<-
   (ops/explode team :> *pokemon)
   (pokemon-categorize *pokemon :fly> <fly> *v :fire> <fire> *v :dragon> <dragon> *v)
   (r/hook> <fly>)
   (println (str *v " is a fly pokemon"))
   (r/hook> <fire>)
   (println (str *v " is a fire pokemon"))
   (r/hook> <dragon>)
   (println (str *v " is a dragon pokemon")))

  nil)
