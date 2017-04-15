(ns atomist.rugs.config-service
  (:require [com.atomist.clj-config.config :as config]
            [mount.core :refer [defstate args]]
            [com.atomist.clj-config.kalapas :as kalapas]))

(defn start-config-service
  ([] (.start (config/new-config-service (or (-> (args) :config) []))))
  ([config] (.start (config/new-config-service [config]))))

(defstate config-service :start (start-config-service))

(defn get-config-value
  "Returns a value from the config service. Will init service is not already started."
  [path]
  (config/get-value config-service path))

(defn get-artifactory-admin-creds
  "returns {:keys [user password]}"
  []
  (let [{:keys [user password]} (kalapas/get-value (kalapas/get-url) "secret/mavenrepos/artifactory/creds")]
    [user password]))
