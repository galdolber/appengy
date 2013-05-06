(ns appengy.util_test
  (:use clojure.test
        appengy.util))

; Fails due to clojure.test bug https://github.com/technomancy/leiningen/issues/912
;(deftest test-definterop []
;  (is (= '(do
;            (clojure.core/defn hello [a b] str a b)
;            (clojure.core/defn -hello [a b] (hello a b)))
;         (apply list (macroexpand-1 '(definterop hello [a b] (str a b)))))))

(deftest test-cache []
  (testing "No headers"
    (is (= {} (add-cache-headers {} "file.js"))))
  (testing "No cache"
    (is (every? #{"Expires" "Cache-control" "Pragma"}
                (keys (:headers (add-cache-headers {} "file.nocache.js"))))))
  (testing "Cache"
    (is (every? #{"Expires"}
                (keys (:headers (add-cache-headers {} "file.cache.js")))))))