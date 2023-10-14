(ns quizy.user.core
  (:require
   [com.rpl.rama :as r]
   [com.rpl.rama.path :as path])
  (:import
   (java.util UUID)))

(defrecord UserSignup [id email display-name password])

;; depot
(def *signup-depot "*signup-depot")
;; pstates
(def $$email-to-signup "$$email-to-signup")
(def $$accounts "$$accounts")

;; For the moment disable kondo on the defmodule
#_:clj-kondo/ignore
(r/defmodule SignupModule [setup topo]
  (r/declare-depot setup *signup-depot (r/hash-by :email))
  (let [s (r/stream-topology topo "signup")]
    (r/declare-pstate s $$email-to-signup {String UUID})
    (r/declare-pstate s $$accounts {UUID (r/fixed-keys-schema {:email String
                                                               :password String
                                                               :display-name String})})
    (r/<<sources s
                 (r/source> *signup-depot :> {:keys [*id *email *password *display-name]})
                 (r/local-select> (path/keypath *email) $$email-to-signup :> *existing-id)
                 (r/<<if (r/or> (nil? *existing-id)
                                (= *id *existing-id))
                         (r/local-transform> [(path/keypath *email) (path/termval *id)] $$email-to-signup)
                         (r/|hash *id)
                         (r/local-transform> [(path/keypath *id)
                                              (path/multi-path [:email (path/termval *email)]
                                                               [:password (path/termval *password)]
                                                               [:display-name (path/termval *display-name)])]
                                             $$accounts)))))

(def signup-module-name (r/get-module-name SignupModule))

(defn get-signup-depot [cluster]
  (r/foreign-depot cluster signup-module-name *signup-depot))

(defn get-accounts-pstate [cluster]
  (r/foreign-pstate cluster signup-module-name $$accounts))

(defn get-emails-pstate [cluster]
  (r/foreign-pstate cluster signup-module-name $$email-to-signup))

(defn send-signup [signup-depot payload]
  (let [id (UUID/randomUUID)
        signup-record (map->UserSignup (assoc payload :id id))]
    (r/foreign-append! signup-depot signup-record)
    id))

(defn get-user-by-id [accounts-pstate user-id]
  (r/foreign-select-one (path/keypath user-id) accounts-pstate))

(defn check-signup [accounts-pstate user-id]
  (some? (get-user-by-id accounts-pstate user-id)))

(defn login [{:keys [accounts emails]} {:keys [email password]}]
  (when-some [user-id (r/foreign-select-one (path/keypath email) emails)]
    (let [user (get-user-by-id accounts user-id)]
      (when (= password (get user "password"))
        user-id))))

(defn get-signup-module []
  SignupModule)
