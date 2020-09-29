;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2020 UXBOX Labs SL

(ns app.services.mutations.teams
  (:require
   [app.common.exceptions :as ex]
   [app.common.spec :as us]
   [app.common.uuid :as uuid]
   [app.db :as db]
   [app.services.mutations :as sm]
   [app.services.queries.teams :as teams]
   [app.services.mutations.projects :as projects]
   [clojure.spec.alpha :as s]))

;; --- Helpers & Specs

(s/def ::id ::us/uuid)
(s/def ::name ::us/string)
(s/def ::profile-id ::us/uuid)

;; --- Mutation: Create Team

(declare create-team)
(declare create-team-profile)
(declare create-team-default-project)

(s/def ::create-team
  (s/keys :req-un [::profile-id ::name]
          :opt-un [::id]))

(sm/defmutation ::create-team
  [params]
  (db/with-atomic [conn db/pool]
    (let [team   (create-team conn params)
          params (assoc params :team-id (:id team))]
      (create-team-profile conn params)
      (create-team-default-project conn params)
      team)))

(defn create-team
  [conn {:keys [id profile-id name default?] :as params}]
  (let [id (or id (uuid/next))
        default? (if (boolean? default?) default? false)]
    (db/insert! conn :team
                {:id id
                 :name name
                 :photo ""
                 :is-default default?})))

(defn create-team-profile
  [conn {:keys [team-id profile-id] :as params}]
  (db/insert! conn :team-profile-rel
              {:team-id team-id
               :profile-id profile-id
               :is-owner true
               :is-admin true
               :can-edit true}))

(defn create-team-default-project
  [conn {:keys [team-id profile-id] :as params}]
  (let [proj (projects/create-project conn {:team-id team-id
                                            :name "Drafts"
                                            :default? true})]
    (projects/create-project-profile conn {:project-id (:id proj)
                                           :profile-id profile-id})))


;; --- Mutation: Update Team

(s/def ::update-team
  (s/keys :req-un [::profile-id ::name ::id]))

(sm/defmutation ::update-team
  [{:keys [id name profile-id] :as params}]
  (db/with-atomic [conn db/pool]
    (teams/check-edition-permissions! conn profile-id id)
    (db/update! conn :team
                {:name name}
                {:id id})
    nil))


;; --- Mutation: Leave Team

(s/def ::leave-team
  (s/keys :req-un [::profile-id ::id]))

(sm/defmutation ::leave-team
  [{:keys [id profile-id] :as params}]
  (db/with-atomic [conn db/pool]
    (let [perms (teams/check-read-permissions! conn profile-id id)]
      (when (:is-owner perms)
        (ex/raise :type :validation
                  :code :owner-cant-leave-team
                  :hint "reasing owner before leave"))

      (db/delete! conn :team-profil-rel
                  {:profile-id profile-id
                   :team-id id})

      nil)))


;; --- Mutation: Delete Team

(s/def ::delete-team
  (s/keys :req-un [::profile-id ::id]))

(sm/defmutation ::delete-team
  [{:keys [id profile-id] :as params}]
  (db/with-atomic [conn db/pool]
    (let [perms (teams/check-edition-permissions! conn profile-id id)]
      (when-not (:is-owner perms)
        (ex/raise :type :validation
                  :code :only-owner-can-delete-team))

      (db/delete! conn :team
                  {:team-id id})

      nil)))



