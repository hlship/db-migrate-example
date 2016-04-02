(ns migration.connection
  "A component that manages a connection to a database.

  In this example code, this is no external database."
  (:require [schema.core :as s]
            [io.aviso.config :as config]))

(s/defschema ConnectionConfig
  {:endpoint (s/enum :localhost :staging :production)})

;; A real implementation would define a protocol for making queries.

(defrecord ConnectionComponent [endpoint]

  config/Configurable
  (configure [this configuration]
    (merge this configuration)))

(defn connection
  []
  (-> (map->ConnectionComponent {})
      (config/with-config-schema :connection ConnectionConfig)))
