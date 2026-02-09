(ns clara-inspect-fx.view
  "Root view, rules/facts editors, buttons, TabPane (Trace, Memory, Inspect facts)."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clara-inspect-fx.core :as core]))

;;; ---------------------------------------------------------------------------
;;; Subviews: Trace, Inspection, Inserts, Memory, Inspect facts
;;; ---------------------------------------------------------------------------
(defn trace-tab [{:keys [state]}]
  {:fx/type :scroll-pane
   :fit-to-width true
   :fit-to-height true
   :content {:fx/type :text-area
             :editable false
             :wrap-text true
             :text (str/join "\n" (or (:trace state) []))}})

(defn inspection-tab [{:keys [state]}]
  {:fx/type :scroll-pane
   :fit-to-width true
   :fit-to-height true
   :content {:fx/type :text-area
             :editable false
             :wrap-text true
             :text (str (or (:output state) ""))}})

(defn- rule-name-for [inspect rule-id]
  (when (and (map? inspect) (some? rule-id))
    (or (get-in inspect [:rules rule-id :name])
        (get-in inspect [:rules (str rule-id) :name])
        (str "rule " rule-id))))

(defn insert-row [idx inspect entry]
  (let [rule-id   (or (:rule-id entry) (:rule_id entry))
        rule-name (rule-name-for inspect rule-id)
        fact      (or (:value entry) (:fact entry))
        bindings  (or (:bindings entry) (:bindings entry))
        fact-str  (if (nil? fact) "nil" (pr-str fact))
        bind-str  (when (seq bindings) (pr-str bindings))]
    {:fx/type :v-box
     :fx/key idx
     :spacing 6
     :padding 8
     :style "-fx-border-color: #ccc; -fx-border-width: 0 0 1 0; -fx-background-color: #fafafa;"
     :children (into [{:fx/type :label
                       :text (str "Rule: " rule-name)
                       :style "-fx-font-weight: bold; -fx-font-size: 1.05em;"}
                      {:fx/type :label
                       :text (str "Fact: " fact-str)
                       :wrap-text true
                       :style "-fx-font-family: monospace;"}]
                     (remove nil?
                       [(when bind-str
                          {:fx/type :label
                           :text (str "Bindings: " bind-str)
                           :wrap-text true
                           :style "-fx-font-family: monospace; -fx-text-fill: #555;"})]))}))

(defn inserts-tab [{:keys [state]}]
  (let [inspect   (:inspect-result state)
        facts     (when (map? inspect) (:facts inspect))
        inserts   (filter #(some? (or (:rule-id %) (:rule_id %))) (or facts []))]
    {:fx/type :scroll-pane
     :fit-to-width true
     :fit-to-height true
     :content {:fx/type :v-box
               :spacing 0
               :padding 10
               :children (if (seq inserts)
                           (map-indexed (fn [i e] (insert-row i inspect e)) inserts)
                           [{:fx/type :label
                             :text "No inserts. Run rules first."}])}}))

(defn memory-tab [{:keys [state]}]
  (let [inspect (:inspect-result state)
        roots   (:root-facts state)
        facts   (when (map? inspect) (:facts inspect))
        n       (count (or facts []))
        n-roots (count (or roots []))]
    {:fx/type :scroll-pane
     :fit-to-width true
     :fit-to-height true
     :content {:fx/type :v-box
               :spacing 10
               :padding 10
               :children (into [{:fx/type :label
                                 :text (str "Working memory: " n " fact(s) total.")}
                                {:fx/type :label
                                 :text (str "Root facts: " n-roots)}]
                       (remove nil?
                         [(when (seq roots)
                            {:fx/type :label
                             :text "Root fact list:"
                             :style "-fx-font-weight: bold"})
                          (when (seq roots)
                            {:fx/type :text-area
                             :v-box/vgrow :always
                             :max-height Double/MAX_VALUE
                             :editable false
                             :wrap-text true
                             :text (str/join "\n" (map pr-str roots))})]))}}))

(defn inspect-fact-row [idx entry]
  (let [types   (or (:fact-types entry) (:fact_types entry) [])
        bindings (or (:bindings entry) [])
        rule-id (or (:rule-id entry) (:rule_id entry))
        value   (or (:value entry) (:fact entry))
        value-str (if (nil? value) "nil" (pr-str value))]
    {:fx/type :v-box
     :fx/key idx
     :spacing 4
     :padding 5
     :style "-fx-border-color: #ccc; -fx-border-width: 0 0 1 0;"
     :children (into [{:fx/type :label
                       :text (str "Types: " (pr-str types))
                       :wrap-text true}
                      {:fx/type :label
                       :text (str "Value: " (if (> (count value-str) 200)
                                              (str (subs value-str 0 200) "...")
                                              value-str))
                       :wrap-text true}]
                     (remove nil?
                       [(when (seq bindings)
                          {:fx/type :label
                           :text (str "Bindings: " (pr-str bindings))
                           :wrap-text true})
                        (when rule-id
                          {:fx/type :label
                           :text (str "Rule: " (pr-str rule-id))
                           :wrap-text true})]))}))

(defn inspect-tab [{:keys [state]}]
  (let [inspect (:inspect-result state)
        facts   (when (map? inspect) (:facts inspect))
        entries (if (sequential? facts) facts (when inspect (seq inspect)))]
    {:fx/type :scroll-pane
     :fit-to-width true
     :content {:fx/type :v-box
               :spacing 0
               :padding 10
               :children (if (seq entries)
                           (map-indexed inspect-fact-row entries)
                           [{:fx/type :label
                             :text "No inspect-facts result. Run rules first."}])}}))

;;; ---------------------------------------------------------------------------
;;; Root view
;;; ---------------------------------------------------------------------------
(defn border-pane-content [{:keys [state]}]
  (let [{:keys [rules-text facts-text error running?]} state
        left-panel {:fx/type :v-box
                    :spacing 8
                    :padding 10
                    :min-width 200
                    :children [{:fx/type :h-box
                                :spacing 8
                                :alignment :center-left
                                :children [{:fx/type :label
                                            :text "Rules (Clojure)"
                                            :style "-fx-font-weight: bold"}
                                           {:fx/type :region
                                            :h-box/hgrow :always}
                                           {:fx/type :button
                                            :graphic {:fx/type :label
                                                      :text "📂"
                                                      :style "-fx-font-size: 1.2em"}
                                            :tooltip {:fx/type :tooltip
                                                      :text "Load from file"}
                                            :on-action {:event/type ::core/load-rules-file}}]}
                               {:fx/type :text-area
                                :v-box/vgrow :always
                                :max-height Double/MAX_VALUE
                                :text (str rules-text)
                                :on-text-changed {:event/type ::core/rules-changed}}
                               {:fx/type :h-box
                                :spacing 8
                                :alignment :center-left
                                :children [{:fx/type :label
                                            :text "Facts (EDN vector of maps with :type)"
                                            :style "-fx-font-weight: bold"
                                            :wrap-text true}
                                           {:fx/type :region
                                            :h-box/hgrow :always}
                                           {:fx/type :button
                                            :graphic {:fx/type :label
                                                      :text "📂"
                                                      :style "-fx-font-size: 1.2em"}
                                            :tooltip {:fx/type :tooltip
                                                      :text "Load from file"}
                                            :on-action {:event/type ::core/load-facts-file}}]}
                               {:fx/type :text-area
                                :v-box/vgrow :always
                                :max-height Double/MAX_VALUE
                                :text (str facts-text)
                                :on-text-changed {:event/type ::core/facts-changed}}
                               {:fx/type :h-box
                                :spacing 10
                                :children [{:fx/type :button
                                            :text (if running? "Running…" "Run")
                                            :disable (or running?
                                                        (empty? (str/trim (or facts-text ""))))
                                            :on-action {:event/type ::core/run}}
                                           {:fx/type :button
                                            :text "Clear"
                                            :on-action {:event/type ::core/clear}}]}]}
        right-panel {:fx/type :tab-pane
                     :tabs [{:fx/type :tab
                             :text "Execution trace"
                             :content (trace-tab {:state state})}
                            {:fx/type :tab
                             :text "Inspection"
                             :content (inspection-tab {:state state})}
                            {:fx/type :tab
                             :text "Inserts"
                             :content (inserts-tab {:state state})}
                            {:fx/type :tab
                             :text "Memory"
                             :content (memory-tab {:state state})}
                            {:fx/type :tab
                             :text "Inspect facts"
                             :content (inspect-tab {:state state})}]}
        split-pane {:fx/type :split-pane
                    :divider-positions [0.35]
                    :items [left-panel right-panel]}
        base {:fx/type :border-pane
              :center split-pane}]
    (if error
      (assoc base :bottom {:fx/type :label
                           :text (str "Error: " error)
                           :style "-fx-text-fill: red; -fx-padding: 5;"})
      base)))

(defn root [state]
  {:fx/type :stage
   :showing true
   :title "Clara Rules Inspector"
   :width 1100
   :height 700
   :on-close-request (fn [e]
                        (.consume e)
                        (System/exit 0))
   :scene {:fx/type :scene
           :stylesheets (if-let [url (io/resource "clara_inspect_fx/style.css")]
                          [(str url)]
                          [])
           :root (border-pane-content {:state state})}})
