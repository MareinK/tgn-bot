(ns tgn-bot.main
  (:require [tgn-bot.core :refer [state config bot-id]]
            [tgn-bot.events :refer [handle-event]]
            [tgn-bot.scheduling :refer [schedule-tasks]]
            [discljord.messaging :as messaging]
            [discljord.connections :as connections]
            [discljord.events :refer [message-pump!]]
            [ring.adapter.jetty :refer [run-jetty]]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [chan close!]]))

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

(defn -main [& args]
  (let [port (System/getenv "PORT")
        discord-bot-token (System/getenv "DISCORD_BOT_TOKEN")
        discord-server-config (System/getenv "DISCORD_SERVER_CONFIG")]
    (if (and port discord-bot-token discord-server-config)
      (do
        (serve (Long/parseLong port))
        (schedule-tasks)
        (reset! state (start-bot! discord-bot-token :guild-members :guild-messages :direct-messages))
        (reset! bot-id (:id @(messaging/get-current-user! (:rest @state))))
        (try
          (message-pump! (:events @state) handle-event)
          (finally (stop-bot! @state))))
      (throw (ex-info "Not all environment variables are set." {})))))
