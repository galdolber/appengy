(ns appengy.server_test
  (:use clojure.test
        clojure.tools.logging
        appengy.server))

(defn conn-mock [session msgs]
  {:send #(swap! msgs conj %)
   :host "test.com"
   :session session
   :close #(swap! msgs conj {:close session})})

(deftest test-flow []
  (let [msgs (atom [])
        server-conn (conn-mock "test.com" msgs)
        client-conn1 (conn-mock "1" msgs)
        client-conn2 (conn-mock "2" msgs)]
    (open-app server-conn (.getCanonicalPath (java.io.File. ".")))
    (open-client client-conn1)
    (client-message client-conn1 {:cmd :send, :data "ping"})
    (app-message {:cmd :send :session (first (keys @clients)) :data "pong"})
    (open-client client-conn2)
    (close-client client-conn1)
    (close-client client-conn2)
    (is (= [{:cmd :startup}
            {:cmd :sessions-change :sessions ["1"]}
            {:cmd :open :session "1"}
            {:cmd :send :session "1" :data {:data "ping" :cmd :send}}
            "pong"
            {:cmd :sessions-change :sessions ["2" "1"]}
            {:cmd :open :session "2"}
            {:close "1"}
            {:cmd :close, :session "1"}
            {:cmd :sessions-change :sessions ["2"]}
            {:close "2"}
            {:cmd :close, :session "2"}
            {:cmd :sessions-change :sessions []}] @msgs))
    (let [r (request-dynamic client-conn1 {:uri "/test"})
          path "/fake/path"]
      (app-message {:cmd :dynamic-response
                    :uuid (first (keys @dynamics))
                    :data path})
      (is (= path @r)))))