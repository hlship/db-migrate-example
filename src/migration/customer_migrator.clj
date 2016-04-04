(ns migration.customer-migrator
  (:require [schema.core :as s]
            [clojure.java.io :as io]
            [clojure.core.async :refer [go <! chan timeout >!]]
            [com.stuartsierra.component :as component]
            [migration.migration-utils :refer [make-containing-dirs]]
            [io.aviso.config :as config])
  (:import (java.util Date)
           (java.io Writer File)))

(defprotocol CustomerMigrator

  (migrate-customer [this dry-run? customer-id job-ch]
    "Migrate the individual customer identified by the given id.

    If dry-run? is true, only the check takes place; no new data is written.

    Returns a channel that conveys:
    * true if the customer was migrated (or needs to be migrated)
    * false if the customer was previously migrated

    Passed a status board job channel, on which to post progress updates."))

(s/defschema CustomerMigratorConfig
  {:log-file s/Str})


(defn migrate-single-customer
  [connection dry-run? customer-id job-ch]
  (go
    (>! job-ch (str "Checking customer " customer-id))

    ;; Here's where we'd do a database query with the connection
    ;; Instead we sleep

    (<! (timeout (+ 10 (rand-int 50))))

    (cond

      (< (rand) 0.3)
      false                                                 ; pretend previously migrated

      dry-run?
      true

      :else
      (do
        (>! job-ch (str "Migrating customer " customer-id))

        ;; Again, sleep instead of actual work

        (<! (timeout (+ 50 (rand-int 200))))

        true))))

(defrecord CustomerMigratorComponent [connection
                                      ^File log-file
                                      ^Writer log-writer]

  config/Configurable

  (configure [this configuration]
    (assoc this :log-file (-> configuration :log-file io/file)))

  component/Lifecycle

  (start [this]
    (make-containing-dirs log-file)

    (assoc this :log-writer (io/writer log-file :append true)))

  (stop [this]
    (.close log-writer)
    this)

  CustomerMigrator
  (migrate-customer [_ dry-run? customer-id job-ch]
    (let [start-ms (System/currentTimeMillis)]
      (go
        (let [result (<! (migrate-single-customer connection dry-run? customer-id job-ch))]
          (when (and (not dry-run?)
                  (true? result))
            (binding [*out* log-writer]
              (printf "%TF %<TT - %17s - %5d ms%n"
                (Date.)
                customer-id
                (- (System/currentTimeMillis) start-ms))
              (flush)))

          ;; The result is returned after the log file is optionally written.
          result)))))

(defn customer-migrator
  []
  (->
    (map->CustomerMigratorComponent [])
    (component/using [:connection])
    (config/with-config-schema :customer-migrator CustomerMigratorConfig)))
