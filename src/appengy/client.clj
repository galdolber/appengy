(ns appengy.client
  (:gen-class
   :state state
   :name appengy.client.Client
   :init init
   :methods [[close [] void]
             [pushMessage [String String] void]
             [sendMessage [String String] void]
             [closeSession [String] void]
             [sendDynamicResponse [String String String] void]
             [start [Object String String] void]])
  (:require [appengy.session])
  (:use appengy.util
        appengy.socket))

(definterface ClientApp
  [^void onClose [^String session]]
  [^void onOpen [^String session]]
  [^void onSessionsChange [^java.util.List sessions]]
  [^void onDynamic [^String session ^String uuid ^String uri ^java.util.Map params]]
  [^void onShutdown []]
  [^void onStartup [^java.util.List sessions]]
  [^void onMessage [^String session ^String data]])

(definterop init [] [[] (atom {})])

(defn conn [this]
  (:conn @(.state this)))

(defn delegate [this]
  (:delegate @(.state this)))

(definterop close [this]
  (.onShutdown (delegate this))
  (swap! (.state this) assoc :reconnect false)
  (future
    (Thread/sleep 10000)
    ((:closer @(.state this)))
    (System/exit 0)))

(definterop closeSession [this session]
  ((conn this) {:cmd :close-session, :session session}))

(definterop pushMessage [this session data]
  ((conn this) {:cmd :push, :session session, :data data}))

(definterop sendMessage [this session data]
  ((conn this) {:cmd :send, :session session, :data data}))

(definterop sendDynamicResponse [this session uuid data]
  ((conn this) {:cmd :dynamic-response, :session session,
                :uuid uuid, :data data}))

(defmulti command (fn [_ data] (:cmd data)))

(defmethod command :startup [this {:keys [sessions]}]
  (.onStartup (delegate this) sessions))

(defmethod command :shutdown [this _]
  (close this))

(defmethod command :sessions-change [this {:keys [sessions]}]
  (.onSessionsChange (delegate this) sessions))

(defmethod command :send [this {:keys [session data]}]
  (.onMessage (delegate this) session data))

(defmethod command :open [this {:keys [session]}]
  (.onOpen (delegate this) session))

(defmethod command :close [this {:keys [session]}]
  (.onClose (delegate this) session))

(defmethod command :dynamic [this {:keys [session uuid data]}]
  (let [{:keys [uri params]} data]
    (.onDynamic (delegate this) session uuid uri params)))

(defn connect [this]
  (swap! (.state this) assoc :closer
         (make-client "localhost" 9090 (:handler @(.state this)))))

(defn reconnect [this]
  (loop [this this]
    (when-not (try
          (Thread/sleep 1000)
          (println "Reconnecting...")
          (connect this)
          (catch java.net.ConnectException e nil))
      (recur this))))

(defn error [this ex]
  (.printStackTrace ex)
  (reconnect this))

(defn handle-close [this conn sess]
  (if (:reconnect @(.state this))
    (reconnect this)
    (.close this)))

(definterop start [this delegate host statics]
  (let [self this
        handler
        (reify Handler
          (on-open [this info sendfn sess]
                  (try
                    (swap! (.state self) assoc :conn sendfn)
                    (sendfn {:cmd :open :statics statics :host host})
                    (catch Exception e (.printStackTrace e))))
          (on-close [this info sendfn sess] (handle-close self sendfn sess))
          (on-message [this info sendfn sess data] (command self data))
          (on-error [this info sendfn sess ex] (error self ex)))]
    (swap! (.state this) assoc :reconnect true)
    (swap! (.state this) assoc :handler handler)
    (swap! (.state this) assoc :delegate delegate)
    (connect this)))
