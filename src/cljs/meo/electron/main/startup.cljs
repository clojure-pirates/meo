(ns meo.electron.main.startup
  (:require [taoensso.timbre :refer-macros [info error]]
            [child_process :refer [spawn fork]]
            [electron :refer [app session shell]]
            [http :as http]
            [cljs.reader :refer [read-string]]
            [path :refer [join normalize]]
            [meo.electron.main.runtime :as rt]
            [fs :refer [existsSync renameSync readFileSync]]
            [clojure.string :as str]))

(def PORT (:port rt/runtime-info))

(defn jvm-up? [{:keys [put-fn current-state cmp-state msg-payload]}]
  (info "JVM up?" (:attempt current-state) msg-payload)
  (let [icon (:icon-path rt/runtime-info)
        environment (:environment msg-payload)
        port (if (= environment :live) PORT (:pg-port rt/runtime-info))
        index-page (if (= environment :live)
                     (:index-page rt/runtime-info)
                     (:index-page-pg rt/runtime-info))
        loading-page (if (= environment :live)
                       "electron/loading.html"
                       "electron/loading-playground.html")
        try-again
        (fn [_]
          (info "- Nope, trying again")
          (when-not (-> @cmp-state :service environment)
            (put-fn [:cmd/schedule-new {:timeout 10 :message [:jvm/start msg-payload]}]))
          (put-fn [:window/new {:url       loading-page
                                :width     400
                                :height    300
                                :opts      {:icon icon}
                                :window-id loading-page}])
          (put-fn [:cmd/schedule-new {:timeout 1000 :message [:jvm/loaded? msg-payload]}]))
        res-handler
        (fn [res]
          (let [status-code (.-statusCode res)
                msg (merge
                      {:url  index-page
                       :opts {:titleBarStyle "hidden"
                              :icon          icon}}
                      (when (= environment :playground)
                        {:window-id index-page}))]
            (info "HTTP response: " status-code (= status-code 200))
            (if (= status-code 200)
              (do (put-fn [:window/new msg])
                  (put-fn (with-meta [:window/close] {:window-id loading-page})))
              (try-again res))))
        req (http/get (clj->js {:host "localhost"
                                :port port}) res-handler)]
    (.on req "error" try-again)
    {:new-state (update-in current-state [:attempt] #(inc (or % 0)))}))

(defn spawn-process [cmd args opts]
  (info "STARTUP: spawning" cmd args opts)
  (spawn cmd (clj->js args) (clj->js opts)))

(defn start-jvm [{:keys [current-state msg-payload]}]
  (let [{:keys [user-data java jar app-path data-path
                gql-port logdir playground-path]} rt/runtime-info
        args ["-Dapple.awt.UIElement=true" "-XX:+AggressiveOpts" "-jar" jar]
        environment (:environment msg-payload)
        port (if (= environment :live) PORT (:pg-port rt/runtime-info))
        data-path (if (= environment :live) data-path playground-path)
        opts {:detached true
              :cwd      user-data
              :env      {:PORT      port
                         :GQL_PORT  gql-port
                         :LOG_FILE  (:logfile-jvm rt/runtime-info)
                         :LOG_DIR   logdir
                         :APP_PATH  app-path
                         :DATA_PATH data-path}}
        service (spawn-process java args opts)]
    (info "JVM: startup" environment)
    {:new-state (assoc-in current-state [:service environment] service)}))

(defn start-spotify [_]
  (info "STARTUP: start spotify")
  (let [{:keys [user-data app-path node-path]} rt/runtime-info
        spotify (spawn-process node-path
                               [(str app-path "/electron/spotify.js")]
                               {:detached true
                                :stdio    "ignore"
                                :cwd      app-path
                                :env      {:USER_DATA user-data
                                           :APP_PATH  app-path}})]
    (info "SPOTIFY spawned" spotify)
    {}))

(defn shutdown [{:keys []}]
  (info "Shutting down")
  (.quit app)
  {})

(defn open-external [{:keys [msg-payload]}]
  (let [url msg-payload
        img-path (:img-path rt/runtime-info)]
    (when-not (str/includes? url (str "localhost:" (:port rt/runtime-info)))
      (info "Opening" url)
      (.openExternal shell url))
    ; not working with blank spaces, e.g. Library/Application Support/
    #_(when (str/includes? url "localhost:7788/photos")
        (let [img (str/replace url "http://localhost:7788/photos/" "")
              path (str "'" (join img-path img) "'")]
          (info "Opening item" path
                (.openItem shell path)))))
  {})

(defn kill [pid]
  (info "Killing PID" pid)
  (when pid
    (if (= (:platform rt/runtime-info) "win32")
      (spawn-process "TaskKill" ["-F" "/PID" pid] {})
      (spawn-process "/bin/kill" ["-KILL" pid] {}))))

(defn shutdown-jvm [{:keys [msg-payload]}]
  (let [environments (:environments msg-payload)
        {:keys [pid-file pg-pid-file]} rt/runtime-info
        pid (readFileSync pid-file "utf-8")
        pg-pid (readFileSync pg-pid-file "utf-8")]
    (when (contains? environments :live)
      (kill pid))
    (when (contains? environments :playground)
      (kill pg-pid)))
  {})

(defn clear-cache [{:keys []}]
  (info "Clearing Electron Cache")
  (let [session (.-defaultSession session)]
    (.clearCache session #(info "Electron Cache Cleared")))
  {})

(defn clear-iww-cache [{:keys []}]
  (info "Clearing meo Cache")
  (let [cache-file (:cache rt/runtime-info)
        cache-exists? (existsSync cache-file)]
    (when cache-exists?
      (.renameSync fs cache-file (str cache-file ".bak"))))
  {})

(defn cmp-map [cmp-id]
  {:cmp-id      cmp-id
   :handler-map {:jvm/start           start-jvm
                 :jvm/loaded?         jvm-up?
                 :spotify/start       start-spotify
                 :app/shutdown        shutdown
                 :wm/open-external    open-external
                 :app/shutdown-jvm    shutdown-jvm
                 :app/clear-iww-cache clear-iww-cache
                 :app/clear-cache     clear-cache}})

(.on app "window-all-closed"
     (fn [ev]
       (info "window-all-closed")
       (when-not (= (:platform rt/runtime-info) "darwin")
         (.quit app))))
