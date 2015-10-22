(ns adtech.macros.core
  (:require [clojure.string :as string]
            [instaparse.core :refer [parser]]))

(def ^:dynamic *registered-filters*
  {:upper string/upper-case
   :lower string/lower-case
   :capitalize string/capitalize})

(def ^:dynamic *filters* [#(java.net.URLEncoder/encode %)])

(def path-parser
  (parser
   "S = FS? { E <'.'> } E BU?
   WS = <#'\\s+'>
   BU = <WS> S
   FS = <'|'> { K <','> } K <WS>
   <K> = #'[-_:0-9a-zA-Z/*?<>]+'
   <E> = K | <'('> S <')'>"))

(defn- macro-spec?
  [t]
  (and (vector? t) (> (count t) 0) (= :S (nth t 0))))

(defn- backup-spec?
  [t]
  (and (vector? t) (> (count t) 0) (= :BU (nth t 0))))

(defn- filters-spec?
  [t]
  (and (vector? t) (> (count t) 0) (= :FS (nth t 0))))

(declare ^:private make-macro-spec)

(defn- make-backup-spec
  [coll]
  (make-macro-spec (nth coll 1)))

(defn- make-filters-spec
  [coll]
  (map keyword (rest coll)))

(defn- make-macro-spec
  [t]
  (if (macro-spec? t)
    (loop [t (rest t) spec {}]
      (if (seq t)
        (let [el (first t)]
          (cond (backup-spec? el)
                (recur (rest t) (assoc spec :backup (make-backup-spec el)))
                (filters-spec? el)
                (recur (rest t) (assoc spec :filters (make-filters-spec el)))
                (macro-spec? el)
                (recur (rest t) (update-in spec [:path] (fnil conj []) (make-macro-spec el)))
                (string? el)
                (recur (rest t) (update-in spec [:path] (fnil conj []) el))))
        spec))))

(declare ^:private replace-macro)

(defn- cleanse
  [v]
  (cond (string? v)
        v
        (or (keyword? v) (symbol? v))
        (name v)
        (not (or (map? v) (sequential? v)))
        (str v)
        :else
        nil))

(defn- filter-value
  [fs v]
  (reduce (fn [v f] (f v))
          v
          (->> (concat fs *filters*)
               (map #(or (*registered-filters* %) %)))))

(defn- finish-value
  [filters coll value missing backup]
  (if-let [v (cleanse value)]
    (filter-value filters v)
    (if backup
      (replace-macro (assoc backup :filters filters) coll missing)
      (filter-value filters missing))))

(defn- replace-macro
  [{:keys [filters backup path] :as tree} coll missing]
  (loop [path path subcoll coll]
    (if (seq path)
      (let [raw-el (first path)
            el (if (map? raw-el)
                 (binding [*filters* nil] (replace-macro raw-el coll nil))
                 raw-el)
            val (cond (nil? el)
                      nil
                      (map? subcoll)
                      (or (get subcoll (keyword el))
                          (get subcoll el))
                      (sequential? subcoll)
                      (let [idx (try (Integer/parseInt el)
                                     (catch NumberFormatException e
                                       nil))]
                        (when (contains? subcoll idx)
                          (nth subcoll idx)))
                      :else
                      nil)]
        (if val
          (recur (rest path) val)
          (finish-value filters coll val missing backup)))
      (finish-value filters coll subcoll missing backup))))

(defn render
  ([s coll] (render s coll ""))
  ([s coll missing]
   (string/replace
    s
    #"\$\{\s*([^}]+?)\s*\}"
    (fn [[_ macro]]
      (-> (path-parser macro)
          make-macro-spec
          (replace-macro coll missing))))))
