(ns tgn-bot.pronouns
  (:require [tgn-bot.core :refer [state config]]
            [tgn-bot.acceptance :refer [accept]]
            [discljord.messaging :as messaging]))

(defn user-pronouns-add! [user pronouns]
  (let [pronoun-roles (->>
                        @(messaging/get-guild-roles! (:rest @state) (:guild-id config))
                        (filter #(= (:color %) (:pronoun-role-color config))))]
    (for [pronoun pronouns]
      (let [pronoun-role (or
                           (seq (filter #(= (:name %) pronoun) pronoun-roles))
                           @(messaging/create-guild-role! (:rest @state) (:guild-id config)
                              :name pronoun :color (:pronoun-role-color config)))]
        (messaging/add-guild-member-role! (:rest @state) (:guild-id config)
          (:id user)
          (:id pronoun-role))))))

(defn user-pronouns-remove! [user pronouns]
  (for [pronoun pronouns]
    ()))
