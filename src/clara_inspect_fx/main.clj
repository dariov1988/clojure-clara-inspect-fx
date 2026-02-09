(ns clara-inspect-fx.main
  "Entry point: -main starts logging, creates cljfx app (renderer + mount-renderer), wires event handler and desc-fn."
  (:gen-class)
  (:require [cljfx.api :as fx]
            [clara-inspect-fx.core :as core]
            [clara-inspect-fx.view :as view]
            [clara-inspect-fx.log :as log-config]
            [taoensso.timbre :as log]))

(defn -main [& _]
  (log-config/init!)
  (log/info "Clara Rules Inspector starting")
  (let [renderer* (atom nil)
        renderer  (fx/create-renderer
                  :middleware (fx/wrap-map-desc (fn [desc] (merge {:fx/type view/root} (or desc {}))))
                  :opts {:fx.opt/map-event-handler
                         (fn [event]
                           (core/event-handler event
                             #(when-let [r @renderer*] (r @core/*state))))})]
    (reset! renderer* renderer)
    (fx/mount-renderer core/*state renderer)))
