(ns local-poller
  (:require [mount.core :as mount]
            [atomist.rugs.poller]
            [atomist.rugs.dynamo :as dynamo]))

(defn start []
  (-> (mount/with-args {:config ["resources/test-config.edn"]})
      (mount/swap {#'atomist.rugs.kafka/kafka-producer (fn [topic message] (println topic " -> " message))})
      (mount/start)))

(comment
  (start)
  (clojure.pprint/pprint @atomist.rugs.mavenrepos/repositories)
  (clojure.pprint/pprint (dynamo/scan :ArchiveOffsets))
  (clojure.pprint/pprint @atomist.rugs.poller/from)
  (mount/stop))

