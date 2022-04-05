(ns tgn-bot.pronouns
  (:require [tgn-bot.core :refer [state config]]
            [tgn-bot.util :as util]
            [discljord.messaging :as messaging]
            [discljord.formatting :as formatting]
            [clojure.string :as str]))

(def pronoun-regex #"[A-Za-z][A-Za-z-//]*[A-Za-z]")

(defn pronouns-added-message
  [pronouns user]
  (format (get-in config [:messages :pronouns-added])
          (if (> (count pronouns) 1) "s" "")
          (str/join " en " (map formatting/bold pronouns))
          (formatting/mention-user user)))

(defn pronouns-removed-message
  [pronouns user]
  (format (get-in config [:messages :pronouns-removed])
          (if (> (count pronouns) 1) "s" "")
          (str/join " en " (map formatting/bold pronouns))
          (formatting/mention-user user)))

(defn pronoun-role? [role] (= (:color role) (:pronoun-role-color config)))

(defn pronoun-help-message
  []
  (let [roles-members (util/get-guild-members-by-role!)
        pronoun-roles (->> @(messaging/get-guild-roles! (:rest @state)
                                                        (:guild-id config))
                           (filter pronoun-role?)
                           (sort-by #(-> (get roles-members (:id %)) count) #(compare %2 %1)))]
    (format (get-in config [:messages :pronouns-help])
            (str/join "\n"
                      (map #(str "- " (formatting/bold (:name %)))
                        pronoun-roles)))))

(defn pronoun-invalid-message
  []
  (format (get-in config [:messages :pronouns-invalid])))

(defn valid-pronoun?
  [roles pronoun]
  (let [non-pronoun-roles (remove pronoun-role? roles)]
    (and (re-matches pronoun-regex pronoun)
         (not-any? #(= pronoun (:name %)) non-pronoun-roles))))

(defn user-pronouns-add!
  [channel-id user pronouns]
  (let [pronouns (map str/lower-case pronouns)
        roles @(messaging/get-guild-roles! (:rest @state) (:guild-id config))]
    (if (every? #(valid-pronoun? roles %) pronouns)
      (let [pronoun-roles (filter pronoun-role? roles)]
        (doseq [pronoun pronouns]
          (let [pronoun-role (or (first (filter #(= (:name %) pronoun)
                                          pronoun-roles))
                                 @(messaging/create-guild-role!
                                    (:rest @state)
                                    (:guild-id config)
                                    :name pronoun
                                    :color (:pronoun-role-color config)))]
            @(messaging/add-guild-member-role! (:rest @state)
                                               (:guild-id config)
                                               (:id user)
                                               (:id pronoun-role))))
        @(messaging/create-message! (:rest @state)
                                    channel-id
                                    :content
                                    (pronouns-added-message pronouns user)))
      @(messaging/create-message! (:rest @state)
                                  channel-id
                                  :content
                                  (pronoun-invalid-message)))))

(defn empty-role?
  [role]
  (let [members @(messaging/list-guild-members! (:rest @state)
                                                (:guild-id config)
                                                :limit
                                                1000)]
    (not-any? #(some #{(:id role)} (:roles %)) members)))

(comment (empty-role? {:id "822538317395787868"}))

(defn user-pronouns-remove!
  [channel-id user pronouns]
  (let [pronouns (map str/lower-case pronouns)
        roles @(messaging/get-guild-roles! (:rest @state) (:guild-id config))]
    (if (every? #(valid-pronoun? roles %) pronouns)
      (let [pronoun-roles (filter pronoun-role? roles)]
        (doseq [pronoun pronouns]
          (when-let [pronoun-role (first (filter #(= (:name %) pronoun)
                                           pronoun-roles))]
            @(messaging/remove-guild-member-role! (:rest @state)
                                                  (:guild-id config)
                                                  (:id user)
                                                  (:id pronoun-role))
            (when (empty-role? pronoun-role)
              @(messaging/delete-guild-role! (:rest @state)
                                             (:guild-id config)
                                             (:id pronoun-role)))))
        @(messaging/create-message! (:rest @state)
                                    channel-id
                                    :content
                                    (pronouns-removed-message pronouns user)))
      @(messaging/create-message! (:rest @state)
                                  channel-id
                                  :content
                                  (pronoun-invalid-message)))))

(defn remove-empty-pronouns
  []
  (let [pronoun-roles (->> @(messaging/get-guild-roles! (:rest @state)
                                                        (:guild-id config))
                           (filter pronoun-role?))
        members @(messaging/list-guild-members! (:rest @state)
                                                (:guild-id config)
                                                :limit
                                                1000)]
    (doseq [pronoun-role pronoun-roles]
      (when (not-any? #(some #{(:id pronoun-role)} (:roles %)) members)
        @(messaging/delete-guild-role! (:rest @state)
                                       (:guild-id config)
                                       (:id pronoun-role))))))
