(ns tgn-bot.core
  (:require [clojure.edn :as edn]))

(def state (atom nil))

(def bot-id (atom nil))

(def config
  (merge
   (edn/read-string (slurp "config.edn"))
   (edn/read-string (System/getenv "DISCORD_SERVER_CONFIG"))))
