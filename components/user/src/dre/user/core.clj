(ns dre.user.core
  (:require
   [com.rpl.rama :as r]
   [com.rpl.rama.path :as path]
   [dre.belt.interface :as belt])
  (:import (java.util UUID)))

(defrecord UserSignup [id email display-name password])

;; depot
(def *signup-depot "*signup-depot")
;; pstates
(def $$emails-to-signup "$$emails-to-signup")
(def $$users "$$users")

;; For the moment disable kondo on the defmodule
#_:clj-kondo/ignore
(r/defmodule UserModule [setup topo]
  (r/declare-depot setup *signup-depot (r/hash-by :email))
  (let [s (r/stream-topology topo "signup")]
    (r/declare-pstate s $$emails-to-signup {String String})
    (r/declare-pstate s $$users {String (r/fixed-keys-schema {:email String
                                                              :password String
                                                              :display-name String})})
    (r/<<sources s
                 (r/source> *signup-depot :> {:keys [*id *email] :as *user})
                 (r/local-select> (path/keypath *email) $$emails-to-signup :> *existing-id)
                 (r/<<if (r/or> (nil? *existing-id) (= *id *existing-id))
                         (r/local-transform> [(path/keypath *email) (path/termval *id)] $$emails-to-signup)
                         (r/|hash *id)
                         (r/local-transform> [(path/keypath *id)
                                              (path/termval (into {} (dissoc *user :id)))]
                                             $$users)))))

(def signup-module-name (r/get-module-name UserModule))

(def depots
  [*signup-depot])

(def pstates
  [$$emails-to-signup $$users])

(defn export-depots [cluster]
  (belt/make-depots-map cluster signup-module-name depots))

(defn export-pstates [cluster]
  (belt/make-pstates-map cluster signup-module-name pstates))

(defn send-signup [signup-depot payload]
  (let [id (str (UUID/randomUUID))
        signup-record (map->UserSignup (assoc payload :id id))]
    (r/foreign-append! signup-depot signup-record)
    id))

(defn get-user-by-id [users-pstate user-id]
  (assoc (r/foreign-select-one (path/keypath user-id) users-pstate)
         :id user-id))

(defn check-signup [users-pstate user-id]
  (some? (get-user-by-id users-pstate user-id)))

(defn login [{:keys [users emails]} {:keys [email password]}]
  (when-some [user-id (r/foreign-select-one (path/keypath email) emails)]
    (let [user (get-user-by-id users user-id)]
      (when (= password (get user "password"))
        user-id))))

(defn get-user-module []
  UserModule)
