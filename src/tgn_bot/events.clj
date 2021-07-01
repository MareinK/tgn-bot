(ns tgn-bot.events
  (:require [tgn-bot.core :refer [state config bot-id]]
            [tgn-bot.commands :refer [handle-command]]
            [discljord.messaging :as messaging]
            [discljord.formatting :as formatting]
            [tgn-bot.pronouns :as pronouns]
            [tgn-bot.fluff :as fluff]
            [tgn-bot.acceptance :as acceptance]
            [tgn-bot.util :refer [get-all-channel-messages]]))

(defmulti handle-event (fn [type data] type))

(defmethod handle-event :default [type data]
  #_(println type data))

(defn introduction-message [user]
  (format
   (get-in config [:messages :introduction])
   (formatting/mention-user user)
   (formatting/mention-channel (get-in config [:channel-ids :introduction]))))

(defmethod handle-event :guild-member-add
  [_ {:keys [user] :as data}]
  (messaging/create-message! (:rest @state) (get-in config [:channel-ids :introduction]) :content (introduction-message user)))

(def command-pattern (re-pattern (str (:command-prefix config) #"(\S+)\s*(.+)?")))

(defmethod handle-event :message-create
  [_ {:keys [author content mentions] :as data}]
  (when (not= (:id author) @bot-id)
    (when (some #{@bot-id} (map :id mentions))
      (fluff/respond-to-bot-mention data))
    (when-let [[_ command args] (re-matches command-pattern content)]
      (handle-command (keyword command) args data))))

(defn user-left-message [user]
  (format
   (get-in config [:messages :user-left])
   (formatting/bold (:username user))))

(defmethod handle-event :guild-member-remove
  [_ {:keys [user]}]
  (pronouns/remove-empty-pronouns)
  (let [deleted (acceptance/clean-introduction-channel)]
    (when (seq deleted)
      (messaging/create-message! (:rest @state) (get-in config [:channel-ids :introduction])
                                 :content (user-left-message user)))))
