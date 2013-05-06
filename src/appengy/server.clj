(ns appengy.server
  (:use [appengy.util]
        [clojure.set :only [difference union]]))

(def apps (atom {}))
(def clients (atom {}))
(def dynamics (atom {}))

(defn send! [conn data]
  ((:send conn) data))

(defn host-clients [host]
  (let [app (@apps host)]
    (vec (map :session
         (filter #(= (:host %) host) (vals @clients))))))

(defn app-clients [app]
  (host-clients (:host (:conn app))))

(add-watch clients :sessions-change-watcher
  (fn [k r old cur]
    (let [o (set (keys old))
          n (set (keys cur))
          h (first (union (difference o n) (difference n o)))
          changed (or (cur h) (old h))]
      (when changed
        (let [app (@apps (:host changed))]
          (send! (:conn app) {:cmd :sessions-change :sessions (app-clients app)}))))))

(defn close-client-by-session [session]
  (if-let [client (@clients session)]
    (do
      ((:close client))
      (if-let [app (@apps (:host client))]
        (do
          (send! (:conn app) {:cmd :close :session session})
          (swap! clients dissoc session))))))

(defn close-client [conn]
  (close-client-by-session (:session conn)))

(defn open-client [conn]
  (if-let [app (@apps (:host conn))]
    (let [session (:session conn)]
      (close-client-by-session session)
      (swap! clients assoc session conn)
      (send! (:conn app) {:cmd :open
                           :session session}))
    (throw (RuntimeException. "App not connected"))))

(defn request-dynamic [conn req]
  (if-let [app (@apps (:host conn))]
    (let [req (select-keys req [:cookies :params :request-method :uri
                                :server-name :headers :character-encoding
                                :server-port :content-length])
          session (:session conn)
          uuid (uuid)
          file-uuid (str session uuid)
          r (atom nil)]
      (swap! dynamics assoc file-uuid r)
      (send! (:conn app)
             {:cmd :dynamic :session session
              :uuid uuid :data req})
      r)
    (throw (RuntimeException. "App not found"))))

(defn client-message [conn data]
  (if-let [app (@apps (:host conn))]
    (send! (:conn app) {:cmd :send
                        :session (:session conn)
                        :data data})))

(defn open-app [conn statics]
  (let [host (:host conn)]
    (when (@apps host)
      (send! (:conn (@apps host)) {:cmd :shutdown}))
    (send! conn {:cmd :startup})
    (swap! apps assoc host {:conn conn, :statics statics})))

(defn close-app [host]
  (doseq [c (host-clients host)]
    (apply (partial swap! c dissoc) c))
  (swap! apps dissoc host))

(defmulti app-message :cmd)

(defmethod app-message :push [{:keys [session data]}]
  (if-let [c (@clients session)]
    (send! c data)))

(defmethod app-message :send [{:keys [session data]}]
  (if-let [c (@clients session)]
    (send! c data)))

(defmethod app-message :close-session [{:keys [session]}]
  (close-client-by-session session))

(defmethod app-message :dynamic-response [{:keys [session uuid data]}]
  (let [file-uuid (str session uuid)]
    (if-let [r (@dynamics file-uuid)]
      (do
        (swap! dynamics dissoc file-uuid)
        (if (nil? data)
          (reset! r :none)
          (reset! r data))))))