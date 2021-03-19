(ns tgn-bot.commands
  (:require [tgn-bot.core :refer [state config]]
            [tgn-bot.acceptance :refer [accept]]
            [tgn-bot.pronouns :refer [user-pronouns-add! user-pronouns-remove!]]
            [discljord.messaging :as messaging]
            [clojure.string :as str]))

(defmulti handle-command (fn [command args data] command))

(defmethod handle-command :default [command args data]
  #_(println type data))

(defn help-message []
  (format
   (get-in config [:messages :help])
   (:command-prefix config)))

(defmethod handle-command :help [command args {:keys [channel-id member]}]
  (when (and
         (= channel-id (get-in config [:channel-ids :introduction]))
         (some #{(get-in config [:role-ids :acceptor])} (:roles member)))
    (messaging/create-message! (:rest @state) channel-id :content (help-message))))

(defmethod handle-command :accept [command args {:keys [guild-id channel-id id author member mentions]}]
  (messaging/delete-message! (:rest @state) channel-id id)
  (when-let [mention (first mentions)]
    (when (and
           (= channel-id (get-in config [:channel-ids :introduction]))
           (some #{(get-in config [:role-ids :acceptor])} (:roles member))
           (not-any? #{(get-in config [:role-ids :accepted])} (get-in mention [:member :roles])))
      (accept author mention guild-id))))

(defmethod handle-command :pronoun [command args {:keys [member]}]
  (if args
    (let [pronouns (filter seq (str/split "" #"\s+"))]
      (do
        (user-pronouns-add! (:user member) pronouns)
        #_ (send channel message)))
    (do
      #_(pronoun help text))))

(defmethod handle-command :unpronoun [command args data]
  )