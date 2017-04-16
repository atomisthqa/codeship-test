(ns atomist.rugs.poller
  (:require [mount.core :refer [defstate start stop]]
            [atomist.rugs.kafka :refer [kafka-producer]]
            [atomist.rugs.dynamo :as dynamo]
            [atomist.rugs.config-service :as cs]
            [atomist.rugs.timer-loop :as loop]
            [atomist.rugs.mavenrepos :as mavenrepos]
            [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [clj-time.core :as time]
            [clj-time.format :as f]
            [clojure.data.json :as json]
            [clojure.test :refer :all])
  (:import (clojure.lang ExceptionInfo)))

;; Invalid format: \"20161225T023639.637Z\" is malformed at \"3639.637Z\""
;; 1970-01-01T01:00:00.000Z
(defn time->str
  "use this format to store timestamps in dynamo"
  [t]
  (f/unparse (f/formatters :date-time) t))

(defn str->time
  "our from maps should store actual clj-time Times"
  [s]
  (f/parse (f/formatters :date-time) s))

(defn artifactory->time
  [s]
  (f/parse (f/formatters :date-hour-minute-second)))

(defn time->artifactory
  [t]
  (f/unparse (f/formatters :date-hour-minute-second) t))

(def ^{:doc "map of repo url to offset clj-time Times"} from (atom {}))

(defn initialize-offsets
  "pull in the current ArchiveOffsets at startup time"
  []
  (->> (dynamo/scan :ArchiveOffsets)
       (map (fn [{:keys [repo timestamp]}] (swap! from assoc repo (str->time timestamp))))
       (doall)))

(defn update-offset
  "we have detected a new repo that we've not seen before"
  [url time]
  (swap! from assoc url time)
  (dynamo/put-item :ArchiveOffsets {:repo url :timestamp (time->str time)}))

(defn raise-event
  "coll will be a collection of maps with name, repo, path, and created
     these are returned from Artifactory AQL queries

   this is the event that we currently raise on our rug_publish channels

   we could use a tx log here because we should commit to to updating the offset
   if we successfully produce a kafka message.  This is the only function that
   really requires an atomic commit log to back it."
  [url team-id coll]
  (when (-> coll empty? not)
    (log/info url (count coll))
    (let [latest-time (->> coll first :created artifactory->time)]
      (kafka-producer :rug_publish {:archives coll
                                    :url url
                                    :team-id team-id})
      (log/infof "update offset for %s to %s" url (str latest-time))
      (log/info (->> coll first :created))
      (update-offset url latest-time))))

(defn- aql-find
  "just some junk to dump data into a entity.find().include().sort() function call chain"
  [data entity names sort]
  (format "%s.find(%s).include(%s).sort(%s)"
          entity
          data
          (->> names (map #(str "\"" % "\"")) (interpose ",") (apply str))
          (json/json-str sort)))

(defn aql-query
  "construct an AQL query string for this searching a repo from the current
   offset.  Use time->str to serialize the timestamp to send to Artifactory"
  [url team-id]
  (-> {:repo    team-id
       :name    {"$match" "*-metadata.json"}
       :created {"$gt" (-> from deref (get url) time->str)}}
      (json/json-str)
      (aql-find "items" ["name" "repo" "path" "created"] {"$desc" ["created"]})))

(defn collect-new-archives
  "Run artifactory AQL queries for each repo (one-by-one)
    returns a coll of maps with name, repo, path, and created"
  [{:keys [url creds team-id]}]
  (let [q (aql-query url (if (= "global" team-id)
                           "rugs-release"
                           team-id))
        response
        (client/post (cs/get-config-value [:artifactory-url])
                     {:body             q
                      :content-type     "text/plain"
                      :basic-auth       (if (= "global" team-id)
                                          (cs/get-artifactory-admin-creds)
                                          [(:user creds) (:password creds)])
                      :as               :json
                      :throw-exceptions false})]
    (if (= 200 (:status response))
      (-> response :body :results)
      (throw (ex-info "unable to run aqm query" response)))))

(defn poll
  "poll all of our current repos for new repos"
  []
  (try
    (if-let [repos @mavenrepos/repositories]
      (doseq [{:keys [url team-id] :as r} repos]
        (when (-> @from (get url) not)
          (log/info @from)
          (log/info "update blank offset for " url)
          (update-offset url (time/now)))
        (->> (collect-new-archives r)
             (raise-event url team-id))))
    (catch ExceptionInfo ex
      (log/warn (ex-data ex)))
    (catch Throwable t
      (log/error t))))

(defstate poller
          :start (do
                   (initialize-offsets)
                   (loop/periodic-loop poll 30000))
          :stop (loop/stop poller))
