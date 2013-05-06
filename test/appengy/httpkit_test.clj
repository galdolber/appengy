(ns appengy.httpkit_test
  (:use appengy.httpkit
        clojure.test))

(deftest test-multipart []
  (let [one {"submit" "submit", "file"
             {:size 30139
              :tempfile (java.io.File. "/some/file")
              :content-type "image/jpeg"
              :filename "name.jpg"}}
        many {"submit" "submit", "file"
           [{:size 30139
             :tempfile (java.io.File. "/some/file1")
             :content-type "image/jpeg"
             :filename "name.jpg"}
            {:size 25170,
             :tempfile (java.io.File. "/some/file2")
             :content-type "image/jpeg"
             :filename "name.jpg"}]}
        none {"submit" "submit", "file"
           {:size 0
            :tempfile (java.io.File. "/no/file")
            :content-type "application/octet-stream"
            :filename ""}}]
    (is (= {:params
            {"submit" "submit"
             "file" [{:size 30139
                      :tempfile "/some/file"
                      :content-type "image/jpeg"
                      :filename "name.jpg"}]}}
           (process-files {:params one})))
    (is (= {:params
            {"submit" "submit"
             "file" [{:size 30139
                      :tempfile "/some/file1"
                      :content-type "image/jpeg"
                      :filename "name.jpg"}
                     {:size 25170
                      :tempfile "/some/file2"
                      :content-type "image/jpeg"
                      :filename "name.jpg"}]}}
           (process-files {:params many})))))
