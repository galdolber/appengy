(ns appengy.httpkit
  (:gen-class
   :name appengy.httpkit.Httpkit)
  (:require [org.httpkit.server :as http])
  (:use appengy.server
        appengy.util
        appengy.socket
        ring.middleware.cookies
        ring.middleware.session
        ring.middleware.reload
        ring.middleware.session.cookie
        ring.middleware.file-info
        ring.middleware.multipart-params
        ring.middleware.params
        [ring.util.response :only [file-response redirect]]
        [clojure.tools.cli :only [cli]]))

(defn get-session [req]
  (get-in req [:cookies "ring-session" :value]))

(defn get-host [req]
  (let [host ((:headers req) "host")]
    (if-let [p (re-find #"(\S+):(\d+)" host)]
      (p 1)
      host)))

(defn conn-impl [req channel]
  {:send #(http/send! channel (if (string? %) % (pr-str %)))
   :close #(http/close channel)
   :host (get-host req)
   :session (get-session req)})

(defn app-conn [sess conn]
  {:send #(conn %)
   :close #(throw (RuntimeException. "Cannot close apps connection"))
   :host (:host @sess)
   :session (:host @sess)})

(defn receive-ws [req channel data]
  (client-message (conn-impl req channel) data))

(defn handle-ws [req]
  (http/with-channel
   req channel
   (http/on-close channel (fn [reason] (close-client (conn-impl req channel))))
   (http/on-receive channel #(receive-ws req channel %))
   (open-client (conn-impl req channel))))

(defmulti handle-req :request-method)

(defn not-found [] {:body "Page not found"})

(defn error [ex]
  (do
    (.printStackTrace ex)
    {:body "Unexpected exception"}))

(defn replace-path [f]
  (if-let [file (:tempfile f)]
    (assoc f :tempfile (.getCanonicalPath file))
    f))

(defn param-mapper [[k v]]
  [k (cond (and (map? v) (:tempfile v)) [(replace-path v)]
           (sequential? v) (vec (map replace-path v))
           :else v)])

(defn process-files [r]
  (if-let [params (:params r)]
    (assoc r :params
      (into {} (map param-mapper params)))
    r))

(defn get-request-dynamic [req]
  (let [req (process-files req)
        r (request-dynamic (conn-impl req nil) (dissoc req :async-channel :websocket?))]
    (loop [n 0]
      (Thread/sleep 100)
      (when-not (or @r (= 500 n))
        (recur (inc n))))
    (if (= :none @r)
      (not-found)
      (if-let [path @r]
        (if (.startsWith path "http")
          (redirect path)
          (file-response path))
        (not-found)))))

(defn get-request [req app]
  (if-let [res (file-response (:uri req) {:root (:statics app)})]
    res
    (get-request-dynamic req)))

(defmethod handle-req :get [req]
  (if-let [app (@apps (get-host req))]
    (get-request req app)
    (not-found)))

(defmethod handle-req :post [req]
  (if-let [app (@apps (get-host req))]
    (get-request-dynamic req)
    (not-found)))

(defn handler [req]
  (if (:websocket? req)
    (handle-ws req)
    ; Force session
    (assoc (handle-req req) :session :appengy)))

(def apps-handler
  (reify Handler
    (onOpen [this conn sess] )
    (onClose [this conn sess] (close-app (:host @sess)))
    (onMessage [this conn sess data]
      (if (= :open (:cmd data))
        (do
          (swap! sess assoc :host (:host data))
          (open-app (app-conn sess conn) (:statics data)))
        (app-message data)))
    (onError [this conn sess ex] (.printStackTrace ex))))

(defn start [port]
  (def ws
    (http/run-server
     (-> handler
         wrap-cookies
         wrap-session
         wrap-file-info
         wrap-params
         wrap-multipart-params
         wrap-reload
         wrap-cache-headers)
     {:port port}))
  (def apps-server (make-server 9090 apps-handler)))

(defn -main [& args]
  (let [[{:keys [port local]} _ usage]
        (cli args
             ["-p" "--port" "Listen on this port" :parse-fn #(Integer. %) :default 8080]
             ["-l" "--local" "true for dev" :default true])]
    (kill-old)
    (start port)
    (println (str "Started server on localhost:" port))))