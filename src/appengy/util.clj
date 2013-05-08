(ns appengy.util
  (:import [java.io File IOException ByteArrayInputStream OutputStream
            ByteArrayOutputStream PrintWriter InputStream]
           [java.text SimpleDateFormat]
           [java.util Date])
  (:use [clojure.set :only [difference]]
        [clojure.string :only [join]]
        [clojure.java.shell :only [sh]]
        [ring.util.response :only [header]])
  (:require [clojure.java.io :as io]
            [clojure.tools.reader.edn :as edn]))

(def pid-path "pid")

(defmacro defn-memo
  "Just like defn, but memoizes the function using clojure.core/memoize"
  [fn-name & defn-stuff]
  `(do
     (defn ~fn-name ~@defn-stuff)
     (alter-var-root (var ~fn-name) memoize)
     (var ~fn-name)))

(defn safe-slurp [file]
  (try
    (slurp file)
    (catch Exception e nil)))

(defn pid []
  (-> (java.lang.management.ManagementFactory/getRuntimeMXBean)
    (.getName)
    (clojure.string/split #"@")
    (first)))

(defn uuid []
  (str (java.util.UUID/randomUUID)))

(defn to-int [s]
  (try
    (Integer/valueOf ^String s)
    (catch Exception e nil)))

(defmacro defn+ [n args & body]
  (let [nstr (str n)]
    `(defn ~n ~args
       (println "")
       (println "----------------------------")
       (println ~nstr ~@args)
        (let [r# (do ~@body)]
          (println " -> " r#)
          r#))))

(defmacro fn+ [n args & body]
  (let [nstr (str n)]
    `(fn ~args
       (println "")
       (println "----------------------------")
       (println ~nstr ~@args)
       (let [r# (do ~@body)]
         (println " -> " r#)
         r#))))

(defn bimap [map-atom]
  (let [inv-atom (atom {})]
    (add-watch map-atom :bimap
      (fn [k r old cur]
        (let [o (set (keys old))
              n (set (keys cur))]
          (doseq [removed (difference o n)]
            (swap! inv-atom dissoc (old removed)))
          (doseq [added (difference n o)]
            (swap! inv-atom assoc (cur added) added)))))
    inv-atom))

(defn kill-old []
  (if-let [p (safe-slurp pid-path)]
    (try
      (sh "kill" "-9" p)
      (catch Exception e nil)))
  (spit pid-path (pid)))

(defmacro deftry [name args handler & body]
  `(defn ~name ~args (try ~@body (catch Exception ex# (~handler ex#)))))

(def ^SimpleDateFormat cache-format (SimpleDateFormat. "EEE, dd MMM yyyy HH:mm:ss zzz"))

(defn add-cache-headers [res path]
  (cond
   (.contains ^String path ".cache.")
     (header res "Expires" (.format cache-format (+ (.getTime ^Date (Date.)) 31536000000N)))
   (.contains ^String path ".nocache.")
     (-> res
       (header "Pragma" "no-cache")
       (header "Cache-control" "no-cache, no-store, must-revalidate")
       (header "Expires" "0"))
   :else res))

(defn wrap-cache-headers [app]
  (fn [req]
    (let [{:keys [body] :as res} (app req)]
      (if (instance? File body)
        (add-cache-headers res (.getCanonicalPath ^File body))
        res))))

(defmacro pcall [f]
  `(future (pcalls ~f)))

(defn data-input-stream [& s]
  (ByteArrayInputStream. (.getBytes (join " " (map pr-str s)))))

(defn data-output-stream []
  (ByteArrayOutputStream.))

(defmacro definterop [n args & body]
  (let [interop-n (symbol (str "-" n))]
    `(do
       (defn ~n ~args ~@body)
       (defn ~interop-n ~args (~n ~@args)))))
