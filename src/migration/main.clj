(ns migration.main
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.core.async :refer [<!!]]
            [io.aviso.toolchest.macros :refer [cond-let]]
            [migration.system :as migration-system]
            [com.walmartlabs.system-viz :refer [visualize-system]])
  (:gen-class))

(def ^:private cli-options
  [["-e" "--env ENV" "Environment"
    :id :environment
    :default :localhost
    :default-desc "localhost"
    :parse-fn keyword
    :validate [(set migration.system/environments) "Must be localhost, staging, or production."]]

   ["-f" "--file FILE" "Source file for customer ids"
    :id :source-file]

   ["-D" "--dry-run" "Dry run - just scan for needed migrations"
    :default false]

   ["-r" "--reset" "Reset paging state (from prior execution)"
    :default false]

   ["-V" "--visualize" "Visualize system map"]

   ["-h" "--help" "Help for command"]
   ])

(defn ^:private print-help
  [summary errors]
  (println "migration: [options] [config options]\n\nOptions:\n")
  (println summary)
  (println "\nWhen -f is not specified, the customer ids to migrate are obtained via\na database query.\n")
  (when (seq errors)
    (println "Errors:")
    (doseq [e errors]
      (println e))))

(defn -main [& args]
  (cond-let

    [{:keys [options arguments errors summary]} (parse-opts args cli-options)]

    (seq errors)
    (do
      (print-help summary errors)
      -1)

    (:help options)
    (print-help summary nil)

    [{:keys [visualize environment reset dry-run source-file]} options
     system (migration-system/migration-system {:dry-run?    dry-run
                                                :reset?      reset
                                                :source-file source-file
                                                :environment environment})]

    visualize
    (do
      (visualize-system system {:format :png})
      ;; When this is run from the command line, it may take a long time to shutdown
      ;; as there's a timeout involved related to Clojure agent pool. Mostly, this
      ;; executed interactively while in development.
      nil)

    :else
    (migration-system/execute system {:args arguments})))
