(ns clara-inspect-fx.pipeline
  "Run rules/facts the same way as clojure-clara-dyn-cli: load-string rules, EDN facts + map->record, mk-session, insert, fire-rules, inspect-facts."
  (:require [clara.rules :as rules]
            [clara.tools.inspect :as inspect]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.walk :as walk]
            [clara-inspect-fx.state :as state]
            [taoensso.timbre :as log]))

;;; ---------------------------------------------------------------------------
;;; Same as clojure-clara-dyn-cli
;;; ---------------------------------------------------------------------------
(def DYNAMIC-NS-NAME "clara_inspect_fx.dynamic_rules")

(defn- drop-first-ns-form [s]
  (let [s (str/trim s), n (count s)]
    (if (str/starts-with? s "(ns ")
      (let [end (loop [i 4 depth 1]
                  (cond (zero? depth) i (>= i n) n
                        :else (case (get s i) \( (recur (inc i) (inc depth)) \) (recur (inc i) (dec depth)) (recur (inc i) depth))))]
        (str/trim (subs s (min end n))))
      s)))

(defn load-rules-from-text
  "Load rules from string. Prepend (ns ... (:require [clara.rules ...] [clara.rules.accumulators :as acc])), then load-string.
   Strips any leading (ns ...) from content. Returns the namespace symbol."
  [content]
  (let [ns-sym (symbol DYNAMIC-NS-NAME)
        body   (drop-first-ns-form (str content))
        full-content (str "(ns " DYNAMIC-NS-NAME
                          " (:require [clara.rules :refer [defrule defquery insert! fire-rules]]"
                          "           [clara.rules.accumulators :as acc]))\n"
                          body)]
    (load-string full-content)
    ns-sym))

(defn map->record
  "Convert a map with :type to a record (same as dyn-cli)."
  [m]
  (let [t (:type m)]
    (when t
      (let [ns-part (or (namespace t) DYNAMIC-NS-NAME)
            record-name (name t)]
        (if (= ns-part DYNAMIC-NS-NAME)
          (let [ctor-sym (symbol DYNAMIC-NS-NAME (str "map->" record-name))]
            (if-let [ctor (resolve ctor-sym)]
              (ctor (dissoc m :type))
              (do (log/warn "Could not resolve constructor for" t) m)))
          m)))))

(defn- clean-for-json [v]
  (cond
    (instance? java.lang.Class v) (str v)
    (instance? clojure.lang.Symbol v) (str v)
    (and (not (or (string? v) (number? v) (boolean? v) (nil? v)
                  (map? v) (sequential? v) (set? v) (instance? clojure.lang.Named v)))
         (not (instance? clojure.lang.IRecord v))) (str v)
    :else v))

(defn- introspection-output-string [introspection-data]
  (let [clean-data (walk/prewalk clean-for-json introspection-data)
        json-str   (json/write-str clean-data
                                   :indent true
                                   :key-fn (fn [k] (if (instance? clojure.lang.Named k) (name k) (str k))))
        rules      (:rules introspection-data)
        facts      (:facts introspection-data)
        n-rules    (count (if (map? rules) (vals rules) rules))
        n-facts    (count (or facts []))
        n-roots    (count (filter #(nil? (or (:rule-id %) (:rule_id %))) (or facts [])))]
    (str "--- Introspection Data (JSON) ---\n"
         json-str
         "\n\n--- Summary ---\n"
         "Total Rules Fired: " n-rules "\n"
         "Total Fact Nodes: " n-facts "\n"
         "Root Facts Count: " n-roots "\n")))

;;; ---------------------------------------------------------------------------
;;; Normalize inspect for UI (inspect uses :fact, view expects :value)
;;; ---------------------------------------------------------------------------
(defn- normalize-inspect [introspection-data]
  (when (and (map? introspection-data) (contains? introspection-data :facts))
    (update introspection-data :facts
            (fn [facts]
              (mapv (fn [entry]
                      (if (contains? entry :value)
                        entry
                        (assoc entry :value (:fact entry))))
                    (or facts []))))))

(defn- runLater [f]
  (javafx.application.Platform/runLater (fn [] (f))))

;;; ---------------------------------------------------------------------------
;;; Run pipeline (same flow as dyn-cli -main)
;;; ---------------------------------------------------------------------------
(defn run-pipeline!
  "Run rules the same way as clojure-clara-dyn-cli: load rules from text, parse EDN facts, map->record, mk-session, insert, fire-rules, inspect-facts. Update *state on FX thread."
  [renderer-fn]
  (future
    (try
      (swap! state/*state assoc :running? true :error nil :trace ["Running…"])
      (runLater renderer-fn)

      (log/info "Loading rules…")
      (swap! state/*state update :trace conj "Loading rules…")
      (runLater renderer-fn)

      (let [rules-text  (:rules-text @state/*state)
            facts-text  (str/trim (or (:facts-text @state/*state) ""))
            rules-ns    (load-rules-from-text rules-text)]

        (log/info "Rules loaded into namespace:" rules-ns)
        (swap! state/*state update :trace conj (str "Rules loaded into " rules-ns))
        (runLater renderer-fn)

        (log/info "Loading facts…")
        (swap! state/*state update :trace conj "Loading facts…")
        (runLater renderer-fn)

        (let [raw-facts (try (edn/read-string (if (seq facts-text) facts-text "[]"))
                             (catch Exception parse-ex
                               (log/error parse-ex "Facts EDN parse failed")
                               (swap! state/*state assoc :running? false :error (str "Facts parse: " (.getMessage parse-ex)) :output "")
                               (runLater renderer-fn)
                               (throw parse-ex)))
              facts     (remove nil? (map map->record (if (sequential? raw-facts) raw-facts [])))
              facts     (vec facts)]

          (when (empty? facts)
            (swap! state/*state assoc :running? false
                   :error "Facts must be a non-empty EDN vector of maps with :type."
                   :trace (conj (:trace @state/*state) "No facts to insert.")
                   :output "")
            (runLater renderer-fn)
            (log/warn "No facts")
            (throw (ex-info "No facts" {})))

          (log/info "Inserting" (count facts) "facts…")
          (swap! state/*state update :trace conj (str "Inserting " (count facts) " facts…"))
          (runLater renderer-fn)

          (let [session (rules/mk-session (symbol (str rules-ns)))
                session-with-facts (apply rules/insert session facts)
                fired-session (rules/fire-rules session-with-facts)]

            (log/info "Firing rules…")
            (swap! state/*state update :trace conj "Fired rules.")
            (runLater renderer-fn)

            (let [introspection-data (inspect/inspect-facts fired-session)
                  normalized (normalize-inspect introspection-data)
                  output-str (introspection-output-string (or introspection-data {}))
                  roots (filter #(nil? (or (:rule-id %) (:rule_id %))) (:facts (or normalized introspection-data {})))]

              (swap! state/*state assoc
                     :running? false
                     :error nil
                     :trace (conj (:trace @state/*state) (str "Introspection: " (count (:facts (or introspection-data {}))) " fact(s)."))
                     :output (str (or output-str ""))
                     :inspect-result (or normalized introspection-data)
                     :root-facts (vec roots))
              (runLater renderer-fn)
              (log/info "Done.")))))
      (catch Exception ex
        (log/error ex "Run pipeline failed")
        (swap! state/*state assoc
               :running? false
               :error (str "Error: " (.getMessage ex))
               :trace (conj (:trace @state/*state) (str "Error: " (.getMessage ex)))
               :output "")
        (runLater renderer-fn)))))
