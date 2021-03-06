(ns meo.jvm.upload
  "Provides upload via REST call."
  (:require [ring.adapter.jetty :as j]
            [compojure.core :refer [routes POST PUT]]
            [clojure.java.io :as io]
            [hiccup.page :refer [html5]]
            [meo.jvm.imports.entries :as ie]
            [image-resizer.util :refer :all]
            [taoensso.timbre :refer [info error]]
            [meo.jvm.file-utils :as fu]
            [matthiasn.systems-toolbox.switchboard :as sb]
            [matthiasn.systems-toolbox-sente.server :as sente]
            [meo.jvm.utils.images :as img])
  (:import (java.net ServerSocket)))

(def upload-port (atom nil))
(def sync-ws-port (atom nil))

(defn get-free-port []
  (let [socket (ServerSocket. 0)
        port (.getLocalPort socket)]
    (.close socket)
    port))

(defn start-server
  "Fires up REST endpoint that accepts import files:
    - /upload/text-entry.json
    - /upload/visits.json

   Then schedules shutdown."
  [{:keys [put-fn cmp-state current-state msg-meta]}]
  (when-let [server (:server current-state)]
    (info "Stopping Upload Server")
    (.stop server))
  (reset! upload-port (get-free-port))
  (info "Starting Upload Server on port" @upload-port)
  (let [post-fn (fn [filename req put-fn]
                  (with-open [rdr (io/reader (:body req))]
                    (case filename
                      "text-entries.json" (ie/import-text-entries-fn
                                            rdr put-fn {} filename)
                      "visits.json" (ie/import-visits-fn rdr put-fn {} filename)
                      (info :backend/upload :text req))
                    "OK"))
        image-post-fn (fn [dir filename req]
                         (let [filename (str fu/data-path "/" dir "/" filename)
                               file (java.io.File. filename)]
                           (io/make-parents file)
                           (info :backend/upload-cmp :binary req)
                           (io/copy (:body req) file)
                           (img/gen-thumbs file (.getName file)))
                         "OK")
        audio-post-fn (fn [filename req]
                         (let [filename (str fu/data-path "/audio/" filename)
                               file (java.io.File. filename)]
                           (io/make-parents file)
                           (info :backend/upload-cmp :binary req)
                           (io/copy (:body req) file))
                         "OK")
        app (routes
              (PUT "/upload/audio/:file" [file :as r]
                (audio-post-fn file r))
              (PUT "/upload/:dir/:file" [dir file :as r]
                (image-post-fn dir file r))
              (POST "/upload/:filename" [filename :as r]
                (post-fn filename r put-fn)))
        server (j/run-jetty app {:port @upload-port :join? false})
        new-meta (assoc-in msg-meta [:sente-uid] :broadcast)]
    {:new-state (assoc-in current-state [:server] server)
     :emit-msg  [[:cmd/schedule-new
                  {:timeout 120000
                   :message [:import/stop-server]}]
                 (with-meta [:cfg/show-qr] new-meta)]}))

(defn stop-server [{:keys [current-state]}]
  (info "Stopping upload server")
  (.stop (:server current-state))
  {:new-state (assoc-in current-state [:server] nil)})

(defn state-fn [switchboard]
  (fn [put-fn]
    (info "Starting upload component")
    {:state (atom {:switchboard switchboard})}))

(defn cmp-map [cmp-id switchboard]
  {:cmp-id      cmp-id
   :state-fn    (state-fn switchboard)
   :handler-map {:import/listen      start-server
                 :import/stop-server stop-server}})

