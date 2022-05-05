(ns tgn-bot.scheduling
  (:require [tgn-bot.acceptance :as acceptance]
            [tgn-bot.announcements :as announcements]
            [chime.core :as chime]
            [clojure.tools.logging :as log]
            java-time))

(def standard-task-execution-time
  (-> (java-time/local-time 7 0)
      (java-time/zoned-date-time (java-time/zone-id "Europe/Amsterdam"))
      java-time/instant))

#_(defn keep-alive-task [time] (log/info "Sending keep-alive message."))

(defn daily-tasks
  [time]
  (log/info "Executing daily tasks.")
  (announcements/daily-event-reminder)
  (acceptance/clean-introduction-channel)
  (acceptance/remind-silent-users)
  (acceptance/kick-silent-users))

(defn bi-weekly-tasks
  [time]
  (log/info "Executing bi-weekly tasks.")
  (announcements/bi-weekly-event-reminder))

(defn monthly-tasks
  [time]
  (log/info "Executing monthly tasks.")
  (announcements/monthly-rules-reminder))

(defn schedule-tasks
  []
  #_(chime/chime-at (chime/periodic-seq (Instant/now) (Duration/ofMinutes 10))
                    keep-alive-task)
  (chime/chime-at (->> (chime/periodic-seq standard-task-execution-time
                                           (java-time/period 1 :days))
                       (chime/without-past-times))
                  daily-tasks)
  (chime/chime-at
    (->> (chime/periodic-seq standard-task-execution-time
                             (java-time/period 1 :days))
         (chime/without-past-times)
         (filter #(java-time/monday? (java-time/local-date %
                                                           "Europe/Amsterdam")))
         (filter #(-> (java-time/time-between (java-time/instant 0) % :days)
                      (quot 7)
                      (even?))))
    bi-weekly-tasks)
  (chime/chime-at (->> (chime/periodic-seq standard-task-execution-time
                                           (java-time/period 1 :months))
                       (chime/without-past-times))
                  monthly-tasks))
