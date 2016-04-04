(ns migration.system
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [<!!]]
            [com.walmartlabs.active-status.component :refer [status-board]]
            [io.aviso.config :as config]
            [migration.customer-migrator :refer [customer-migrator]]
            [migration.customer-scanner :refer [customer-scanner]]
            [migration.overseer :refer [migration-overseer] :as overseer]
            [migration.connection :refer [connection]]))

(defn migration-system
  "Reads configuration and uses it to create a system that can perform migrations.


  The config root is the root folder from which configuration files may be read."
  []
  (component/system-map
    :status-board (status-board)
    :migration-overseer (migration-overseer)
    :customer-migrator (customer-migrator)
    :customer-scanner (customer-scanner)
    :connection (connection)))

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
