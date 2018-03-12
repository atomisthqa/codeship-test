(ns atomist.rugs.dynamo
  (:require
   [taoensso.faraday :as far]
   [atomist.rugs.config-service :refer [config-service]]
   [com.atomist.clj-config.config :as config]
   [clojure.tools.logging :as log]
   [clojure.string :as str]
   [diehard.core :as diehard]
   [taoensso.faraday :as ddb]
   [clj-time.core :as time]
   [mount.core :refer [defstate]])
  (:import (com.amazonaws AmazonServiceException)
           (clojure.lang Ref Atom)))

(def freeze far/freeze)

(defn- ref? [x]
  (or (instance? clojure.lang.Ref x)
      (instance? clojure.lang.Agent x)))

(defn get-config-value
  "Returns a value from the config service. Will init service is not already started."
  [path]
  (config/get-value config-service path))

(def prefix (comp not #{"Organizations"}))

(defn table*
  [table-name]
  (let [domain-name (get-config-value [:domain])]
    (assert (not (str/blank? domain-name)) "Domain name is missing or blank")
    (keyword (str (if (prefix table-name) domain-name) table-name))))

(def table (memoize table*))

(def dynamo-creds-cache (atom nil))

(defn dynamo-creds*
  []
  (let [{:keys [access_key secret_key]} (get-config-value [:dynamo :creds])
        {:keys [endpoint]} (get-config-value [:dynamo :endpoint])]
    (log/infof "Dynamo endpoint: %s; access_key: %s" endpoint access_key)
    (cond->
     {:access-key access_key
      :secret-key secret_key}
      (not (str/blank? endpoint)) (assoc :endpoint endpoint))))

(defn dynamo-creds
  []
  (if-let [creds @dynamo-creds-cache]
    creds
    (swap! dynamo-creds-cache (constantly (dynamo-creds*)))))

(defn retryable-dynamo-error?
  [e]
  (and
   (= AmazonServiceException (class e))
   (not (.contains (.getMessage e)
                   "Status Code: 400; Error Code: ValidationException;"))))

(defmacro with-retry
  [& body]
  `(diehard/with-retry
     {:retry-if (fn [_# e#] (retryable-dynamo-error? e#))
      :max-retries 5
      :backoff-ms [1000 10000]
      :on-failed-attempt (fn [r# e#]
                           (when (retryable-dynamo-error? e#)
                             (swap! dynamo-creds-cache (constantly (dynamo-creds*)))))}
     ~@body))

(defn- dynamo-not-found?
  [e]
  (and
   (= AmazonServiceException (class e))
   (.contains (.getMessage e)
              "Status Code: 400; Error Code: ValidationException;")
   (.contains (.getMessage e)
              "The provided key element does not match the schema")))

(defmacro with-get-retry
  "hmmm, 400 code ValidationExceptions for gets really do not need to be re-tried.
   AmazonServiceException "
  [& body]
  `(diehard/with-retry
     {:retry-if          (fn [_# e#]
                           (cond
                             (dynamo-not-found? e#)
                             false

                             (= AmazonServiceException (class e#))
                             true

                             :else
                             false))
      :max-retries       5
      :backoff-ms        [1000 10000]
      :on-failed-attempt (fn [r# e#]
                           (cond
                             (dynamo-not-found? e#)
                             (log/warn (.getMessage e#))

                             :else
                             (do
                               (log/error e# (str r#))
                               (swap! dynamo-creds-cache (constantly (dynamo-creds*))))))
      :fallback          (fn [v# e#]
                           (cond
                             (dynamo-not-found? e#)
                             nil

                             :else
                             (throw e#)))}
     ~@body))

;===============

(defn get-item
  [t m]

  (with-get-retry
    (far/get-item
     (dynamo-creds)
     (table (name t))
     m)))

(defn update-item [t k update]
  (with-retry
    (far/update-item
     (dynamo-creds)
     (table (name t))
     k update)))

(defn with-timestamps [item]
  (assoc item :updated_at (str (time/now))))

(defn put-item
  [t item]
  (with-retry
    (far/put-item
     (dynamo-creds)
     (table (name t))
     (with-timestamps item))))

(defn delete-item [t m]
  (with-retry
    (far/delete-item
     (dynamo-creds)
     (table (name t))
     m)))

(defn query
  [t q & [opts]]
  (with-retry (far/query
               (dynamo-creds)
               (table (name t))
               q
               opts)))

(defn scan
  [t]
  (with-retry
    (far/scan
     (dynamo-creds)
     (table (name t)))))

(defn ensure-table
  "note that ensure-table only creates a table if
   it does not exist at all.  It won't reconfigure an existing table.

   Also note that the default throughput is read 1 write 1 so these tables
   are not configured for a production scenario.  They will need throughput tuning."
  [{:keys [partition-key] table-name :table :as table-spec}]
  (let [full-name (table (name table-name))]
    (try
      (log/infof "Ensuring table %s" full-name)
      (with-retry
        (ddb/ensure-table (dynamo-creds)
                          full-name
                          partition-key
                          (-> table-spec
                              (dissoc :table :partition-key)
                              (assoc :block? true)
                              (assoc :throughput {:read 1 :write 1}))))
      (catch Throwable ex
        (log/error ex "Failed to create table: " full-name)))))

(def rugarchives-tables
  [{:table         :ArchiveOffsets
    :partition-key [:repo :s]}])

(defn ensure-tables []
  (doseq [table-spec rugarchives-tables]
    (ensure-table table-spec)))

(defstate dynamo-init :start (ensure-tables))

