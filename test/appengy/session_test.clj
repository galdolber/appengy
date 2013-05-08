(ns appengy.session_test
  (:use clojure.test
        appengy.session)
  (:import [appengy.session EdnSerializer]))

(deftest test-edn-session []
  (let [s (EdnSerializer.)
        v "hello"
        ser (.serialize s v)
        des (.deserialize s ser)]
    (is (= v des))))