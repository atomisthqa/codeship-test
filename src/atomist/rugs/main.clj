(ns atomist.rugs.main
  (:require [environ.core                    :refer [env]]
            [atomist.rugs.jetty              :refer [jetty]]
            [atomist.rugs.handler            :as handler]
            [atomist.rugs.poller]
            [clojure.tools.logging           :as log]
            [mount.core :as mount]))

(defn on-stop
  "prepare a shutdown hook that
     - tells kubernetes we're not ready to receive requests
     - stops kafka consumers
     - quiesces for 2 minutes
     - stops web listener (tells kubernetes that we're not healthy
     - gets killed by kubernetes"
  []
  (fn []
    (log/info "Marking service as not-ready")
    (swap! handler/ready not)
    (log/info "stop polling")
    (mount/stop #'atomist.rugs.poller/poller)
    (log/info "Waiting for kube to recognize ready check, waiting for running reactions to complete")
    (Thread/sleep (* 1000 10))
    (log/info "Closing down jetty")
    (mount/stop)
    (log/info "Finished shutdown handler")))

(defn -main [& args]
  (log/info
    (-> (mount/except #{#'atomist.rugs.kafka/kafka-consumer})
        (mount/start)))
  (.. Runtime getRuntime (addShutdownHook (Thread. (on-stop))))
  (.. Thread currentThread join))
