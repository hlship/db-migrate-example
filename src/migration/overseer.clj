(ns migration.overseer
  "The overseer is responsible for coordinating the overall migration process."
  (:require [schema.core :as s]
            [io.aviso.toolchest.macros :refer [cond-let]]
            [migration.customer-migrator :refer [migrate-customer]]
            [com.walmartlabs.active-status.component :refer [add-job]]
            [com.walmartlabs.active-status :refer [set-prefix change-status]]
            [io.aviso.config :as config]
            [clojure.core.async :refer [go <! >! close! chan] :as async]
            [migration.customer-scanner :as customer-scanner]
            [com.stuartsierra.component :as component]
            [clojure.string :as str]))

(def ^:private humanized-periods
  [[(* 1000 60 60 24 7) "week"]
   [(* 1000 60 60 24) "day"]
   [(* 1000 60 60) "hour"]
   [(* 1000 60) "minute"]
   [1000 "second"]])

(defn ^:private humanize-ellapsed-ms
  [ms]
  (if (< ms 1000)
    "less than a second"
    (let [periods (loop [remainder ms
                         [[period-ms period-name] & more-periods] humanized-periods
                         terms []]
                    (cond-let
                      (nil? period-ms)
                      terms

                      (< remainder period-ms)
                      (recur remainder more-periods terms)

                      :else
                      (let
                        [period-count (int (/ remainder period-ms))
                         next-remainder (mod remainder period-ms)
                         formatted (str period-count " " period-name
                                     (when-not (= 1 period-count) "s"))]
                        (recur next-remainder
                          more-periods
                          (conj terms formatted)))))]
      (str/join ", " periods))))

(defn ^:private start-process
  "Starts a go process to handle customer ids that need to be migrated.

  Returns a channel that conveys a sequence of values for how customer ids were processed.

  The values are :migrated, :skipped, or :failed.  The :migrated keyword is used for
  customers that have migrated, or would be migrated if it were not a dry run.

  :failed isn't meaningful in this example code (failures could occur in the original
  code this is extracted from)."
  [customer-migrator customer-ids-ch status-board prefix]
  (let [job-ch (add-job status-board)
        output-ch (chan 1)]
    (go
      (>! job-ch (set-prefix prefix))
      (>! job-ch "Waiting for customers to migrate")
      (loop []
        (when-let [customer-id (<! customer-ids-ch)]
          (let [result (<! (migrate-customer customer-migrator customer-id job-ch))
                output-result (if result :migrated :skipped)]
            (>! output-ch output-result)
            (recur))))

      (close! output-ch)
      (>! job-ch "Done")
      (close! job-ch))

    output-ch))

(defprotocol MigrationOverseer

  (run-migration [this]
    "Runs the migration, asyncronously. Returns a channel that closes when the migration
    is complete."))

(s/defschema MigrationOverseerConfig
  {:processes s/Int})

(defrecord MigrationOverseerComponent [customer-scanner status-board customer-migrator
                                       processes dry-run?]

  config/Configurable
  (configure [this configuration]
    (merge this configuration))

  MigrationOverseer
  (run-migration [_]
    (go
      (let [start-ms (System/currentTimeMillis)
            customer-ids-ch (customer-scanner/scan customer-scanner)
            process-chs (for [i (range processes)]
                          (start-process customer-migrator customer-ids-ch status-board
                            (format "Process #%2d: " (inc i))))
            combined-ch (async/merge process-chs)
            job-ch (add-job status-board)]
        (loop [counts {:migrated 0
                       :skipped  0
                       :failed   0}]
          (when-let [result (<! combined-ch)]
            (let [{:keys [migrated skipped failed]
                   :as   counts'} (update counts result inc)]
              (>! job-ch (format "%s %,d customers (%,d previously migrated, %,d failures) in %s"
                           (if dry-run? "Migration needed for" "Migrated")
                           migrated
                           skipped
                           failed
                           (humanize-ellapsed-ms (- (System/currentTimeMillis) start-ms))))
              (recur counts'))))
        (<! (async/timeout 250))
        (>! job-ch (change-status :success))
        (close! job-ch)))))

(defn migration-overseer
  [dry-run?]
  (-> (map->MigrationOverseerComponent {:dry-run? dry-run?})
      (component/using [:customer-scanner :status-board :customer-migrator])
      (config/with-config-schema :customer-migration MigrationOverseerConfig)))
