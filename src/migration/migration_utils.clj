(ns migration.migration-utils
  "Some general utility functions used across the migration code."
  (:import (java.io File)))

(defn make-containing-dirs
  [^File file]
  ;; .getParentFile may be nil if the log-file is just a file name.
  (some-> file
    .getParentFile
    .mkdirs))
