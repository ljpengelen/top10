# Front end

## Prerequisites

- Java 8 or newer
- Leiningen
- Chrome

## Development mode

To start the Figwheel compiler, navigate to the project folder and run `lein fig:build`.
Figwheel will automatically push changes to the browser.
Once Figwheel starts up, the app is opened in the browser.

## Running tests

To run all tests once, execute `lein fig:test`.
After executing `lein fig:build`, the tests are watched and ran on each change.
A page with a summary of the test results is available at http://localhost:9500/figwheel-extra-main/auto-testing.

### Building for production

To create a production build, execute `lein fig:min`.
