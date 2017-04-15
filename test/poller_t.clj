(ns poller-t
  (:require [clojure.test :refer :all]
            [atomist.rugs.poller :refer :all]
            [clj-time.core :as t]
            [atomist.rugs.dynamo :as dynamo]
            [atomist.rugs.mavenrepos :as mavenrepos]
            [clj-http.client :as client]
            [mount.core :as mount]))

(defn local-dynamo-harness
  [f]
  (-> (mount/only [#'atomist.rugs.config-service/config-service
                   #'atomist.rugs.kafka/kafka-producer
                   #'atomist.rugs.dynamo/dynamo-init])
      (mount/with-args {:config ["resources/test-config.edn"]})
      (mount/swap {#'atomist.rugs.kafka/kafka-producer (fn [topic message] (println topic " -> " message))})
      (mount/start))
  (f)
  (mount/stop))

(use-fixtures :once local-dynamo-harness)

(deftest aql-query-gen-test
         (testing "that an aql-query generates correctly"
                  (with-redefs [time->str (constantly "from")
                                from (atom {"http://something" "nervous tension"})]
                    (is (= "items.find({\"repo\":\"team\",\"name\":{\"$match\":\"*-metadata.json\"},\"created\":{\"$gt\":\"from\"}}).include(\"name\",\"repo\",\"path\",\"created\").sort({\"$desc\":[\"created\"]})"
                           (aql-query "http://something" "team"))))))

(deftest time-conversions
  (let [t (t/now)]
    (is (= t (-> t time->str str->time)))))

(deftest test-offset-initialization
  (let [time (t/now)]
    (with-redefs [dynamo/scan (constantly [{:repo "http://something" :timestamp (time->str time)}])]
      (reset! from nil)
      (initialize-offsets)
      (is (= @from {"http://something" time})))))


(deftest test-poll
  (reset! mavenrepos/repositories [{:url "https://atomist.jfrog.io/atomist/guid1234" :creds {:user "user" :password "password"} :team-id "guid1234"}])
  (let [timestamp (t/now)]
    ;; "2015-01-01T10:10;10"
    (with-redefs [client/post (fn [url m]
                                (is (= "https://atomist.jfrog.io/atomist/api/search/aql" url))
                                (is (= ["user" "password"] (-> m :basic-auth)))
                                (println (:body m))
                                {:status 200 :body {:results [{:name "name" :repo "guid1234" :path "path" :created (time->artifactory timestamp)}]}})
                  atomist.rugs.config-service/get-artifactory-admin-creds (constantly ["user" "password"])]
      (poll)
      (is (< (-> (t/interval timestamp (-> (dynamo/get-item :ArchiveOffsets {:repo "https://atomist.jfrog.io/atomist/guid1234"}) :timestamp str->time))
                 (t/in-millis))
             1000)))))

(deftest test-poll-with-global
  (reset! mavenrepos/repositories [{:url "https://atomist.jfrog.io/atomist/guid1234" :creds {:user "user" :password "password"} :team-id "guid1234"}
                                   {:url "https://atomist.jfrog.io/atomist/rugs" :creds {} :team-id "global"}])
  (with-redefs [client/post (fn [url m]
                              (is (= "https://atomist.jfrog.io/atomist/api/search/aql" url))
                              (is (= ["user" "password"] (-> m :basic-auth)))
                              (println (:body m))
                              {:status 200 :body {:results []}})
                atomist.rugs.config-service/get-artifactory-admin-creds (constantly ["user" "password"])]
    (poll)))
