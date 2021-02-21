(defproject top10 "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.773"
                  :exclusions [com.google.javascript/closure-compiler-unshaded
                               org.clojure/google-closure-library
                               org.clojure/google-closure-library-third-party]]
                 [thheller/shadow-cljs "2.11.4"]
                 [arttuka/reagent-material-ui "4.11.0-3"]
                 [reagent "0.10.0"]
                 [re-frame "1.1.1"]
                 [day8.re-frame/async-flow-fx "0.1.0"]
                 [day8.re-frame/http-fx "0.2.1"]
                 [clj-commons/secretary "1.2.4"]
                 [venantius/accountant "0.2.5"]
                 [garden "1.3.10"]
                 [ns-tracker "0.4.0"]
                 [digest "1.4.10"]]

  :plugins [[lein-garden "0.3.0"]
            [lein-shell "0.5.0"]]

  :min-lein-version "2.9.0"

  :source-paths ["src/clj" "src/cljs"]

  :test-paths   ["test/cljs"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"
                                    "test/js"
                                    "resources/public/css"]

  :garden {:builds [{:id           "screen"
                     :source-paths ["src/clj"]
                     :stylesheet   top10.css/screen
                     :compiler     {:output-to     "resources/public/css/screen.css"
                                    :pretty-print? true}}]}
  
  :shell {:commands {"karma" {:windows         ["cmd" "/c" "karma"]
                              :default-command "node_modules/.bin/karma"}
                     "open"  {:windows         ["cmd" "/c" "start"]
                              :macosx          "open"
                              :linux           "xdg-open"}}}

  :dist {:source-root "resources/public"
         :target-root "dist"
         :index "index.html"
         :files ["favicon.ico" "css/screen.css" "js/compiled/app.js"]}

  :aliases {"watch"        ["with-profile" "dev" "do"
                            ["run" "-m" "shadow.cljs.devtools.cli" "--npm" "watch" "app" "browser-test"]]
            "release"      ["with-profile" "prod" "do"
                            ["run" "-m" "shadow.cljs.devtools.cli" "--npm" "release" "app"]]
            "build-report" ["with-profile" "prod" "do"
                            ["run" "-m" "shadow.cljs.devtools.cli" "--npm" "run" "shadow.cljs.build-report" "app" "target/build-report.html"]
                            ["shell" "open" "target/build-report.html"]]
            "ci"           ["with-profile" "prod" "do"
                            ["run" "-m" "shadow.cljs.devtools.cli" "--npm" "compile" "karma-test"]
                            ["shell" "karma" "start" "--single-run" "--reporters" "junit,dots"]]
            "dist"          ["run" "-m" "top10.dist/dist" :project/dist]}

  :profiles  {:dev {:dependencies [[binaryage/devtools "1.0.2"]]
                    :source-paths ["dev"]}
              :prod {}})
