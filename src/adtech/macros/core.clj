(ns adtech.macros.core
  (:require [clojure.string :as string]
            [clojure.zip :as zip]
            [instaparse.core :refer [parser]]))

(defn- string->path
  [path coll]
  [(keyword path)])

(def path-parser
  (parser
   "S = { E <'.'> } E
   <K> = #'[-_:0-9a-zA-Z]+'
   <E> = K | <'('> S <')'>"))

(defn- clean-tree
  [t]
  (let [elems (rest t)]
    (for [el elems]
      (if (vector? el) (clean-tree el) el))))

(defn- path-tree
  [path]
  (let [tree (path-parser path)]
    (clean-tree tree)))

(declare ^:private get-path)

(defn- resolve-path-elem
  [coll elem]
  (if (sequential? elem) (get-path coll elem) elem))

(defn- get-path
  ([coll tree] (get-path coll tree nil))
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
         sub-coll)))))

(defn render
  ([s coll] (render s coll ""))
  ([s coll default]
   (string/replace s #"\$\{\s*([^}]+?)\s*\}"
                   (fn [[_ k]] (str (get-path coll (path-tree k) default))))))
