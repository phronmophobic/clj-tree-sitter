(ns com.phronemophobic.tree-sitter.raw
  (:require [com.phronemophobic.clong.gen.jna :as gen]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [com.rpl.specter :as specter])
  (:import java.lang.ref.Cleaner)
  (:gen-class))

(def cleaner (Cleaner/create))

(defn ^:private write-edn [w obj]
  (binding [*print-length* nil
            *print-level* nil
            *print-dup* false
            *print-meta* false
            *print-readably* true

            ;; namespaced maps not part of edn spec
            *print-namespace-maps* false

            *out* w]
    (pr obj)))

(def lib-options
  {com.sun.jna.Library/OPTION_STRING_ENCODING "UTF8"})
(def ^:no-doc lib-tree-sitter
  (com.sun.jna.NativeLibrary/getInstance "tree-sitter" lib-options))

(defn ^:private dump-api []
  (let [outf (io/file
              "resources"
              "com"
              "phronemophobic"
              "tree-sitter"
              "api.edn")]
    (.mkdirs (.getParentFile outf))
    (with-open [w (io/writer outf)]
      (write-edn w
                 ((requiring-resolve 'com.phronemophobic.clong.clang/easy-api)
                  "/Users/adrian/workspace/tree-sitter/lib/include/tree_sitter/api.h")
                 ))))


(def api
  ((requiring-resolve 'com.phronemophobic.clong.clang/easy-api) "/Users/adrian/workspace/tree-sitter/lib/include/tree_sitter/api.h")
  #_(with-open [rdr (io/reader
                     (io/resource
                      "com/phronemophobic/tree-sitter/api.edn"))
                rdr (java.io.PushbackReader. rdr)]
      (edn/read rdr)))


(gen/def-api lib-tree-sitter api)

(let [struct-prefix (gen/ns-struct-prefix *ns*)]
  (defmacro import-structs! []
    `(gen/import-structs! api ~struct-prefix)))
