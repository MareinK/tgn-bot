(ns tgn-bot.fluff
  (:require [discljord.messaging :as messaging]
            [tgn-bot.core :refer [state config bot-id]]
            [discljord.formatting :as formatting]
            [clojure.string :as str]
            [tgn-bot.util :as util]))

(defn random-greeting [user]
  (str (rand-nth (:fluff-greetings config)) ", " (formatting/mention-user user) \!))

(defn random-answer [user]
  (str (formatting/mention-user user) " " (util/weighted-random-nth (:fluff-answers config))))

(defn respond-to-bot-mention [{:keys [channel-id author content]}]
  (if (str/includes? content "?")
    (messaging/create-message! (:rest @state) channel-id :content (random-answer author))
    (messaging/create-message! (:rest @state) channel-id :content (random-greeting author))))
