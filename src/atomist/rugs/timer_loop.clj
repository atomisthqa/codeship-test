(ns atomist.rugs.timer-loop
  (:require [clojure.core.async :as async]))

(defn periodic-loop
  "calls a function periodically
   uses core.async

   returns a channel that can take a stop event to end the timer"
  [f time-in-ms]
  (let [stop (async/chan)]
    (async/go-loop []
      (async/alt!
        (async/timeout time-in-ms) (do (async/<! (async/thread (f)))
                                       (recur))
        stop :stop))
    stop))

(defn stop
  "stop the periodic loop"
  [chan]
  (async/>!! chan :stop))