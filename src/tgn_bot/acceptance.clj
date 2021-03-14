(ns tgn-bot.acceptance
  (:require [tgn-bot.core :refer [state config]]
            [tgn-bot.util :refer [get-all-channel-messages]]
            [discljord.messaging :as messaging]
            [discljord.formatting :as formatting]
            [clojure.string :as str]))

(defn welcome-message [user]
  (format
    (get-in config [:messages :welcome])
    (formatting/mention-user user)))

(defn accepted-private-message [has-messages]
  (let [accepted-private (format
                           (get-in config [:messages :accepted-private])
                           (formatting/mention-channel (get-in config [:channel-ids :introduction]))
                           (formatting/mention-channel (get-in config [:channel-ids :welcome])))
        your-messages (when has-messages (get-in config [:messages :accepted-private-your-messages]))]
    (str/join " " [accepted-private your-messages])))

(defn message-author-and-mentions-ids [message]
  (map :id (conj (:mentions message) (:author message))))

(defn relevant-message? [id->user message]
  (let [users-roles (->> (message-author-and-mentions-ids message)
                      (map id->user)
                      (map :roles)
                      (filter some?))]
    (some #(not-any? #{(get-in config [:role-ids :accepted])} %) users-roles)))

(defn irrelevant-messages [guild-id messages]
  (let [messages-user-ids (map message-author-and-mentions-ids messages)
        unique-user-ids (set (apply concat messages-user-ids))
        id->user (into {} (for [id unique-user-ids] [id @(messaging/get-guild-member! (:rest @state) guild-id id)]))]
    (take-while #(not (relevant-message? id->user %)) (reverse messages))))

(defn accepted-channel-message [acceptor accepted]
  (format
    (get-in config [:messages :accepted-channel])
    (formatting/mention-user acceptor)
    (:username accepted)))

(defn accept [acceptor accepted guild-id]
  (messaging/modify-guild-member!
    (:rest @state)
    guild-id
    (:id accepted)
    :roles (conj (get-in accepted [:member :roles]) (get-in config [:role-ids :accepted])))
  (let [channel-messages (get-all-channel-messages (get-in config [:channel-ids :introduction]))
        user-messages (filter #(= (get-in % [:author :id]) (:id accepted)) channel-messages)
        dm-channel @(messaging/create-dm! (:rest @state) (:id accepted))
        user-messages-message (str/join "\n\n" (map :content (reverse user-messages)))
        messages-to-delete (set (concat user-messages (irrelevant-messages guild-id channel-messages)))
        message-ids-to-delete (map :id messages-to-delete)]
    (messaging/create-message! (:rest @state) (get-in config [:channel-ids :welcome]) :content (welcome-message accepted))
    (messaging/create-message! (:rest @state) (:id dm-channel) :content (accepted-private-message (seq user-messages-message)))
    (messaging/create-message! (:rest @state) (:id dm-channel) :content user-messages-message)
    (messaging/bulk-delete-messages! (:rest @state) (get-in config [:channel-ids :introduction]) message-ids-to-delete)
    (messaging/create-message! (:rest @state) (get-in config [:channel-ids :introduction]) :content (accepted-channel-message acceptor accepted))))
