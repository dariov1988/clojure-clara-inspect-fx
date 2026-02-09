(ns clara-inspect-fx.log
  "Logging configuration: Timbre with file and console appenders."
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as log]))

(def log-dir "log")
(def log-file "clara-inspect.log")

(defn- ensure-log-dir!
  []
  (let [f (io/file log-dir)]
    (when-not (.exists f)
      (.mkdirs f))))

(defn init!
  "Configure Timbre: file appender (log/clara-inspect.log) and console appender.
   Call once at startup (e.g. in -main)."
  []
  (ensure-log-dir!)
  (let [path (str log-dir "/" log-file)]
    (log/merge-config!
     {:min-level :debug
      :appenders
      {:println {:enabled? true}
       :file   {:enabled? true
                :fn (fn [data]
                      (try
                        (when-let [x (or (:output_ data) (:msg_ data))]
                          (spit path (str (if (string? x) x (force x)) "\n") :append true))
                        (catch Exception _ nil)))}}})))
