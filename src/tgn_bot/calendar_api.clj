(ns tgn-bot.calendar-api
  (:require [tgn-bot.core :refer [state config bot-id]]
            [clj-jwt.core :as jwt]
            [clj-jwt.key :as key]
            [clj-time.core :as time]
            [clj-http.client :as http]
            [clojure.string :as str]
            java-time)
  (:import java.io.StringReader))

(def scopes ["https://www.googleapis.com/auth/calendar"])

(defn create-claim [creds]
  (->
    (merge {:iss (:client_email creds)
            :scope (str/join " " scopes)
            :aud "https://www.googleapis.com/oauth2/v4/token"
            :exp (-> 1 time/hours time/from-now)
            :iat (time/now)})
    jwt/jwt
    (jwt/sign :RS256 (-> creds :private_key (#(StringReader. %)) (#(key/pem->private-key % nil))))
    jwt/to-str))

(defn request-token [creds]
  (let [claim (create-claim creds)
        resp (http/post
              "https://www.googleapis.com/oauth2/v4/token"
              {:form-params {:grant_type "urn:ietf:params:oauth:grant-type:jwt-bearer"
                             :assertion claim}
               :as :json})]
    (when (= 200 (-> resp :status))
      (-> resp :body :access_token))))

(defn api-req [request token]
  (http/request (-> request
                    (assoc-in [:headers "Authorization"] (str "Bearer " token))
                    (assoc :as :json))))

(defn get-events-days [skips days]
  (let [today (->
                (java-time/local-time 0 0)
                (java-time/zoned-date-time (java-time/zone-id "Europe/Amsterdam"))
                java-time/instant)
        time-min (java-time/plus today (java-time/period skips :days))
        time-max (java-time/plus time-min (java-time/period days :days))]
    (-> (api-req
          {:url (str
                  "https://www.googleapis.com/calendar/v3/calendars/"
                  (:calendar-id config)
                  "/events"
                  "?timeMin=" (java-time/format time-min)
                  "&timeMax=" (java-time/format time-max)
                  "&orderBy=startTime"
                  "&singleEvents=true")
           :method "GET"}
          (request-token (:google-service-account-credentials config)))
      (get-in [:body :items]))))

(comment
  (get-events-days 0 1)
  (get-events-days 0 14)
  (get-events-days 14 14))
