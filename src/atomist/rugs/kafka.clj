(ns atomist.rugs.kafka
  (:require [com.stuartsierra.component :as component]
            [com.atomist.kafka.client :as kafka]
            [mount.core :refer [defstate args]]
            [atomist.rugs.config-service :as config-service]
            [clojure.tools.logging :as log]
            [io.clj.logging :as mdc])
  (:import (java.util UUID)))

(defstate kafka-producer
  :start (let [{:keys [brokers ssl store-pass key-pass]} (config-service/get-config-value [:kafka])
               producer (component/start (kafka/new-producer brokers nil store-pass ssl key-pass))]
           (fn [topic message]
             (let [correlation-id (str (UUID/randomUUID))]
               (mdc/with-logging-context {:topic          topic
                                          :correlation-id correlation-id
                                          :event          message}
                 (log/info "Sending message to kafka topic:" topic)
                 (try
                   (kafka/send-message producer topic (assoc message :correlation-id correlation-id))
                   (catch Exception e (log/error e "Exception writing message to kafka topic"))))))))

(def kafka-options {"auto.offset.reset" "latest"
                    "enable.auto.commit" "true"})

(defstate kafka-consumer
  :start (let [{:keys [brokers ssl store-pass key-pass]} (config-service/get-config-value [:kafka])]
           (println "brokers:  " brokers " for " (args))
           (component/start (kafka/new-consumer brokers
                                                (-> (args) :group-id)
                                                (-> (args) :topic)
                                                (-> (args) :callback)
                                                kafka-options
                                                :store-password store-pass
                                                :ssl? ssl
                                                :key-pass key-pass)))
  :stop #(component/stop kafka-consumer))