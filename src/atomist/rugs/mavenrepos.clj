(ns atomist.rugs.mavenrepos
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [atomist.rugs.config-service :as cs]
            [atomist.rugs.timer-loop :as loop]
            [mount.core :refer [defstate]]))

(def ^{:doc "map of current mavenrepos to poll"} repositories (atom nil))

(defn fetch-maven-repos
  "periodically synchronize the maven repos that we are polling"
  []
  (let [response (client/get (cs/get-config-value [:mavenrepos-url]) {:as :json :throw-exceptions false})]
    (if (= 200 (:status response))
      (swap! repositories (constantly (-> response :body)))
      (log/warn "unable to fetch repos"))))

(defstate mavenrepos
          :start (loop/periodic-loop fetch-maven-repos 60000)
          :stop (loop/stop mavenrepos))