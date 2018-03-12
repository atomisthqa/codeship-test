(ns atomist.rugs.jetty
  (:require
   [ring.adapter.jetty             :as jetty]
   [atomist.rugs.handler       :refer [handler]]
   [atomist.rugs.config-service :refer [config-service]]
   [com.atomist.clj-config.config       :as config]
   [mount.core :refer [defstate]]))

(defstate jetty :start (jetty/run-jetty handler {:port (Integer. (config/get-value config-service [:port]))
                                                 :join? false})
  :stop  (.stop jetty))
