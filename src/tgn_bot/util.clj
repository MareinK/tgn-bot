(ns tgn-bot.util
  (:require [tgn-bot.core :refer [state]]
            [discljord.messaging :as messaging]
            java-time
            [clojure.tools.logging :as log]))

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

(defn can-be-bulk-deleted? [message]
   (-> message
        (:timestamp)
        (java-time/instant)
        (java-time/time-between (java-time/instant) :days)
        (< 14)))

(defn delete-messages! [channel-id messages]
  (let [{bulk-deletable true single-deletable false} (group-by can-be-bulk-deleted? (set messages))]
    (cond
      (= (count bulk-deletable) 1) (messaging/delete-message! (:rest @state) channel-id (:id (first bulk-deletable)))
      (> (count bulk-deletable) 1) (doseq [messages-part (partition-all 200 bulk-deletable)]
                                     (messaging/bulk-delete-messages! (:rest @state) channel-id (map :id messages-part))))
    (doseq [message single-deletable]
      (messaging/delete-message! (:rest @state) channel-id (:id message)))))
