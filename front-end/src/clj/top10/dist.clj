(ns top10.dist (:require [clojure.java.io :as io]
                         [clojure.string :as str]
                         [digest]))

(defn hash-files [source-root files]
  (loop [files (seq files)
         result (list)]
    (if-not files
      result
      (let [source (first files)
            content (slurp (str source-root "/" source))
            hash (digest/md5 content)
            target (str/replace-first source "." (str "-" hash "."))]
        (recur (next files) (conj result [source target]))))))

(defn replace-all [content replacements]
  (reduce (fn [s [match replacement]] (str/replace s match replacement)) content replacements))

(defn dist
  [raw-args]
  (let [{:keys [source-root target-root index files]} (read-string raw-args)
        hashed-files (hash-files source-root files)
        index-content (slurp (str source-root "/" index))
        target-index (str target-root "/" index)
        target-index-content (replace-all index-content hashed-files)]
    (io/make-parents target-index)
    (spit target-index target-index-content)
    (doseq [[original hashed] hashed-files
            :let [original-path (str source-root "/" original)]
            :let [hashed-path (str target-root "/" hashed)]]
      (io/make-parents hashed-path)
      (io/copy (io/file original-path) (io/file hashed-path)))))
