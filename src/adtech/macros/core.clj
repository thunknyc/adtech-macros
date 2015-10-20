(ns adtech.macros.core
  (:require [clojure.string :as string]
            [clojure.zip :as zip]
            [instaparse.core :refer [parser]]))

(def ^:dynamic *registered-filters*
  {:upper string/upper-case
   :lower string/lower-case
   :capitalize string/capitalize})

(def ^:dynamic *filters* [#(java.net.URLEncoder/encode %)])

(defn- apply-filters
  [tree v]
  (let [tree-filters (:filters (meta tree))
        filters (->> (concat tree-filters *filters*)
                     (map #(or (*registered-filters* %) %)))]
    (reduce #(%2 %1) v filters)))

(defn- string->path
  [path coll]
  [(keyword path)])

(def path-parser
  (parser
   "S = FS? { E <'.'> } E
   FS = <'|'> { K <','> } K <#'\\s+'>
   <K> = #'[-_:0-9a-zA-Z/*?<>]+'
   <E> = K | <'('> S <')'>"))

(defn- tree-has-filters?
  [t]
  (= :FS (first (get t 1))))

(defn- tree-filters
  [t]
  (->> (get t 1)
       (rest)
       (map keyword)))

(defn- tree?
  [t]
  (and (vector? t) (= :S (first t))))

(defn- clean-tree
  [t]
  (cond (not (tree? t))
        t
        (tree-has-filters? t)
        (with-meta (map clean-tree (nthrest t 2)) {:filters (tree-filters t)})
        :else
        (map clean-tree (rest t))))

(defn- parse-path
  [path]
  (when path
    (let [tree (path-parser path)]
      (clean-tree tree))))

(declare ^:private resolve-path)

(defn- resolve-path-elem
  [coll elem default]
  (if (sequential? elem)
    (let [subpath (binding [*filters* nil] (resolve-path coll elem nil))]
      (ref (resolve-path coll (parse-path subpath) default)))
    elem))

(defn- ref? [x]
  (instance? clojure.lang.IDeref x))

(defn- normalize-value [v]
  (cond (or (keyword? v) (symbol? v))
        (name v)
        :else
        v))

(defn- resolve-path
  ([coll tree default]
   (loop [sub-coll coll els tree]
     (if (and sub-coll (seq els))
       (let [el (resolve-path-elem coll (first els) default)]
         (cond (nil? el)
               default
               (ref? el)
               (recur sub-coll (cons @el (rest els)))
               (sequential? sub-coll)
               (let [idx (try (Integer/parseInt el)
                              (catch NumberFormatException e default))]
                 (if (contains? sub-coll idx)
                   (recur (get sub-coll (Integer/parseInt el)) (rest els))
                   default))
               (map? sub-coll)
               (let [el-kw (keyword el)]
                 (cond (contains? sub-coll el-kw)
                       (recur (get sub-coll el-kw) (rest els))
                       (contains? sub-coll el)
                       (recur (get sub-coll el) (rest els))
                       :else
                       default))
               :else
               default))
       (if (or (sequential? sub-coll) (map? sub-coll))
         default
         (apply-filters tree (str (normalize-value sub-coll))))))))

(defn render
  ([s coll] (render s coll ""))
  ([s coll default]
   (string/replace s #"\$\{\s*([^}]+?)\s*\}"
                   (fn [[_ k]] (resolve-path coll (parse-path k) default)))))

