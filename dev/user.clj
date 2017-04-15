(ns user
  (:require [mount.core :as mount]
            [atomist.rugs.poller]
            [atomist.rugs.kafka]
            [atomist.rugs.config-service]
            [atomist.rugs.dynamo]
            [clojure.tools.logging :as log]
            [io.clj.logging :as mdc]))


(defn simple-callback
  [_ message key]
  (try
    (mdc/with-logging-context
      {:key key}
      (log/info " ----> " key)
      (log/info (with-out-str (clojure.pprint/pprint message)))
      (catch Throwable t (log/error (.getMessage t))))))

(comment

  (-> (mount/only #{#'atomist.rugs.config-service/config-service
                    #'atomist.rugs.kafka/kafka-consumer})
      (mount/with-args {:config ["resources/test-config.edn"]
                        :group-id "jim"
                        :topic :kube
                        :callback #'simple-callback})
      (mount/start))

  (mount/start #'atomist.rugs.config-service/config-service)
  (mount/start #'atomist.rugs.kafka/kafka-consumer)
  (mount/stop #'atomist.rugs.kafka/kafka-consumer)

  (.printStackTrace *e)

  (keys atomist.rugs.kafka/kafka-consumer)
  (atomist.rugs.config-service/get-config-value [:kafka])

  (mount/stop))


