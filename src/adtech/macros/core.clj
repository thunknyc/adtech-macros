(ns adtech.macros.core
  (:require [clojure.string :as string]))

(defn- string->path
  [path coll]
  [(keyword path)])

(defn- path-elements
  [path]
  (string/split path #"\."))

(defn- get-path
  ([coll path] (get-path coll path nil))
  ([coll path default]
   (let [els (path-elements path)]
     (loop [coll coll els els]
       (if (and coll (seq els))
         (let [el (first els)]
           (cond (sequential? coll)
                 (let [idx (try (Integer/parseInt el)
                                (catch NumberFormatException e default))]
                   (if (contains? coll idx)
                     (recur (get coll (Integer/parseInt el)) (rest els))
                     default))
                 (map? coll)
                 (let [el-kw (keyword el)]
                   (cond (contains? coll el-kw)
                         (recur (get coll el-kw) (rest els))
                         (contains? coll el)
                         (recur (get coll el) (rest els))
                         :else
                         default))
                 :else
                 default))
         (if (or (sequential? coll) (map? coll))
           default
           coll))))))

(defn render
  ([s coll] (render s coll ""))
  ([s coll default]
   (string/replace s #"\$\{\s*([^}]+?)\s*\}"
                   (fn [[_ k]] (str (get-path coll k  default))))))
