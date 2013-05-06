(ns appengy.socket
  (:import [java.net ServerSocket Socket InetAddress]
           [java.io OutputStream PrintWriter])
  (:use appengy.util
        [clojure.tools.reader.reader-types :only [input-stream-push-back-reader]])
  (:require [clojure.tools.reader.edn :as edn]))

(defprotocol IsSocket
  (getOutputStream [this])
  (getInputStream [this])
  (isClosed [this]))

(extend Socket IsSocket)

(defprotocol Handler
  (onOpen [this conn session])
  (onClose [this conn session])
  (onMessage [this conn session data])
  (onError [this conn session ex]))

(defn init-socket [socket ^appengy.socket.Handler handler]
  (let [out (PrintWriter. ^OutputStream (.getOutputStream socket) true)
        in (input-stream-push-back-reader (.getInputStream socket))
        send-agent (agent nil)
        conn #(send-off send-agent (fn [_] (.println out (pr-str %))))
        session (atom {})]
    (.onOpen handler conn session)
    (try
      (while (not (.isClosed socket))
        (let [data (edn/read in)]
          (.onMessage handler conn session data)))
      (catch clojure.lang.ExceptionInfo e nil)
      (catch Exception e (.onError handler conn session e))
      (finally (.onClose handler conn session)))))

(defn listen [server handler]
  (while (not (.isClosed server))
    (if-let [socket (.accept server)]
      (pcall (init-socket socket handler)))))

(defn make-server [port handler]
  (let [server (ServerSocket. port)]
    (pcall (listen server handler))
    #(.close server)))

(defn make-client [host port handler]
  (let [socket (Socket. ^InetAddress (InetAddress/getByName host) ^Integer port)]
    (pcall (init-socket socket handler))
    #(.close socket)))