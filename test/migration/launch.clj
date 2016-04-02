(ns migration.launch
  "Used for interactive testing of the customer migration tool."
  (:require [migration.main :refer [-main]]))

(defn go [& args]
  (apply -main
    (into args
      ["status-board/mode=minimal"
       "customer-migrator/log-file=target/migrated.txt"
       "customer-scanner/paging-state-path=target/paging-state"
       "customer-migration/processes=5"])))

(defn viz [] (-main "-V"))

(defn help [] (-main "-h"))
