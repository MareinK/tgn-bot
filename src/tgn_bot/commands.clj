(ns tgn-bot.commands
  (:require [tgn-bot.core :refer [state config]]
            [tgn-bot.acceptance :refer [accept]]
            [tgn-bot.pronouns :as pronouns]
            [discljord.messaging :as messaging]
            [clojure.string :as str]))

(defmulti handle-command (fn [command args data] command))

(defmethod handle-command :default [command args data]
  #_(println type data))

(defn intro-help-message []
  (format
    (get-in config [:messages :intro-help])
    (:command-prefix config)))

(defn help-message []
  (format
    (get-in config [:messages :help])
    (:command-prefix config)))

(defmethod handle-command :help [command args {:keys [channel-id member]}]
  (if (and
         (= channel-id (get-in config [:channel-ids :introduction]))
         (some #{(get-in config [:role-ids :acceptor])} (:roles member)))
    (messaging/create-message! (:rest @state) channel-id :content (intro-help-message))
    (messaging/create-message! (:rest @state) channel-id :content (help-message))))

(defmethod handle-command :accept [command args {:keys [guild-id channel-id id author member mentions]}]
  (messaging/delete-message! (:rest @state) channel-id id)
  (when-let [mention (first mentions)]
    (when (and
           (= channel-id (get-in config [:channel-ids :introduction]))
           (some #{(get-in config [:role-ids :acceptor])} (:roles member))
           (not-any? #{(get-in config [:role-ids :accepted])} (get-in mention [:member :roles])))
      (accept author mention guild-id))))

(defmethod handle-command :pronoun [command args {:keys [channel-id author]}]
  (if args
    (let [pronouns (set (filter seq (str/split args #"\s+")))]
      (pronouns/user-pronouns-add! channel-id author pronouns))
    @(messaging/create-message! (:rest @state) channel-id
       :content (pronouns/pronoun-help-message))))

(defmethod handle-command :pronouns [command & args]
  (apply handle-command :pronoun args))

(defmethod handle-command :unpronoun [command args {:keys [channel-id author]}]
  (if args
    (let [pronouns (set (filter seq (str/split args #"\s+")))]
      (pronouns/user-pronouns-remove! channel-id author pronouns))
    @(messaging/create-message! (:rest @state) channel-id
       :content (pronouns/pronoun-help-message))))

(defmethod handle-command :unpronouns [command & args]
  (apply handle-command :unpronoun args))

(comment
  (let [member (first @(messaging/list-guild-members! (:rest @state) (:guild-id config)))]
    (handle-command :pronoun "she/her" {:channel-id "820057899446304851" :author (:user member)}))
  (let [member (first @(messaging/list-guild-members! (:rest @state) (:guild-id config)))]
    (handle-command :pronouns "Accepted" {:channel-id "820057899446304851" :author (:user member)}))
  (let [member (first @(messaging/list-guild-members! (:rest @state) (:guild-id config)))]
    (handle-command :unpronoun "she/her" {:channel-id "820057899446304851" :author (:user member)})))
