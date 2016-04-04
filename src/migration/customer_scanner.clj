(ns migration.customer-scanner
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [chan go <! >! >!! close! pipe] :as async]
            [com.walmartlabs.active-status :as active-status]
            [com.walmartlabs.active-status.component :refer [add-job]]
            [migration.migration-utils :refer [make-containing-dirs]]
            [schema.core :as s]
            [io.aviso.config :as config]
            [clojure.java.io :as io])
  (:import (java.io File)))

(s/defschema CustomerScannerSchema
  {:paging-state-path            s/Str
   ;; source-path is actually set from the --file command line option
   (s/optional-key :source-path) (s/maybe s/Str)
   :reset                        s/Bool                     ; ignore any paging state, start from scratch
   :block-size                   s/Int
   :fetch-size                   s/Int})

(defprotocol CustomerScanner

  (scan [this]
    "Return a channel that conveys a series of customer ids."))

(defn ^:private do-scan
  [component]
  (let [{:keys [status-board connection fetch-size block-size reset]} component
        job-ch (add-job status-board)
        ch (chan block-size)
        customer-ids-ch (chan fetch-size)]
    ;; This is where the query would normally go; further, this is where we woudl use
    ;; reset to discard any saved paging state.
    (async/onto-chan customer-ids-ch
      (repeatedly (+ 100 (rand-int 1000))
        #(rand-int 100000)))
    (go
      (>! job-ch "Starting customer scan ...")
      (loop [row-count 0]
        (if-let [customer-id (<! customer-ids-ch)]
          (do
            (>! ch customer-id)
            (when (and (pos? row-count)
                    (zero? (mod row-count 1000)))
              (>! job-ch (format "Scanned %,d customer rows" row-count)))
            (recur (inc row-count)))
          (do
            (>! job-ch (active-status/change-status :success))
            (>! job-ch (format "Completed table scan, after %,d customer rows" row-count))
            (close! ch)
            (close! job-ch)))))
    ch))

(defn ^:private read-from-source-file
  [^File file fetch-size]
  (let [ch (chan fetch-size)]
    (->> file
      io/reader                                             ; the reader never gets closed, but that's ok here
      line-seq
      (async/onto-chan ch))
    ch))

(defrecord CustomerScannerComponent [connection
                                     ^File source-file
                                     ^File paging-state-file
                                     fetch-size
                                     block-size
                                     reset]

  config/Configurable
  (configure [this configuration]
    (-> this
        (assoc :paging-state-file (-> configuration :paging-state-path io/file)
               :source-file (some-> configuration :source-path io/file))
        (merge (select-keys configuration [:block-size :fetch-size :reset]))))

  component/Lifecycle
  (start [this]
    (make-containing-dirs paging-state-file)
    this)

  (stop [this] this)

  CustomerScanner

  (scan [this]
    (if source-file
      (read-from-source-file source-file fetch-size)
      (do-scan this))))


(defn customer-scanner []
  (-> (map->CustomerScannerComponent {})
      (component/using [:status-board :connection])
      (config/with-config-schema :customer-scanner CustomerScannerSchema)))
