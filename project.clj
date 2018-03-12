(defproject rugarchvies-service "0.1.0-SNAPSHOT"
  :description "get external events on to our Atomist Kafka topics"
  :dependencies [[org.clojure/clojure          "1.9.0"]
                 [metosin/compojure-api        "1.1.2"]
                 [ring/ring-jetty-adapter      "1.4.0"]

                 [environ                      "1.0.0"]
                 [clj-time                     "0.11.0"]
                 [cheshire "5.6.3"]
                 [clj-http-fake "1.0.2"]
                 [mount "0.1.11"]
                 [com.taoensso/faraday         "1.9.0"]
                 [diehard "0.5.0"]

                 [com.stuartsierra/component   "0.3.1"]
                 [org.clojure/data.json        "0.2.6"]

                 [com.atomist/kafka-lib        "2.3.1" :exclusions [org.clojure/clojure commons-logging org.slf4j/slf4j-log4j12]]
                 [com.atomist/clj-config       "12.2.4-20160812082847" :exclusions [org.clojure/clojure commons-logging log4j org.slf4j/slf4j-log4j12]]

                 [org.clojure/tools.logging     "0.3.1"]
                 [ch.qos.logback/logback-classic  "1.0.13"]
                 [org.slf4j/slf4j-api "1.7.21"]
                 [org.slf4j/jcl-over-slf4j "1.7.21"]
                 [io.clj/logging "0.8.1"]]

  :exclusions [commons-logging org.slf4j/slf4j-log4j12]

  :min-lein-version "2.6.1"

  :plugins [[lein-metajar "0.1.1"]
            [lein-codox "0.9.4"]
            [clj-plugin   "0.3.0"]
            [lein-dynamodb-local "0.2.10"]]

  :container {:name "rugarchives"
              :dockerfile "/docker"
              :hub "sforzando-docker-dockerv2-local.artifactoryonline.com"}

  :jar-name "rugarchives-service.jar"

  :dynamodb-local {:port 6798
                   :in-memory? true
                   :shared-db? true}

  :repositories [["releases" {:url      "https://sforzando.artifactoryonline.com/sforzando/libs-release-local"
                              :username [:gpg :env/artifactory_user]
                              :password [:gpg :env/artifactory_pwd]}]
                 ["plugins" {:url      "https://sforzando.artifactoryonline.com/sforzando/plugins-release"
                             :username [:gpg :env/artifactory_user]
                             :password [:gpg :env/artifactory_pwd]}]]

  :profiles {:dev        {:dependencies [[javax.servlet/servlet-api "2.5"]
                                         [ring/ring-mock "0.3.0"]]
                          :plugins [[lein-ring "0.9.6"]]
                          :source-paths ["dev"]}
             :production {:env {:production true}}})
