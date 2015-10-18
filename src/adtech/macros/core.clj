(ns adtech.macros.core
  (:require [clojure.string :as string]
            [clojure.zip :as zip]
            [instaparse.core :refer [parser]]))

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
  (let [tree (path-parser path)]
    (clean-tree tree)))

(declare ^:private resolve-path)

(defn- resolve-path-elem
  [coll elem]
  (if (sequential? elem)
    (binding [*filters* []]
      (let [parsed (->> (resolve-path coll elem nil)
                        parse-path)]
        (if (= 1 (count parsed))
          (resolve-path-elem coll (first parsed))
          (resolve-path-elem coll parsed))))
    elem))

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

(defn- resolve-path
  ([coll tree default]
   (loop [sub-coll coll els tree]
     (if (and sub-coll (seq els))
       (let [el (resolve-path-elem coll (first els))]
         (cond (nil? el)
               default
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
         (apply-filters tree (str sub-coll)))))))

(defn render
  ([s coll] (render s coll ""))
  ([s coll default]
   (string/replace s #"\$\{\s*([^}]+?)\s*\}"
                   (fn [[_ k]] (resolve-path coll (parse-path k) default)))))

