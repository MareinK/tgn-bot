(ns tgn-bot.announcements
  (:require [tgn-bot.core :refer [state config bot-id]]
            [discljord.messaging :as messaging]
            [discljord.formatting :as formatting]
            [tgn-bot.calendar-api :as calendar-api]
            [tgn-bot.util :as util]
            [clojure.string :as str]
            [discljord.formatting :as formatting]))


(def evening-time
  (->
    (java-time/local-time 18 0)
    (java-time/zoned-date-time (java-time/zone-id "Europe/Amsterdam"))
    (java-time/local-time "Europe/Amsterdam")))

(defn daily-message [event]
  (let [name (util/remove-prefix (:summary event) "TGN ")
        time (->
               (java-time/instant (get-in event [:start :dateTime]))
               (java-time/local-time "Europe/Amsterdam"))
        time-str (java-time/format "H:mm" time)
        today (if (java-time/before? time evening-time) "Vandaag" "Vanavond")]
    (format
      (get-in config [:messages :daily])
      today
      (formatting/bold time-str)
      (formatting/bold name))))

(defn daily-event-reminder []
  (doseq [event (calendar-api/get-events-days 0 1)]
    (messaging/create-message! (:rest @state) (get-in config [:channel-ids :daily-announcements])
      :content (daily-message event))))

(defn events->list [events]
  (str/join
    "\n"
    (for [event events]
      (let [name (util/remove-prefix (:summary event) "TGN ")
            start (:start event)
            date (if (:dateTime start)
                   (->
                     (java-time/instant (:dateTime start))
                     (java-time/local-date "Europe/Amsterdam"))
                   (->
                     (java-time/local-date (:date start))))
            date-str (java-time/format "d/M" date)]
        (format "- %s: %s" date-str (formatting/bold name))))))

(defn bi-weekly-message [events-one events-two]
  (format
    (get-in config [:messages :bi-weekly])
    (events->list events-one)
    (events->list events-two)))

(defn bi-weekly-event-reminder []
  (let [events-one (calendar-api/get-events-days 0 14)
        events-two (calendar-api/get-events-days 14 14)]
    (messaging/create-message! (:rest @state) (get-in config [:channel-ids :weekly-announcements])
      :embed {:description (bi-weekly-message events-one events-two)})))

(comment
  (daily-event-reminder)
  (bi-weekly-event-reminder))
