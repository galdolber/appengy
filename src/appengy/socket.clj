(ns appengy.socket
  (:import [java.net ServerSocket Socket InetAddress]
           [java.io OutputStream PrintWriter])
  (:use [clojure.tools.reader.reader-types :only [input-stream-push-back-reader]])
  (:require [clojure.tools.reader.edn :as edn]))

(defprotocol Handler
  (on-open [this info sendfn session])
  (on-close [this info sendfn session])
  (on-message [this info sendfn session data])
  (on-error [this info sendfn session ex]))

(defn init-socket [socket ^appengy.socket.Handler handler]
  (let [out (PrintWriter. ^OutputStream (.getOutputStream socket) true)
        in (input-stream-push-back-reader (.getInputStream socket))
        send-agent (agent nil)
        remote (.getRemoteSocketAddress socket)
        info {:ip (-> remote .getAddress .getHostAddress)
              :host (.getHostName remote)
              :port (.getPort remote)}
        sendfn #(send-off send-agent (fn [_] (.println out (pr-str %))))
        session (atom {})]
    (when-not (= :close (on-open handler info sendfn session))
      (try
        (while (not (.isClosed socket))
          (let [data (edn/read in)]
            (when (= :close (on-message handler info sendfn session data))
              (.close socket))))
        (catch clojure.lang.ExceptionInfo e nil)
        (catch Exception e (on-error handler info sendfn session e))
        (finally (on-close handler info sendfn session))))))

(defn listen [server handler]
  (while (not (.isClosed server))
    (if-let [socket (.accept server)]
      (future (init-socket socket handler)))))

(defn make-server [port handler]
  (let [server (ServerSocket. port)]
    (future (listen server handler))
    #(.close server)))

(defn make-client [host port handler]
  (let [socket (Socket. ^InetAddress (InetAddress/getByName host) ^Integer port)]
    (future (init-socket socket handler))
    #(.close socket)))