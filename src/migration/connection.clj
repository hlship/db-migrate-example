(ns migration.connection
  "A component that manages a connection to a database.

  In this example code, this is no external database, and this is just a placeholder."
  (:require [schema.core :as s]
            [io.aviso.config :as config]))

;; A real implementation would define a protocol for making queries.

(defrecord ConnectionComponent [endpoint]

  config/Configurable
  (configure [this configuration]
    (merge this configuration)))

(defn connection
  [endpoint]
  (map->ConnectionComponent {:endpoint endpoint}))
