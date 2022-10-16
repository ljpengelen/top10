# Top 10 - Front end

A [re-frame](https://github.com/day8/re-frame) application designed to get to know people's musical taste.

## Getting Started

### Environment Setup

1. Install [JDK 8 or later](https://openjdk.java.net/install/) (Java Development Kit)
2. Install [Leiningen](https://leiningen.org/#install) (Clojure/ClojureScript project task & dependency management)
3. Install [Node.js](https://nodejs.org/) (JavaScript runtime environment), which should include
   [NPM](https://docs.npmjs.com/cli/npm).

### Browser Setup

Browser caching should be disabled when developer tools are open to prevent interference with [`shadow-cljs`](https://github.com/thheller/shadow-cljs) hot reloading.

Custom formatters must be enabled in the browser before [CLJS DevTools](https://github.com/binaryage/cljs-devtools) can display ClojureScript data in the console in a more readable way.

#### Chrome/Chromium

1. Open [DevTools](https://developers.google.com/web/tools/chrome-devtools/) (Linux/Windows: `F12` or `Ctrl-Shift-I`; macOS: `⌘-Option-I`)
2. Open DevTools Settings (Linux/Windows: `?` or `F1`; macOS: `?` or `Fn+F1`)
3. Select `Preferences` in the navigation menu on the left, if it is not already selected
4. Under the `Network` heading, enable the `Disable cache (while DevTools is open)` option
5. Under the `Console` heading, enable the `Enable custom formatters` option

#### Firefox

1. Open [Developer Tools](https://developer.mozilla.org/en-US/docs/Tools) (Linux/Windows: `F12` or `Ctrl-Shift-I`; macOS: `⌘-Option-I`)
2. Open [Developer Tools Settings](https://developer.mozilla.org/en-US/docs/Tools/Settings) (Linux/macOS/Windows: `F1`)
3. Under the `Advanced settings` heading, enable the `Disable HTTP Cache (when toolbox is open)` option

Unfortunately, Firefox does not yet support custom formatters in their devtools. For updates, follow the enhancement request in their bug tracker:
[1262914 - Add support for Custom Formatters in devtools](https://bugzilla.mozilla.org/show_bug.cgi?id=1262914).

## Development

### Running the App

Start a temporary local web server, build the app with the `dev` profile, and serve the app, browser test runner and karma test runner with hot reload:

```sh
lein watch
```

Please be patient; it may take over 20 seconds to see any output, and over 40 seconds to complete.

When `[:app] Build completed` appears in the output, browse to [http://localhost:9500/](http://localhost:9500/).

[`shadow-cljs`](https://github.com/thheller/shadow-cljs) will automatically push ClojureScript code changes to your browser on save.
To prevent a few common issues, see [Hot Reload in ClojureScript: Things to avoid](https://code.thheller.com/blog/shadow-cljs/2019/08/25/hot-reload-in-clojurescript.html#things-to-avoid).

Opening the app in your browser starts a [ClojureScript browser REPL](https://clojurescript.org/reference/repl#using-the-browser-as-an-evaluation-environment),
to which you may now connect.

#### Connecting to the browser REPL from your editor

See [Shadow CLJS User's Guide: Editor Integration](https://shadow-cljs.github.io/docs/UsersGuide.html#_editor_integration).
Note that `lein watch` runs `shadow-cljs watch` for you, and that this project's running build ID is `app`, or the keyword `:app` in a Clojure context.

Alternatively, search the web for info on connecting to a `shadow-cljs` ClojureScript browser REPL from your editor and configuration.

For example, in Vim / Neovim with `fireplace.vim`
1. Open a `.cljs` file in the project to activate `fireplace.vim`
2. In normal mode, execute the `Piggieback` command with this project's running build id, `:app`:
    ```vim
    :Piggieback :app
    ```

#### Connecting to the browser REPL from a terminal

1. Connect to the `shadow-cljs` nREPL:
    ```sh
    lein repl :connect localhost:8777
    ```
    The REPL prompt, `shadow.user=>`, indicates that is a Clojure REPL, not ClojureScript.

2. In the REPL, switch the session to this project's running build id, `:app`:
    ```clj
    (shadow.cljs.devtools.api/nrepl-select :app)
    ```
    The REPL prompt changes to `cljs.user=>`, indicating that this is now a ClojureScript REPL.
3. See [`user.cljs`](dev/cljs/user.cljs) for symbols that are immediately accessible in the REPL
without needing to `require`.

### Compiling CSS with `lein-garden`

Use Clojure and [Garden](https://github.com/noprompt/garden) to edit styles in `.clj` files located in the [`src/clj/front_end_v2/`](src/clj/front_end_v2/) directory. CSS files are compiled automatically on [`dev`](#running-the-app) or [`prod`](#production) build.

Manually compile CSS files:
```sh
lein garden once
```

The `resources/public/css/` directory is created, containing the compiled CSS files.

#### Compiling CSS with Garden on change

Enable automatic compiling of CSS files when source `.clj` files are changed:
```sh
lein garden auto
```

### Running `shadow-cljs` Actions

See a list of [`shadow-cljs CLI`](https://shadow-cljs.github.io/docs/UsersGuide.html#_command_line)
actions:
```sh
lein run -m shadow.cljs.devtools.cli --help
```

Please be patient; it may take over 10 seconds to see any output. Also note that some actions shown may not actually be supported, outputting "Unknown action." when run.

Run a shadow-cljs action on this project's build id (without the colon, just `app`):
```sh
lein run -m shadow.cljs.devtools.cli <action> app
```
### Debug Logging

The `debug?` variable in [`config.cljs`](src/cljs/front_end_v2/config.cljs) defaults to `true` in [`dev`](#running-the-app) builds, and `false` in [`prod`](#production) builds.

Use `debug?` for logging or other tasks that should run only on `dev` builds:

```clj
(ns top10.example
  (:require [top10.config :as config])

(when config/debug?
  (println "This message will appear in the browser console only on dev builds."))
```

## Production

Build the app with the `prod` profile:

```sh
lein release
```

Please be patient; it may take over 15 seconds to see any output, and over 30 seconds to complete.

The `resources/public/js/compiled` directory is created, containing the compiled `app.js` and `manifest.edn` files.

The [`resources/public`](resources/public/) directory contains the complete, production web front end of your app.

Always inspect the `resources/public/js/compiled` directory prior to deploying the app.
Running any `lein` alias in this project after `lein watch` will, at the very least, run `lein clean`, which deletes this generated directory.
Further, running `lein watch` will generate many, much larger development versions of the files in this directory.
