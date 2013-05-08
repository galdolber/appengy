(ns appengy.session
  (:use appengy.util))

(definterface Session
  (^Object getData [^String session ^String k])
  (^void setData [^String session ^String k ^Object v])
  (^void clean [^String session]))

(definterface SessionSerializer
  (^Object serialize [^Object data])
  (^Object deserialize [^Object data]))

(deftype NoSessionSerializer [] SessionSerializer
  (serialize [this v] v)
  (deserialize [this v] v))

(deftype EdnSerializer [] SessionSerializer
  (serialize [this v] (pr-str v))
  (deserialize [this v] (read-string v)))

(def dev-session (atom {}))
(deftype DevSession [] Session
  (getData [this session k] (@dev-session (str session "/" k)))
  (setData [this session k v]
            (let [k (str session "/" k)]
              (if v
                (swap! dev-session assoc k v)
                (swap! dev-session dissoc k))))
  (clean [this session] (swap! dev-session dissoc session)))