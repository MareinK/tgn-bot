(defproject tgn-bot "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [clojure.java-time "0.3.1"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jmdk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 [ring/ring-core "1.9.1"]
                 [ring/ring-jetty-adapter "1.9.1"]
                 [ring/ring-servlet "1.9.1"]
                 [jarohen/chime "0.3.2"]
                 [org.suskalo/discljord "1.2.2"]
                 [cheshire "5.10.0"]
                 [clj-jwt "0.1.1"]
                 [clj-time "0.15.2"]
                 [clj-http "3.12.1"]]
  :main tgn-bot.main
  :min-lein-version "2.0.0")
