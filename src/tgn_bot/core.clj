(ns tgn-bot.core
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.core.async :refer [chan close!]]
            [discljord.messaging :as messaging]
            [discljord.connections :as connections]
            [discljord.formatting :as formatting]
            [discljord.events :refer [message-pump!]]
            [ring.adapter.jetty :refer [run-jetty]]
            [chime.core :as chime]
            [clojure.tools.logging :as log]))
(import '[java.time Instant Duration LocalTime ZonedDateTime ZoneId Period])

(def state (atom nil))

(def bot-id (atom nil))

(def config (edn/read-string (slurp "config.edn")))

(defmulti handle-event (fn [type data] type))

(defmulti handle-command (fn [command args data] command))

(defn help-message []
  (format
    (get-in config [:messages :help])
    (:command-prefix config)))

(defmethod handle-command :help [command args {:keys [channel-id member]}]
  (when (and
          (= channel-id (get-in config [:channel-ids :introduction]))
          (some #{(get-in config [:role-ids :acceptor])} (:roles member)))
    (messaging/create-message! (:rest @state) channel-id :content (help-message))))

(defn welcome-message [user]
  (format
    (get-in config [:messages :welcome])
    (formatting/mention-user user)))

(defn accepted-channel-message [acceptor accepted]
  (format
    (get-in config [:messages :accepted-channel])
    (formatting/mention-user acceptor)
    (:username accepted)))

(defn accepted-private-message [has-messages]
  (let [accepted-private (format
                           (get-in config [:messages :accepted-private])
                           (formatting/mention-channel (get-in config [:channel-ids :introduction]))
                           (formatting/mention-channel (get-in config [:channel-ids :welcome])))
        your-messages (when has-messages (get-in config [:messages :accepted-private-your-messages]))]
    (str/join " " [accepted-private your-messages])))

(defn get-all-channel-messages [channel-id]
  (loop [messages []]
    (let [new-messages (if (seq messages)
                         @(messaging/get-channel-messages!
                            (:rest @state)
                            channel-id
                            :limit 100
                            :before (:id (last messages)))
                         @(messaging/get-channel-messages!
                            (:rest @state)
                            channel-id
                            :limit 100))]
      (if (seq new-messages)
        (recur (concat messages new-messages))
        messages))))

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

(defmethod handle-command :accept [command args {:keys [guild-id channel-id id author member mentions]}]
  (messaging/delete-message! (:rest @state) channel-id id)
  (when-let [mention (first mentions)]
    (when (and
            (= channel-id (get-in config [:channel-ids :introduction]))
            (some #{(get-in config [:role-ids :acceptor])} (:roles member))
            (not-any? #{(get-in config [:role-ids :accepted])} (get-in mention [:member :roles])))
      (messaging/modify-guild-member!
        (:rest @state)
        guild-id
        (:id mention)
        :roles (conj (get-in mention [:member :roles]) (get-in config [:role-ids :accepted])))
      (messaging/create-message! (:rest @state) (get-in config [:channel-ids :welcome]) :content (welcome-message mention))
      (let [channel-messages (get-all-channel-messages (get-in config [:channel-ids :introduction]))
            user-messages (filter #(= (get-in % [:author :id]) (:id mention)) channel-messages)]
        (let [dm-channel @(messaging/create-dm! (:rest @state) (:id mention))
              user-messages-message (str/join "\n\n" (map :content (reverse user-messages)))]
          (messaging/create-message! (:rest @state) (:id dm-channel) :content (accepted-private-message (seq user-messages-message)))
          (messaging/create-message! (:rest @state) (:id dm-channel) :content user-messages-message))
        (let [messages-to-delete (set (concat user-messages (irrelevant-messages guild-id channel-messages)))
              message-ids-to-delete (map :id messages-to-delete)]
          (messaging/bulk-delete-messages! (:rest @state) channel-id message-ids-to-delete)))
      (messaging/create-message! (:rest @state) channel-id :content (accepted-channel-message author mention)))))

(defmethod handle-command :default [command args data]
  #_(println type data))

(defn random-response [user]
  (str (rand-nth (:responses config)) ", " (formatting/mention-user user) \!))

(def command-pattern (re-pattern (str (:command-prefix config) #"(\S+)\s*(.*)")))

(defmethod handle-event :message-create
  [_ {:keys [channel-id author content] :as data}]
  #_(when (some #{@bot-id} (map :id mentions))
      (messaging/create-message! (:rest @state) channel-id :content (str (random-response author) " " channel-id)))
  (when-let [[_ command args] (re-matches command-pattern content)]
    (handle-command (keyword command) args data)))

(defn introduction-message [user]
  (format
    (get-in config [:messages :introduction])
    (formatting/mention-user user)
    (formatting/mention-channel (get-in config [:channel-ids :introduction]))))

(defmethod handle-event :guild-member-add
  [_ {:keys [user] :as data}]
  (messaging/create-message! (:rest @state) (get-in config [:channel-ids :introduction]) :content (introduction-message user)))

(defmethod handle-event :default [type data]
  #_(println type data))

(defn start-bot! [token & intents]
  (let [event-channel (chan 100)
        gateway-connection (connections/connect-bot! token event-channel :intents (set intents))
        rest-connection (messaging/start-connection! token)]
    {:events event-channel
     :gateway gateway-connection
     :rest rest-connection}))

(defn stop-bot! [{:keys [rest gateway events] :as _state}]
  (messaging/stop-connection! rest)
  (connections/disconnect-bot! gateway)
  (close! events))

(defn serve [port]
  (run-jetty
    (constantly {:status 200})
    {:host "0.0.0.0"
     :port port
     :join? false}))

#_(defn keep-alive-task [time]
    (log/info "Sending keep-alive message."))

(defn daily-tasks [time]
  (log/info "Executing daily tasks.")
  #_(do
      (purge-introduction-channel)
      (daily-event-reminder)))

(defn monthly-tasks [time]
  (log/info "Executing monthly tasks.")
  #_(monthly-event-reminder))

(def standard-task-execution-time
  (->
    (LocalTime/of 9 0)
    (.adjustInto (ZonedDateTime/now (ZoneId/of "Europe/Amsterdam")))
    .toInstant))

(defn schedule-tasks []
  #_(chime/chime-at
      (chime/periodic-seq
        (Instant/now)
        (Duration/ofMinutes 10))
      keep-alive-task)
  (chime/chime-at
    (chime/periodic-seq
      standard-task-execution-time
      (Period/ofDays 1))
    daily-tasks)
  (chime/chime-at
    (->>
      (chime/periodic-seq
        standard-task-execution-time
        (Period/ofDays 1))
      (partition-by #(.getMonth (.atZone % (ZoneId/of "Europe/Amsterdam"))))
      (map last))
    monthly-tasks))

(defn -main [& args]
  (serve (Long/parseLong (System/getenv "PORT")))
  (schedule-tasks)
  (reset! state (start-bot! (System/getenv "DISCORD_BOT_TOKEN") :guild-members :guild-messages :direct-messages))
  (reset! bot-id (:id @(messaging/get-current-user! (:rest @state))))
  (try
    (message-pump! (:events @state) handle-event)
    (finally (stop-bot! @state))))
