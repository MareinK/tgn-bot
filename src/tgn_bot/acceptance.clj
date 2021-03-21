(ns tgn-bot.acceptance
  (:require [tgn-bot.core :refer [state config]]
            [tgn-bot.util :refer [get-all-channel-messages delete-messages!]]
            [discljord.messaging :as messaging]
            [discljord.formatting :as formatting]
            [clojure.string :as str]
            java-time))

(defn member-acceptor? [member]
  (some #{(get-in config [:role-ids :acceptor])} (:roles member)))

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

(defn accepted-channel-message [acceptor accepted]
  (format
    (get-in config [:messages :accepted-channel])
    (formatting/mention-user acceptor)
    (:username accepted)))

(defn mentions-unaccepted? [id->member message]
  (some
    (complement member-accepted?)
    (filter identity (->> message :mentions (map :id) (map id->member)))))

(defn unwanted? [id->member message]
  (let [author-member (id->member (get-in message [:author :id]))
        mention-members (map #(-> % :id id->member) (:mentions message))]
    (or
      (nil? author-member)
      (if (member-acceptor? author-member)
        (and
          (not-empty mention-members)
          (every?
            #(or
               (nil? %)
               (and
                 (not (member-acceptor? %))
                 (member-accepted? %)))
            mention-members))
        (member-accepted? author-member)))))

(defn clean-introduction-messages [messages]
  (let [members @(messaging/list-guild-members! (:rest @state) (:guild-id config) :limit 1000)
        id->member (zipmap (map (comp :id :user) members) members)
        [irrelevant relevant] (split-with (partial (complement mentions-unaccepted?) id->member) (reverse messages))
        unwanted (filter (partial unwanted? id->member) relevant)]
    (delete-messages! (get-in config [:channel-ids :introduction]) (concat irrelevant unwanted))))

(comment
  (let [messages (get-all-channel-messages (get-in config [:channel-ids :introduction]))]
    (clean-introduction-messages messages)))

(defn user-messages-string [messages user-id]
  (->>
    messages
    (filter #(= (get-in % [:author :id]) user-id))
    (reverse)
    (map :content)
    (str/join "\n\n" )))

(defn accept [acceptor accepted guild-id]
  @(messaging/modify-guild-member!
    (:rest @state)
    guild-id
    (:id accepted)
    :roles (conj (get-in accepted [:member :roles]) (get-in config [:role-ids :accepted])))
  (let [channel-messages (get-all-channel-messages (get-in config [:channel-ids :introduction]))
        user-messages-message (user-messages-string channel-messages (:id accepted))
        dm-channel @(messaging/create-dm! (:rest @state) (:id accepted))]
    (messaging/create-message! (:rest @state) (get-in config [:channel-ids :welcome]) :content (welcome-message accepted))
    (messaging/create-message! (:rest @state) (:id dm-channel) :content (accepted-private-message (seq user-messages-message)))
    (messaging/create-message! (:rest @state) (:id dm-channel) :content user-messages-message)
    (messaging/create-message! (:rest @state) (get-in config [:channel-ids :introduction]) :content (accepted-channel-message acceptor accepted))
    (clean-introduction-messages channel-messages)))

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
          (doseq [user silent-users]
              @(messaging/remove-guild-member! (:rest @state) (:guild-id config) (:id user)))
          (messaging/create-message! (:rest @state) (get-in config [:channel-ids :introduction])
            :content (kick-silent-users-message silent-users)))
        (messaging/create-message! (:rest @state) (get-in config [:channel-ids :introduction])
          :content (get-in config [:messages :too-many-to-kick]))))))
