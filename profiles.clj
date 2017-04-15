{
 :dev {
       :source-paths ["dev"]
       :repl-options {:init-ns user}
       :dependencies [[org.clojure/tools.namespace "0.2.11"]]
       :jvm-opts ["-Dkalapas.url=http://localhost:8080"]
       }
 :poller {
          :source-paths ["dev"]
          :repl-options [:init-ns local-poller]
          :dependencies [[org.clojure/tools.namespace "0.2.11"]]
          :jvm-opts ["-Dkalapas.url=http://localhost:8080"]
          }
 }
