(defproject top10 "0.1.0"
  :description "Top 10 front end"
  :url "https://github.com/ljpengelen/top10"
  :license {:name "MIT License"}

  :min-lein-version "2.7.1"

  :dependencies [[clj-commons/secretary "1.2.4"]
                 [cljs-http "0.1.46"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.764"]
                 [reagent "1.0.0-alpha2"]]

  :source-paths ["src"]

  :aliases {"fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dev"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" "top10.test-runner"]
            "fig:ci"    ["run" "-m" "figwheel.main" "-co" "ci.cljs.edn" "-m" "top10.test-runner"]}

  :profiles {:dev {:dependencies [[com.bhauman/figwheel-main "0.2.11"]
                                  [com.bhauman/rebel-readline-cljs "0.1.4"]]}})
