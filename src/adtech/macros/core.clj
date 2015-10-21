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
   "S = FS? { E <'.'> } E BU?
   WS = <#'\\s+'>
   BU = <WS> S
   FS = <'|'> { K <','> } K <WS>
   <K> = #'[-_:0-9a-zA-Z/*?<>]+'
   <E> = K | <'('> S <')'>"))

(defn- tree-has-filters?
  [t]
  (= :FS (first (nth t 1))))

(defn- tree-has-backup?
  [t]
  (= :BU (first (last t))))

(defn- extract-backup [t]
  (if (tree-has-backup? t)
    [(with-meta (butlast t) (meta t)) (clean-tree (nth (last t) 1))]
    [t nil]))

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
    (binding [*filters* nil] (resolve-path coll elem nil))
    elem))

(defn- ref? [x]
  (instance? clojure.lang.IDeref x))

(defn- normalize-value [v]
  (cond (or (keyword? v) (symbol? v))
        (name v)
        :else
        (str v)))

(defn- return-missing
  [coll tree backup default]
  (if backup
    (apply-filters
     tree
     (normalize-value (resolve-path coll backup default)))
    default))

(defn- resolve-path
  ([coll tree default]
   (let [[tree bu] (extract-backup tree)]
     (loop [sub-coll coll els tree]
       (if (and sub-coll (seq els))
         (let [el (resolve-path-elem coll (first els) default)]
           (cond (nil? el)
                 (return-missing coll tree bu default)
                 (sequential? sub-coll)
                 (let [idx (try (Integer/parseInt el)
                                (catch NumberFormatException e (return-missing coll tree bu default)))]
                   (if (contains? sub-coll idx)
                     (recur (get sub-coll (Integer/parseInt el)) (rest els))
                     (return-missing coll tree bu default)))
                 (map? sub-coll)
                 (let [el-kw (keyword el)]
                   (cond (contains? sub-coll el-kw)
                         (recur (get sub-coll el-kw) (rest els))
                         (contains? sub-coll el)
                         (recur (get sub-coll el) (rest els))
                         :else
                         (return-missing coll tree bu default)))
                 :else
                 (return-missing coll tree bu default)))
         (if (or (sequential? sub-coll) (map? sub-coll))
           (return-missing coll tree bu default)
           (apply-filters tree (normalize-value sub-coll))))))))

(defn render
  ([s coll] (render s coll ""))
  ([s coll default]
   (string/replace s #"\$\{\s*([^}]+?)\s*\}"
                   (fn [[_ k]] (resolve-path coll (parse-path k) default)))))
