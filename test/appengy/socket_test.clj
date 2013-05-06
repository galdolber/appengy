(ns appengy.socket_test
  (:require [clojure.tools.reader.edn :as edn])
  (:use clojure.test
        clojure.tools.logging
        appengy.socket
        appengy.util
        [clojure.java.io :only [input-stream delete-file output-stream]]
        [clojure.tools.reader.reader-types :only [input-stream-push-back-reader]]))

(deftest edn-stream []
  (let [d0 12
        d1 [1 2 3]
        d2 {:a 1 :b 2 :c 3}]
    (with-open [iss (data-input-stream d0 d1 d2)]
      (let [isr (input-stream-push-back-reader iss)]
        (is (= d0 (edn/read isr)))
        (is (= d1 (edn/read isr)))
        (is (= d2 (edn/read isr)))))))

(deftest test-init-socket []
  (let [m1 [1 2 3]
        m2 {:cmd "Hello"}
        in (data-input-stream m1 m2)
        out (data-output-stream)
        closed (atom false)]
    (init-socket
     (reify IsSocket
       (getInputStream [this] in)
       (getOutputStream [this] out)
       (isClosed [this] @closed))
     (reify Handler
       (onOpen [this conn sess] )
       (onClose [this conn sess] )
       (onMessage [this conn sess data] (conn data))
       (onError [this conn sess ex] )))
    (Thread/sleep 300)
    (is (= (str (pr-str m1) "\n" (pr-str m2) "\n")
           (str out)))))

(defn make-handler [c out]
  (reify Handler
    (onOpen [this conn sess] (reset! c conn))
    (onClose [this conn sess] )
    (onMessage [this conn sess data] (swap! out conj data))
    (onError [this conn sess ex] (.printStackTrace ex))))

(deftest test-server-client []
  (let [sh (atom nil)
        ch (atom nil)
        s-out (atom [])
        c-out (atom [])
        server-handler (make-handler sh s-out)
        client-handler (make-handler ch c-out)
        s (make-server 9090 server-handler)
        c (make-client "127.0.0.1" 9090 client-handler)]
    (Thread/sleep 300)
    (@sh [1 2 3])
    (@ch [3 2 1])
    (is (= [[1 2 3]] @c-out))
    (is (= [[3 2 1]] @s-out))
    (c)
    (s)))
