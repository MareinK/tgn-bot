(ns tgn-bot.acceptance
  (:require [tgn-bot.core :refer [state config]]
            [tgn-bot.util :refer [get-all-channel-messages delete-messages!]]
            [discljord.messaging :as messaging]
            [discljord.formatting :as formatting]
            [clojure.string :as str]
            java-time))

(defn member-accepted? [member]
  (some #{(get-in config [:role-ids :accepted])} (:roles member)))

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

(defn relevant-message? [id->member message]
  (let [users-roles (->> (message-author-and-mentions-ids message)
                      (map id->member)
                      (map :roles)
                      (filter some?))]
    (some #(not-any? #{(get-in config [:role-ids :accepted])} %) users-roles)))

(defn construct-id->member [guild-id user-ids]
  (apply merge
    (for [user-id user-ids]
      {user-id @(messaging/get-guild-member! (:rest @state) guild-id user-id)})))

(defn irrelevant-messages [guild-id messages]
  (let [messages-user-ids (map message-author-and-mentions-ids messages)
        unique-user-ids (set (apply concat messages-user-ids))
        id->member (construct-id->member guild-id unique-user-ids)]
    (take-while #(not (relevant-message? id->member %)) (reverse messages))))

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
        messages-to-delete (concat user-messages (irrelevant-messages guild-id channel-messages))
        message-ids-to-delete (map :id messages-to-delete)]
    (messaging/create-message! (:rest @state) (get-in config [:channel-ids :welcome]) :content (welcome-message accepted))
    (messaging/create-message! (:rest @state) (:id dm-channel) :content (accepted-private-message (seq user-messages-message)))
    (messaging/create-message! (:rest @state) (:id dm-channel) :content user-messages-message)
    ;(messaging/bulk-delete-messages! (:rest @state) (get-in config [:channel-ids :introduction]) message-ids-to-delete)
    (delete-messages! (get-in config [:channel-ids :introduction]) messages-to-delete)
    (messaging/create-message! (:rest @state) (get-in config [:channel-ids :introduction]) :content (accepted-channel-message acceptor accepted))))

(defn remind-silent-users-message [users]
  (format
    (get-in config [:messages :intro-reminder])
    (->> users
      (map :id)
      (map formatting/mention-user)
      (str/join " "))))

(defn kick-silent-users-message [users]
  (format
    (get-in config [:messages :kicked-channel])
    (->> users
      (map :id)
      (map formatting/mention-user)
      (str/join " "))))

(defn unaccepted-guild-members []
  (->> @(messaging/list-guild-members! (:rest @state) (:guild-id config) :limit 1000)
    (remove member-accepted?)))

(defn message->user-timestamps [message]
  (apply merge
    (for [id (message-author-and-mentions-ids message)]
      {id (java-time/instant (:timestamp message))})))

(defn user-id->max-timestamp []
  (let [messages (get-all-channel-messages (get-in config [:channel-ids :introduction]))
        author-timestamps (for [message messages]
                            {(get-in message [:author :id]) (java-time/instant (:timestamp message))})
        max-author-timestamps (apply merge-with java-time/max author-timestamps)]
    max-author-timestamps))

(defn users-silent-for-n-days [days]
  (let [max-timestamps (user-id->max-timestamp)]
    (->>
      (unaccepted-guild-members)
      (filter
        (fn [member]
          (-> (or
                (max-timestamps (get-in member [:user :id]))
                (java-time/instant (:joined-at member)))
            (java-time/time-between (java-time/instant) :days)
            (= days))))
      (map :user))))

(defn remind-silent-users []
  (let [silent-users (users-silent-for-n-days (:introduction-reminder-days config))]
    (when (seq silent-users)
      (messaging/create-message! (:rest @state) (get-in config [:channel-ids :introduction])
        :content (remind-silent-users-message silent-users)))))

(defn kick-silent-users []
  (let [silent-users (users-silent-for-n-days (:introduction-kick-days config))]
    (when (seq silent-users)
      (if (<= (count silent-users) 3)
        (do
          (messaging/create-message! (:rest @state) (get-in config [:channel-ids :introduction])
            :content (kick-silent-users-message silent-users))
          (for [user silent-users]
              (messaging/remove-guild-member! (:rest @state) (:guild-id config) (:id user))))
        (messaging/create-message! (:rest @state) (get-in config [:channel-ids :introduction])
          :content (get-in config [:messages :too-many-to-kick]))))))
