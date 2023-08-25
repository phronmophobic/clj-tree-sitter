(ns com.phronemophobic.tree-sitter
  (:require [com.phronemophobic.tree-sitter.raw :as raw])
  (:import java.lang.ref.Cleaner
           com.sun.jna.Memory
           com.sun.jna.Pointer
           com.sun.jna.ptr.IntByReference
           com.sun.jna.ptr.FloatByReference
           com.sun.jna.CallbackReference
           com.sun.jna.Structure))

(raw/import-structs!)
(defonce cleaner (delay (Cleaner/create)))

(def ^:no-doc lib-json-tree-sitter
  (com.sun.jna.NativeLibrary/getInstance "tree-sitter-json"))

(def ^:private tree_sitter_json
  (.getFunction ^com.sun.jna.NativeLibrary lib-json-tree-sitter
                "tree_sitter_json"))

(defn tree-sitter-json []
  (.invoke tree_sitter_json Pointer (into-array [])))


(def ^:no-doc lib-clojure-tree-sitter
  (com.sun.jna.NativeLibrary/getInstance "tree-sitter-clojure"))

(def ^:private tree_sitter_clojure
  (.getFunction ^com.sun.jna.NativeLibrary lib-clojure-tree-sitter
                "tree_sitter_clojure"))

(defn tree-sitter-clojure []
  (.invoke tree_sitter_clojure Pointer (into-array [])))


(comment
  ;; TSParser *parser = ts_parser_new();
  (def parser (raw/ts_parser_new))

  ;; // Set the parser's language (JSON in this case).
  ;;   ts_parser_set_language(parser, tree_sitter_json());
  (raw/ts_parser_set_language parser (tree-sitter-json))

  ;; // Build a syntax tree based on source code stored in a string.
  ;;   const char *source_code = "[1, null]";
  ;;   TSTree *tree = ts_parser_parse_string(
  ;;     parser,
  ;;     NULL,
  ;;     source_code,
  ;;     strlen(source_code)
  ;;   );
  (def source-code "[1, null]")
  (def tree (raw/ts_parser_parse_string parser nil source-code (count source-code)))

  ;; // Get the root node of the syntax tree.
  ;;   TSNode root_node = ts_tree_root_node(tree);
  (def root-node (raw/ts_tree_root_node tree))

  ;; // Get some child nodes.
  ;;   TSNode array_node = ts_node_named_child(root_node, 0);
  ;;   TSNode number_node = ts_node_named_child(array_node, 0);
  (def array-node (raw/ts_node_named_child root-node 0))
  (def number-node (raw/ts_node_named_child array-node 0))

  ;; // Check that the nodes have the expected types.
  ;;   assert(strcmp(ts_node_type(root_node), "document") == 0);
  ;;   assert(strcmp(ts_node_type(array_node), "array") == 0);
  ;;   assert(strcmp(ts_node_type(number_node), "number") == 0);
  (raw/ts_node_type root-node)   ;; "document"
  (raw/ts_node_type array-node)  ;; "array" 
  (raw/ts_node_type number-node) ;; "number"



  (raw/ts_node_child_count root-node)   ;; 1
  (raw/ts_node_child_count array-node)  ;; 5
  (raw/ts_node_child_count number-node) ;; 0

  ;; // Print the syntax tree as an S-expression.
  ;; char *string = ts_node_string(root_node);
  ;; printf("Syntax tree: %s\n", string);
  (raw/ts_node_string root-node) ;; "(document (array (number) (null)))"

  ;; // Free all of the heap-allocated memory.
  ;;   free(string);
  ;;   ts_tree_delete(tree);
  ;;   ts_parser_delete(parser);
  ;;   return 0;
  ;; memory handling should happen via cleaners.

  ,)



(def ^:private main-class-loader @clojure.lang.Compiler/LOADER)
(deftype GenericCallback [return-type parameter-types callback]
  com.sun.jna.CallbackProxy
  (getParameterTypes [_]
    (into-array Class parameter-types))
  (getReturnType [_]
    return-type)
  (callback [_ args]
    (.setContextClassLoader (Thread/currentThread) main-class-loader)

    (import 'com.sun.jna.Native)
    ;; https://java-native-access.github.io/jna/4.2.1/com/sun/jna/Native.html#detach-boolean-
    ;; for some other info search https://java-native-access.github.io/jna/4.2.1/ for CallbackThreadInitializer

    ;; turning off detach here might give a performance benefit,
    ;; but more importantly, it prevents jna from spamming stdout
    ;; with "JNA: could not detach thread"
    (com.sun.jna.Native/detach false)
      (prn (vec args))
    (let [ret (apply callback args)]

      ;; need turn detach back on so that
      ;; we don't prevent the jvm exiting
      ;; now that we're done
      (try
        (com.sun.jna.Native/detach true)
        (catch IllegalStateException e
          nil))
      ret)))


(def source-code "{ 
\"a\": [42]
} ")
(def source-bytes (.getBytes source-code "utf-8"))
(def source-mem
  (doto (Memory. (alength source-bytes))
    (.write 0 source-bytes 0 (alength source-bytes))))



(defn read* [_ byte-offset pos* bytes-read]
  
  (let [pos (doto (Structure/newInstance TSPoint pos*)
              (.read))]
    (prn pos)
    (if (zero? byte-offset)
      (do
        (.setInt bytes-read 0 (alength source-bytes))
        source-mem)
      ;; else
      (do
        (.setInt bytes-read 0 0)
        nil))))

(def read-callback
  (->GenericCallback
   Pointer [Pointer Integer/TYPE TSPoint Pointer]
   read*))


(def input (doto (TSInput.)
   (.writeField "read" (CallbackReference/getFunctionPointer read-callback))
   (.writeField "encoding" raw/TSInputEncodingUTF8)
   ))

(comment
  (def tree (raw/ts_parser_parse parser nil input))


  (def root-node (raw/ts_tree_root_node tree))
  (raw/ts_node_string root-node)

  ,)
  


(comment
  ;; TSParser *parser = ts_parser_new();
  (def parser (raw/ts_parser_new))

  ;; // Set the parser's language (JSON in this case).
  ;;   ts_parser_set_language(parser, tree_sitter_json());
  (raw/ts_parser_set_language parser (tree-sitter-clojure))

  ;; // Build a syntax tree based on source code stored in a string.
  ;;   const char *source_code = "[1, null]";
  ;;   TSTree *tree = ts_parser_parse_string(
  ;;     parser,
  ;;     NULL,
  ;;     source_code,
  ;;     strlen(source_code)
  ;;   );
  (def source-code (slurp "deps.edn"))
  (def tree (raw/ts_parser_parse_string parser nil source-code (count source-code)))

  ;; // Get the root node of the syntax tree.
  ;;   TSNode root_node = ts_tree_root_node(tree);
  (def root-node (raw/ts_tree_root_node tree))
  
  (clojure.pprint/pprint
   (raw/ts_node_string root-node)) 

  ,)
