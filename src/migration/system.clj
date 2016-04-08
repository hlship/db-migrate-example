(ns migration.system
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [<!!]]
            [com.walmartlabs.active-status.component :refer [status-board]]
            [io.aviso.config :as config]
            [migration.customer-migrator :refer [customer-migrator]]
            [migration.customer-scanner :refer [customer-scanner]]
            [migration.overseer :refer [migration-overseer] :as overseer]
            [migration.connection :refer [connection]]
            [schema.core :as s]))

(def environments [:localhost :staging :production])

(s/defschema SystemOptions
  "Defines options known at startup (from command line options) even before configuration is read."
  {:dry-run?    s/Bool                                      ; Check, don't update
   :reset?      s/Bool                                      ; Discard stored paging state for query
   :source-file (s/maybe s/Str)                             ; Read customer ids from file, instead of running query
   :environment (apply s/enum environments)})               ; Database to connect to

(s/defn migration-system
  "Reads configuration and uses it to create a system that can perform migrations.


  The config root is the root folder from which configuration files may be read."
  [options :- SystemOptions]
  (let [{:keys [dry-run? reset? environment source-file]} options]
    (component/system-map
      :status-board (status-board)
      :migration-overseer (migration-overseer dry-run?)
      :customer-migrator (customer-migrator dry-run?)
      :customer-scanner (customer-scanner reset? source-file)
      :connection (connection environment))))

(defn execute
  "Starts the system and invokes the overseer to run the migration.

  Returns nil."
  [system config]
  (let [started (-> system
                    (config/extend-system-map (assoc config
                                                ;; You want the code that creates the system to also provide
                                                ;; the list of profiles.
                                                :profiles [:status-board
                                                           :customer-migration
                                                           :customer-migrator
                                                           :customer-scanner
                                                           :connection]))
                    (config/configure-components)
                    (component/start-system))]
    (-> started :migration-overseer overseer/run-migration <!!)
    (component/stop-system started)
    nil))
