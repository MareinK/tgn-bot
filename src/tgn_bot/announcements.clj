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

(defn location-string [event]
  (let [locations-string (->>
                          (:location-regexes config)
                          (filter (fn [{:keys [regex]}] (re-find (re-pattern (str "(?i)" regex)) (:location event))))
                          (map :message)
                          (map formatting/bold)
                          (str/join " en "))]
    (when (not (str/blank? locations-string))
      (format
       (get-in config [:messages :daily-location])
       locations-string))))

(defn daily-message [event]
  (let [name (util/remove-prefix (:summary event) "TGN ")
        start (:start event)]
    (if (:dateTime start)
      (let [time (->
                  (java-time/instant (:dateTime start))
                  (java-time/local-time "Europe/Amsterdam"))
            time-str (java-time/format "H:mm" time)
            today (if (java-time/before? time evening-time) "Vandaag" "Vanavond")]
        (str
         (format
          (get-in config [:messages :daily-timed])
          today
          (formatting/bold time-str)
          (formatting/bold name))
         (location-string event)))
      (when (= (java-time/local-date (:date start)) (java-time/local-date))
        (str
         (format
          (get-in config [:messages :daily-full])
          (formatting/bold name))
         (location-string event))))))

(defn daily-event-reminder []
  (doseq [event (calendar-api/get-events-days 0 1)]
    (when-let [message (daily-message event)]
      (messaging/create-message! (:rest @state) (get-in config [:channel-ids :daily-announcements])
                                 :content message))))

(defn events->list [events]
  (str/join
   "\n"
   (for [event events]
     (let [name (util/remove-prefix (:summary event) "TGN ")
           start (:start event)]
       (if (:dateTime start)
         (let [date (->
                     (java-time/instant (:dateTime start))
                     (java-time/local-date "Europe/Amsterdam"))
               date-str (java-time/format "d/M" date)
               time (->
                     (java-time/instant (:dateTime start))
                     (java-time/local-time "Europe/Amsterdam"))
               time-str (java-time/format "H:mm" time)]
           (format "- %s: %s vanaf %s" date-str (formatting/bold name) time-str))
         (let [date (->
                     (java-time/local-date (:date start)))
               date-str (java-time/format "d/M" date)]
           (format "- %s: %s" date-str (formatting/bold name))))))))

(defn bi-weekly-message [events-one events-two]
  (format
   (get-in config [:messages :bi-weekly])
   (events->list events-one)
   (events->list events-two)))

(defn bi-weekly-event-reminder []
  (let [events-one (calendar-api/get-events-days 0 14)
        events-two (calendar-api/get-events-days 14 14)]
    @(messaging/create-message! (:rest @state) (get-in config [:channel-ids :weekly-announcements])
                                :embed {:description (bi-weekly-message events-one events-two)})))

(comment
  (daily-event-reminder)
  (bi-weekly-event-reminder))
