(ns tgn-bot.pruning
  (:require [tgn-bot.core :refer [state config]]
            [discljord.messaging :as messaging]
            [clojure.set :as set]))

(defn channel-history
  ([channel-id]
   (channel-history channel-id nil))
  ([channel-id before-message-id]
   (let [messages @(apply messaging/get-channel-messages!
                          (:rest @state) channel-id :limit 100
                          (when before-message-id [:before before-message-id]))]
     (when (seq messages)
       (lazy-cat messages (channel-history channel-id (:id (last messages))))))))

(defn active-user-ids-since [cutoff-time]
  (->> @(messaging/get-guild-channels! (:rest @state) (:guild-id config))
       (map #(->> (channel-history (:id %))
                  (take-while (fn [message] (some-> (:timestamp message)
                                                    (java-time/instant)
                                                    (java-time/after? cutoff-time))))
                  (map (fn [message] (get-in message [:author :id])))
                  (set)))
       (apply set/union)))

(defn inactive-users-since [cutoff-time]
  (let [active-user-ids (active-user-ids-since cutoff-time)]
    (->> @(messaging/list-guild-members! (:rest @state) (:guild-id config) :limit 1000)
         (remove #(or
                   (contains? active-user-ids (get-in % [:user :id]))
                   (some-> (:joined-at %)
                           (java-time/instant)
                           (java-time/after? cutoff-time)))))))

(comment
  (for [days [(* 30 36) (* 30 30) (* 30 24) (* 30 18) (* 30 12) (* 30 6) (* 30 3) (* 30 2) 30 15 2 1 0]]
    (let [cutoff-time (java-time/instant (- (System/currentTimeMillis) (* 1000 60 60 24 days)))]
      [days (count (inactive-users-since cutoff-time))])))
