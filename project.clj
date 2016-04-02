(defproject db-migrate-example "0.1.0-SNAPSHOT"
  :description "Example code to demonstrate CLI and Config interaction."
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [walmartlabs/system-viz "0.1.1"]
                 [walmartlabs/active-status "0.1.4"]
                 [org.clojure/core.async "0.2.374"]
                 [org.clojure/tools.cli "0.3.3"]
                 [io.aviso/config "0.1.12"]]
  :main migration.main
  :profiles {:uberjar {:aot [migration.main]}})
