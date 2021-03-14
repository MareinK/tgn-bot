(ns tgn-bot.util
  (:require [tgn-bot.core :refer [state]]
            [discljord.messaging :as messaging]))

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
