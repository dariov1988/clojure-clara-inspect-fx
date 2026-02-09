(ns clara-inspect-fx.core
  "Public API: *state, event-handler, event types. State and pipeline live in state.clj and pipeline.clj."
  (:require [clara-inspect-fx.pipeline :as pipeline]
            [clara-inspect-fx.state :as state]
            [clojure.java.io :as io])
  (:import [javafx.stage FileChooser FileChooser$ExtensionFilter Stage Window]))

;;; ---------------------------------------------------------------------------
;;; Re-exports for view and main (single entry point)
;;; ---------------------------------------------------------------------------
(def *state state/*state)

;;; ---------------------------------------------------------------------------
;;; Load from file (FileChooser; must run on FX thread)
;;; ---------------------------------------------------------------------------
(defn- owner-window []
  (when-let [windows (seq (Window/getWindows))]
    (some (fn [w] (when (and (.isShowing w) (instance? Stage w)) w))
          windows)))

(defn- load-from-file! [state-key title extension-description extension]
  (when-let [window (owner-window)]
    (let [chooser (FileChooser.)]
      (.setTitle chooser title)
      (.add (.getExtensionFilters chooser) (FileChooser$ExtensionFilter. extension-description extension))
      (when-let [file (.showOpenDialog chooser window)]
        (try
          (swap! *state assoc state-key (slurp (io/file (.getAbsolutePath file))))
          (catch Exception e
            (swap! *state assoc :error (str "Failed to load file: " (.getMessage e)))))))))

;;; ---------------------------------------------------------------------------
;;; Event handler (map events -> state updates)
;;; ---------------------------------------------------------------------------
(defn event-handler [event renderer-fn]
  (case (:event/type event)
    ::rules-changed
    (swap! *state assoc :rules-text (or (:fx/event event) (:text event) ""))
    ::facts-changed
    (swap! *state assoc :facts-text (or (:fx/event event) (:text event) ""))
    ::load-rules-file
    (load-from-file! :rules-text "Load rules (Clojure)" "Clojure / text files" ["*.clj" "*.cljc" "*.txt" "*"])
    ::load-facts-file
    (load-from-file! :facts-text "Load facts (EDN)" "EDN / text files" ["*.edn" "*.clj" "*.txt" "*"])
    ::run
    (pipeline/run-pipeline! renderer-fn)
    ::clear
    (swap! *state assoc :trace [] :output "" :error nil :inspect-result nil :root-facts nil)
    nil))
