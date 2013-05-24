(defproject appengy "0.1.9"
  :description "Appengy app server"
  :url "http://github.com/galdolber/appengy"
  :main appengy.httpkit.Httpkit
  :aot :all
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-deps-tree "0.1.2"]
            [lein-kibit "0.0.8"]
            [lein-catnip "0.5.1"]]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [ring/ring-core "1.2.0-beta2"]
                 [http-kit "2.1.1"]
                 [org.clojure/tools.reader "0.7.4"]
                 [org.clojure/tools.logging "0.2.3"]
                 [ch.qos.logback/logback-classic "1.0.11"]
                 [org.slf4j/log4j-over-slf4j "1.7.5"]])
