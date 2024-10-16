(defproject top10 "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [org.clojure/clojurescript "1.11.132"
                  :exclusions [com.google.javascript/closure-compiler-unshaded
                               org.clojure/google-closure-library
                               org.clojure/google-closure-library-third-party]]
                 [thheller/shadow-cljs "2.28.16"]
                 [arttuka/reagent-material-ui "5.11.12-0"]
                 [reagent "1.2.0"]
                 [re-frame "1.4.3"]
                 [day8.re-frame/async-flow-fx "0.4.0"]
                 [day8.re-frame/http-fx "0.2.4"]
                 [day8.re-frame/tracing "0.6.2"]
                 [clj-commons/secretary "1.2.4"]
                 [expound "0.9.0"]
                 [venantius/accountant "0.2.5"]
                 [garden "1.3.10"]
                 [ns-tracker "1.0.0"]
                 [digest "1.4.10"]]

  :plugins [[lein-garden "0.3.0"]
            [lein-shell "0.5.0"]
            [com.github.ljpengelen/lein-hash-assets "1.0.0"]]

  :min-lein-version "2.9.0"

  :source-paths ["src/clj" "src/cljs"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"
                                    "resources/public/css"]

  :garden {:builds [{:id           "screen"
                     :source-paths ["src/clj"]
                     :stylesheet   top10.css/screen
                     :compiler     {:output-to     "resources/public/css/screen.css"
                                    :pretty-print? true}}]}

  :shell {:commands {"open"  {:windows         ["cmd" "/c" "start"]
                              :macosx          "open"
                              :linux           "xdg-open"}}}

  :hash-assets {:source-root "resources/public"
                :target-root "dist"
                :index "index.html"
                :files ["favicon.ico" "css/screen.css" "js/compiled/app.js"]}

  :aliases {"watch"        ["with-profile" "dev" "do"
                            ["run" "-m" "shadow.cljs.devtools.cli" "--npm" "watch" "app"]]
            "release"      ["with-profile" "prod" "do"
                            ["run" "-m" "shadow.cljs.devtools.cli" "--npm" "release" "app"]]
            "build-report" ["with-profile" "prod" "do"
                            ["run" "-m" "shadow.cljs.devtools.cli" "--npm" "run" "shadow.cljs.build-report" "app" "target/build-report.html"]
                            ["shell" "open" "target/build-report.html"]]}

  :profiles  {:dev {:dependencies [[day8.re-frame/re-frame-10x "1.9.9"]]}
              :prod {}})
