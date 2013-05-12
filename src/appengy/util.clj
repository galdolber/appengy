(ns appengy.util)

(defn uuid []
  (str (java.util.UUID/randomUUID)))

(defmacro definterop [n args & body]
  (let [interop-n (symbol (str "-" n))]
    `(do
       (defn ~n ~args ~@body)
       (defn ~interop-n ~args (~n ~@args)))))