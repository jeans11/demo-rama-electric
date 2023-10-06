(ns quizy.user.core
  (:require
   [quizy.clj-rama.interface.block :as block]
   [quizy.clj-rama.interface.helpers :as helpers]
   [quizy.clj-rama.interface.path :as path])
  (:import
   (com.rpl.rama
    Block
    Depot
    Expr
    PState
    Path
    RamaModule)
   (com.rpl.rama.ops Ops RamaFunction1)
   (java.util UUID)))

(defrecord UserSignup [id email display-name password])

(def fixed-account-schema
  (PState/fixedKeysSchema
   (into-array Object ["email" String
                       "password" String
                       "display-name" String])))

(deftype ExtractEmail []
  RamaFunction1
  (invoke [_ _]
    "email"))

(deftype SignupModule []
  RamaModule
  (define [_ setup topo]
    (.declareDepot setup "*signup-depot" (Depot/hashBy ExtractEmail))
    (let [signup (.stream topo "signup")]
      (.pstate signup "$$email-to-signup" (PState/mapSchema String UUID))
      (.pstate signup "$$accounts" (PState/mapSchema UUID fixed-account-schema))

      (-> (.source signup "*signup-depot") (block/out "*payload")
          (helpers/bind-field "*payload" "id" "*id")
          (helpers/bind-field "*payload" "email" "*email")
          (helpers/bind-field "*payload" "password" "*password")
          (helpers/bind-field "*payload" "display_name" "*display-name")
          (.localSelect "$$email-to-signup" (path/key "*email")) (block/out "*existing-id")
          (.ifTrue (Expr. Ops/OR
                          (into-array Object
                                      [(Expr. Ops/IS_NULL "*existing-id")
                                       (Expr. Ops/EQUAL "*id" "*existing-id")]))
                   (-> (Block/localTransform "$$email-to-signup" (-> (path/key "*email") (.termVal "*id")))
                       (.hashPartition "*id")
                       (.localTransform "$$accounts"
                                        (-> (path/key "*id")
                                            (.multiPath
                                             (into-array Path [(-> (path/key "email") (.termVal "*email"))
                                                               (-> (path/key "password") (.termVal "*password"))
                                                               (-> (path/key "display-name") (.termVal "*display-name"))]))))))))))

(def signup-module-name (.getName SignupModule))

(defn get-signup-depot [cluster]
  (.clusterDepot cluster signup-module-name "*signup-depot"))

(defn get-accounts-pstate [cluster]
  (.clusterPState cluster signup-module-name "$$accounts"))

(defn get-emails-pstate [cluster]
  (.clusterPState cluster signup-module-name "$$email-to-signup"))

(defn send-signup [signup-depot payload]
  (let [id (UUID/randomUUID)
        signup-record (map->UserSignup (assoc payload :id id))]
    (.append signup-depot signup-record)
    id))

(defn get-user-by-id [accounts-pstate user-id]
  (.selectOne accounts-pstate (path/key user-id)))

(defn check-signup [accounts-pstate user-id]
  (some? (get-user-by-id accounts-pstate user-id)))

(defn login [{:keys [accounts emails]} {:keys [email password]}]
  (when-some [user-id (.selectOne emails (path/key email))]
    (let [user (get-user-by-id accounts user-id)]
      (when (= password (get user "password"))
        user-id))))

(defn get-signup-module []
  (->SignupModule))
